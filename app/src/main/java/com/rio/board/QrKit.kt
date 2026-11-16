package com.rio.board

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR code maker (with the app icon in the middle) and QR reader (from a picture). Uses the small ZXing core library. */
object QrKit {

    /** Returns null when the text is too long for a QR code. */
    fun make(ctx: Context, text: String, size: Int = 900): Bitmap? {
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H     // 30% can be covered: room for the icon
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"                      // Sinhala works
        hints[EncodeHintType.MARGIN] = 0
        val m = try {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        } catch (e: Exception) {
            return null
        }
        val n = m.width
        val quiet = 4
        val total = n + quiet * 2
        val scale = maxOf(1, size / total)
        val px = total * scale
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val cv = Canvas(bmp)
        val p = Paint().apply { color = Color.BLACK; isAntiAlias = false }
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (m.get(x, y)) {
                    val l = ((x + quiet) * scale).toFloat()
                    val t = ((y + quiet) * scale).toFloat()
                    cv.drawRect(l, t, l + scale, t + scale, p)
                }
            }
        }
        // app icon in the middle (about 5% of the code area, well inside what level H can lose)
        try {
            val box = (px * 0.27f).toInt()
            val bl = (px - box) / 2
            val white = Paint().apply { color = Color.WHITE; isAntiAlias = true }
            cv.drawRoundRect(RectF(bl.toFloat(), bl.toFloat(), (bl + box).toFloat(), (bl + box).toFloat()), box * 0.22f, box * 0.22f, white)
            val inner = (px * 0.22f).toInt()
            val il = (px - inner) / 2
            val d = ctx.resources.getDrawable(R.mipmap.ic_launcher_round, ctx.theme)
            d.setBounds(il, il, il + inner, il + inner)
            d.draw(cv)
        } catch (e: Exception) {
        }
        return bmp
    }

    /** Reads a QR code from a picture. Returns null when none is found. */
    fun decode(src: Bitmap): String? {
        var bmp = src
        val maxSide = 1600
        if (maxOf(bmp.width, bmp.height) > maxSide) {
            val k = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true)
        }
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val source: LuminanceSource = RGBLuminanceSource(w, h, px)
        val hints = HashMap<DecodeHintType, Any>()
        hints[DecodeHintType.TRY_HARDER] = true
        hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)
        fun attempt(s: LuminanceSource): String? = try {
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(s)), hints).text
        } catch (e: Exception) {
            null
        }
        return attempt(source) ?: attempt(source.invert())      // second try: light code on dark background
    }
}
