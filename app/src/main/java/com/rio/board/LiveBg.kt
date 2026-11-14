package com.rio.board

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.SystemClock
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

enum class LiveKind { AURORA, GALAXY, LAVA, NEON, WAVES, MATRIX, RGB }

/**
 * Animated keyboard background ("live theme"). Pure drawing code, ~30 fps,
 * pauses by itself when the keyboard is hidden (Drawable.setVisible(false)).
 */
class LiveBgDrawable(private val kind: LiveKind, private val d: Float) : Drawable() {

    private companion object {
        const val TAU = 6.2831855f
    }

    private val t0 = SystemClock.uptimeMillis()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rnd = java.util.Random(7)
    private val addMode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val hsv = FloatArray(3)
    private var running = true
    private val tick = Runnable { invalidateSelf() }

    private var w = 0f
    private var h = 0f
    private var baseShader: Shader? = null
    private val ribbonShaders = arrayOfNulls<Shader>(4)
    private var ribbonBand = 0f
    private val glows = HashMap<Int, RadialGradient>()

    // star field / bubbles share these
    private val n = 70
    private val sx = FloatArray(n) { rnd.nextFloat() }
    private val sy = FloatArray(n) { rnd.nextFloat() }
    private val ss = FloatArray(n) { 0.5f + rnd.nextFloat() * 1.3f }
    private val sv = FloatArray(n) { 0.2f + rnd.nextFloat() * 1.3f }
    private val sp = FloatArray(n) { rnd.nextFloat() * TAU }

    // matrix rain
    private var cols = 0
    private var head = FloatArray(0)
    private var speed = FloatArray(0)
    private var lastT = 0f
    private val glyphs = "01ABCDEF23456789<>/+=*#%"

    private val auroraCols = intArrayOf(0xFF2CFFB0.toInt(), 0xFF3D9BFF.toInt(), 0xFF9B5CFF.toInt(), 0xFF2CFFB0.toInt())
    private val lavaCols = intArrayOf(0xFFFF5A1F.toInt(), 0xFFFF2D6F.toInt(), 0xFFFFB000.toInt(), 0xFFB0124A.toInt())
    private val neonCols = intArrayOf(0xFF00E5FF.toInt(), 0xFFFF2EE6.toInt(), 0xFF6A5CFF.toInt(), 0xFF00FFA3.toInt())
    private val nebulaCols = intArrayOf(0xFF6A2CFF.toInt(), 0xFFFF3DA8.toInt(), 0xFF2C6BFF.toInt())
    private val waveCols = intArrayOf(0x401B8FB5, 0x382AB7CA, 0x305ED3E8, 0x28A8F0FF)

