package com.rio.board

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.View
import java.util.Random
import kotlin.math.sin

/**
 * Transparent overlay that draws falling effects.
 * effect: 0 snow, 1 rain, 2 hearts, 3 stars, 4 confetti, 5 bubbles (rise up).
 * Does not consume touches.
 */
class SnowView(context: Context) : View(context) {

    private class Flake(
        var x: Float, var y: Float,
        val r: Float, val vy: Float,
        var phase: Float, val amp: Float,
        val ci: Int
    )

    private val flakes = ArrayList<Flake>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rnd = Random()
    private val d = resources.displayMetrics.density
    private var last = 0L

    var colors: IntArray = intArrayOf(Color.WHITE)
    var base: Int = 45

    /** Effects only appear below this y (keeps them on the keyboard area only). */
    var topLimit: Float = 0f

    var effect: Int = 0
        set(value) {
            if (field != value) {
                field = value
                flakes.clear()
                last = 0L
                invalidate()
            }
        }

    var running: Boolean = false
        set(value) {
            field = value
            last = 0L
            if (!value) flakes.clear()
            invalidate()
        }

    private val speed: Float
        get() = when (effect) {
            1 -> 7f
            2 -> 0.8f
            3 -> 0.7f
            4 -> 1.6f
            5 -> 0.9f
            else -> 1f
        }

    private val goesUp: Boolean get() = effect == 5
    private val count: Int get() = if (effect == 1) (base * 1.6f).toInt() else base

    fun burst() {
        if (!running || width == 0) return
        repeat(4) {
            if (flakes.size < 160) flakes.add(newFlake(true))
        }
    }

    private fun newFlake(entry: Boolean): Flake {
        val r = (1.2f + rnd.nextFloat() * 2.8f) * d
        val span = (height - topLimit).coerceAtLeast(1f)
        val y = if (entry) {
            if (goesUp) height + r + rnd.nextFloat() * 30f * d
            else topLimit - r - rnd.nextFloat() * 30f * d
        } else {
            topLimit + rnd.nextFloat() * span
        }
        val amp = if (effect == 1) 0f else (4f + rnd.nextFloat() * 12f) * d
        return Flake(
            rnd.nextFloat() * width, y, r,
            (35f + rnd.nextFloat() * 75f) * d * speed,
            rnd.nextFloat() * 6.28f,
            amp,
            rnd.nextInt(1000)
        )
    }

    private fun drawHeart(c: Canvas, cx: Float, cy: Float, s: Float) {
        path.reset()
        path.moveTo(cx, cy + s)
        path.cubicTo(cx - 1.6f * s, cy - 0.2f * s, cx - 0.9f * s, cy - 1.3f * s, cx, cy - 0.5f * s)
        path.cubicTo(cx + 0.9f * s, cy - 1.3f * s, cx + 1.6f * s, cy - 0.2f * s, cx, cy + s)
        path.close()
        c.drawPath(path, paint)
    }

    private fun drawSparkle(c: Canvas, cx: Float, cy: Float, s: Float) {
        path.reset()
        path.moveTo(cx, cy - s)
        path.quadTo(cx, cy, cx + s, cy)
        path.quadTo(cx, cy, cx, cy + s)
        path.quadTo(cx, cy, cx - s, cy)
        path.quadTo(cx, cy, cx, cy - s)
        path.close()
        c.drawPath(path, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!running || width == 0 || height == 0) return

        val now = SystemClock.uptimeMillis()
        val dt = if (last == 0L) 0.016f else minOf((now - last) / 1000f, 0.05f)
        last = now

        val n = count
        while (flakes.size < n) flakes.add(newFlake(false))

        val cols = if (colors.isEmpty()) intArrayOf(Color.WHITE) else colors
        canvas.save()
        canvas.clipRect(0f, topLimit, width.toFloat(), height.toFloat())
        val iter = flakes.iterator()
        while (iter.hasNext()) {
            val f = iter.next()
            f.y += if (goesUp) -f.vy * dt else f.vy * dt
            f.phase += dt * 1.6f
            val out = if (goesUp) f.y + f.r * 3f < topLimit else f.y - f.r * 3f > height
            if (out) {
                if (flakes.size > n) {
                    iter.remove()
                    continue
                }
                f.y = if (goesUp) height + f.r * 3f else topLimit - f.r * 3f
                f.x = rnd.nextFloat() * width
            }
            paint.style = Paint.Style.FILL
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = cols[f.ci % cols.size]
            paint.alpha = (120 + (f.r / (4f * d)) * 135).toInt().coerceIn(80, 255)
            val x = f.x + sin(f.phase) * f.amp
            when (effect) {
                1 -> {
                    val len = f.r * 6f
                    paint.strokeWidth = 1.3f * d
                    canvas.drawLine(x, f.y - len, x - len * 0.2f, f.y, paint)
                }
                2 -> drawHeart(canvas, x, f.y, f.r * 2.4f)
                3 -> {
                    paint.alpha = (60 + 195 * (0.5f + 0.5f * sin(f.phase * 3f))).toInt().coerceIn(40, 255)
                    drawSparkle(canvas, x, f.y, f.r * 2.6f)
                }
                4 -> {
                    canvas.save()
                    canvas.translate(x, f.y)
                    canvas.rotate(f.phase * 120f)
                    canvas.drawRect(-f.r, -f.r * 0.55f, f.r, f.r * 0.55f, paint)
                    canvas.restore()
                }
                5 -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.2f * d
                    paint.alpha = 150
                    canvas.drawCircle(x, f.y, f.r * 3f, paint)
                }
                else -> canvas.drawCircle(x, f.y, f.r, paint)
            }
        }
        canvas.restore()
        postInvalidateOnAnimation()
    }
}
