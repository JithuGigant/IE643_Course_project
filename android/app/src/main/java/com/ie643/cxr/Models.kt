package com.ie643.cxr

import org.json.JSONArray
import org.json.JSONObject

data class FlagDef(
    val label: String, val index: Int, val display: String, val threshold: Double,
    val sens: Double, val sensCi: String, val ppv: Double, val spec: Double, val auc: Double,
    val textSens: Double, val positives: Int,
)

/** Parsed app_config.json written by the export notebook. */
class AppConfig(root: JSONObject) {
    val version: String = root.optString("version")
    val run: String = root.optString("run")
    val probe: String = root.optString("probe")
    private val enc = root.getJSONObject("encoder")
    private val dec = root.getJSONObject("decoder")
    val encoderFile: String = enc.optString("file", "encoder.onnx")
    val decoderFile: String = dec.optString("file", "decoder_f16w.onnx")
    val decoderPrecision: String = dec.optString("precision")
    val nPrefix: Int = dec.getInt("n_prefix")
    val maxNew: Int = dec.getInt("max_new")
    val vocabSize: Int = dec.getInt("vocab_size")
    val eosIndex: Int = dec.getInt("eos_index")
    val bosIndex: Int = dec.getInt("bos_index")
    val cacheShape: LongArray = dec.getJSONArray("cache_shape").let { a -> LongArray(a.length()) { a.getLong(it) } }
    val prefixDim: Int = enc.getJSONObject("outputs").getJSONArray("prefix").getInt(2)
    val nLabels: Int = root.getJSONArray("labels").length()
    val limits: String = root.optString("limits")
    val flagTarget: Double = root.optDouble("flag_target_sensitivity", 0.9)
    val notAssessed: List<String> = strings(root.optJSONArray("not_assessed"))
    val textNeverNames: List<String> = strings(root.optJSONArray("text_never_names"))
    val flags: List<FlagDef> = root.getJSONArray("flags").let { a ->
        List(a.length()) { i ->
            val f = a.getJSONObject(i)
            FlagDef(
                label = f.getString("label"), index = f.getInt("index"), display = f.optString("display"),
                threshold = f.getDouble("threshold_logit"), sens = f.optDouble("test_sensitivity"),
                sensCi = f.optString("test_sensitivity_ci"), ppv = f.optDouble("test_ppv"),
                spec = f.optDouble("test_specificity"), auc = f.optDouble("test_auc"),
                textSens = f.optDouble("text_sensitivity"), positives = f.optInt("test_positives"),
            )
        }
    }

    companion object {
        fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else List(a.length()) { a.getString(it) }
    }
}

class Vocab(root: JSONObject) {
    private val pieces: List<String> = AppConfig.strings(root.getJSONArray("pieces"))
    fun decode(ids: IntArray, n: Int = ids.size): String {
        val sb = StringBuilder()
        for (i in 0 until n) sb.append(pieces[ids[i]])
        return sb.toString().trim()
    }
}

data class Sample(
    val file: String, val idx: Int, val gt: String, val model: String,
    val gtPositive: List<String>, val flags: List<String>,
) {
    companion object {
        fun parse(a: JSONArray): List<Sample> = List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Sample(
                file = o.getString("file"), idx = o.getInt("idx"), gt = o.optString("gt_impression"),
                model = o.optString("model_impression"), gtPositive = AppConfig.strings(o.optJSONArray("gt_positive")),
                flags = AppConfig.strings(o.optJSONArray("flags")),
            )
        }
    }
}

data class MetricRow(val label: String, val value: String, val note: String)
data class MetricSection(val title: String, val note: String, val rows: List<MetricRow>)
data class ResultsData(val updated: String, val source: String, val sections: List<MetricSection>) {
    companion object {
        fun parse(o: JSONObject): ResultsData {
            val secs = o.getJSONArray("sections")
            return ResultsData(
                updated = o.optString("updated"), source = o.optString("source"),
                sections = List(secs.length()) { i ->
                    val s = secs.getJSONObject(i)
                    val rows = s.getJSONArray("rows")
                    MetricSection(
                        title = s.getString("title"), note = s.optString("note"),
                        rows = List(rows.length()) { j ->
                            val r = rows.getJSONArray(j)
                            MetricRow(r.getString(0), r.getString(1), if (r.length() > 2) r.getString(2) else "")
                        },
                    )
                },
            )
        }
    }
}

class Timing(
    val loadMs: Long, val prepMs: Long, val encoderMs: Long, val generateMs: Long, val steps: Int,
    val totalMs: Long, val peakPssMb: Long, val basePssMb: Long, val threads: Int,
) {
    val msPerStep: Double get() = if (steps > 0) generateMs.toDouble() / steps else 0.0
}

/** One recorded finding (copy of core.Finding, kept simple for storage). */
data class FindingRec(val id: String, val level: String, val title: String, val detail: String)

class Analysis(
    val id: Long,
    val ts: Long,
    val source: String,            // "sample" | "upload" | "camera"
    val sample: Sample?,
    val canvas: ByteArray?,        // the 512x512 image the model saw
    val report: String,
    val fired: List<String>,       // labels of flags that fired
    val findings: List<FindingRec>,
    val reliable: Boolean,         // false when analysed after overriding a blocking warning
    val d2: Double?,
    val timing: Timing?,
    val matchesSaved: Boolean?,    // samples only: report identical to the one saved at export time
)
