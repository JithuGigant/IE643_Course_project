package com.ie643.cxr

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.os.SystemClock
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer

class Enc(val prefix: FloatArray, val probeLogits: FloatArray, val features: FloatArray, val ms: Long)
class Gen(val text: String, val ms: Long, val steps: Int, val tokens: Int)

/**
 * On-device inference with ONNX Runtime (CPU).
 *  encoder.onnx : uint8 [1,512,512] -> prefix [1,32,768], probe_logits [1,14], features [1,384]
 *  decoder      : one step, static KV cache; positions 0..31 feed the prefix, 32 feeds BOS, then greedy feedback.
 * The loop is the one verified on the 24 sample reports (identical to the PyTorch reference).
 */
class Engine(
    private val cfg: AppConfig,
    private val vocab: Vocab,
    encFile: File,
    decFile: File,
    val threads: Int,
) : AutoCloseable {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val enc: OrtSession
    private val dec: OrtSession

    init {
        val opts = OrtSession.SessionOptions()
        opts.setIntraOpNumThreads(threads)
        opts.setInterOpNumThreads(1)
        enc = env.createSession(encFile.absolutePath, opts)
        dec = env.createSession(decFile.absolutePath, opts)
    }

    private fun floats(v: Any): FloatArray {
        val fb = (v as OnnxTensor).floatBuffer
        val a = FloatArray(fb.remaining())
        fb.get(a)
        return a
    }

    fun encode(canvas: ByteArray): Enc {
        val t0 = SystemClock.elapsedRealtime()
        val buf = ByteBuffer.allocateDirect(canvas.size).order(ByteOrder.nativeOrder())
        buf.put(canvas)
        buf.rewind()
        val t = OnnxTensor.createTensor(env, buf, longArrayOf(1, 512, 512), OnnxJavaType.UINT8)
        try {
            val r = enc.run(mapOf("image" to t))
            try {
                val prefix = floats(r.get(0))
                val logits = floats(r.get(1))
                val feats = floats(r.get(2))
                return Enc(prefix, logits, feats, SystemClock.elapsedRealtime() - t0)
            } finally {
                r.close()
            }
        } finally {
            t.close()
        }
    }

    fun generate(e: Enc): Gen {
        val t0 = SystemClock.elapsedRealtime()
        val nPrefix = cfg.nPrefix
        val prefixT = OnnxTensor.createTensor(env, FloatBuffer.wrap(e.prefix), longArrayOf(1, nPrefix.toLong(), cfg.prefixDim.toLong()))
        val cacheN = cfg.cacheShape.fold(1L) { a, b -> a * b }.toInt()
        val k0 = OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(cacheN)), cfg.cacheShape)
        val v0 = OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(cacheN)), cfg.cacheShape)
        var kT: OnnxTensor = k0
        var vT: OnnxTensor = v0
        var prev: OrtSession.Result? = null
        var steps = 0
        val ids = IntArray(cfg.maxNew)
        var n = 0

        fun step(tok: Long, pos: Long, wantLogits: Boolean): FloatArray? {
            val tokT = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(tok)), longArrayOf(1))
            val posT = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(pos)), longArrayOf(1))
            val res = dec.run(mapOf("prefix" to prefixT, "token" to tokT, "pos" to posT, "k_cache" to kT, "v_cache" to vT))
            tokT.close(); posT.close()
            if (prev == null) { k0.close(); v0.close() } else prev!!.close()
            prev = res
            kT = res.get(1) as OnnxTensor
            vT = res.get(2) as OnnxTensor
            steps++
            return if (wantLogits) floats(res.get(0)) else null
        }

        try {
            for (p in 0 until nPrefix) step(0L, p.toLong(), false)
            var lg = step(cfg.bosIndex.toLong(), nPrefix.toLong(), true)!!
            var pos = nPrefix + 1L
            while (n < cfg.maxNew) {
                var best = 0
                var bv = lg[0]
                for (i in 1 until lg.size) if (lg[i] > bv) { bv = lg[i]; best = i }
                if (best == cfg.eosIndex) break
                ids[n++] = best
                lg = step(best.toLong(), pos, true)!!
                pos++
            }
        } finally {
            if (prev == null) { k0.close(); v0.close() } else prev!!.close()
            prefixT.close()
        }
        return Gen(vocab.decode(ids, n), SystemClock.elapsedRealtime() - t0, steps, n)
    }

    override fun close() {
        enc.close()
        dec.close()
    }
}
