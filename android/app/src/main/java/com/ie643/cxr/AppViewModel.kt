package com.ie643.cxr

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ie643.cxr.core.Checks
import com.ie643.cxr.core.Finding
import com.ie643.cxr.core.Gray
import com.ie643.cxr.core.Level
import com.ie643.cxr.core.Plausibility
import com.ie643.cxr.core.Prep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

enum class Tab { ANALYZE, HISTORY, METRICS, ABOUT }
enum class Sub { NONE, PREPARE, RESULT }

/** Crop rectangle as fractions (0..1) of the displayed image. */
data class CropRect(val l: Float = 0f, val t: Float = 0f, val r: Float = 1f, val b: Float = 1f)

/** Everything the "prepare image" screen edits. */
class PrepState(val source: String, val original: Bitmap) {
    var rot by mutableIntStateOf(0)
    var rotated by mutableStateOf(original)
    var crop by mutableStateOf(CropRect())
    var invert by mutableStateOf(false)
    var equalize by mutableStateOf(true)
    var findings by mutableStateOf<List<Finding>>(emptyList())
    var preview by mutableStateOf<Bitmap?>(null)
    var canvas: ByteArray? = null
    var prepMs: Long = 0
    var trimmedBorders by mutableStateOf(false)
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        const val ORT_VERSION = "1.22.0"
    }

    private val repo = Repo(app)
    private val engineLock = Mutex()
    private var engine: Engine? = null
    private var plaus: Plausibility? = null

    var config by mutableStateOf<AppConfig?>(null); private set
    var samples by mutableStateOf<List<Sample>>(emptyList()); private set
    var results by mutableStateOf<ResultsData?>(null); private set
    var engineReady by mutableStateOf(false); private set
    var engineStatus by mutableStateOf("Starting…"); private set
    var engineError by mutableStateOf<String?>(null); private set
    private var loadMs = 0L

    var tab by mutableStateOf(Tab.ANALYZE)
    var sub by mutableStateOf(Sub.NONE); private set
    var busy by mutableStateOf(false); private set
    var busyMsg by mutableStateOf(""); private set
    var prep by mutableStateOf<PrepState?>(null); private set
    var current by mutableStateOf<Analysis?>(null); private set
    var pendingBlock by mutableStateOf<List<Finding>?>(null); private set
    var message by mutableStateOf<String?>(null)
    val history = mutableStateListOf<Analysis>()
    var historyOn by mutableStateOf(true); private set

    private val prefs = app.getSharedPreferences("settings", 0)
    private lateinit var store: HistoryStore
    private var prepJob: Job? = null

    val deviceLine: String get() = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Runtime.getRuntime().availableProcessors()} cores"

    init {
        historyOn = prefs.getBoolean("history_on", true)
        viewModelScope.launch(Dispatchers.Default) { loadAll() }
    }

    // ---------------------------------------------------------------- loading
    private fun loadAll() {
        try {
            val t0 = SystemClock.elapsedRealtime()
            val c = repo.config()
            config = c
            samples = repo.samples()
            results = try { repo.results() } catch (e: Exception) { null }
            store = HistoryStore(getApplication()) { samples }
            history.clear(); history.addAll(store.list())
            plaus = repo.plausibility()
            engineStatus = "Preparing model files (first start copies about 150 MB)…"
            val encFile = repo.ensureModel(c.encoderFile)
            val decFile = repo.ensureModel(c.decoderFile)
            engineStatus = "Loading the model…"
            val threads = max(2, min(4, Runtime.getRuntime().availableProcessors() / 2))
            engine = Engine(c, repo.vocab(), encFile, decFile, threads)
            loadMs = SystemClock.elapsedRealtime() - t0
            engineStatus = "Model ready (loaded in ${loadMs} ms)"
            engineReady = true
        } catch (t: Throwable) {
            engineError = (t.message ?: t.javaClass.simpleName)
            engineStatus = "Could not load the model"
        }
    }

    fun sampleThumb(file: String): Bitmap = repo.sampleThumb(file)
    fun historyThumb(a: Analysis): Bitmap? = store.thumb(a)

    // ---------------------------------------------------------------- navigation
    fun back() {
        when (sub) {
            Sub.RESULT -> { sub = if (prep != null && fromPrepare) Sub.PREPARE else Sub.NONE }
            Sub.PREPARE -> { sub = Sub.NONE; prep = null }
            Sub.NONE -> {}
        }
    }
    private var fromPrepare = false

    fun closeResult() { sub = Sub.NONE; prep = null; fromPrepare = false }

    // ---------------------------------------------------------------- prepare image
    fun startPrepare(uri: Uri, source: String) {
        launchBusy("Loading the image…") {
            try {
                val bmp = ImageIO.load(getApplication(), uri)
                val p = PrepState(source, bmp)
                prep = p
                computePrep(p)
                sub = Sub.PREPARE
            } catch (e: Exception) {
                message = "Could not open this image: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun rotatePrep(dir: Int) {
        val p = prep ?: return
        viewModelScope.launch(Dispatchers.Default) {
            p.rot = ((p.rot + dir) % 4 + 4) % 4
            p.rotated = ImageIO.rotate(p.original, p.rot)
            p.crop = CropRect()
            computePrep(p)
        }
    }

    fun setCrop(c: CropRect) { prep?.crop = c }
    fun commitCrop() = schedulePrep()
    fun resetCrop() { prep?.crop = CropRect(); schedulePrep() }
    fun setInvert(v: Boolean) { prep?.invert = v; schedulePrep() }
    fun setEqualize(v: Boolean) { prep?.equalize = v; schedulePrep() }

    private fun schedulePrep() {
        val p = prep ?: return
        prepJob?.cancel()
        prepJob = viewModelScope.launch(Dispatchers.Default) { delay(120); computePrep(p) }
    }

    /** crop -> grayscale -> trim flat borders -> resize / equalise / pad, then the encoder-free checks. */
    private fun computePrep(p: PrepState) {
        val t0 = SystemClock.elapsedRealtime()
        val bmp = p.rotated
        val cr = p.crop
        var l = (cr.l * bmp.width).toInt().coerceIn(0, bmp.width - 1)
        var t = (cr.t * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        var r = (cr.r * bmp.width).toInt().coerceIn(l + 1, bmp.width)
        var b = (cr.b * bmp.height).toInt().coerceIn(t + 1, bmp.height)
        if (r - l < 16) { l = max(0, min(l, bmp.width - 16)); r = min(bmp.width, l + 16) }
        if (b - t < 16) { t = max(0, min(t, bmp.height - 16)); b = min(bmp.height, t + 16) }
        val w = r - l; val h = b - t
        val argb = IntArray(w * h)
        bmp.getPixels(argb, 0, w, l, t, w, h)
        val sat = Prep.meanSaturation(argb)
        var gray: Gray = Prep.argbToGray(argb, w, h)
        if (p.invert) gray = Prep.invert(gray)
        val tr = Prep.autoTrim(gray)
        val trimmed = tr[0] > 0 || tr[2] > 0 || tr[1] < h || tr[3] < w
        val crop = if (trimmed) Prep.crop(gray, tr[0], tr[1], tr[2], tr[3]) else gray
        val res = Prep.pipeline(crop, p.equalize)
        val list = ArrayList(Checks.evaluate(max(crop.w, crop.h), sat, p.source == "camera", res.contentPre, res.contentHe))
        if (trimmed) list.add(Finding("trim", Level.HINT, "Plain borders removed", "Flat black or white edges were trimmed automatically."))
        p.canvas = res.canvas
        p.preview = ImageIO.grayToBitmap(res.canvas, Prep.SIZE, Prep.SIZE)
        p.findings = list.sortedBy { -it.level.ordinal }
        p.trimmedBorders = trimmed
        p.prepMs = SystemClock.elapsedRealtime() - t0
    }

    // ---------------------------------------------------------------- analysis
    fun analyzeSample(s: Sample) {
        fromPrepare = false
        launchBusy("Analysing…") {
            val canvas = repo.sampleCanvas(s.file)
            runAnalysis("sample", s, canvas, emptyList(), 0L, override = true)
        }
    }

    fun analyzePrepared() {
        val p = prep ?: return
        val canvas = p.canvas ?: return
        val source = p.source
        val findings = p.findings
        val prepMs = p.prepMs
        fromPrepare = true
        launchBusy("Analysing…") { runAnalysis(source, null, canvas, findings, prepMs, override = false) }
    }

    fun confirmBlock() {
        val p = prep ?: return
        val canvas = p.canvas ?: return
        val findings = p.findings
        pendingBlock = null
        fromPrepare = true
        launchBusy("Analysing…") { runAnalysis(p.source, null, canvas, findings, p.prepMs, override = true) }
    }

    fun dismissBlock() { pendingBlock = null }

    private class PssSampler {
        @Volatile var peak = 0L
        @Volatile private var running = true
        val base = Debug.getPss()
        private val thread = Thread {
            while (running) { peak = max(peak, Debug.getPss()); try { Thread.sleep(100) } catch (e: InterruptedException) { return@Thread } }
        }
        init { peak = base; thread.start() }
        fun stop() { running = false; peak = max(peak, Debug.getPss()) }
    }

    private suspend fun runAnalysis(source: String, sample: Sample?, canvas: ByteArray, pre: List<Finding>, prepMs: Long, override: Boolean) {
        val eng = engine
        val c = config
        val pl = plaus
        if (eng == null || c == null || pl == null) { message = "The model is not loaded yet."; return }
        engineLock.withLock {
            val sampler = PssSampler()
            try {
                val t0 = SystemClock.elapsedRealtime()
                val enc = eng.encode(canvas)
                val d2 = pl.d2(enc.features)
                val all = pre + listOfNotNull(Checks.plausibility(d2))
                val blocking = all.filter { it.level == Level.BLOCK }
                if (blocking.isNotEmpty() && !override) {
                    pendingBlock = all
                    return
                }
                val gen = eng.generate(enc)
                val fired = c.flags.filter { enc.probeLogits[it.index] >= it.threshold }.map { it.label }
                val total = SystemClock.elapsedRealtime() - t0 + prepMs
                sampler.stop()
                val timing = Timing(loadMs, prepMs, enc.ms, gen.ms, gen.steps, total, sampler.peak / 1024, sampler.base / 1024, eng.threads)
                val matches = sample?.let { it.model.trim() == gen.text && it.flags.toSet() == fired.toSet() }
                val a = Analysis(
                    id = System.currentTimeMillis(), ts = System.currentTimeMillis(), source = source, sample = sample, canvas = canvas,
                    report = gen.text, fired = fired,
                    findings = all.map { FindingRec(it.id, it.level.name, it.title, it.detail) },
                    reliable = blocking.isEmpty(), d2 = d2, timing = timing, matchesSaved = matches,
                )
                current = a
                sub = Sub.RESULT
                if (historyOn) { store.save(a); history.add(0, a) }
            } catch (t: Throwable) {
                message = "Analysis failed: ${t.message ?: t.javaClass.simpleName}"
            } finally {
                sampler.stop()
            }
        }
    }

    private fun launchBusy(msg: String, block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            busyMsg = msg; busy = true
            try { block() } finally { busy = false }
        }
    }

    // ---------------------------------------------------------------- history
    fun openHistory(a: Analysis) {
        viewModelScope.launch(Dispatchers.Default) {
            val canvas = a.canvas ?: when {
                a.sample != null -> try { repo.sampleCanvas(a.sample.file) } catch (e: Exception) { null }
                else -> store.loadCanvas(a)
            }
            val full = Analysis(a.id, a.ts, a.source, a.sample, canvas, a.report, a.fired, a.findings, a.reliable, a.d2, a.timing, a.matchesSaved)
            current = full
            fromPrepare = false
            sub = Sub.RESULT
        }
    }

    fun deleteHistory(a: Analysis) { viewModelScope.launch(Dispatchers.Default) { store.delete(a.id); history.remove(a) } }
    fun clearHistory() { viewModelScope.launch(Dispatchers.Default) { store.clear(); history.clear() } }
    fun updateHistoryOn(v: Boolean) { historyOn = v; prefs.edit().putBoolean("history_on", v).apply() }

    // ---------------------------------------------------------------- export
    fun cardFile(a: Analysis, pdf: Boolean): File {
        val c = config ?: throw IllegalStateException("not loaded")
        val dir = File(getApplication<Application>().cacheDir, "shared").also { it.mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date(a.ts))
        val f = File(dir, "ie643_report_$stamp.${if (pdf) "pdf" else "png"}")
        f.outputStream().use { out ->
            if (pdf) CardRenderer.writePdf(a, c, true, out)
            else CardRenderer.renderPng(a, c, true).compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return f
    }

    fun suggestedName(a: Analysis, pdf: Boolean): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date(a.ts))
        return "ie643_report_$stamp.${if (pdf) "pdf" else "png"}"
    }
}
