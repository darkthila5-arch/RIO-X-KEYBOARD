package com.rio.board

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/** Everything the maker needs to draw one sticker / GIF. */
data class StickerSpec(
    var emoji: String = "😀",
    var text: String = "",
    var bgStyle: Int = 0,                 // 0 none, 1 circle, 2 rounded, 3 gradient
    var bgColor: Int = 0xFFFFC107.toInt(),
    var textColor: Int = Color.WHITE,
    var motion: Int = 1,                  // see StickerRenderer.motions
    var textFx: Int = 0,                  // see StickerRenderer.textFxs
    var overlay: Int = 0                  // see StickerRenderer.overlays
)

/**
 * Draws a sticker at time t (0..1 = one loop). Same code is used for the live preview,
 * the PNG sticker (t = 0) and every frame of the GIF.
 * Not thread-safe: use one instance per thread.
 */
class StickerRenderer {

    companion object {
        val bgs = listOf("None", "Circle", "Rounded", "Gradient")
        val motions = listOf("None", "Bounce", "Shake", "Pulse", "Flip", "Float", "Heartbeat", "Spin")
        val textFxs = listOf("None", "Rainbow", "Wave", "Blink")
        val overlays = listOf("None", "Hearts ❤", "Stars ⭐", "Sparkles ✨")
        private const val TAU = 6.2831855f
        private const val PI = 3.1415927f
    }

