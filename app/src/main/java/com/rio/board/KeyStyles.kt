package com.rio.board

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object KeyStyles {
    val templates = listOf("Classic", "Frost", "Fluffy", "Sci-Fi", "Glass", "Bubble", "Neon")
    val decors = listOf("None", "Snow ❄", "Hearts ♥", "Flowers ✿", "Stars ✦", "Bows 🎀", "Paws 🐾", "Mix ✨")

    // ornament kinds
    const val SNOW = 1
    const val HEART = 2
    const val FLOWER = 3
    const val STAR = 4
    const val BOW = 5
    const val PAW = 6
    const val SPARKLE = 7

    // unit shapes (about -1..1) shared by every key
    val heart = Path().apply {
        moveTo(0f, 0.95f)
        cubicTo(-0.25f, 0.70f, -1.00f, 0.25f, -1.00f, -0.30f)
        cubicTo(-1.00f, -0.80f, -0.45f, -1.00f, 0f, -0.55f)
        cubicTo(0.45f, -1.00f, 1.00f, -0.80f, 1.00f, -0.30f)
        cubicTo(1.00f, 0.25f, 0.25f, 0.70f, 0f, 0.95f)
        close()
    }
    val star = Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) 1f else 0.45f
            val a = Math.toRadians(-90.0 + i * 36.0)
            val x = (cos(a) * r).toFloat()
            val y = (sin(a) * r).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    val sparkle = Path().apply {
        moveTo(0f, -1f)
        quadTo(0f, 0f, 1f, 0f)
        quadTo(0f, 0f, 0f, 1f)
        quadTo(0f, 0f, -1f, 0f)
        quadTo(0f, 0f, 0f, -1f)
        close()
    }
    val bow = Path().apply {
        moveTo(0f, 0f); lineTo(-1f, -0.65f); lineTo(-1f, 0.65f); close()
        moveTo(0f, 0f); lineTo(1f, -0.65f); lineTo(1f, 0.65f); close()
    }
    val paw = Path().apply {
        addOval(RectF(-0.55f, 0.0f, 0.55f, 0.85f), Path.Direction.CW)
        addCircle(-0.75f, -0.20f, 0.26f, Path.Direction.CW)
        addCircle(-0.28f, -0.62f, 0.26f, Path.Direction.CW)
        addCircle(0.28f, -0.62f, 0.26f, Path.Direction.CW)
        addCircle(0.75f, -0.20f, 0.26f, Path.Direction.CW)
    }
}

/**
 * One key background. [tpl] is the look (see KeyStyles.templates), [decor] the little
 * ornaments in the key corners (see KeyStyles.decors). Drawn with plain canvas calls.
 */
