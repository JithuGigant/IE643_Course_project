// JVM parity test of the pure-Kotlin core against the Python reference (tools/run_core_test.sh).
import com.ie643.cxr.core.*
import java.io.File
import kotlin.math.abs

fun main(args: Array<String>) {
    val dir = File(args[0])
    val exp = HashMap<String, String>()
    for (line in File(dir, "expected.txt").readLines()) {
        val sp = line.indexOf(' '); exp[line.substring(0, sp) + " " + line.substring(sp + 1).substringBefore(' ')] = line.substring(sp + 1)
    }
    val plaus = Plausibility.fromBytes(File(args[1]).readBytes())
    var fails = 0
    val names = File(dir, "expected.txt").readLines().map { it.substringBefore(' ') }.distinct()
    for (name in names) {
        val head = File(dir, "expected.txt").readLines().first { it.startsWith("$name w ") }.split(' ')
        val w = head[2].toInt(); val h = head[4].toInt(); val sat = head[6].toDouble()
        val trimExp = head.subList(8, 12).map { it.toInt() }; val d2Exp = head[13].toDouble()
        val rgb = File(dir, "$name.rgb").readBytes()
        val argb = IntArray(w * h) { i -> (255 shl 24) or ((rgb[3 * i].toInt() and 255) shl 16) or ((rgb[3 * i + 1].toInt() and 255) shl 8) or (rgb[3 * i + 2].toInt() and 255) }
        val gray = Prep.argbToGray(argb, w, h)
        val satK = Prep.meanSaturation(argb)
        val tr = Prep.autoTrim(gray)
        val crop = Prep.crop(gray, tr[0], tr[1], tr[2], tr[3])
        val res = Prep.pipeline(crop, true)
        val canExp = File(dir, "$name.canvas").readBytes()
        var nd = 0; var mx = 0
        for (i in canExp.indices) { val d = abs((canExp[i].toInt() and 255) - (res.canvas[i].toInt() and 255)); if (d > 0) nd++; if (d > mx) mx = d }
        val feat = File(dir, "$name.feat").readBytes().let { b -> val bb = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN); FloatArray(b.size / 4) { bb.float } }
        val d2 = plaus.d2(feat)
        val mp = Checks.metrics(res.contentPre); val mh = Checks.metrics(res.contentHe)
        fun cmp(label: String, a: Double, b: Double, tol: Double): String? = if (abs(a - b) <= tol * maxOf(1.0, abs(b))) null else "$label kotlin=$a python=$b"
        val problems = ArrayList<String>()
        if (tr.toList() != trimExp) problems.add("trim kotlin=${tr.toList()} python=$trimExp")
        if (abs(satK - sat) > 1e-4) problems.add("sat kotlin=$satK python=$sat")
        cmp("d2", d2, d2Exp, 1e-4)?.let { problems.add(it) }
        fun m(prefix: String, mm: Metrics) {
            val pairs = mapOf("lapvar" to mm.lapvar, "mean" to mm.mean, "p1" to mm.p1, "p99" to mm.p99, "rng" to mm.rng, "sat255" to mm.sat255,
                "corners" to mm.corners, "center" to mm.center, "symV" to mm.symV, "symH" to mm.symH, "aspect" to mm.aspect)
            for ((k, v) in pairs) {
                val line = File(dir, "expected.txt").readLines().first { it.startsWith("$name $prefix.$k ") }
                cmp("$prefix.$k", v, line.split(' ')[2].toDouble(), 2e-3)?.let { problems.add(it) }
            }
        }
        // metrics are compared on Kotlin's own canvas content; tiny resize rounding differences are tolerated
        m("pre", mp); m("he", mh)
        val ev = Checks.evaluate(maxOf(crop.w, crop.h), satK, false, res.contentPre, res.contentHe).filter { it.id != "padded" }
            .map { it.id + ":" + it.level.name.lowercase() }.sorted().joinToString(",")
        val evExp = File(dir, "expected.txt").readLines().first { it.startsWith("$name events") }.removePrefix("$name events").trim()
        if (ev != evExp) problems.add("events kotlin='$ev' python='$evExp'")
        println("%-10s canvas diff px=%d max=%d  d2 %.3f vs %.3f  %s".format(name, nd, mx, d2, d2Exp, if (problems.isEmpty()) "OK" else "PROBLEMS: $problems"))
        if (problems.isNotEmpty()) fails++
    }
    println(if (fails == 0) "CORE TEST PASSED" else "CORE TEST: $fails case(s) differ")
}
