package com.rio.board

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import java.util.Random
import kotlin.math.sin

/** Transparent overlay that draws falling snow. Does not consume touches. */
class SnowView(context: Context) : View(context) {

    private class Flake(
        var x: Float, var y: Float,
        val r: Float, val vy: Float,
        var phase: Float, val amp: Float
    )

    private val flakes = ArrayList<Flake>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rnd = Random()
    private val d = resources.displayMetrics.density
    private var last = 0L

    var color: Int = Color.WHITE
    var base: Int = 45

    /** Snow only appears below this y (used to keep snow on the keyboard area only). */
    var topLimit: Float = 0f

    var running: Boolean = false
        set(value) {
            field = value
            last = 0L
            if (!value) flakes.clear()
            invalidate()
        }

    /** Extra flakes on every key press. */
    fun burst() {
        if (!running || width == 0) return
        repeat(4) {
            if (flakes.size < 150) flakes.add(newFlake(true))
        }
    }

    private fun newFlake(top: Boolean): Flake {
        val r = (1.2f + rnd.nextFloat() * 2.8f) * d
        val span = (height - topLimit).coerceAtLeast(1f)
        val y = if (top) topLimit - r - rnd.nextFloat() * 30f * d
        else topLimit + rnd.nextFloat() * span
        return Flake(
            rnd.nextFloat() * width, y, r,
            (35f + rnd.nextFloat() * 75f) * d,
            rnd.nextFloat() * 6.28f,
            (4f + rnd.nextFloat() * 12f) * d
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!running || width == 0 || height == 0) return

        val now = SystemClock.uptimeMillis()
        val dt = if (last == 0L) 0.016f else minOf((now - last) / 1000f, 0.05f)
        last = now

        while (flakes.size < base) flakes.add(newFlake(false))

        canvas.save()
        canvas.clipRect(0f, topLimit, width.toFloat(), height.toFloat())
        paint.color = color
        val iter = flakes.iterator()
        while (iter.hasNext()) {
            val f = iter.next()
            f.y += f.vy * dt
            f.phase += dt * 1.6f
            if (f.y - f.r > height) {
                if (flakes.size > base) {
                    iter.remove()
                    continue
                }
                f.y = topLimit - f.r
                f.x = rnd.nextFloat() * width
            }
            paint.alpha = (120 + (f.r / (4f * d)) * 135).toInt().coerceIn(80, 255)
            canvas.drawCircle(f.x + sin(f.phase) * f.amp, f.y, f.r, paint)
        }
        canvas.restore()
        postInvalidateOnAnimation()
    }
}
