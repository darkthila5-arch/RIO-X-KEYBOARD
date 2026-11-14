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

    enum class Kind { CLIPBOARD, HISTORY, SELECT_ALL, COPY, CUT, SNOW, THEME, SETTINGS, BACK, TRASH, EMOJI, UNDO, REDO, LEFT, RIGHT, GLOBE, GRID, MIC, BOLT, IMAGE, BOMB, STATUS, CALENDAR, HOME, END, UP, DOWN }

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
            Kind.EMOJI -> {
                c.drawCircle(12f, 12f, 9f, p)
                c.drawCircle(9f, 10f, 1.2f, fill)
                c.drawCircle(15f, 10f, 1.2f, fill)
                path.reset()
                path.moveTo(8f, 14f)
                path.quadTo(12f, 18.5f, 16f, 14f)
                c.drawPath(path, p)
            }
            Kind.UNDO -> drawUndo(c)
            Kind.REDO -> {
                c.save()
                c.translate(24f, 0f)
                c.scale(-1f, 1f)
                drawUndo(c)
                c.restore()
            }
            Kind.LEFT -> {
                path.reset()
                path.moveTo(15f, 5f)
                path.lineTo(8f, 12f)
                path.lineTo(15f, 19f)
                c.drawPath(path, p)
            }
            Kind.RIGHT -> {
                path.reset()
                path.moveTo(9f, 5f)
                path.lineTo(16f, 12f)
                path.lineTo(9f, 19f)
                c.drawPath(path, p)
            }
            Kind.GLOBE -> {
                c.drawCircle(12f, 12f, 9f, p)
                c.drawOval(RectF(8f, 3f, 16f, 21f), p)
                c.drawLine(3f, 12f, 21f, 12f, p)
            }
            Kind.GRID -> {
                c.drawRoundRect(RectF(4f, 4f, 10.5f, 10.5f), 1.5f, 1.5f, p)
                c.drawRoundRect(RectF(13.5f, 4f, 20f, 10.5f), 1.5f, 1.5f, p)
                c.drawRoundRect(RectF(4f, 13.5f, 10.5f, 20f), 1.5f, 1.5f, p)
                c.drawRoundRect(RectF(13.5f, 13.5f, 20f, 20f), 1.5f, 1.5f, p)
            }
            Kind.MIC -> {
                c.drawRoundRect(RectF(9f, 3f, 15f, 14f), 3f, 3f, p)
                c.drawArc(RectF(6f, 6f, 18f, 18f), 0f, 180f, false, p)
                c.drawLine(12f, 18f, 12f, 21f, p)
                c.drawLine(9f, 21f, 15f, 21f, p)
            }
            Kind.BOLT -> {
                path.reset()
                path.moveTo(13.5f, 3f)
                path.lineTo(6f, 13f)
                path.lineTo(11.5f, 13f)
                path.lineTo(10.5f, 21f)
                path.lineTo(18f, 10.5f)
                path.lineTo(12.5f, 10.5f)
                path.close()
                c.drawPath(path, p)
            }
            Kind.IMAGE -> {
                c.drawRoundRect(RectF(3.5f, 4.5f, 20.5f, 19.5f), 2.5f, 2.5f, p)
                c.drawCircle(8.5f, 9.5f, 1.6f, p)
                path.reset()
                path.moveTo(4f, 18f)
                path.lineTo(9.5f, 12.5f)
                path.lineTo(13f, 16f)
                path.lineTo(15.5f, 13.5f)
                path.lineTo(20f, 18f)
                c.drawPath(path, p)
            }
            Kind.BOMB -> {
                c.drawCircle(10.5f, 14f, 6.5f, p)
                c.drawArc(RectF(6.5f, 10f, 11f, 14.5f), 180f, 90f, false, p)
                c.drawLine(15f, 9.5f, 17.5f, 7f, p)
                c.drawLine(19.5f, 2f, 19.5f, 3.4f, p)
                c.drawLine(22f, 4.7f, 20.6f, 4.7f, p)
                c.drawLine(21.2f, 2.8f, 20.3f, 3.7f, p)
                c.drawLine(17.8f, 2.8f, 18.7f, 3.7f, p)
            }
            Kind.STATUS -> {
                p.pathEffect = DashPathEffect(floatArrayOf(3.2f, 2.4f), 0f)
                c.drawCircle(12f, 12f, 9f, p)
                p.pathEffect = null
                c.drawLine(12f, 7.5f, 12f, 15f, p)
                path.reset()
                path.moveTo(8.5f, 11.5f)
                path.lineTo(12f, 15.2f)
                path.lineTo(15.5f, 11.5f)
                c.drawPath(path, p)
            }
            Kind.CALENDAR -> {
                c.drawRoundRect(RectF(4f, 5f, 20f, 20f), 2.5f, 2.5f, p)
                c.drawLine(4f, 10f, 20f, 10f, p)
                c.drawLine(8.5f, 3f, 8.5f, 7f, p)
                c.drawLine(15.5f, 3f, 15.5f, 7f, p)
                c.drawCircle(8.5f, 13.8f, 1f, fill)
                c.drawCircle(12f, 13.8f, 1f, fill)
                c.drawCircle(15.5f, 13.8f, 1f, fill)
                c.drawCircle(8.5f, 17f, 1f, fill)
                c.drawCircle(12f, 17f, 1f, fill)
            }
            Kind.HOME -> {
                c.drawLine(5.5f, 5f, 5.5f, 19f, p)
                path.reset()
                path.moveTo(17f, 6f)
                path.lineTo(10f, 12f)
                path.lineTo(17f, 18f)
                c.drawPath(path, p)
            }
            Kind.END -> {
                c.drawLine(18.5f, 5f, 18.5f, 19f, p)
                path.reset()
                path.moveTo(7f, 6f)
                path.lineTo(14f, 12f)
                path.lineTo(7f, 18f)
                c.drawPath(path, p)
            }
            Kind.UP -> {
                path.reset()
                path.moveTo(5f, 15f)
                path.lineTo(12f, 8f)
                path.lineTo(19f, 15f)
                c.drawPath(path, p)
            }
            Kind.DOWN -> {
                path.reset()
                path.moveTo(5f, 9f)
                path.lineTo(12f, 16f)
                path.lineTo(19f, 9f)
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

    private fun drawUndo(c: Canvas) {
        path.reset()
        path.moveTo(9f, 5f)
        path.lineTo(4.5f, 9.5f)
        path.lineTo(9f, 14f)
        c.drawPath(path, p)
        path.reset()
        path.moveTo(4.5f, 9.5f)
        path.lineTo(13f, 9.5f)
        path.cubicTo(18f, 9.5f, 20f, 13f, 19.5f, 18f)
        c.drawPath(path, p)
    }
}
