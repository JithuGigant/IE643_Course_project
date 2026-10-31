package com.ie643.cxr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Draws the shareable result card (PNG or PDF). Every export carries the research-prototype watermark. */
object CardRenderer {
    private const val W = 1080
    private const val PAD = 48
    const val WATERMARK = "Research prototype, not for clinical use"

    private fun tp(size: Float, bold: Boolean = false, color: Int = Color.rgb(25, 30, 35)) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.12f).setIncludePad(false).build()

    /** Measures when [canvas] is null, draws otherwise. Returns the total content height. */
    private fun draw(canvas: Canvas?, a: Analysis, cfg: AppConfig, includeImage: Boolean): Int {
        var y = PAD.toFloat()
        val inner = W - 2 * PAD
        fun text(s: String, paint: TextPaint, indent: Int = 0, gap: Float = 10f) {
            val l = layout(s, paint, inner - indent)
            if (canvas != null) { canvas.save(); canvas.translate((PAD + indent).toFloat(), y); l.draw(canvas); canvas.restore() }
            y += l.height + gap
        }
        fun heading(s: String) { y += 14f; text(s, tp(34f, true, Color.rgb(11, 90, 90)), gap = 8f) }

        text("IE643 chest X-ray report assistant", tp(48f, true), gap = 6f)
        val src = a.sample?.let { "Test-set sample #${it.idx}" } ?: if (a.source == "camera") "User image (camera photo)" else "User image (uploaded)"
        text("$src  |  " + SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(a.ts)), tp(28f, color = Color.GRAY), gap = 6f)
        text(WATERMARK, tp(30f, true, Color.rgb(180, 30, 30)), gap = 16f)
        if (!a.reliable) text("NOT RELIABLE: analysed after overriding a blocking warning.", tp(30f, true, Color.rgb(180, 30, 30)), gap = 12f)

        val c = a.canvas
        if (includeImage && c != null && a.sample == null) {
            val size = 480
            if (canvas != null) {
                val bmp = ImageIO.grayToBitmap(c, 512, 512)
                canvas.drawBitmap(bmp, null, Rect((W - size) / 2, y.toInt(), (W + size) / 2, y.toInt() + size), null)
            }
            y += size + 8f
            text("Image as seen by the model (512 x 512 after cropping, grayscale, contrast equalisation)", tp(24f, color = Color.GRAY), gap = 8f)
        } else if (a.sample != null) {
            text("Sample image omitted: CheXpert Plus images cannot be redistributed.", tp(26f, color = Color.GRAY), gap = 8f)
        }

        heading("Draft report (model output)")
        text(a.report.ifBlank { "(empty report)" }, tp(34f), gap = 6f)
        text("Generated on the phone by the distilled student model. A draft for a clinician to review, not a diagnosis.", tp(24f, color = Color.GRAY), gap = 8f)

        heading("Screening flags")
        val fired = cfg.flags.filter { it.label in a.fired }
        if (fired.isEmpty()) text("No flag fired.", tp(30f))
        for (f in fired) {
            text("${f.display}", tp(30f, true), gap = 2f)
            text("TEST sensitivity ${"%.2f".format(f.sens)}, PPV ${"%.2f".format(f.ppv)} (${f.positives} positives)", tp(26f, color = Color.GRAY), indent = 24, gap = 8f)
        }
        text("Not assessed: ${cfg.notAssessed.joinToString(", ")}. Flags are an independent screening layer tuned for about ${(cfg.flagTarget * 100).toInt()}% sensitivity; they raise many false alarms and can disagree with the report text.", tp(24f, color = Color.GRAY), gap = 8f)

        val notes = a.findings.filter { it.id != "padded" }
        if (notes.isNotEmpty()) {
            heading("Input checks")
            for (f in notes) {
                val tag = when (f.level) { "BLOCK" -> "[blocking]"; "WARN" -> "[warning]"; else -> "[hint]" }
                text("$tag ${f.title}: ${f.detail}", tp(26f), gap = 6f)
            }
        }
        heading("Limits")
        text(cfg.limits, tp(26f), gap = 6f)
        y += 10f
        text("$WATERMARK. Model ${cfg.run}, ${cfg.version}.", tp(22f, color = Color.GRAY), gap = 0f)
        y += PAD
        if (canvas != null) {
            // faint diagonal watermark over the whole card
            val p = tp(78f, true, Color.argb(34, 180, 30, 30))
            canvas.save(); canvas.rotate(-28f, W / 2f, y / 2f)
            val tw = p.measureText(WATERMARK)
            canvas.drawText(WATERMARK, (W - tw) / 2f, y / 2f, p)
            canvas.restore()
        }
        return y.toInt()
    }

    fun renderPng(a: Analysis, cfg: AppConfig, includeImage: Boolean): Bitmap {
        val h = draw(null, a, cfg, includeImage)
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        draw(c, a, cfg, includeImage)
        return bmp
    }

    fun writePdf(a: Analysis, cfg: AppConfig, includeImage: Boolean, out: OutputStream) {
        val h = draw(null, a, cfg, includeImage)
        val pageW = 595
        val s = pageW.toFloat() / W
        val pageH = (h * s).toInt() + 1
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
        page.canvas.drawColor(Color.WHITE)
        page.canvas.scale(s, s)
        draw(page.canvas, a, cfg, includeImage)
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }
}