    private val bgP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emojiP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val strokeP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        typeface = Typeface.DEFAULT_BOLD
    }
    private val fillP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        typeface = Typeface.DEFAULT_BOLD
    }
    private val rect = RectF()
    private val hsv = FloatArray(3)

    fun renderBitmap(size: Int, s: StickerSpec, t: Float): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), size, s, t)
        return bmp
    }

    fun draw(c: Canvas, size: Int, s: StickerSpec, t: Float) {
        val S = size.toFloat()
        drawBackground(c, S, s)

        val hasText = s.text.isNotBlank()
        val hasEmoji = s.emoji.isNotEmpty()

        if (hasEmoji) {
            if (hasText) drawEmoji(c, S, s, t, S * 0.5f, S * 0.38f, S * 0.78f, S * 0.50f)
            else drawEmoji(c, S, s, t, S * 0.5f, S * 0.5f, S * 0.80f, S * 0.80f)
        }
        if (hasText) {
            if (hasEmoji) drawText(c, S, s, t, S * 0.5f, S * 0.66f, S * 0.88f, S * 0.30f, S * 0.18f)
            else drawText(c, S, s, t, S * 0.5f, S * 0.12f, S * 0.88f, S * 0.76f, S * 0.30f)
        }
        if (s.overlay != 0) drawOverlay(c, S, s, t)
    }

    // ---------------------------------------------------------------- background
    private fun drawBackground(c: Canvas, S: Float, s: StickerSpec) {
        if (s.bgStyle == 0) return
        val pad = S * 0.04f
        bgP.style = Paint.Style.FILL
        bgP.shader = null
        bgP.color = s.bgColor
        when (s.bgStyle) {
            1 -> c.drawCircle(S / 2f, S / 2f, S / 2f - pad, bgP)
            2 -> {
                rect.set(pad, pad, S - pad, S - pad)
                c.drawRoundRect(rect, S * 0.2f, S * 0.2f, bgP)
            }
            else -> {
                Color.colorToHSV(s.bgColor, hsv)
                hsv[0] = (hsv[0] + 40f) % 360f
                val c2 = Color.HSVToColor(hsv)
                bgP.shader = LinearGradient(pad, pad, S - pad, S - pad, s.bgColor, c2, Shader.TileMode.CLAMP)
                rect.set(pad, pad, S - pad, S - pad)
                c.drawRoundRect(rect, S * 0.2f, S * 0.2f, bgP)
                bgP.shader = null
            }
        }
    }

    // ---------------------------------------------------------------- emoji + motion
    private fun bump(t: Float, centre: Float): Float {
        val x = (t - centre) / 0.05f
        return exp(-x * x)
    }

    private fun drawEmoji(c: Canvas, S: Float, s: StickerSpec, t: Float, cx: Float, cy: Float, boxW: Float, boxH: Float) {
        var ts = minOf(boxW, boxH) * 0.85f
        emojiP.textSize = ts
        val w = emojiP.measureText(s.emoji)
        if (w > boxW) {
            ts *= boxW / w
            emojiP.textSize = ts
        }

        var dx = 0f; var dy = 0f; var rot = 0f; var sx = 1f; var sy = 1f
        when (s.motion) {
            1 -> {   // bounce with squash on landing
                val u = abs(sin(PI * t))
                dy = -u * 0.12f * S
                val k = 1f - u
                val sq = k * k * k * 0.14f
                sy = 1f - sq; sx = 1f + sq
            }
            2 -> {   // shake
                val v = sin(TAU * t * 3f)
                rot = v * 12f
                dx = v * 0.02f * S
            }
            3 -> {   // pulse
                val p = 1f + 0.12f * sin(TAU * t)
                sx = p; sy = p
            }
            4 -> {   // coin flip
                var k = cos(TAU * t)
                if (abs(k) < 0.06f) k = if (k < 0f) -0.06f else 0.06f
                sx = k
            }
            5 -> {   // float
                dy = sin(TAU * t) * 0.05f * S
                rot = sin(TAU * t) * 5f
            }
            6 -> {   // heartbeat: two quick beats
                val b = bump(t, 0.12f) + 0.75f * bump(t, 0.32f)
                val p = 1f + 0.16f * b
                sx = p; sy = p
            }
            7 -> rot = 360f * t   // spin
        }

        val fm = emojiP.fontMetrics
        c.save()
        c.translate(cx + dx, cy + dy)
        c.rotate(rot)
        c.scale(sx, sy)
        c.drawText(s.emoji, 0f, -(fm.ascent + fm.descent) / 2f, emojiP)
        c.restore()
    }

    // ---------------------------------------------------------------- caption
    private fun luminance(col: Int): Float =
        (0.299f * Color.red(col) + 0.587f * Color.green(col) + 0.114f * Color.blue(col)) / 255f

    private fun layoutFor(text: String, p: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    private fun drawText(
        c: Canvas, S: Float, s: StickerSpec, t: Float,
        cx: Float, top: Float, areaW: Float, areaH: Float, startSize: Float
    ) {
        var fill = s.textColor
        when (s.textFx) {
            1 -> {
                hsv[0] = (t * 360f) % 360f; hsv[1] = 0.85f; hsv[2] = 1f
                fill = Color.HSVToColor(hsv)
            }
            3 -> {
                val on = (t * 2f) % 1f < 0.5f
                fill = (fill and 0xFFFFFF) or ((if (on) 255 else 40) shl 24)
            }
        }
        val outline = if (luminance(fill) > 0.55f) 0xFF1B1B1B.toInt() else Color.WHITE
        val alphaMul = Color.alpha(fill)

        if (s.textFx == 2) {
            drawWave(c, S, s.text, t, fill, outline, cx, top, areaW, areaH, startSize)
            return
        }

        var ts = startSize
        var layout: StaticLayout
        while (true) {
            fillP.textSize = ts
            layout = layoutFor(s.text.trim(), fillP, areaW.toInt())
            if ((layout.height <= areaH && layout.lineCount <= 3) || ts <= S * 0.06f) break
            ts *= 0.9f
        }
        strokeP.textSize = ts
        strokeP.strokeWidth = ts * 0.14f
        val strokeLayout = layoutFor(s.text.trim(), strokeP, areaW.toInt())

        val y = top + (areaH - layout.height) / 2f
        c.save()
        c.translate(cx - areaW / 2f, y)
        strokeP.color = (outline and 0xFFFFFF) or (alphaMul shl 24)
        strokeLayout.draw(c)
        fillP.color = fill
        layout.draw(c)
        c.restore()
    }

    private fun drawWave(
        c: Canvas, S: Float, text0: String, t: Float, fill: Int, outline: Int,
        cx: Float, top: Float, areaW: Float, areaH: Float, startSize: Float
    ) {
        val text = text0.replace('\n', ' ').trim()
        var ts = startSize
        fillP.textAlign = Paint.Align.LEFT
        fillP.textSize = ts
        var total = fillP.measureText(text)
        if (total > areaW) {
            ts *= areaW / total
            fillP.textSize = ts
            total = fillP.measureText(text)
        }
        strokeP.textSize = ts
        strokeP.strokeWidth = ts * 0.14f
        strokeP.textAlign = Paint.Align.LEFT
        strokeP.color = outline
        fillP.color = fill

        val fm = fillP.fontMetrics
        val base = top + areaH / 2f - (fm.ascent + fm.descent) / 2f
        var x = cx - total / 2f
        var i = 0
        var n = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val len = Character.charCount(cp)
            val w = fillP.measureText(text, i, i + len)
            val y = base + sin(TAU * (t + n * 0.09f)) * S * 0.025f
            c.drawText(text, i, i + len, x, y, strokeP)
            c.drawText(text, i, i + len, x, y, fillP)
            x += w
            i += len
            n++
        }
        fillP.textAlign = Paint.Align.LEFT
    }

    // ---------------------------------------------------------------- particles
    private fun fract(v: Float): Float = v - floor(v)

    private fun drawOverlay(c: Canvas, S: Float, s: StickerSpec, t: Float) {
        val glyph = when (s.overlay) {
            1 -> "❤️"
            2 -> "⭐"
            3 -> "✨"
            else -> return
        }
        val n = 7
        for (i in 0 until n) {
            val ph = (t + i / n.toFloat()) % 1f
            val x = S * (0.1f + 0.8f * fract(i * 0.381f + 0.17f)) + sin(TAU * (ph + i * 0.13f)) * 0.03f * S
            val y = S * (0.92f - 0.85f * ph)
            emojiP.textSize = S * (0.07f + 0.05f * fract(i * 0.61f))
            emojiP.alpha = (sin(PI * ph) * 255f).toInt().coerceIn(0, 255)
            c.drawText(glyph, x, y, emojiP)
        }
        emojiP.alpha = 255
    }
}

