package com.ie643.cxr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Recent analyses, stored only inside the app's private folder. */
class HistoryStore(private val ctx: Context, private val samples: () -> List<Sample>) {
    private val dir: File get() = File(ctx.filesDir, "history").also { it.mkdirs() }

    fun save(a: Analysis) {
        val o = JSONObject()
        o.put("id", a.id); o.put("ts", a.ts); o.put("source", a.source)
        a.sample?.let { o.put("sample", it.file) }
        o.put("report", a.report)
        o.put("fired", JSONArray(a.fired))
        o.put("reliable", a.reliable)
        a.d2?.let { o.put("d2", it) }
        a.matchesSaved?.let { o.put("matches", it) }
        val fs = JSONArray()
        for (f in a.findings) fs.put(JSONObject().put("id", f.id).put("level", f.level).put("title", f.title).put("detail", f.detail))
        o.put("findings", fs)
        a.timing?.let { t ->
            o.put("timing", JSONObject().put("load", t.loadMs).put("prep", t.prepMs).put("enc", t.encoderMs).put("gen", t.generateMs)
                .put("steps", t.steps).put("total", t.totalMs).put("peak", t.peakPssMb).put("base", t.basePssMb).put("threads", t.threads))
        }
        File(dir, "${a.id}.json").writeText(o.toString())
        val c = a.canvas
        if (c != null && a.sample == null) {
            File(dir, "${a.id}.png").outputStream().use { ImageIO.grayToBitmap(c, 512, 512).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    fun list(): List<Analysis> {
        val byFile = samples().associateBy { it.file }
        val out = ArrayList<Analysis>()
        for (f in dir.listFiles { x -> x.name.endsWith(".json") }.orEmpty()) {
            try {
                val o = JSONObject(f.readText())
                val t = o.optJSONObject("timing")
                val fs = o.optJSONArray("findings") ?: JSONArray()
                out.add(
                    Analysis(
                        id = o.getLong("id"), ts = o.getLong("ts"), source = o.getString("source"),
                        sample = o.optString("sample").takeIf { it.isNotEmpty() }?.let { byFile[it] },
                        canvas = null, report = o.getString("report"), fired = AppConfig.strings(o.optJSONArray("fired")),
                        findings = List(fs.length()) { i -> fs.getJSONObject(i).let { FindingRec(it.getString("id"), it.getString("level"), it.getString("title"), it.getString("detail")) } },
                        reliable = o.optBoolean("reliable", true), d2 = if (o.has("d2")) o.getDouble("d2") else null,
                        timing = t?.let { Timing(it.getLong("load"), it.getLong("prep"), it.getLong("enc"), it.getLong("gen"), it.getInt("steps"), it.getLong("total"), it.getLong("peak"), it.getLong("base"), it.getInt("threads")) },
                        matchesSaved = if (o.has("matches")) o.getBoolean("matches") else null,
                    ),
                )
            } catch (e: Exception) { /* skip unreadable entry */ }
        }
        return out.sortedByDescending { it.ts }
    }

    fun loadCanvas(a: Analysis): ByteArray? {
        val f = File(dir, "${a.id}.png")
        if (!f.exists()) return null
        val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        val argb = IntArray(bmp.width * bmp.height)
        bmp.getPixels(argb, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return ByteArray(argb.size) { (argb[it] and 0xFF).toByte() }
    }

    fun thumb(a: Analysis): Bitmap? {
        val f = File(dir, "${a.id}.png")
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 })
    }

    fun delete(id: Long) { File(dir, "$id.json").delete(); File(dir, "$id.png").delete() }
    fun clear() { dir.listFiles()?.forEach { it.delete() } }
}
