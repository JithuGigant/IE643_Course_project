package com.ie643.cxr.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class Level { HINT, WARN, BLOCK }

/** One input-quality finding. [action] is "invert", "rotate" or null. */
data class Finding(val id: String, val level: Level, val title: String, val detail: String, val action: String? = null)

/**
 * Thresholds. Calibrated on the 24 sample X-rays, on blurred / dark / inverted / rotated / noisy / JPEG /
 * low-resolution / camera-like copies of them, on synthetic non-X-ray images, and (plausibility) on the
 * validation features. Heuristics only: they have not been tested on real camera photos.
 */
data class CheckConfig(
    val resBlock: Int = 256, val resWarn: Int = 384,
    val satBlock: Double = 18.0, val satWarn: Double = 4.0,
    val lapBlur: Double = 40.0, val lapNoise: Double = 3000.0,
    val meanLo: Double = 25.0, val meanHi: Double = 230.0, val rngMin: Double = 45.0, val sat255Max: Double = 0.30,
    val rngHint: Double = 90.0,
    val invDelta: Double = 30.0, val rotDelta: Double = 0.30, val aspectMax: Double = 1.5,
    val d2Warn: Double = 324.0, val d2Block: Double = 1500.0,
)

data class Metrics(
    val lapvar: Double, val mean: Double, val p1: Double, val p99: Double, val rng: Double, val sat255: Double,
    val corners: Double, val center: Double, val symV: Double, val symH: Double, val aspect: Double,
)

object Checks {
    private fun percentile(hist: IntArray, n: Int, q: Double): Double {
        val pos = q / 100.0 * (n - 1)
        val lo = floor(pos).toInt()
        val frac = pos - lo
        fun valueAtRank(k: Int): Int {
            var cum = 0
            for (v in 0 until 256) { cum += hist[v]; if (cum > k) return v }
            return 255
        }
        val a = valueAtRank(lo)
        val b = valueAtRank(min(lo + 1, n - 1))
        return a + (b - a) * frac
    }

    private fun lapVar(c: Gray): Double {
        if (c.h < 3 || c.w < 3) return 0.0
        var sum = 0.0; var sum2 = 0.0; var n = 0L
        for (y in 1 until c.h - 1) for (x in 1 until c.w - 1) {
            val l = 4.0 * c.at(x, y) - c.at(x, y - 1) - c.at(x, y + 1) - c.at(x - 1, y) - c.at(x + 1, y)
            sum += l; sum2 += l * l; n++
        }
        val m = sum / n
        return sum2 / n - m * m
    }

    private fun corr(c: Gray, flipX: Boolean): Double {
        val n = c.w * c.h
        var ma = 0.0; var mb = 0.0
        for (y in 0 until c.h) for (x in 0 until c.w) {
            ma += c.at(x, y)
            mb += if (flipX) c.at(c.w - 1 - x, y) else c.at(x, c.h - 1 - y)
        }
        ma /= n; mb /= n
        var sab = 0.0; var saa = 0.0; var sbb = 0.0
        for (y in 0 until c.h) for (x in 0 until c.w) {
            val a = c.at(x, y) - ma
            val b = (if (flipX) c.at(c.w - 1 - x, y) else c.at(x, c.h - 1 - y)) - mb
            sab += a * b; saa += a * a; sbb += b * b
        }
        val d = sqrt(saa * sbb)
        return if (d > 1e-9) sab / d else 0.0
    }

    private fun meanRect(c: Gray, y0: Int, y1: Int, x0: Int, x1: Int): Double {
        var s = 0.0; var n = 0
        for (y in y0 until y1) for (x in x0 until x1) { s += c.at(x, y); n++ }
        return if (n == 0) 0.0 else s / n
    }

    fun metrics(c: Gray): Metrics {
        val h = c.h; val w = c.w
        val hist = IntArray(256)
        var sum = 0L; var sat = 0
        for (b in c.px) { val v = b.toInt() and 0xFF; hist[v]++; sum += v; if (v >= 250) sat++ }
        val n = h * w
        val ph = max(1, (0.1 * h).toInt()); val pw = max(1, (0.1 * w).toInt())
        val corners = (meanRect(c, 0, ph, 0, pw) + meanRect(c, 0, ph, w - pw, w) +
            meanRect(c, h - ph, h, 0, pw) + meanRect(c, h - ph, h, w - pw, w)) / 4.0
        val ch = (0.3 * h).toInt(); val cw = (0.3 * w).toInt()
        val center = meanRect(c, ch, h - ch, cw, w - cw)
        val p1 = percentile(hist, n, 1.0); val p99 = percentile(hist, n, 99.0)
        return Metrics(
            lapvar = lapVar(c), mean = sum.toDouble() / n, p1 = p1, p99 = p99, rng = p99 - p1,
            sat255 = sat.toDouble() / n, corners = corners, center = center,
            symV = corr(c, true), symH = corr(c, false), aspect = max(h, w).toDouble() / min(h, w),
        )
    }

