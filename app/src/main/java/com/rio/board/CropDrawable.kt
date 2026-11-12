package com.rio.board

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

/** Draws a photo scaled to fill the keyboard area (centre-crop, nothing stretched). */
class CropDrawable(private val bmp: Bitmap) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val scale = maxOf(b.width() / bw, b.height() / bh)
        val sw = b.width() / scale
        val sh = b.height() / scale
        val left = (bw - sw) / 2f
        val top = (bh - sh) / 2f
        val src = Rect(left.toInt(), top.toInt(), (left + sw).toInt(), (top + sh).toInt())
        canvas.drawBitmap(bmp, src, b, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