class KeyDrawable(
    private val d: Float,
    private val tpl: Int,
    private val fill: Int,
    private val accent: Int,
    private val pressed: Boolean,
    private val decor: Int,
    private val seed: Int
) : Drawable() {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val path = Path()
    private var w = 0f
    private var h = 0f
    private var furA = FloatArray(0)
    private var furB = FloatArray(0)
    private var shader: Shader? = null
    private var shader2: Shader? = null

    // ------------------------------------------------------------------ colour helpers
    private fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.argb(
            Color.alpha(a) + ((Color.alpha(b) - Color.alpha(a)) * k).toInt(),
            Color.red(a) + ((Color.red(b) - Color.red(a)) * k).toInt(),
            Color.green(a) + ((Color.green(b) - Color.green(a)) * k).toInt(),
            Color.blue(a) + ((Color.blue(b) - Color.blue(a)) * k).toInt()
        )
    }

    private fun lighten(c: Int, t: Float) = mix(c, Color.argb(Color.alpha(c), 255, 255, 255), t)
    private fun darken(c: Int, t: Float) = mix(c, Color.argb(Color.alpha(c), 0, 0, 0), t)
    private fun withAlpha(c: Int, a: Int) = (c and 0xFFFFFF) or (a.coerceIn(0, 255) shl 24)
    private fun scaleAlpha(c: Int, f: Float) = withAlpha(c, (Color.alpha(c) * f).toInt())

    // ------------------------------------------------------------------ drawable plumbing
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun onBoundsChange(b: Rect) {
        w = b.width().toFloat()
        h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return
        shader = null
        shader2 = null
        when (tpl) {
            2 -> buildFur(b)
            4 -> shader = LinearGradient(0f, 0f, 0f, h * 0.55f, 0x5FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            5 -> {
                val top = lighten(fill, 0.28f)
                val bot = darken(fill, 0.24f)
                shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(top, fill, bot), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                shader2 = LinearGradient(0f, 0f, 0f, h * 0.42f, 0x73FFFFFF, 0x0DFFFFFF, Shader.TileMode.CLAMP)
            }
        }
    }

    /** Fluffy keys: soft plush body with a fringe of short hairs all around the edge. */
    private fun buildFur(b: Rect) {
        val inset = 4.2f * d
        val rr = RectF(inset, inset, w - inset, h - inset)
        val pth = Path()
        pth.addRoundRect(rr, 12f * d, 12f * d, Path.Direction.CW)
        val pm = PathMeasure(pth, true)
        val len = pm.length
        val n = max(8, (len / (2.2f * d)).toInt())
        val a = FloatArray((n / 2 + 1) * 4)
        val bb = FloatArray((n / 2 + 1) * 4)
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        val rnd = java.util.Random(seed.toLong() * 31L + b.width())
        var ia = 0
        var ib = 0
        for (i in 0 until n) {
            pm.getPosTan(i * len / n, pos, tan)
            val nx = tan[1]
            val ny = -tan[0]
            val l = (1.6f + rnd.nextFloat() * 2.0f) * d
            val tilt = (rnd.nextFloat() - 0.5f) * 0.9f
            val x0 = pos[0] - nx * 1.0f * d
            val y0 = pos[1] - ny * 1.0f * d
            val x1 = pos[0] + (nx + tan[0] * tilt) * l
            val y1 = pos[1] + (ny + tan[1] * tilt) * l
            if (i % 2 == 0) {
                if (ia + 4 <= a.size) { a[ia++] = x0; a[ia++] = y0; a[ia++] = x1; a[ia++] = y1 }
            } else {
                if (ib + 4 <= bb.size) { bb[ib++] = x0; bb[ib++] = y0; bb[ib++] = x1; bb[ib++] = y1 }
            }
        }
        furA = a.copyOf(ia)
        furB = bb.copyOf(ib)
        shader = RadialGradient(w * 0.35f, h * 0.3f, max(w, h) * 0.75f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
    }

    override fun draw(c: Canvas) {
        if (w <= 0f || h <= 0f) return
        val b = bounds
        c.save()
        c.translate(b.left.toFloat(), b.top.toFloat())
        p.reset()
        p.isAntiAlias = true
        when (tpl) {
            1 -> drawFrost(c)
            2 -> drawFluffy(c)
            3 -> drawSciFi(c)
            4 -> drawGlass(c)
            5 -> drawBubble(c)
            6 -> drawNeon(c)
            else -> drawClassic(c)
        }
        if (decor != 0) drawOrnaments(c)
        c.restore()
    }

    // ------------------------------------------------------------------ templates
    private fun drawClassic(c: Canvas) {
        r.set(0f, 0f, w, h)
        p.color = fill
        c.drawRoundRect(r, 8f * d, 8f * d, p)
    }

    /** Facemoji style: see-through key with a double white outline. */
    private fun drawFrost(c: Canvas) {
        r.set(1.2f * d, 1.2f * d, w - 1.2f * d, h - 1.2f * d)
        p.style = Paint.Style.FILL
        p.color = scaleAlpha(fill, if (pressed) 0.95f else 0.42f)
        c.drawRoundRect(r, 12f * d, 12f * d, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f * d
        p.color = withAlpha(Color.WHITE, if (pressed) 255 else 235)
        c.drawRoundRect(r, 12f * d, 12f * d, p)
        r.inset(3.2f * d, 3.2f * d)
        p.strokeWidth = 0.8f * d
        p.color = withAlpha(Color.WHITE, 150)
        c.drawRoundRect(r, 8.5f * d, 8.5f * d, p)
    }

    private fun drawFluffy(c: Canvas) {
        val inset = 4.2f * d
        r.set(inset, inset, w - inset, h - inset)
        val body = if (pressed) darken(fill, 0.14f) else fill
        p.style = Paint.Style.FILL
        p.color = body
        c.drawRoundRect(r, 12f * d, 12f * d, p)
        // hairs: lighter and darker strands
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 1.15f * d
        p.color = withAlpha(lighten(body, 0.45f), 235)
        c.drawLines(furA, p)
        p.color = withAlpha(darken(body, 0.10f), 200)
        c.drawLines(furB, p)
        // soft glow on the upper left
        p.reset()
        p.isAntiAlias = true
        p.shader = shader
        c.drawRoundRect(r, 12f * d, 12f * d, p)
    }

    /** Cut corners, thin light border and a glowing accent edge along the bottom and right. */
    private fun drawSciFi(c: Canvas) {
        val l = 1f * d
        val t = 1f * d
        val rt = w - 1f * d
        val bt = h - 1f * d
        val cut = 6f * d
        path.reset()
        path.moveTo(l + cut, t)
        path.lineTo(rt, t)
        path.lineTo(rt, bt - cut)
        path.lineTo(rt - cut, bt)
        path.lineTo(l, bt)
        path.lineTo(l, t + cut)
        path.close()
        p.style = Paint.Style.FILL
        p.color = if (pressed) mix(fill, accent, 0.40f) else fill
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f * d
        p.color = withAlpha(Color.WHITE, 150)
        c.drawPath(path, p)
        p.strokeWidth = 2.2f * d
        p.strokeCap = Paint.Cap.ROUND
        p.color = accent
        c.drawLine(l + 2f * d, bt, rt - cut - 1f * d, bt, p)
        c.drawLine(rt, t + 2f * d, rt, bt - cut - 1f * d, p)
    }

    private fun drawGlass(c: Canvas) {
        r.set(1f * d, 1f * d, w - 1f * d, h - 1f * d)
        p.style = Paint.Style.FILL
        p.color = scaleAlpha(fill, if (pressed) 0.9f else 0.5f)
        c.drawRoundRect(r, 12f * d, 12f * d, p)
        p.color = withAlpha(Color.WHITE, if (pressed) 70 else 30)
        c.drawRoundRect(r, 12f * d, 12f * d, p)
        p.shader = shader
        c.drawRoundRect(r, 12f * d, 12f * d, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f * d
        p.color = withAlpha(Color.WHITE, 125)
        c.drawRoundRect(r, 12f * d, 12f * d, p)
    }

    /** Glossy 3D button with a drop shadow; sinks when pressed. */
    private fun drawBubble(c: Canvas) {
        val sink = if (pressed) 1.6f * d else 0f
        val lift = 2.2f * d
        if (!pressed) {
            r.set(1.2f * d, 1.2f * d + lift, w - 1.2f * d, h - 0.4f * d)
            p.style = Paint.Style.FILL
            p.color = 0x59000000
            c.drawRoundRect(r, 12f * d, 12f * d, p)
        }
        c.save()
        c.translate(0f, sink)
        r.set(1.2f * d, 1.2f * d, w - 1.2f * d, h - 1.2f * d - lift)
        p.style = Paint.Style.FILL
        p.shader = shader
        c.drawRoundRect(r, 12f * d, 12f * d, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.8f * d
        p.color = 0x40000000
        c.drawRoundRect(r, 12f * d, 12f * d, p)
        // gloss on the top half
        r.set(5f * d, 2.6f * d, w - 5f * d, h * 0.42f)
        p.style = Paint.Style.FILL
        p.shader = shader2
        c.drawRoundRect(r, 8f * d, 8f * d, p)
        p.shader = null
        c.restore()
    }

    /** Glowing outline in the accent colour. */
    private fun drawNeon(c: Canvas) {
        r.set(3f * d, 3f * d, w - 3f * d, h - 3f * d)
        p.style = Paint.Style.FILL
        p.color = scaleAlpha(fill, if (pressed) 0.55f else 0.30f)
        if (pressed) p.color = withAlpha(accent, 95)
        c.drawRoundRect(r, 10f * d, 10f * d, p)
        p.style = Paint.Style.STROKE
        p.color = withAlpha(accent, 26)
        p.strokeWidth = 5.5f * d
        c.drawRoundRect(r, 10f * d, 10f * d, p)
        p.color = withAlpha(accent, 70)
        p.strokeWidth = 3.2f * d
        c.drawRoundRect(r, 10f * d, 10f * d, p)
        p.color = lighten(accent, 0.35f)
        p.strokeWidth = 1.5f * d
        c.drawRoundRect(r, 10f * d, 10f * d, p)
    }

    // ------------------------------------------------------------------ corner ornaments
    private fun ornament(c: Canvas, kind: Int, cx: Float, cy: Float, s: Float) {
        c.save()
        c.translate(cx, cy)
        c.scale(s, s)
        p.reset()
        p.isAntiAlias = true
        when (kind) {
            KeyStyles.SNOW -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 0.30f
                p.strokeCap = Paint.Cap.ROUND
                p.color = 0xFFD6F1FF.toInt()
                for (i in 0 until 3) {
                    c.save()
                    c.rotate(i * 60f)
                    c.drawLine(-1f, 0f, 1f, 0f, p)
                    c.restore()
                }
                p.style = Paint.Style.FILL
                p.color = 0xFFFFFFFF.toInt()
                c.drawCircle(0f, 0f, 0.22f, p)
            }
            KeyStyles.HEART -> {
                p.style = Paint.Style.FILL
                p.color = 0xFFFF6FA5.toInt()
                c.scale(0.95f, 0.95f)
                c.drawPath(KeyStyles.heart, p)
            }
            KeyStyles.FLOWER -> {
                p.style = Paint.Style.FILL
                p.color = 0xFFFFB3D1.toInt()
                for (i in 0 until 5) {
                    val a = Math.toRadians(-90.0 + i * 72.0)
                    c.drawCircle((cos(a) * 0.52).toFloat(), (sin(a) * 0.52).toFloat(), 0.44f, p)
                }
                p.color = 0xFFFFD54F.toInt()
                c.drawCircle(0f, 0f, 0.32f, p)
            }
            KeyStyles.STAR -> {
                p.style = Paint.Style.FILL
                p.color = 0xFFFFD54F.toInt()
                c.drawPath(KeyStyles.star, p)
            }
            KeyStyles.BOW -> {
                p.style = Paint.Style.FILL
                p.color = 0xFFFF5C8A.toInt()
                c.drawPath(KeyStyles.bow, p)
                p.color = 0xFFD63A6A.toInt()
                c.drawCircle(0f, 0f, 0.3f, p)
            }
            KeyStyles.PAW -> {
                p.style = Paint.Style.FILL
                p.color = 0xFFF4B6C8.toInt()
                c.drawPath(KeyStyles.paw, p)
            }
            KeyStyles.SPARKLE -> {
                p.style = Paint.Style.FILL
                p.color = 0xFFFFE9A8.toInt()
                c.scale(1.15f, 1.15f)
                c.drawPath(KeyStyles.sparkle, p)
            }
        }
        c.restore()
    }

    private fun drawOrnaments(c: Canvas) {
        val tl: Int
        val br: Int
        when (decor) {
            1 -> { tl = KeyStyles.SNOW; br = KeyStyles.SPARKLE }
            2 -> { tl = KeyStyles.HEART; br = KeyStyles.HEART }
            3 -> { tl = KeyStyles.FLOWER; br = KeyStyles.FLOWER }
            4 -> { tl = KeyStyles.STAR; br = KeyStyles.SPARKLE }
            5 -> { tl = KeyStyles.BOW; br = KeyStyles.HEART }
            6 -> { tl = KeyStyles.PAW; br = KeyStyles.PAW }
            else -> {   // mix: each key gets its own pair
                val s = if (seed < 0) -seed else seed
                tl = s % 6 + 1
                br = (s / 7) % 6 + 1
            }
        }
        val pad = if (tpl == 2) 7.5f * d else 4.2f * d
        val big = 4.2f * d
        val small = 3.3f * d
        ornament(c, tl, pad + big, pad + big, big)
        if (w > 30f * d) ornament(c, br, w - pad - small, h - pad - small, small)
    }
}