    /**
     * All checks that do not need the encoder.
     * [cropLongSide]: long side of the cropped image in source pixels. [sat]: mean colour spread of the crop.
     */
    fun evaluate(cropLongSide: Int, sat: Double, camera: Boolean, pre: Gray, he: Gray, cfg: CheckConfig = CheckConfig()): List<Finding> {
        val mp = metrics(pre); val mh = metrics(he)
        val out = ArrayList<Finding>()
        if (cropLongSide < cfg.resBlock)
            out.add(Finding("lowres", Level.BLOCK, "Image too small",
                "Only $cropLongSide px on the long side; at least ${cfg.resBlock} px are needed."))
        else if (cropLongSide < cfg.resWarn)
            out.add(Finding("lowres", Level.WARN, "Low resolution",
                "$cropLongSide px on the long side, below the model's ${cfg.resWarn} px input. The image is enlarged and fine detail is lost."))
        if (sat >= cfg.satBlock)
            out.add(Finding("colour", Level.BLOCK, "Looks like a colour photograph", "A chest X-ray is grayscale. This image has strong colour."))
        else if (sat >= cfg.satWarn)
            out.add(Finding("tint", Level.WARN, "Coloured tint", "The image is slightly coloured (screenshot or tinted scan). It was converted to grayscale."))
        if (mh.lapvar < cfg.lapBlur)
            out.add(Finding("blurry", Level.WARN, "Looks blurry", "Edges are very soft. Small findings may be missed."))
        if (mh.lapvar > cfg.lapNoise)
            out.add(Finding("noisy", Level.WARN, "Looks very grainy", "Strong pixel noise can change the report."))
        if (mp.mean < cfg.meanLo || mp.mean > cfg.meanHi || mp.rng < cfg.rngMin || mp.sat255 > cfg.sat255Max)
            out.add(Finding("exposure", Level.WARN, "Badly exposed", "The image is too dark, too bright or clipped. Contrast was stretched, but information may be lost."))
        else if (mp.rng < cfg.rngHint)
            out.add(Finding("lowcontrast", Level.HINT, "Low contrast", "Contrast was stretched automatically."))
        if (mh.corners - mh.center > cfg.invDelta)
            out.add(Finding("inverted", Level.WARN, "May be inverted", "The corners are brighter than the chest, so bones may appear dark on a bright background.", "invert"))
        if (mh.symH - mh.symV > cfg.rotDelta)
            out.add(Finding("orientation", Level.WARN, "May not be upright or frontal", "The chest does not look upright and left-right symmetric. The model expects an upright frontal view.", "rotate"))
        if (mh.aspect > cfg.aspectMax)
            out.add(Finding("shape", Level.WARN, "Unusual shape", "The long side is more than ${cfg.aspectMax}x the short side. Crop to the chest if possible; padding may distort the input."))
        if (camera)
            out.add(Finding("camera", Level.WARN, "Photo taken with the camera", "Glare, distortion and screen or film artefacts make the result less reliable."))
        if (abs(mh.aspect - 1.0) > 0.02 && mh.aspect <= cfg.aspectMax)
            out.add(Finding("padded", Level.HINT, "Padded to a square", "Black bars were added so the image fits the model's square input."))
        return out
    }

    fun plausibility(d2: Double, cfg: CheckConfig = CheckConfig()): Finding? = when {
        d2 > cfg.d2Block -> Finding("plausibility", Level.BLOCK, "Far outside the training X-rays",
            "Distance ${"%.0f".format(d2)} (genuine X-rays are mostly below ${"%.0f".format(cfg.d2Warn)}). This may not be a frontal chest X-ray.")
        d2 > cfg.d2Warn -> Finding("plausibility", Level.WARN, "Unlike the training X-rays",
            "Distance ${"%.0f".format(d2)} (genuine X-rays are mostly below ${"%.0f".format(cfg.d2Warn)}; about 1 in 100 real ones exceed it). Treat the result as unreliable.")
        else -> null
    }
}

/** Regularised Mahalanobis distance of the encoder's 384-d feature vector to the training features. */
class Plausibility(private val mu: DoubleArray, private val p: FloatArray) {
    val dim: Int = mu.size
    fun d2(f: FloatArray): Double {
        val d = DoubleArray(dim) { f[it] - mu[it] }
        var s = 0.0
        for (i in 0 until dim) {
            var row = 0.0
            val base = i * dim
            for (j in 0 until dim) row += p[base + j] * d[j]
            s += d[i] * row
        }
        return s
    }

    companion object {
        /** File layout: int32 dim (little endian), float32[dim] mean, float32[dim*dim] precision. */
        fun fromBytes(b: ByteArray): Plausibility {
            val bb = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val dim = bb.int
            val mu = DoubleArray(dim) { bb.float.toDouble() }
            val p = FloatArray(dim * dim)
            bb.asFloatBuffer().get(p)
            return Plausibility(mu, p)
        }
    }
}
