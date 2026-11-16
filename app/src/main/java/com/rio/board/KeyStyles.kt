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
    val templates = listOf("Classic", "Frost", "Fluffy", "Sci-Fi", "Glass", "Bubble", "Neon", "Kawaii 🍓", "Plush 🎀")
    const val KAWAII = 7
    const val PLUSH = 8
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

private val PLUSH_KINDS = intArrayOf(1, 2, 5, 1, 6, 2, 0, 3, 4, 5)

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
    private var inL = FloatArray(0)      // plush: light strands inside the key
    private var inD = FloatArray(0)      // plush: dark strands inside the key
    private var tint = 0                 // non-zero while a motif is drawn as its own shadow

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
            8 -> {
                buildFur(b)
                buildInterior(b)
                shader2 = LinearGradient(0f, 0f, 0f, h, intArrayOf(lighten(fill, 0.18f), fill, darken(fill, 0.07f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            }
            7 -> {
                shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(lighten(fill, 0.30f), fill, darken(fill, 0.08f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                shader2 = LinearGradient(0f, 0f, 0f, h * 0.40f, 0x99FFFFFF.toInt(), 0x10FFFFFF, Shader.TileMode.CLAMP)
            }
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

    /** Plush keys: thousands of short hairs inside the key body (mostly pointing down, a little random). */
    private fun buildInterior(b: Rect) {
        val inset = 5.5f * d
        val n = max(60, ((w - 2 * inset) * (h - 2 * inset) / (2.6f * d * d)).toInt())
        val rnd = java.util.Random(seed.toLong() * 17L + b.width() * 3L)
        val la = FloatArray(n * 4)
        val da = FloatArray(n * 4)
        var i1 = 0
        var i2 = 0
        for (i in 0 until n) {
            val x = inset + rnd.nextFloat() * (w - 2 * inset)
            val y = inset + rnd.nextFloat() * (h - 2 * inset)
            val ang = Math.PI / 2 + (rnd.nextFloat() - 0.5f) * 1.7
            val len = (1.6f + rnd.nextFloat() * 2.2f) * d
            val x1 = x + (cos(ang) * len).toFloat()
            val y1 = y + (sin(ang) * len).toFloat()
            if (i % 2 == 0) { la[i1++] = x; la[i1++] = y; la[i1++] = x1; la[i1++] = y1 }
            else { da[i2++] = x; da[i2++] = y; da[i2++] = x1; da[i2++] = y1 }
        }
        inL = la.copyOf(i1)
        inD = da.copyOf(i2)
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
            7 -> drawKawaii(c)
            8 -> drawPlush(c)
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

    // ------------------------------------------------------------------ kawaii: glossy pink key with a big sweet motif
    private fun drawKawaii(c: Canvas) {
        val sink = if (pressed) 1.4f * d else 0f
        val lift = 2f * d
        if (!pressed) {
            r.set(1.2f * d, 1.2f * d + lift, w - 1.2f * d, h - 0.4f * d)
            p.style = Paint.Style.FILL
            p.color = 0x40B0306A
            c.drawRoundRect(r, 11f * d, 11f * d, p)
        }
        c.save()
        c.translate(0f, sink)
        r.set(1.2f * d, 1.2f * d, w - 1.2f * d, h - 1.2f * d - lift)
        p.style = Paint.Style.FILL
        p.shader = shader
        c.drawRoundRect(r, 11f * d, 11f * d, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f * d
        p.color = 0xE6FFFFFF.toInt()
        c.drawRoundRect(r, 11f * d, 11f * d, p)
        // soft gloss on the upper half
        r.set(5f * d, 3f * d, w - 5f * d, h * 0.38f)
        p.style = Paint.Style.FILL
        p.shader = shader2
        c.drawRoundRect(r, 8f * d, 8f * d, p)
        p.shader = null

        val s0 = if (seed < 0) -seed else seed
        val bodyH = h - lift
        if (w > 2.2f * bodyH) {
            // long key (space bar): little motifs on both ends, the text stays in the middle
            val sz = bodyH * 0.27f
            motif(c, 2, 14f * d + sz, bodyH * 0.5f, sz)
            motif(c, 3, w - 14f * d - sz, bodyH * 0.5f, sz)
        } else {
            val sz = minOf(bodyH * 0.33f, w * 0.34f)
            val pad = 3.6f * d
            motif(c, s0 % 5, w - pad - sz, pad + sz, sz)
            // tiny sparkle top left
            val sp = sz * 0.26f
            c.save()
            c.translate(pad + sp + 1f * d, pad + sp + 1f * d)
            c.scale(sp, sp)
            p.reset()
            p.isAntiAlias = true
            p.color = 0xFFFFFFFF.toInt()
            c.drawPath(KeyStyles.sparkle, p)
            c.restore()
        }
        c.restore()
    }

    /** Plush: soft fur key (white or pink) with a puffy sweet motif that casts a small shadow. */
    private fun drawPlush(c: Canvas) {
        val inset = 4.2f * d
        val sink = if (pressed) 1.6f * d else 0f
        c.save()
        c.translate(0f, sink)
        if (!pressed) {
            r.set(inset, inset + 2.2f * d, w - inset, h - inset + 2.2f * d)
            p.style = Paint.Style.FILL
            p.color = 0x38A03060
            c.drawRoundRect(r, 12f * d, 12f * d, p)
        }
        r.set(inset, inset, w - inset, h - inset)
        p.style = Paint.Style.FILL
        p.shader = shader2
        c.drawRoundRect(r, 12f * d, 12f * d, p)
        p.shader = null
        // inside hairs
        c.save()
        path.reset()
        path.addRoundRect(r, 12f * d, 12f * d, Path.Direction.CW)
        c.clipPath(path)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 0.9f * d
        p.color = withAlpha(lighten(fill, 0.55f), 130)
        c.drawLines(inL, p)
        p.color = withAlpha(darken(fill, 0.10f), 85)
        c.drawLines(inD, p)
        c.restore()
        // fringe around the edge
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * d
        p.color = withAlpha(lighten(fill, 0.5f), 240)
        c.drawLines(furA, p)
        p.color = withAlpha(darken(fill, 0.08f), 210)
        c.drawLines(furB, p)
        // soft glow upper left
        p.reset()
        p.isAntiAlias = true
        p.shader = shader
        c.drawRoundRect(r, 12f * d, 12f * d, p)
        p.shader = null

        val s0 = if (seed < 0) -seed else seed
        val bodyH = h - 2 * inset
        if (w > 2.2f * bodyH) {
            val sz = bodyH * 0.30f
            puffy(c, 1, inset + 12f * d + sz, inset + bodyH * 0.5f, sz)
            puffy(c, 2, w - inset - 12f * d - sz, inset + bodyH * 0.5f, sz)
        } else {
            val sz = minOf(bodyH * 0.30f, (w - 2 * inset) * 0.33f)
            puffy(c, PLUSH_KINDS[s0 % PLUSH_KINDS.size], w / 2f, inset + bodyH * 0.40f, sz)
        }
        c.restore()
    }

    /** A motif with a soft drop shadow underneath, so it looks like it is sewn on. */
    private fun puffy(c: Canvas, kind: Int, cx: Float, cy: Float, s: Float) {
        tint = 0x44A03060
        motif(c, kind, cx + s * 0.06f, cy + s * 0.12f, s)
        tint = 0
        motif(c, kind, cx, cy, s)
    }

    private fun fc(col: Int): Int = if (tint != 0) tint else col

    /** 0 strawberry, 1 ribbon bow, 2 heart, 3 daisy, 4 cupcake. Own drawings, unit size about -1..1. */
    private fun motif(c: Canvas, kind: Int, cx: Float, cy: Float, s: Float) {
        c.save()
        c.translate(cx, cy)
        c.scale(s, s)
        p.reset()
        p.isAntiAlias = true
        p.style = Paint.Style.FILL
        when (kind) {
            0 -> {
                // strawberry
                path.reset()
                path.moveTo(0f, 1f)
                path.cubicTo(-0.2f, 0.95f, -0.95f, 0.35f, -0.9f, -0.2f)
                path.cubicTo(-0.85f, -0.65f, -0.35f, -0.75f, 0f, -0.62f)
                path.cubicTo(0.35f, -0.75f, 0.85f, -0.65f, 0.9f, -0.2f)
                path.cubicTo(0.95f, 0.35f, 0.2f, 0.95f, 0f, 1f)
                path.close()
                p.color = fc(0xFFFF4D6D.toInt())
                c.drawPath(path, p)
                p.color = fc(0xFFFFD86B.toInt())
                val sx = floatArrayOf(-0.35f, 0.35f, 0f, -0.55f, 0.55f, -0.18f, 0.18f)
                val sy = floatArrayOf(0.05f, 0.05f, 0.45f, -0.3f, -0.3f, 0.65f, 0.65f)
                for (i in sx.indices) c.drawOval(RectF(sx[i] - 0.07f, sy[i] - 0.1f, sx[i] + 0.07f, sy[i] + 0.1f), p)
                p.color = fc(0x99FFFFFF.toInt())
                c.drawOval(RectF(-0.72f, -0.5f, -0.42f, -0.2f), p)
                p.color = fc(0xFF4CAF50.toInt())
                for (a in floatArrayOf(-60f, -25f, 0f, 25f, 60f)) {
                    c.save()
                    c.translate(0f, -0.62f)
                    c.rotate(a)
                    c.drawOval(RectF(-0.13f, -0.5f, 0.13f, 0.05f), p)
                    c.restore()
                }
            }
            1 -> {
                // ribbon bow
                p.color = fc(0xFFFF5C8A.toInt())
                path.reset()
                path.moveTo(0f, 0f)
                path.cubicTo(-0.4f, -0.95f, -1.05f, -0.8f, -1.0f, 0f)
                path.cubicTo(-1.05f, 0.8f, -0.4f, 0.95f, 0f, 0f)
                path.close()
                c.drawPath(path, p)
                path.reset()
                path.moveTo(0f, 0f)
                path.cubicTo(0.4f, -0.95f, 1.05f, -0.8f, 1.0f, 0f)
                path.cubicTo(1.05f, 0.8f, 0.4f, 0.95f, 0f, 0f)
                path.close()
                c.drawPath(path, p)
                p.color = fc(0xFFD63A6A.toInt())
                path.reset()
                path.moveTo(-0.1f, 0.15f); path.lineTo(-0.55f, 1f); path.lineTo(-0.15f, 0.82f); path.lineTo(0.05f, 0.3f); path.close()
                path.moveTo(0.1f, 0.15f); path.lineTo(0.55f, 1f); path.lineTo(0.15f, 0.82f); path.lineTo(-0.05f, 0.3f); path.close()
                c.drawPath(path, p)
                p.color = fc(0xFFE23E73.toInt())
                c.drawCircle(0f, 0f, 0.3f, p)
                p.color = fc(0x99FFFFFF.toInt())
                c.drawOval(RectF(-0.78f, -0.45f, -0.45f, -0.2f), p)
                c.drawOval(RectF(0.45f, -0.45f, 0.78f, -0.2f), p)
            }
            2 -> {
                // glossy heart
                c.scale(0.95f, 0.95f)
                p.color = fc(0xFFFF4F86.toInt())
                c.drawPath(KeyStyles.heart, p)
                p.color = fc(0x99FFFFFF.toInt())
                c.drawOval(RectF(-0.72f, -0.62f, -0.34f, -0.28f), p)
            }
            3 -> {
                // daisy
                p.color = fc(0xFFFFA8CB.toInt())
                for (i in 0 until 6) {
                    val a = Math.toRadians(-90.0 + i * 60.0)
                    c.drawCircle((cos(a) * 0.58).toFloat(), (sin(a) * 0.58).toFloat(), 0.42f, p)
                }
                p.color = fc(0xFFFFFFFF.toInt())
                for (i in 0 until 6) {
                    val a = Math.toRadians(-90.0 + i * 60.0)
                    c.drawCircle((cos(a) * 0.55).toFloat(), (sin(a) * 0.55).toFloat(), 0.34f, p)
                }
                p.color = fc(0xFFFFC93C.toInt())
                c.drawCircle(0f, 0f, 0.32f, p)
            }
            5 -> {
                // slice of cake
                p.color = fc(0xFFFFE9B8.toInt())
                r.set(-0.85f, -0.15f, 0.85f, 0.78f)
                c.drawRoundRect(r, 0.12f, 0.12f, p)
                p.color = fc(0xFFFF9EC4.toInt())
                c.drawRect(-0.85f, 0.22f, 0.85f, 0.36f, p)
                p.color = fc(0xFFFFFFFF.toInt())
                c.drawRoundRect(RectF(-0.9f, -0.42f, 0.9f, 0.05f), 0.22f, 0.22f, p)
                p.color = fc(0xFFFF4D6D.toInt())
                c.drawCircle(0.1f, -0.52f, 0.26f, p)
                p.color = fc(0xFF4CAF50.toInt())
                c.drawOval(RectF(-0.12f, -0.86f, 0.14f, -0.62f), p)
                p.color = fc(0x99FFFFFF.toInt())
                c.drawCircle(0.02f, -0.58f, 0.07f, p)
            }
            6 -> {
                // donut with sprinkles
                path.reset()
                path.fillType = Path.FillType.EVEN_ODD
                path.addCircle(0f, 0f, 0.98f, Path.Direction.CW)
                path.addCircle(0f, 0f, 0.28f, Path.Direction.CW)
                p.color = fc(0xFFF0BE78.toInt())
                c.drawPath(path, p)
                path.reset()
                path.fillType = Path.FillType.EVEN_ODD
                path.addCircle(0f, -0.04f, 0.88f, Path.Direction.CW)
                path.addCircle(0f, -0.04f, 0.3f, Path.Direction.CW)
                p.color = fc(0xFFFF8FBF.toInt())
                c.drawPath(path, p)
                path.fillType = Path.FillType.WINDING
                val sc = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFE066.toInt(), 0xFF7FD6FF.toInt(), 0xFFA8F0C6.toInt())
                val sa = floatArrayOf(0.0f, 0.9f, 1.8f, 2.7f, 3.6f, 4.5f, 5.4f, 0.45f)
                for (i in sa.indices) {
                    val rr = if (i < 7) 0.62f else 0.48f
                    c.save()
                    c.translate((cos(sa[i].toDouble()) * rr).toFloat(), (sin(sa[i].toDouble()) * rr).toFloat() - 0.04f)
                    c.rotate(sa[i] * 57f + 40f)
                    p.color = fc(sc[i % 4])
                    c.drawRoundRect(RectF(-0.14f, -0.045f, 0.14f, 0.045f), 0.05f, 0.05f, p)
                    c.restore()
                }
            }
            else -> {
                // cupcake
                p.color = fc(0xFFFFD79A.toInt())
                path.reset()
                path.moveTo(-0.75f, 0.1f); path.lineTo(0.75f, 0.1f); path.lineTo(0.52f, 0.98f); path.lineTo(-0.52f, 0.98f); path.close()
                c.drawPath(path, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 0.08f
                p.color = fc(0xFFE5AE5C.toInt())
                for (x in floatArrayOf(-0.35f, 0f, 0.35f)) c.drawLine(x * 1.05f, 0.14f, x * 0.8f, 0.94f, p)
                p.style = Paint.Style.FILL
                p.color = fc(0xFFFF8FBA.toInt())
                c.drawOval(RectF(-0.85f, -0.18f, 0.85f, 0.38f), p)
                p.color = fc(0xFFFFB6D2.toInt())
                c.drawOval(RectF(-0.62f, -0.5f, 0.62f, 0.05f), p)
                p.color = fc(0xFFFFE1EE.toInt())
                c.drawOval(RectF(-0.4f, -0.8f, 0.4f, -0.3f), p)
                p.color = fc(0xFFE53050.toInt())
                c.drawCircle(0f, -0.88f, 0.2f, p)
                p.color = fc(0x99FFFFFF.toInt())
                c.drawCircle(-0.06f, -0.94f, 0.06f, p)
            }
        }
        c.restore()
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