// =============================================================================
//  GIF89a writer (no libraries): fixed 6x7x6 colour cube + ordered dithering,
//  index 255 = transparent, LZW compressed, loops forever.
// =============================================================================
class GifEncoder(private val os: OutputStream, private val w: Int, private val h: Int, private val delayCs: Int) {

    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
    private val hSize = 8209
    private val hKey = IntArray(hSize)
    private val hVal = IntArray(hSize)

    private fun writeShort(v: Int) {
        os.write(v and 0xFF)
        os.write((v shr 8) and 0xFF)
    }

    fun start() {
        os.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(w)
        writeShort(h)
        os.write(0xF7)
        os.write(0)
        os.write(0)
        val pal = ByteArray(768)
        for (r in 0 until 6) for (g in 0 until 7) for (b in 0 until 6) {
            val i = (r * 7 + g) * 6 + b
            pal[i * 3] = (r * 51).toByte()
            pal[i * 3 + 1] = ((g * 255 + 3) / 6).toByte()
            pal[i * 3 + 2] = (b * 51).toByte()
        }
        os.write(pal)
        os.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        os.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        os.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))
    }

    fun addFrame(px: IntArray) {
        val idx = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = px[y * w + x]
                val k = y * w + x
                if ((p ushr 24) < 128) {
                    idx[k] = 255.toByte()
                } else {
                    val d = (bayer[(y and 3) * 4 + (x and 3)] - 8) * 3
                    val r = ((((p shr 16) and 255) + d).coerceIn(0, 255) * 5 + 127) / 255
                    val g = ((((p shr 8) and 255) + d).coerceIn(0, 255) * 6 + 127) / 255
                    val b = (((p and 255) + d).coerceIn(0, 255) * 5 + 127) / 255
                    idx[k] = ((r * 7 + g) * 6 + b).toByte()
                }
            }
        }
        // graphic control: dispose to background, transparent colour = 255
        os.write(0x21)
        os.write(0xF9)
        os.write(4)
        os.write(0x09)
        writeShort(delayCs)
        os.write(255)
        os.write(0)
        // image descriptor
        os.write(0x2C)
        writeShort(0)
        writeShort(0)
        writeShort(w)
        writeShort(h)
        os.write(0)
        os.write(8)
        lzw(idx)
    }

    fun finish() {
        os.write(0x3B)
        os.flush()
    }

    private fun lzw(idx: ByteArray) {
        val buf = ByteArrayOutputStream(idx.size / 2 + 16)
        var cur = 0
        var curBits = 0
        var codeSize = 9
        var nextCode = 258
        java.util.Arrays.fill(hKey, -1)

        fun emit(code: Int) {
            cur = cur or (code shl curBits)
            curBits += codeSize
            while (curBits >= 8) {
                buf.write(cur and 0xFF)
                cur = cur ushr 8
                curBits -= 8
            }
        }

        emit(256)
        var prefix = idx[0].toInt() and 0xFF
        for (i in 1 until idx.size) {
            val c = idx[i].toInt() and 0xFF
            val key = (prefix shl 8) or c
            var hi = ((key shl 3) xor key) % hSize
            var found = -1
            while (hKey[hi] != -1) {
                if (hKey[hi] == key) {
                    found = hVal[hi]
                    break
                }
                hi++
                if (hi == hSize) hi = 0
            }
            if (found >= 0) {
                prefix = found
                continue
            }
            emit(prefix)
            if (nextCode < 4096) {
                hKey[hi] = key
                hVal[hi] = nextCode
                nextCode++
                if (nextCode - 1 == (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                emit(256)
                java.util.Arrays.fill(hKey, -1)
                codeSize = 9
                nextCode = 258
            }
            prefix = c
        }
        emit(prefix)
        if (nextCode < 4096) {
            nextCode++
            if (nextCode - 1 == (1 shl codeSize) && codeSize < 12) codeSize++
        }
        emit(257)
        if (curBits > 0) buf.write(cur and 0xFF)

        val data = buf.toByteArray()
        var pos = 0
        while (pos < data.size) {
            val n = minOf(255, data.size - pos)
            os.write(n)
            os.write(data, pos, n)
            pos += n
        }
        os.write(0)
    }
}

// =============================================================================
//  Library of made stickers (app-private folder, served by RioFileProvider)
// =============================================================================
object StickerStore {
    const val GIF_SIZE = 256
    const val GIF_FRAMES = 20          // 20 frames x 50 ms = 1 s loop (matches the live preview)
    const val PNG_SIZE = 512

    fun dir(ctx: Context): File = File(ctx.filesDir, "stickers").also { it.mkdirs() }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.isFile && (f.name.endsWith(".png") || f.name.endsWith(".gif")) }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun mimeOf(f: File): String = if (f.name.endsWith(".gif")) "image/gif" else "image/png"

    /** Blocking: call from a background thread. */
    fun make(ctx: Context, spec: StickerSpec, gif: Boolean): File {
        val r = StickerRenderer()
        val out = File(dir(ctx), "rio_" + System.currentTimeMillis() + if (gif) ".gif" else ".png")
        if (gif) {
            val size = GIF_SIZE
            FileOutputStream(out).buffered().use { os ->
                val enc = GifEncoder(os, size, size, 5)
                enc.start()
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val cv = Canvas(bmp)
                val px = IntArray(size * size)
                for (f in 0 until GIF_FRAMES) {
                    bmp.eraseColor(Color.TRANSPARENT)
                    r.draw(cv, size, spec, f / GIF_FRAMES.toFloat())
                    bmp.getPixels(px, 0, size, 0, 0, size, size)
                    enc.addFrame(px)
                }
                enc.finish()
                bmp.recycle()
            }
        } else {
            val bmp = r.renderBitmap(PNG_SIZE, spec, 0f)
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
        }
        return out
    }

    /** Blocking: copy into Pictures/RIO Stickers. */
    fun saveToGallery(ctx: Context, f: File): Boolean {
        return try {
            val mime = mimeOf(f)
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues()
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
                cv.put(MediaStore.MediaColumns.MIME_TYPE, mime)
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RIO Stickers")
                cv.put(MediaStore.MediaColumns.IS_PENDING, 1)
                val dest = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return false
                ctx.contentResolver.openOutputStream(dest)?.use { out -> f.inputStream().use { it.copyTo(out) } } ?: return false
                val done = ContentValues()
                done.put(MediaStore.MediaColumns.IS_PENDING, 0)
                ctx.contentResolver.update(dest, done, null, null)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "RIO Stickers")
                dir.mkdirs()
                val out = File(dir, f.name)
                f.inputStream().use { ins -> out.outputStream().use { ins.copyTo(it) } }
                MediaScannerConnection.scanFile(ctx, arrayOf(out.path), arrayOf(mime), null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
