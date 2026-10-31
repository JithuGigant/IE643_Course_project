package com.ie643.cxr.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** 8-bit grayscale image, row-major. */
class Gray(val w: Int, val h: Int, val px: ByteArray) {
    fun at(x: Int, y: Int): Int = px[y * w + x].toInt() and 0xFF
    fun copy(): Gray = Gray(w, h, px.copyOf())
}

/**
 * Image pipeline for uploaded X-rays. Mirrors the training data preparation:
 *   crop -> grayscale -> longer side to 512 (area averaging, like cv2.INTER_AREA)
 *   -> global histogram equalisation of the content -> centred on a black 512x512 canvas.
 * The 384 px resize and the 0.5/0.5 normalisation happen inside encoder.onnx.
 * Pure Kotlin (no Android classes) so it can be unit-tested on a normal JVM.
 */
object Prep {
    const val SIZE = 512

    /** OpenCV's fixed-point BT.601 luma: (R*4899 + G*9617 + B*1868 + 8192) >> 14. */
    fun argbToGray(argb: IntArray, w: Int, h: Int): Gray {
        val out = ByteArray(w * h)
        for (i in 0 until w * h) {
            val c = argb[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            out[i] = ((r * 4899 + g * 9617 + b * 1868 + 8192) shr 14).toByte()
        }
        return Gray(w, h, out)
    }

    /** Mean of (max channel - min channel): 0 for a grayscale image. */
    fun meanSaturation(argb: IntArray): Double {
        var s = 0L
        for (c in argb) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            s += max(r, max(g, b)) - min(r, min(g, b))
        }
        return s.toDouble() / argb.size
    }

    private fun flatLine(g: Gray, fixed: Int, from: Int, to: Int, isRow: Boolean): Boolean {
        var mn = 255; var mx = 0; var sum = 0L
        for (k in from until to) {
            val v = if (isRow) g.at(k, fixed) else g.at(fixed, k)
            if (v < mn) mn = v
            if (v > mx) mx = v
            sum += v
        }
        val n = (to - from).coerceAtLeast(1)
        val mean = sum.toDouble() / n
        return (mx - mn) < 12 && (mean < 12.0 || mean > 243.0)
    }

    /** Edge rows/columns that are flat black or flat white (borders, letterbox). Returns [top, bottom, left, right). */
    fun autoTrim(g: Gray, maxFrac: Double = 0.25): IntArray {
        val h = g.h; val w = g.w
        var t = 0; var b = h; var l = 0; var r = w
        val limH = (h * maxFrac).toInt(); val limW = (w * maxFrac).toInt()
        while (t < limH && flatLine(g, t, l, r, true)) t++
        while ((h - b) < limH && flatLine(g, b - 1, l, r, true)) b--
        while (l < limW && flatLine(g, l, t, b, false)) l++
        while ((w - r) < limW && flatLine(g, r - 1, t, b, false)) r--
        return intArrayOf(t, b, l, r)
    }

    fun crop(g: Gray, t: Int, b: Int, l: Int, r: Int): Gray {
        val nw = r - l; val nh = b - t
        val out = ByteArray(nw * nh)
        for (y in 0 until nh) System.arraycopy(g.px, (t + y) * g.w + l, out, y * nw, nw)
        return Gray(nw, nh, out)
    }

    fun invert(g: Gray): Gray {
        val out = ByteArray(g.px.size)
        for (i in out.indices) out[i] = (255 - (g.px[i].toInt() and 0xFF)).toByte()
        return Gray(g.w, g.h, out)
    }

    private class Weights(val start: IntArray, val w: Array<DoubleArray>)

    /** Exact fractional-coverage weights (what cv2.INTER_AREA does, in both directions). */
    private fun areaWeights(nSrc: Int, nDst: Int): Weights {
        val scale = nSrc.toDouble() / nDst
        val starts = IntArray(nDst)
        val ws = Array(nDst) { DoubleArray(0) }
        for (i in 0 until nDst) {
            val a = i * scale; val b = (i + 1) * scale
            val s = floor(a + 1e-9).toInt(); val e = ceil(b - 1e-9).toInt()
            val list = ArrayList<Double>()
            var first = -1
            for (j in s until e) {
                val ov = min(b, (j + 1).toDouble()) - max(a, j.toDouble())
                if (ov > 1e-9) { if (first < 0) first = j; list.add(ov) }
            }
            var sum = 0.0
            for (v in list) sum += v
            starts[i] = if (first < 0) s else first
            ws[i] = DoubleArray(list.size) { list[it] / sum }
        }
        return Weights(starts, ws)
    }

    fun resizeArea(g: Gray, nh: Int, nw: Int): Gray {
        if (g.w == nw && g.h == nh) return g.copy()
        val wx = areaWeights(g.w, nw); val wy = areaWeights(g.h, nh)
        val tmp = DoubleArray(g.h * nw)
        for (y in 0 until g.h) {
            val row = y * g.w
            for (x in 0 until nw) {
                val s = wx.start[x]; val ws = wx.w[x]
                var acc = 0.0
                for (k in ws.indices) acc += (g.px[row + s + k].toInt() and 0xFF) * ws[k]
                tmp[y * nw + x] = acc
            }
        }
        val out = ByteArray(nh * nw)
        for (y in 0 until nh) {
            val s = wy.start[y]; val ws = wy.w[y]
            for (x in 0 until nw) {
                var acc = 0.0
                for (k in ws.indices) acc += tmp[(s + k) * nw + x] * ws[k]
                val v = floor(acc + 0.5).toInt().coerceIn(0, 255)
                out[y * nw + x] = v.toByte()
            }
        }
        return Gray(nw, nh, out)
    }

    /** Global histogram equalisation, same look-up table as cv2.equalizeHist. */
    fun equalize(g: Gray): Gray {
        val hist = LongArray(256)
        for (b in g.px) hist[b.toInt() and 0xFF]++
        val total = g.px.size.toLong()
        var first = 0
        while (first < 256 && hist[first] == 0L) first++
        if (first >= 256 || hist[first] == total) return g.copy()
        val scale = 255.0 / (total - hist[first])
        val lut = IntArray(256)
        var s = 0L
        for (i in first until 256) {
            s += hist[i]
            val v = if (i > first) floor((s - hist[first]) * scale + 0.5).toInt() else 0
            lut[i] = v.coerceIn(0, 255)
        }
        val out = ByteArray(g.px.size)
        for (i in out.indices) out[i] = lut[g.px[i].toInt() and 0xFF].toByte()
        return Gray(g.w, g.h, out)
    }

    class Result(val canvas: ByteArray, val contentPre: Gray, val contentHe: Gray)

    /** crop (grayscale) -> canvas of exactly what the encoder receives. */
    fun pipeline(crop: Gray, he: Boolean): Result {
        val s = SIZE.toDouble() / max(crop.h, crop.w)
        val nh = max(floor(crop.h * s + 0.5).toInt(), 1)
        val nw = max(floor(crop.w * s + 0.5).toInt(), 1)
        val pre = resizeArea(crop, nh, nw)
        val post = if (he) equalize(pre) else pre
        val canvas = ByteArray(SIZE * SIZE)
        val y0 = (SIZE - nh) / 2; val x0 = (SIZE - nw) / 2
        for (y in 0 until nh) System.arraycopy(post.px, y * nw, canvas, (y0 + y) * SIZE + x0, nw)
        return Result(canvas, pre, post)
    }
}
