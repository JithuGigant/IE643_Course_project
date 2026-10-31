package com.ie643.cxr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import com.ie643.cxr.core.Plausibility
import com.ie643.cxr.core.Prep
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.math.max

/** Access to the files bundled in assets/ and to the image decoder. */
class Repo(private val ctx: Context) {
    fun text(name: String): String = ctx.assets.open(name).bufferedReader().use { it.readText() }
    fun bytes(name: String): ByteArray = ctx.assets.open(name).use { it.readBytes() }

    fun config() = AppConfig(JSONObject(text("app_config.json")))
    fun vocab() = Vocab(JSONObject(text("vocab.json")))
    fun samples() = Sample.parse(JSONArray(text("samples/samples.json")))
    fun results() = ResultsData.parse(JSONObject(text("results.json")))
    fun plausibility() = Plausibility.fromBytes(bytes("plausibility.bin"))

    /** Copies a model from assets to internal storage once (ONNX Runtime opens it by path). */
    fun ensureModel(name: String): File {
        val dir = File(ctx.filesDir, "models").also { it.mkdirs() }
        val out = File(dir, name)
        val len = try { ctx.assets.openFd(name).use { it.length } } catch (e: IOException) { -1L }
        if (out.exists() && len > 0 && out.length() == len) return out
        val tmp = File(dir, "$name.tmp")
        ctx.assets.open(name).use { i -> tmp.outputStream().use { o -> i.copyTo(o, 1 shl 20) } }
        if (out.exists()) out.delete()
        tmp.renameTo(out)
        return out
    }

    /** The 512x512 8-bit canvas of a bundled sample (already training-style: no further processing). */
    fun sampleCanvas(file: String): ByteArray {
        val bmp = ctx.assets.open("samples/$file").use { BitmapFactory.decodeStream(it) }
            ?: throw IOException("Cannot decode sample $file")
        val argb = IntArray(bmp.width * bmp.height)
        bmp.getPixels(argb, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val g = Prep.argbToGray(argb, bmp.width, bmp.height)
        if (g.w != Prep.SIZE || g.h != Prep.SIZE) throw IOException("Sample $file is not 512x512")
        return g.px
    }

    fun sampleThumb(file: String): Bitmap {
        val o = BitmapFactory.Options().apply { inSampleSize = 2 }
        return ctx.assets.open("samples/$file").use { BitmapFactory.decodeStream(it, null, o) }!!
    }
}

object ImageIO {
    /** Decodes an image, applies its EXIF orientation, and limits the long side to about [maxSide] px. */
    fun load(ctx: Context, uri: Uri, maxSide: Int = 3000): Bitmap {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (cr.openInputStream(uri) ?: throw IOException("Cannot open image")).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("This file is not a readable image")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val o = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        var bmp = (cr.openInputStream(uri) ?: throw IOException("Cannot open image")).use { BitmapFactory.decodeStream(it, null, o) }
            ?: throw IOException("Cannot decode image")
        val orient = try {
            (cr.openInputStream(uri) ?: throw IOException()).use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } catch (e: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val m = Matrix()
        when (orient) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(-90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(-90f)
        }
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        return bmp
    }

    fun rotate(src: Bitmap, quarterTurnsCw: Int): Bitmap {
        val q = ((quarterTurnsCw % 4) + 4) % 4
        if (q == 0) return src
        val m = Matrix().apply { postRotate(90f * q) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /** 8-bit grayscale pixels as an ARGB bitmap, e.g. for showing the 512x512 model input. */
    fun grayToBitmap(px: ByteArray, w: Int, h: Int): Bitmap {
        val argb = IntArray(w * h) { i -> val v = px[i].toInt() and 0xFF; (255 shl 24) or (v shl 16) or (v shl 8) or v }
        return Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888)
    }
}
