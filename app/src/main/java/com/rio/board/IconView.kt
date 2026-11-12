package com.rio.board

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Simple line icons drawn in code (no image files needed). */
class IconView(context: Context, private val kind: Kind) : View(context) {

    enum class Kind { CLIPBOARD, HISTORY, SELECT_ALL, COPY, CUT, SNOW, THEME, SETTINGS, BACK, TRASH }

    var tint: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val size = minOf(width, height) * 0.56f
        val sc = size / 24f
        c.save()
        c.translate((width - size) / 2f, (height - size) / 2f)
        c.scale(sc, sc)

        p.color = tint
        fill.color = tint
        p.strokeWidth = 1.8f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.pathEffect = null

        when (kind) {
            Kind.CLIPBOARD -> {
                c.drawRoundRect(RectF(5f, 4f, 19f, 21f), 2.5f, 2.5f, p)
                c.drawRoundRect(RectF(9f, 2f, 15f, 6f), 1.5f, 1.5f, p)
                c.drawLine(8.5f, 11f, 15.5f, 11f, p)
                c.drawLine(8.5f, 15f, 13f, 15f, p)
            }
            Kind.HISTORY -> {
                c.drawCircle(12f, 12f, 8.5f, p)
                path.reset()
                path.moveTo(12f, 7.5f)
                path.lineTo(12f, 12f)
                path.lineTo(15.5f, 14f)
                c.drawPath(path, p)
            }
            Kind.SELECT_ALL -> {
                p.pathEffect = DashPathEffect(floatArrayOf(2.6f, 2.4f), 0f)
                c.drawRoundRect(RectF(3.5f, 3.5f, 20.5f, 20.5f), 2f, 2f, p)
                p.pathEffect = null
                c.drawRoundRect(RectF(8f, 8f, 16f, 16f), 1.5f, 1.5f, p)
            }
            Kind.COPY -> {
                c.drawRoundRect(RectF(9f, 9f, 20f, 21f), 2f, 2f, p)
                path.reset()
                path.moveTo(9f, 15f)
                path.lineTo(4f, 15f)
                path.lineTo(4f, 3f)
                path.lineTo(15f, 3f)
                path.lineTo(15f, 9f)
                c.drawPath(path, p)
            }
            Kind.CUT -> {
                c.drawCircle(7f, 17.5f, 3f, p)
                c.drawCircle(17f, 17.5f, 3f, p)
                c.drawLine(9.3f, 15.3f, 17.5f, 3.5f, p)
                c.drawLine(14.7f, 15.3f, 6.5f, 3.5f, p)
            }
            Kind.SNOW -> {
                c.save()
                c.translate(12f, 12f)
                repeat(6) {
                    c.drawLine(0f, 0f, 9.5f, 0f, p)
                    c.drawLine(6f, 0f, 8.4f, -2.4f, p)
                    c.drawLine(6f, 0f, 8.4f, 2.4f, p)
                    c.rotate(60f)
                }
                c.restore()
            }
            Kind.THEME -> {
                c.drawCircle(12f, 12f, 9f, p)
                c.drawCircle(8f, 10f, 1.4f, fill)
                c.drawCircle(12f, 7.2f, 1.4f, fill)
                c.drawCircle(16f, 10f, 1.4f, fill)
                c.drawCircle(8.3f, 15f, 1.4f, fill)
                c.drawCircle(15.5f, 15.5f, 2.2f, p)
            }
            Kind.SETTINGS -> {
                c.drawCircle(12f, 12f, 3f, p)
                c.drawCircle(12f, 12f, 6.4f, p)
                p.strokeWidth = 3.2f
                p.strokeCap = Paint.Cap.BUTT
                c.save()
                c.translate(12f, 12f)
                repeat(8) {
                    c.drawLine(6.4f, 0f, 9.4f, 0f, p)
                    c.rotate(45f)
                }
                c.restore()
            }
            Kind.BACK -> {
                c.drawLine(19f, 12f, 5f, 12f, p)
                path.reset()
                path.moveTo(11f, 6f)
                path.lineTo(5f, 12f)
                path.lineTo(11f, 18f)
                c.drawPath(path, p)
            }
            Kind.TRASH -> {
                c.drawLine(4f, 7f, 20f, 7f, p)
                path.reset()
                path.moveTo(6f, 7f)
                path.lineTo(7f, 20f)
                path.lineTo(17f, 20f)
                path.lineTo(18f, 7f)
                c.drawPath(path, p)
                path.reset()
                path.moveTo(9f, 7f)
                path.lineTo(9f, 4f)
                path.lineTo(15f, 4f)
                path.lineTo(15f, 7f)
                c.drawPath(path, p)
                c.drawLine(10f, 11f, 10f, 17f, p)
                c.drawLine(14f, 11f, 14f, 17f, p)
            }
        }
        c.restore()
    }
}