    // ------------------------------------------------------------------ Drawable plumbing
    override fun onBoundsChange(bounds: Rect) {
        w = bounds.width().toFloat()
        h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        when (kind) {
            LiveKind.AURORA -> {
                baseShader = LinearGradient(0f, 0f, 0f, h, 0xFF050B1A.toInt(), 0xFF0B1E3A.toInt(), Shader.TileMode.CLAMP)
                ribbonBand = h * 0.9f
                for (i in 0 until 4) {
                    val c = auroraCols[i] and 0xFFFFFF
                    ribbonShaders[i] = LinearGradient(
                        0f, 0f, 0f, ribbonBand,
                        intArrayOf(c, c or (0xB0 shl 24), c),
                        floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
                    )
                }
            }
            LiveKind.GALAXY -> baseShader = RadialGradient(
                w * 0.5f, h * 0.4f, max(w, h) * 0.9f,
                0xFF1A0F3D.toInt(), 0xFF07041A.toInt(), Shader.TileMode.CLAMP
            )
            LiveKind.WAVES -> baseShader = LinearGradient(
                0f, 0f, 0f, h, 0xFF063049.toInt(), 0xFF021822.toInt(), Shader.TileMode.CLAMP
            )
            LiveKind.MATRIX -> {
                val cell = 13f * d
                cols = max(1, (w / cell).toInt())
                head = FloatArray(cols) { -rnd.nextFloat() * h }
                speed = FloatArray(cols) { (60f + rnd.nextFloat() * 120f) * d }
            }
            else -> {}
        }
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val r = super.setVisible(visible, restart)
        running = visible
        if (visible) invalidateSelf() else unscheduleSelf(tick)
        return r
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    override fun draw(c: Canvas) {
        if (w <= 0f || h <= 0f) return
        val b = bounds
        val t = (SystemClock.uptimeMillis() - t0) / 1000f
        c.save()
        c.clipRect(b)
        c.translate(b.left.toFloat(), b.top.toFloat())
        p.reset()
        p.isAntiAlias = true
        when (kind) {
            LiveKind.AURORA -> drawAurora(c, t)
            LiveKind.GALAXY -> drawGalaxy(c, t)
            LiveKind.LAVA -> drawBlobs(c, t, 0xFF1A0505.toInt(), lavaCols)
            LiveKind.NEON -> drawBlobs(c, t, 0xFF070617.toInt(), neonCols)
            LiveKind.WAVES -> drawWaves(c, t)
            LiveKind.MATRIX -> drawMatrix(c, t)
            LiveKind.RGB -> drawRgb(c, t)
        }
        c.restore()
        if (running) scheduleSelf(tick, SystemClock.uptimeMillis() + 33)
    }

    private fun glow(rgb: Int): RadialGradient {
        val key = rgb and 0xFFFFFF
        glows[key]?.let { return it }
        val g = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(key or (0xFF shl 24), key or (0x70 shl 24), key),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        glows[key] = g
        return g
    }

    // ------------------------------------------------------------------ AURORA
    private fun drawAurora(c: Canvas, t: Float) {
        p.shader = baseShader
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        p.xfermode = addMode
        for (i in 0 until 4) {
            val baseY = h * (0.08f + 0.15f * i)
            val amp = h * (0.07f + 0.02f * i)
            val f = TAU / (w * (0.9f - 0.1f * i))
            val spd = (0.35f + 0.12f * i) * (if (i % 2 == 0) 1f else -1f)
            path.reset()
            var x = 0f
            path.moveTo(0f, sin(f * 0f + t * spd + i * 1.7f) * amp)
            while (x <= w) {
                val y = sin(x * f + t * spd + i * 1.7f) * amp + sin(x * f * 2.3f - t * spd * 0.8f) * amp * 0.4f
                path.lineTo(x, y)
                x += 14f * d
            }
            x = w
            while (x >= 0f) {
                path.lineTo(x, ribbonBand * 0.5f + sin(x * f * 0.8f + t * spd * 0.6f + i) * amp * 0.5f)
                x -= 14f * d
            }
            path.close()
            c.save()
            c.translate(0f, baseY)
            p.shader = ribbonShaders[i]
            p.alpha = 210
            c.drawPath(path, p)
            c.restore()
        }
    }

    // ------------------------------------------------------------------ GALAXY
    private fun drawGalaxy(c: Canvas, t: Float) {
        p.shader = baseShader
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        p.xfermode = addMode
        for (i in 0 until 3) {
            val cx = w * (0.5f + 0.35f * sin(t * 0.13f + i * 2.1f))
            val cy = h * (0.5f + 0.30f * cos(t * 0.11f + i * 1.3f))
            val r = max(w, h) * (0.5f + 0.1f * i)
            c.save()
            c.translate(cx, cy)
            c.scale(r, r)
            p.shader = glow(nebulaCols[i])
            p.alpha = 70
            c.drawCircle(0f, 0f, 1f, p)
            c.restore()
        }
        p.shader = null
        p.color = Color.WHITE
        for (i in 0 until n) {
            val x = (sx[i] * w + t * sv[i] * 6f * d) % w
            val y = sy[i] * h
            val tw = 0.55f + 0.45f * sin(t * (0.8f + sv[i]) + sp[i])
            p.alpha = (tw * 255f * (0.4f + 0.6f * ss[i] / 1.8f)).toInt().coerceIn(0, 255)
            c.drawCircle(x, y, ss[i] * d, p)
        }
    }

    // ------------------------------------------------------------------ LAVA / NEON (drifting colour blobs)
    private fun drawBlobs(c: Canvas, t: Float, base: Int, colors: IntArray) {
        p.color = base
        c.drawRect(0f, 0f, w, h, p)
        p.xfermode = addMode
        for (i in 0 until 4) {
            val cx = w * (0.5f + 0.42f * sin(t * (0.21f + 0.07f * i) + i * 1.9f))
            val cy = h * (0.5f + 0.45f * sin(t * (0.17f + 0.05f * i) + i * 2.7f))
            val r = w * (0.30f + 0.05f * i) + h * 0.2f
            c.save()
            c.translate(cx, cy)
            c.scale(r, r)
            p.shader = glow(colors[i])
            p.alpha = 190
            c.drawCircle(0f, 0f, 1f, p)
            c.restore()
        }
    }

    // ------------------------------------------------------------------ WAVES
    private fun drawWaves(c: Canvas, t: Float) {
        p.shader = baseShader
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        // tiny rising bubbles
        p.style = Paint.Style.STROKE
        p.strokeWidth = d
        p.color = Color.WHITE
        for (i in 0 until 14) {
            val bx = sx[i] * w + sin(t * 0.8f + i) * 6f * d
            val by = h - ((t * (18f + sv[i] * 14f) * d + sy[i] * h) % (h + 20f * d)) + 10f * d
            p.alpha = 60
            c.drawCircle(bx, by, (2f + ss[i] * 2f) * d, p)
        }
        p.style = Paint.Style.FILL
        for (i in 0 until 4) {
            val y0 = h * (0.42f + 0.14f * i)
            val amp = h * (0.045f - 0.006f * i)
            val f = TAU / (w * (0.55f + 0.18f * i))
            val spd = (0.9f + 0.35f * i) * (if (i % 2 == 0) 1f else -1f)
            path.reset()
            path.moveTo(0f, h)
            var x = 0f
            while (x <= w) {
                path.lineTo(x, y0 + sin(x * f + t * spd + i * 1.3f) * amp + sin(x * f * 1.7f - t * spd * 0.6f) * amp * 0.35f)
                x += 10f * d
            }
            path.lineTo(w, h)
            path.close()
            p.color = waveCols[i]
            c.drawPath(path, p)
        }
    }

    // ------------------------------------------------------------------ MATRIX
    private fun drawMatrix(c: Canvas, t: Float) {
        p.color = 0xFF020A04.toInt()
        c.drawRect(0f, 0f, w, h, p)
        val dt = (t - lastT).coerceIn(0f, 0.1f)
        lastT = t
        val cell = 13f * d
        val trail = 12
        p.typeface = Typeface.MONOSPACE
        p.textSize = cell * 0.95f
        p.textAlign = Paint.Align.CENTER
        for (col in 0 until cols) {
            head[col] += speed[col] * dt
            if (head[col] - trail * cell > h) {
                head[col] = -rnd.nextFloat() * h * 0.5f
                speed[col] = (60f + rnd.nextFloat() * 120f) * d
            }
            val x = col * cell + cell / 2f
            for (k in 0 until trail) {
                val y = head[col] - k * cell
                if (y < -cell || y > h + cell) continue
                val row = (y / cell).toInt()
                val flick = if (k < 2) (t * 10f).toInt() else (t * 1.5f).toInt()
                val idx = Math.floorMod(col * 31 + row * 17 + flick, glyphs.length)
                if (k == 0) {
                    p.color = 0xFFE8FFEF.toInt()
                } else {
                    val f = 1f - k / trail.toFloat()
                    p.color = Color.argb((255f * f * f).toInt(), 0, 255, 102)
                }
                c.drawText(glyphs, idx, idx + 1, x, y, p)
            }
        }
    }

    // ------------------------------------------------------------------ RGB (slow rainbow sweep)
    private fun col(h0: Float, s: Float, v: Float): Int {
        hsv[0] = ((h0 % 360f) + 360f) % 360f
        hsv[1] = s
        hsv[2] = v
        return Color.HSVToColor(hsv)
    }

    private fun drawRgb(c: Canvas, t: Float) {
        val hue = (t * 22f) % 360f
        val a = t * 0.25f
        val cx = w / 2f
        val cy = h / 2f
        val r = hypot(w, h) / 2f
        p.shader = LinearGradient(
            cx - cos(a) * r, cy - sin(a) * r, cx + cos(a) * r, cy + sin(a) * r,
            intArrayOf(col(hue, 0.8f, 0.72f), col(hue + 70f, 0.8f, 0.66f), col(hue + 140f, 0.8f, 0.72f)),
            null, Shader.TileMode.MIRROR
        )
        c.drawRect(0f, 0f, w, h, p)
    }
}
