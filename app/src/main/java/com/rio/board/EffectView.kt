package com.rio.board

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.SystemClock
import android.view.View
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Transparent overlay with 11 effects: SNOW, DROPS (water drops sliding down glass),
 * LEAVES (tumbling leaves), FLOWERS (falling blossoms), SMOKE (rising plumes),
 * FIRE (flickering flames + sparks), HEARTS (floating glossy hearts),
 * RAIN, FIREFLIES, BUBBLES, STARS.
 * Replaces SnowView (same API + [type] + [natural]). Does not consume touches.
 */
class EffectView(context: Context) : View(context) {

    enum class Type { SNOW, DROPS, LEAVES, FLOWERS, SMOKE, FIRE, HEARTS, RAIN, FIREFLIES, BUBBLES, STARS }

    // ---------- public API (same as SnowView) ----------
    var type: Type = Type.SNOW
        set(value) {
            if (field != value) {
                field = value
                clearAll()
                last = 0L
                invalidate()
            }
        }

    /** One colour, or several (each particle gets one of them). */
    var colors: IntArray = intArrayOf(Color.WHITE)

    /** true = use the built-in natural look (autumn leaves / clear water / sakura petals / grey smoke / real flames / red hearts) instead of [colors]. */
    var natural: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                clearAll()
            }
        }

    var base: Int = 45

    /** Effect only appears below this y (keeps it on the keyboard area only). */
    var topLimit: Float = 0f

    var running: Boolean = false
        set(value) {
            field = value
            last = 0L
            if (!value) clearAll()
            invalidate()
        }

    /** Extra particles on every key press. */
    fun burst() {
        if (!running || width == 0) return
        when (type) {
            Type.SNOW -> repeat(4) { if (flakes.size < 150) flakes.add(newFlake(true)) }
            Type.DROPS -> repeat(3) { if (drops.size < maxDrops()) drops.add(newDrop(false, true)) }
            Type.LEAVES -> repeat(2) { if (leaves.size < 40) leaves.add(newLeaf(true)) }
            Type.FLOWERS -> repeat(3) { if (petals.size < maxPetals()) petals.add(newPetal(true)) }
            Type.SMOKE -> repeat(3) { if (puffs.size < base * 4) puffs.add(newPuff(false, true)) }
            Type.FIRE -> {
                repeat(5) { if (flames.size < 380) flames.add(newFlame(true)) }
                repeat(2) { if (sparks.size < 40) sparks.add(newSpark()) }
            }
            Type.HEARTS -> repeat(2) { if (hearts.size < base * 2) hearts.add(newHeart(false)) }
            Type.RAIN -> repeat(6) { addSplash(rnd.nextFloat() * width, 200, rnd.nextInt(1000)) }
            Type.FIREFLIES -> for (f in flies) if (rnd.nextFloat() < 0.25f) f.boost = 1f
            Type.BUBBLES -> repeat(2) { if (bubbles.size < base * 3 / 2) bubbles.add(newBubble(false)) }
            Type.STARS -> if (stars.isNotEmpty()) repeat(4) { stars[rnd.nextInt(stars.size)].flash = 1f }
        }
    }

    // ---------- shared ----------
    private val rnd = Random()
    private val d = resources.displayMetrics.density
    private var last = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun clearAll() {
        flakes.clear(); drops.clear(); trails.clear(); leaves.clear(); leavesFilled = false
        petals.clear(); petalsFilled = false
        puffs.clear(); puffsFilled = false; puffAcc = 0f
        flames.clear(); sparks.clear(); fireAcc = 0f; sparkAcc = 0f
        hearts.clear(); heartsFilled = false; heartAcc = 0f
        streaks.clear(); splashes.clear(); rainFilled = false
        flies.clear()
        bubbles.clear(); bubblesFilled = false; bubbleAcc = 0f
        stars.clear(); shoots.clear(); starsFilled = false; shootIn = 3f
    }

    private fun palette(): IntArray = if (colors.isEmpty()) intArrayOf(Color.WHITE) else colors

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!running || width == 0 || height == 0) return

        val now = SystemClock.uptimeMillis()
        val dt = if (last == 0L) 0.016f else minOf((now - last) / 1000f, 0.05f)
        last = now

        canvas.save()
        canvas.clipRect(0f, topLimit, width.toFloat(), height.toFloat())
        when (type) {
            Type.SNOW -> drawSnow(canvas, dt)
            Type.DROPS -> drawDrops(canvas, dt)
            Type.LEAVES -> drawLeaves(canvas, dt)
            Type.FLOWERS -> drawFlowers(canvas, dt)
            Type.SMOKE -> drawSmoke(canvas, dt)
            Type.FIRE -> drawFire(canvas, dt)
            Type.HEARTS -> drawHearts(canvas, dt)
            Type.RAIN -> drawRain(canvas, dt)
            Type.FIREFLIES -> drawFireflies(canvas, dt)
            Type.BUBBLES -> drawBubbles(canvas, dt)
            Type.STARS -> drawStars(canvas, dt)
        }
        canvas.restore()
        postInvalidateOnAnimation()
    }

    // =====================================================================
    //  SNOW  (unchanged behaviour)
    // =====================================================================
    private class Flake(
        var x: Float, var y: Float,
        val r: Float, val vy: Float,
        var phase: Float, val amp: Float,
        val ci: Int
    )

    private val flakes = ArrayList<Flake>()

    private fun newFlake(top: Boolean): Flake {
        val r = (1.2f + rnd.nextFloat() * 2.8f) * d
        val span = (height - topLimit).coerceAtLeast(1f)
        val y = if (top) topLimit - r - rnd.nextFloat() * 30f * d
        else topLimit + rnd.nextFloat() * span
        return Flake(
            rnd.nextFloat() * width, y, r,
            (35f + rnd.nextFloat() * 75f) * d,
            rnd.nextFloat() * 6.28f,
            (4f + rnd.nextFloat() * 12f) * d,
            rnd.nextInt(1000)
        )
    }

    private fun drawSnow(canvas: Canvas, dt: Float) {
        while (flakes.size < base) flakes.add(newFlake(false))
        val cols = palette()
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
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = cols[f.ci % cols.size]
            paint.alpha = (120 + (f.r / (4f * d)) * 135).toInt().coerceIn(80, 255)
            canvas.drawCircle(f.x + sin(f.phase) * f.amp, f.y, f.r, paint)
        }
    }

    // =====================================================================
    //  WATER DROPS  – drops form on "glass", grow, then slide down leaving
    //  a trail (stop-and-go) and tiny droplets behind them.
    // =====================================================================
    private class Drop(
        var x: Float, var y: Float, var r: Float,
        val slideAt: Float, val grow: Float, val ci: Int
    ) {
        var sliding = false
        var vy = 0f
        var pause = 0f
        var trailY = 0f
        var trailW = 0f
        var a = 0f              // appear / fade multiplier 0..1
        var life = 0f           // seconds left (static drops)
        var dying = false
        var spawnAcc = 0f       // distance since last tiny droplet
    }

    private class Trail(val x: Float, val y1: Float, val y2: Float, val w: Float, var a: Float, val ci: Int)

    private val drops = ArrayList<Drop>()
    private val trails = ArrayList<Trail>()

    private fun maxDrops() = (base * 1.6f).toInt()

    // unit-circle shaders so one Paint serves every drop (canvas is scaled per drop)
    private val bodyP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            -0.15f, -0.2f, 1.25f,
            intArrayOf(0x10FFFFFF, 0x08FFFFFF, 0x30FFFFFF, 0x66FFFFFF),
            floatArrayOf(0f, 0.55f, 0.88f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    private val tintP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.07f; color = Color.BLACK
    }
    private val rimP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.13f; color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
    }
    private val specP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val trailP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.WHITE
    }
    private val arcRect = RectF(-0.72f, -0.72f, 0.72f, 0.72f)

    private fun newDrop(initial: Boolean, tiny: Boolean = false): Drop {
        val span = (height - topLimit).coerceAtLeast(1f)
        val big = rnd.nextFloat() < 0.18f
        val r = when {
            tiny -> (0.9f + rnd.nextFloat() * 1.3f) * d
            big -> (3.5f + rnd.nextFloat() * 2.5f) * d
            else -> (1.2f + rnd.nextFloat() * 2.6f) * d
        }
        val dr = Drop(
            rnd.nextFloat() * width,
            topLimit + rnd.nextFloat() * span,
            r,
            (5.2f + rnd.nextFloat() * 2.4f) * d,
            (0.12f + rnd.nextFloat() * 0.35f) * d,
            rnd.nextInt(1000)
        )
        dr.life = 6f + rnd.nextFloat() * 20f
        dr.a = if (initial) 1f else 0f
        return dr
    }

    private fun dropTint(ci: Int): Int? {
        if (natural) return null
        val c = palette()
        return c[ci % c.size]
    }

    private fun drawDropShape(c: Canvas, x: Float, y: Float, r: Float, stretch: Float, a: Float, tint: Int?) {
        if (a <= 0.01f) return
        c.save()
        c.translate(x, y)
        c.scale(r, r * stretch)
        if (tint != null) {
            tintP.color = tint
            tintP.alpha = (46 * a).toInt()
            c.drawCircle(0f, 0f, 1f, tintP)
        }
        bodyP.alpha = (255 * a).toInt()
        c.drawCircle(0f, 0f, 1f, bodyP)
        ringP.alpha = (70 * a).toInt()
        c.drawCircle(0f, 0f, 1f, ringP)
        if (r > 2.2f * d) {
            rimP.alpha = (110 * a).toInt()
            c.drawArc(arcRect, 20f, 70f, false, rimP)       // light refracted at the lower edge
            specP.alpha = (150 * a).toInt()
            c.drawCircle(-0.08f, -0.64f, 0.09f, specP)     // small second glint
        }
        specP.alpha = (235 * a).toInt()
        c.drawCircle(-0.38f, -0.40f, 0.2f, specP)          // main specular highlight
        c.restore()
    }

    private fun drawDrops(canvas: Canvas, dt: Float) {
        val target = (base * 0.9f).toInt()
        while (drops.size < target) drops.add(newDrop(drops.size < target / 2))

        // fading trails
        var i = 0
        while (i < trails.size) {
            val t = trails[i]
            t.a -= dt * 0.45f
            if (t.a <= 0f) { trails.removeAt(i); continue }
            trailP.strokeWidth = t.w
            trailP.alpha = (34 * t.a).toInt()
            tintFor(trailP, t.ci)
            canvas.drawLine(t.x, t.y1, t.x, t.y2, trailP)
            i++
        }

        i = 0
        while (i < drops.size) {
            val dr = drops[i]
            if (!dr.sliding) {
                // appear, slowly grow, eventually fade away or start sliding
                if (!dr.dying && dr.a < 1f) dr.a = minOf(1f, dr.a + dt * 2.2f)
                dr.r += dr.grow * dt * 0.5f
                dr.life -= dt
                if (dr.life <= 0f) dr.dying = true
                if (dr.dying) {
                    dr.a -= dt * 1.2f
                    if (dr.a <= 0f) { drops.removeAt(i); continue }
                } else if (dr.r >= dr.slideAt) {
                    dr.sliding = true
                    dr.trailY = dr.y
                    dr.trailW = dr.r * 0.62f
                    dr.vy = 8f * d
                }
                drawDropShape(canvas, dr.x, dr.y, dr.r, 1.06f, dr.a, dropTint(dr.ci))
            } else {
                // sliding: accelerate, sudden stops, shrink as it leaves water behind
                if (dr.pause > 0f) {
                    dr.pause -= dt
                    dr.vy *= (1f - 6f * dt).coerceAtLeast(0f)
                } else {
                    val vmax = (70f + dr.r / d * 22f) * d
                    dr.vy = minOf(vmax, dr.vy + 520f * d * dt)
                    if (rnd.nextFloat() < 1.3f * dt) dr.pause = 0.12f + rnd.nextFloat() * 0.45f
                }
                val dist = dr.vy * dt
                dr.y += dist
                dr.r -= dist * 0.0045f
                dr.spawnAcc += dist
                // leave tiny droplets behind
                if (dr.spawnAcc > 34f * d) {
                    dr.spawnAcc = 0f
                    if (drops.size < maxDrops() && rnd.nextFloat() < 0.7f) {
                        val t = Drop(
                            dr.x + (rnd.nextFloat() - 0.5f) * dr.r * 1.2f, dr.y - dr.r * 2.2f,
                            (0.7f + rnd.nextFloat() * 0.9f) * d,
                            (6f) * d, (0.08f + rnd.nextFloat() * 0.2f) * d, dr.ci
                        )
                        t.a = 0.9f
                        t.life = 3f + rnd.nextFloat() * 8f
                        drops.add(t)
                    }
                }
                val gone = dr.r < 1.6f * d || dr.y - dr.r > height
                // live trail: wet streak from where it started to the drop
                trailP.strokeWidth = dr.trailW * (dr.r / dr.slideAt).coerceIn(0.4f, 1f)
                trailP.alpha = 34
                tintFor(trailP, dr.ci)
                canvas.drawLine(dr.x, dr.trailY, dr.x, dr.y, trailP)
                if (gone) {
                    trails.add(Trail(dr.x, dr.trailY, dr.y, dr.trailW * 0.5f, 1f, dr.ci))
                    drops.removeAt(i)
                    continue
                }
                drawDropShape(canvas, dr.x, dr.y, dr.r, 1.28f, 1f, dropTint(dr.ci))
            }
            i++
        }
    }

    private fun tintFor(p: Paint, ci: Int) {
        val t = dropTint(ci)
        val a = p.alpha
        p.color = t ?: Color.WHITE
        p.alpha = a
    }

    // =====================================================================
    //  LEAVES – tumbling, swaying, 3D-flipping leaves with vein + shading.
    // =====================================================================
    private class Leaf(
        var x: Float, var y: Float,
        val size: Float, val vy: Float,
        var phase: Float, val amp: Float,
        val rotAmp: Float, val spin: Float,
        var flip: Float, val flipSpd: Float,
        val color: Int, val dark: Int
    ) {
        var angle = 0f
    }

    private val leaves = ArrayList<Leaf>()
    private var leavesFilled = false

    private val autumn = intArrayOf(
        0xFFE65100.toInt(), 0xFFBF360C.toInt(), 0xFFF9A825.toInt(), 0xFFFBC02D.toInt(),
        0xFF8BC34A.toInt(), 0xFF689F38.toInt(), 0xFF8D6E63.toInt(), 0xFFC62828.toInt()
    )

    // unit leaf: tip (0,-1) -> base (0,1), width +-0.7
    private val leafPath = Path().apply {
        moveTo(0f, -1f)
        cubicTo(0.85f, -0.5f, 0.75f, 0.5f, 0f, 1f)
        cubicTo(-0.75f, 0.5f, -0.85f, -0.5f, 0f, -1f)
        close()
    }
    private val leafHalf = Path().apply {          // right half, drawn darker for depth
        moveTo(0f, -1f)
        cubicTo(0.85f, -0.5f, 0.75f, 0.5f, 0f, 1f)
        close()
    }
    private val veinP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.07f; strokeCap = Paint.Cap.ROUND
    }
    private val hsv = FloatArray(3)

    private fun newLeaf(top: Boolean): Leaf {
        val size = (6f + rnd.nextFloat() * 8f) * d
        val span = (height - topLimit).coerceAtLeast(1f)
        val y = if (top) topLimit - size * 2f - rnd.nextFloat() * 40f * d
        else topLimit + rnd.nextFloat() * span

        val col: Int = if (natural) {
            autumn[rnd.nextInt(autumn.size)]
        } else {
            val pal = palette()
            val c0 = pal[rnd.nextInt(pal.size)]
            Color.colorToHSV(c0, hsv)
            hsv[2] = (hsv[2] * (0.75f + rnd.nextFloat() * 0.3f)).coerceIn(0.2f, 1f)
            Color.HSVToColor(hsv)
        }
        Color.colorToHSV(col, hsv)
        hsv[2] *= 0.62f
        val dark = Color.HSVToColor(hsv)

        return Leaf(
            rnd.nextFloat() * width, y, size,
            (28f + rnd.nextFloat() * 42f) * d,
            rnd.nextFloat() * 6.28f,
            (14f + rnd.nextFloat() * 30f) * d,
            25f + rnd.nextFloat() * 45f,
            (rnd.nextFloat() - 0.5f) * 40f,
            rnd.nextFloat() * 6.28f,
            1.2f + rnd.nextFloat() * 2.2f,
            col, dark
        )
    }

    private fun drawLeaves(canvas: Canvas, dt: Float) {
        val target = maxOf(8, base / 6)
        while (leaves.size < target) leaves.add(newLeaf(leavesFilled))
        leavesFilled = true

        val iter = leaves.iterator()
        while (iter.hasNext()) {
            val l = iter.next()
            l.y += l.vy * dt
            l.phase += dt * 1.15f
            l.flip += dt * l.flipSpd
            l.angle += l.spin * dt
            if (l.y - l.size * 2f > height) {
                iter.remove()
                continue
            }

            val sx = cos(l.flip)
            val sxAbs = maxOf(0.16f, abs(sx))
            val px = l.x + sin(l.phase) * l.amp
            val rot = l.angle + sin(l.phase + 1.2f) * l.rotAmp

            canvas.save()
            canvas.translate(px, l.y)
            canvas.rotate(rot)
            canvas.scale(l.size * 0.62f * sxAbs, l.size)

            // leaf body (back side a little darker when flipped)
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = if (sx >= 0f) l.color else l.dark
            paint.alpha = if (sx >= 0f) 235 else 215
            canvas.drawPath(leafPath, paint)

            // shaded half for depth
            paint.color = l.dark
            paint.alpha = if (sx >= 0f) 70 else 40
            canvas.drawPath(leafHalf, paint)

            // midrib, side veins, stem
            veinP.color = l.dark
            veinP.alpha = 190
            canvas.drawLine(0f, -0.9f, 0f, 1.0f, veinP)
            veinP.alpha = 120
            var vy = -0.55f
            while (vy < 0.6f) {
                canvas.drawLine(0f, vy + 0.25f, 0.45f, vy - 0.05f, veinP)
                canvas.drawLine(0f, vy + 0.25f, -0.45f, vy - 0.05f, veinP)
                vy += 0.4f
            }
            veinP.alpha = 210
            canvas.drawLine(0f, 1.0f, 0f, 1.35f, veinP)
            canvas.restore()
        }
    }

    // =====================================================================
    //  FLOWERS – whole blossoms tumbling down (not just petals):
    //    0 sakura   – 5 notched petals, deeper pink towards the middle, stamens
    //    1 daisy    – 13 slim petals around a golden disc
    //    2 cosmos   – 8 wide toothed petals, golden centre
    //    3 loose petal – single notched sakura petal that flips as it falls
    //  Flowers turn in-plane and tilt (squash) like a real flower catching
    //  the wind; when tilted past edge-on you see the darker back side.
    // =====================================================================

    private class Petal(
        var x: Float, var y: Float,
        val r: Float,            // flower radius / loose-petal half-height (px)
        val kind: Int,           // 0 sakura, 1 daisy, 2 cosmos, 3 loose petal
        val aspect: Float,       // loose petal width / height
        val vy: Float, var vx: Float,
        var phase: Float, val swayAmp: Float, val swayFreq: Float,
        var tilt: Float, val tiltSpd: Float,
        var spin: Float, val spinSpd: Float,
        val tint: Int, val deep: Int, val back: Int, val centre: Int,
        val alpha: Int
    )

    private val petals = ArrayList<Petal>()
    private var petalsFilled = false

    private val sakura = intArrayOf(
        0xFFFFB7C5.toInt(), 0xFFFF8FAB.toInt(), 0xFFFFC0CB.toInt(), 0xFFFFD1DC.toInt(),
        0xFFFFECF1.toInt(), 0xFFFF9DB5.toInt(), 0xFFFF6F91.toInt(), 0xFFF8BBD0.toInt()
    )
    private val cosmosCols = intArrayOf(
        0xFFE83E8C.toInt(), 0xFFFF6FA8.toInt(), 0xFFF06292.toInt(),
        0xFFFF8A65.toInt(), 0xFFFFFFFF.toInt(), 0xFFBA68C8.toInt()
    )

    // all petal paths: base at (0,0), tip at y = -1
    private val sakuraPetal = Path().apply {
        moveTo(0f, 0f)
        cubicTo(-0.60f, -0.22f, -0.70f, -0.88f, -0.20f, -1.00f)
        quadTo(0f, -0.78f, 0.20f, -1.00f)
        cubicTo(0.70f, -0.88f, 0.60f, -0.22f, 0f, 0f)
        close()
    }
    private val daisyPetal = Path().apply {
        moveTo(0f, 0f)
        cubicTo(-0.22f, -0.25f, -0.20f, -0.82f, 0f, -1.00f)
        cubicTo(0.20f, -0.82f, 0.22f, -0.25f, 0f, 0f)
        close()
    }
    private val cosmosPetal = Path().apply {
        moveTo(0f, 0f)
        cubicTo(-0.58f, -0.25f, -0.62f, -0.82f, -0.32f, -1.00f)
        lineTo(-0.16f, -0.90f)
        lineTo(0f, -1.00f)
        lineTo(0.16f, -0.90f)
        lineTo(0.32f, -1.00f)
        cubicTo(0.62f, -0.82f, 0.58f, -0.25f, 0f, 0f)
        close()
    }

    private val bloomBounds = RectF(-1.15f, -1.15f, 1.15f, 1.15f)
    private val bloomP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bloomGlowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bloomEdgeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.03f
        strokeCap = Paint.Cap.ROUND
    }

    private fun maxPetals() = maxOf(20, base)

    private fun newPetal(fromTop: Boolean): Petal {
        val roll = rnd.nextFloat()
        val kind = if (natural) {
            when { roll < 0.45f -> 0; roll < 0.60f -> 1; roll < 0.75f -> 2; else -> 3 }
        } else {
            when { roll < 0.40f -> 0; roll < 0.55f -> 1; roll < 0.75f -> 2; else -> 3 }
        }
        val size = if (kind == 3) (5f + rnd.nextFloat() * 6f) * d else (7f + rnd.nextFloat() * 8f) * d
        val span = (height - topLimit).coerceAtLeast(1f)
        val y = if (fromTop) topLimit - size * 2f - rnd.nextFloat() * 60f * d
        else topLimit + rnd.nextFloat() * span

        val base0: Int = if (natural) {
            when (kind) {
                1 -> if (rnd.nextBoolean()) 0xFFFFFFFF.toInt() else 0xFFFFFAEB.toInt()
                2 -> cosmosCols[rnd.nextInt(cosmosCols.size)]
                else -> sakura[rnd.nextInt(sakura.size)]
            }
        } else {
            val pal = palette()
            Color.colorToHSV(pal[rnd.nextInt(pal.size)], hsv)
            hsv[1] = (hsv[1] * (0.6f + rnd.nextFloat() * 0.4f)).coerceIn(0f, 1f)
            hsv[2] = (hsv[2] * (0.75f + rnd.nextFloat() * 0.35f)).coerceIn(0.25f, 1f)
            Color.HSVToColor(hsv)
        }
        val tint = if (natural && (kind == 0 || kind == 3)) mixRgb(base0, Color.WHITE, 0.30f)
        else base0 or (0xFF shl 24)
        val deep = when {
            natural && (kind == 0 || kind == 3) -> mixRgb(base0, 0xC2185B, 0.55f)
            natural && kind == 1 -> 0xFFD2D2C8.toInt()
            else -> mixRgb(base0, Color.BLACK, 0.35f)
        }
        val back = mixRgb(tint, Color.BLACK, 0.20f)
        val centre = if (kind == 0) 0xFFF2DC6B.toInt() else 0xFFFFB300.toInt()

        return Petal(
            x = rnd.nextFloat() * width, y = y,
            r = size,
            kind = kind,
            aspect = 0.62f + rnd.nextFloat() * 0.2f,
            vy = (24f + rnd.nextFloat() * 32f) * d,
            vx = (rnd.nextFloat() - 0.5f) * 18f * d,
            phase = rnd.nextFloat() * 6.28f,
            swayAmp = (12f + rnd.nextFloat() * 26f) * d,
            swayFreq = 0.7f + rnd.nextFloat() * 1.0f,
            tilt = rnd.nextFloat() * 6.28f,
            tiltSpd = (if (kind == 3) 1.6f + rnd.nextFloat() * 1.8f else 0.5f + rnd.nextFloat() * 0.9f) *
                (if (rnd.nextBoolean()) 1f else -1f),
            spin = rnd.nextFloat() * 360f,
            spinSpd = (10f + rnd.nextFloat() * 30f) * (if (rnd.nextBoolean()) 1f else -1f),
            tint = tint, deep = deep, back = back, centre = centre,
            alpha = 200 + rnd.nextInt(56)
        )
    }

    private fun drawBlossom(canvas: Canvas, p: Petal) {
        val c = cos(p.tilt)
        val front = c >= 0f
        val sq = 0.42f + 0.58f * abs(c)
        val path = when (p.kind) { 0 -> sakuraPetal; 1 -> daisyPetal; else -> cosmosPetal }
        val n = when (p.kind) { 0 -> 5; 1 -> 13; else -> 8 }

        canvas.save()
        canvas.translate(p.x, p.y)
        canvas.rotate(p.spin)
        canvas.scale(p.r, p.r * sq)
        canvas.saveLayerAlpha(bloomBounds, p.alpha)

        val step = 360f / n
        for (i in 0 until n) {
            canvas.save()
            canvas.rotate(i * step)
            bloomP.color = if (front) p.tint else p.back
            canvas.drawPath(path, bloomP)
            bloomEdgeP.color = p.deep
            bloomEdgeP.alpha = if (front) 110 else 60
            canvas.drawPath(path, bloomEdgeP)
            canvas.restore()
        }

        if (front) {
            if (p.kind != 1) {
                // petals get deeper in colour towards the heart of the flower
                bloomGlowP.shader = soft(quant(p.deep))
                bloomGlowP.alpha = 150
                canvas.save()
                canvas.scale(0.62f, 0.62f)
                canvas.drawCircle(0f, 0f, 1f, bloomGlowP)
                canvas.restore()
                bloomGlowP.shader = null
            }
            when (p.kind) {
                0 -> {
                    for (i in 0 until 10) {
                        canvas.save()
                        canvas.rotate(i * 36f + 7f)
                        val len = 0.30f + (i % 3) * 0.045f
                        bloomEdgeP.color = p.deep
                        bloomEdgeP.alpha = 210
                        bloomEdgeP.strokeWidth = 0.028f
                        canvas.drawLine(0f, 0f, 0f, -len, bloomEdgeP)
                        bloomP.color = p.centre
                        canvas.drawCircle(0f, -len, 0.05f, bloomP)
                        canvas.restore()
                    }
                    bloomEdgeP.strokeWidth = 0.03f
                    bloomP.color = p.deep
                    canvas.drawCircle(0f, 0f, 0.07f, bloomP)
                }
                1 -> {
                    bloomP.color = mixRgb(p.centre, Color.BLACK, 0.25f)
                    canvas.drawCircle(0f, 0f, 0.30f, bloomP)
                    bloomP.color = p.centre
                    canvas.drawCircle(0f, 0f, 0.24f, bloomP)
                    bloomP.color = (0xA0 shl 24) or (mixRgb(p.centre, Color.WHITE, 0.5f) and 0xFFFFFF)
                    canvas.drawCircle(-0.06f, -0.07f, 0.09f, bloomP)
                }
                else -> {
                    bloomP.color = mixRgb(p.centre, Color.BLACK, 0.25f)
                    canvas.drawCircle(0f, 0f, 0.15f, bloomP)
                    bloomP.color = p.centre
                    canvas.drawCircle(0f, 0f, 0.11f, bloomP)
                }
            }
        } else {
            bloomP.color = p.deep
            canvas.drawCircle(0f, 0f, 0.10f, bloomP)
        }

        canvas.restore()   // layer
        canvas.restore()
    }

    private fun drawLoosePetal(canvas: Canvas, p: Petal) {
        val fl = cos(p.tilt)
        val absF = maxOf(0.10f, abs(fl))
        val front = fl >= 0f
        canvas.save()
        canvas.translate(p.x, p.y)
        canvas.rotate(p.spin)
        canvas.scale(p.r * p.aspect * absF, p.r)
        canvas.translate(0f, 0.5f)      // pivot in the middle of the petal

        bloomP.color = if (front) p.tint else p.back
        bloomP.alpha = if (front) p.alpha else (p.alpha * 0.8f).toInt()
        canvas.drawPath(sakuraPetal, bloomP)
        if (front) {
            // deeper colour near the base
            canvas.save()
            canvas.scale(0.5f, 0.45f)
            bloomP.color = p.deep
            bloomP.alpha = (p.alpha * 0.40f).toInt()
            canvas.drawPath(sakuraPetal, bloomP)
            canvas.restore()
        }
        bloomEdgeP.color = p.deep
        bloomEdgeP.alpha = 80
        canvas.drawPath(sakuraPetal, bloomEdgeP)
        canvas.restore()
    }

    private fun drawFlowers(canvas: Canvas, dt: Float) {
        val target = maxOf(12, base / 3)
        while (petals.size < target) petals.add(newPetal(petalsFilled))
        petalsFilled = true

        val iter = petals.iterator()
        while (iter.hasNext()) {
            val p = iter.next()

            p.phase += p.swayFreq * dt
            p.vx += (sin(p.phase) * p.swayAmp * p.swayFreq * 2.5f - p.vx * 0.4f) * dt
            p.y += (p.vy + cos(p.tilt) * p.vy * 0.12f) * dt
            p.x += p.vx * dt
            p.tilt += p.tiltSpd * dt
            p.spin += p.spinSpd * dt

            if (p.x < -p.r * 3f) p.x = width + p.r * 2f
            if (p.x > width + p.r * 3f) p.x = -p.r * 2f

            if (p.y - p.r * 2f > height) {
                iter.remove()
                continue
            }

            if (p.kind == 3) drawLoosePetal(canvas, p) else drawBlossom(canvas, p)
        }
    }

    // =====================================================================
    //  shared helpers for SMOKE / FIRE / HEARTS
    // =====================================================================
    private val softCache = HashMap<Int, RadialGradient>()

    /** Unit-radius soft round sprite (opaque centre -> transparent edge). Canvas is scaled per particle. */
    private fun soft(rgb: Int): RadialGradient {
        val key = rgb and 0xFFFFFF
        softCache[key]?.let { return it }
        if (softCache.size > 200) softCache.clear()
        val g = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(key or (0xFF shl 24), key or (0x80 shl 24), key),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        softCache[key] = g
        return g
    }

    private fun mixRgb(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val r = Color.red(a) + ((Color.red(b) - Color.red(a)) * k).toInt()
        val g = Color.green(a) + ((Color.green(b) - Color.green(a)) * k).toInt()
        val bl = Color.blue(a) + ((Color.blue(b) - Color.blue(a)) * k).toInt()
        return Color.rgb(r, g, bl)
    }

    /** Snap a colour to 32 levels per channel so the shader cache stays small. */
    private fun quant(c: Int): Int = c and 0xF8F8F8

    // =====================================================================
    //  SMOKE – soft grey plumes: puffs are born dense & dark at a few wandering
    //  sources along the bottom, rise while slowing down, swirl sideways,
    //  expand, lighten and dissolve.
    // =====================================================================
    private class Puff(
        val x0: Float, val y0: Float, val r0: Float, val grow: Float,
        val vy: Float, val vx: Float, val phase: Float, val freq: Float, val sway: Float,
        var age: Float, val life: Float, val aMax: Float, val tone: Int
    )

    private val puffs = ArrayList<Puff>()
    private var puffsFilled = false
    private var puffAcc = 0f
    private var clock = 0f

    private fun smokeSrcX(i: Int, n: Int): Float =
        width * (i + 0.5f) / n + sin(clock * 0.35f + i * 1.9f) * width * 0.07f

    private fun newPuff(prefill: Boolean, boost: Boolean = false): Puff {
        val n = 4
        val span = (height - topLimit).coerceAtLeast(1f)
        val life = 3.0f + rnd.nextFloat() * 2.4f
        val vy = span * (0.17f + rnd.nextFloat() * 0.13f)
        val age = if (prefill) rnd.nextFloat() * life * 0.9f else 0f
        val tone = if (natural) {
            val g = 55 + rnd.nextInt(65)
            Color.rgb(g, g, g + 6)
        } else {
            val pal = palette()
            pal[rnd.nextInt(pal.size)] and 0xFFFFFF
        }
        return Puff(
            x0 = smokeSrcX(rnd.nextInt(n), n) + (rnd.nextFloat() - 0.5f) * 30f * d,
            y0 = height + 8f * d,
            r0 = (7f + rnd.nextFloat() * 7f) * d,
            grow = (24f + rnd.nextFloat() * 28f) * d,
            vy = vy,
            vx = (rnd.nextFloat() - 0.5f) * 16f * d,
            phase = rnd.nextFloat() * 6.28f,
            freq = 0.6f + rnd.nextFloat() * 1.0f,
            sway = (10f + rnd.nextFloat() * 22f) * d,
            age = age,
            life = life,
            aMax = (0.16f + rnd.nextFloat() * 0.16f) * (if (boost) 1.4f else 1f),
            tone = tone
        )
    }

    private fun drawSmoke(canvas: Canvas, dt: Float) {
        clock += dt
        if (!puffsFilled) {
            repeat(base * 2) { puffs.add(newPuff(true)) }
            puffsFilled = true
        }
        puffAcc += dt * base * 0.7f
        while (puffAcc >= 1f) {
            puffAcc -= 1f
            if (puffs.size < base * 4) puffs.add(newPuff(false))
        }

        paint.style = Paint.Style.FILL
        val iter = puffs.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.age += dt
            val t = p.age / p.life
            if (t >= 1f) { iter.remove(); continue }
            val u = 1f - t
            // rises fast at first, then slows down
            val y = p.y0 - p.vy * (p.age - 0.25f * p.age * p.age / p.life)
            val x = p.x0 + p.vx * p.age + sin(p.phase + p.age * p.freq) * p.sway * (0.3f + t)
            val r = p.r0 + p.grow * (1f - u * u)       // expands quickly, then keeps spreading slowly
            if (y + r < topLimit) { iter.remove(); continue }
            val a = p.aMax * minOf(1f, t / 0.12f) * u * u
            val c = if (natural) mixRgb(p.tone, 0xC8C8CC, t * 0.85f)
            else mixRgb(p.tone, Color.WHITE, t * 0.55f)
            paint.shader = soft(quant(c))
            paint.alpha = (a * 255f).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(r, r)
            canvas.drawCircle(0f, 0f, 1f, paint)
            canvas.restore()
        }
        paint.shader = null
    }

    // =====================================================================
    //  FIRE – additive flame sprites rising from the bottom in "tongues" that
    //  flicker. Colour goes white-yellow core -> orange -> red -> dark ember
    //  while the sprite shrinks and fades. Plus a few flying sparks.
    // =====================================================================
    private val fireKeys = intArrayOf(0xFFFAC8, 0xFFD75A, 0xFF8C19, 0xE13C0A, 0x6E140A)

    private val firePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    private class Flame(
        val x0: Float, val xj: Float, val yS: Float, val r0: Float, val rise: Float,
        val phase: Float, val freq: Float, val sway: Float,
        var age: Float, val life: Float, val tint: Int, val nat: Boolean
    )

    private class Spark(
        var x: Float, var y: Float, val vx: Float, var vy: Float,
        var age: Float, val life: Float, val r: Float, val tint: Int, val nat: Boolean
    )

    private val flames = ArrayList<Flame>()
    private val sparks = ArrayList<Spark>()
    private var fireAcc = 0f
    private var sparkAcc = 0f

    private fun kTongues(): Int = (width / (55f * d)).toInt().coerceIn(5, 14)

    private fun tongueX(k: Int): Float {
        val n = kTongues()
        val cell = width.toFloat() / n
        return cell * (k + 0.5f) + sin(clock * 0.6f + k * 2.7f) * cell * 0.25f
    }

    /** Each tongue breathes between short and tall. 0.25 .. 1 */
    private fun tongueStr(k: Int): Float =
        0.25f + 0.75f * (0.5f + 0.5f * sin(clock * (1.9f + 0.31f * k) + k * 2.1f))

    private fun fireKey(i: Int, tint: Int, nat: Boolean): Int =
        if (nat) fireKeys[i] else when (i) {
            0 -> mixRgb(tint, Color.WHITE, 0.80f)
            1 -> mixRgb(tint, Color.WHITE, 0.35f)
            2 -> tint and 0xFFFFFF
            3 -> mixRgb(tint, Color.BLACK, 0.35f)
            else -> mixRgb(tint, Color.BLACK, 0.70f)
        }

    private fun flameRgb(t: Float, tint: Int, nat: Boolean): Int {
        val f = t.coerceIn(0f, 1f) * 4f
        val i = f.toInt().coerceAtMost(3)
        return mixRgb(fireKey(i, tint, nat), fireKey(i + 1, tint, nat), f - i)
    }

    private fun newFlame(strong: Boolean = false): Flame {
        val k = kTongues()
        val ti = rnd.nextInt(k)
        val str = if (strong) 1f else tongueStr(ti)
        val cell = width.toFloat() / k
        val span = (height - topLimit).coerceAtLeast(1f)
        val maxRise = minOf(span * 0.5f, 135f * d)
        val r0 = (9f + rnd.nextFloat() * 9f) * d * (0.7f + 0.5f * str)
        val pal = palette()
        return Flame(
            x0 = tongueX(ti),
            xj = (rnd.nextFloat() + rnd.nextFloat() - 1f) * cell * 0.5f,
            yS = height + r0 * 0.3f,
            r0 = r0,
            rise = maxRise * (0.3f + 0.7f * str) * (0.8f + 0.4f * rnd.nextFloat()) * (if (strong) 1.15f else 1f),
            phase = rnd.nextFloat() * 6.28f,
            freq = 5f + rnd.nextFloat() * 6f,
            sway = (2f + rnd.nextFloat() * 4f) * d,
            age = 0f,
            life = 0.7f + rnd.nextFloat() * 0.6f + str * 0.25f,
            tint = pal[rnd.nextInt(pal.size)],
            nat = natural
        )
    }

    private fun newSpark(): Spark {
        val pal = palette()
        return Spark(
            x = tongueX(rnd.nextInt(kTongues())) + (rnd.nextFloat() - 0.5f) * 30f * d,
            y = height.toFloat(),
            vx = (rnd.nextFloat() - 0.5f) * 70f * d,
            vy = -(90f + rnd.nextFloat() * 130f) * d,
            age = 0f,
            life = 0.9f + rnd.nextFloat() * 0.9f,
            r = (1.1f + rnd.nextFloat() * 1.6f) * d,
            tint = pal[rnd.nextInt(pal.size)],
            nat = natural
        )
    }

    private fun drawFire(canvas: Canvas, dt: Float) {
        clock += dt
        fireAcc += dt * base * 4.5f
        while (fireAcc >= 1f) {
            fireAcc -= 1f
            if (flames.size < 380) flames.add(newFlame())
        }
        sparkAcc += dt * base * 0.22f
        while (sparkAcc >= 1f) {
            sparkAcc -= 1f
            if (sparks.size < 40) sparks.add(newSpark())
        }

        val fi = flames.iterator()
        while (fi.hasNext()) {
            val f = fi.next()
            f.age += dt
            val t = f.age / f.life
            if (t >= 1f) { fi.remove(); continue }
            val u = 1f - t
            val y = f.yS - f.rise * (1.35f * t - 0.35f * t * t)
            // flame narrows towards the tip and licks sideways
            val x = f.x0 + f.xj * (1f - 0.85f * t) + sin(f.age * f.freq + f.phase) * f.sway * (0.3f + t)
            val r = f.r0 * (1f - 0.78f * t)
            val a = minOf(1f, t / 0.08f) * u * (0.55f + 0.45f * u) * 0.8f
            firePaint.shader = soft(quant(flameRgb(t, f.tint, f.nat)))
            firePaint.alpha = (a * 255f).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(r, r)
            canvas.drawCircle(0f, 0f, 1f, firePaint)
            canvas.restore()
        }

        val si = sparks.iterator()
        while (si.hasNext()) {
            val s = si.next()
            s.age += dt
            val t = s.age / s.life
            if (t >= 1f) { si.remove(); continue }
            s.vy += 110f * d * dt
            s.x += (s.vx + sin(s.age * 9f + s.life * 7f) * 22f * d) * dt
            s.y += s.vy * dt
            if (s.y < topLimit - 10f * d) { si.remove(); continue }
            val a = (1f - t) * (0.75f + 0.25f * sin(s.age * 28f))
            val r = s.r * 2.8f * (1f - 0.5f * t)
            firePaint.shader = soft(quant(flameRgb(0.15f + 0.55f * t, s.tint, s.nat)))
            firePaint.alpha = (a * 255f).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(s.x, s.y)
            canvas.scale(r, r)
            canvas.drawCircle(0f, 0f, 1f, firePaint)
            canvas.restore()
        }
        firePaint.shader = null
    }

    // =====================================================================
    //  HEARTS – glossy hearts that pop in at the bottom, float up with a lazy
    //  sway and wobble, and fade out near the top. Each heart has a soft glow,
    //  shaded body, inner highlight and a small specular gleam.
    // =====================================================================
    private class Heart(
        val x0: Float, val y0: Float, val size: Float, val vy: Float,
        val phase: Float, val freq: Float, val sway: Float, val rotAmp: Float,
        var age: Float, val col: Int, val dark: Int, val light: Int, val alpha: Float
    )

    private val hearts = ArrayList<Heart>()
    private var heartsFilled = false
    private var heartAcc = 0f

    private val heartCols = intArrayOf(
        0xFFFF2D55.toInt(), 0xFFFF5C8A.toInt(), 0xFFE91E63.toInt(), 0xFFFF1744.toInt(),
        0xFFFF7A9C.toInt(), 0xFFD81B60.toInt(), 0xFFFF4081.toInt(), 0xFFC2185B.toInt()
    )

    // unit heart: tip at (0, 0.95), lobes reach x = +-1, centre dip at (0, -0.55)
    private val heartPath = Path().apply {
        moveTo(0f, 0.95f)
        cubicTo(-0.25f, 0.70f, -1.00f, 0.25f, -1.00f, -0.30f)
        cubicTo(-1.00f, -0.80f, -0.45f, -1.00f, 0f, -0.55f)
        cubicTo(0.45f, -1.00f, 1.00f, -0.80f, 1.00f, -0.30f)
        cubicTo(1.00f, 0.25f, 0.25f, 0.70f, 0f, 0.95f)
        close()
    }
    private val heartBounds = RectF(-1.3f, -1.3f, 1.3f, 1.3f)
    private val specRect = RectF(-0.66f, -0.58f, -0.38f, -0.42f)
    private val heartP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun newHeart(prefill: Boolean): Heart {
        val span = (height - topLimit).coerceAtLeast(1f)
        val size = (7f + rnd.nextFloat() * 11f) * d
        val vy = (42f + rnd.nextFloat() * 58f) * d
        val raw = if (natural) {
            heartCols[rnd.nextInt(heartCols.size)]
        } else {
            val pal = palette()
            Color.colorToHSV(pal[rnd.nextInt(pal.size)], hsv)
            hsv[1] = (hsv[1] * (0.7f + rnd.nextFloat() * 0.3f)).coerceIn(0f, 1f)
            hsv[2] = (hsv[2] * (0.8f + rnd.nextFloat() * 0.25f)).coerceIn(0.3f, 1f)
            Color.HSVToColor(hsv)
        }
        val rgb = quant(raw)
        val col = rgb or (0xFF shl 24)
        val dark = mixRgb(col, Color.BLACK, 0.25f)
        val light = (0x66 shl 24) or (mixRgb(col, Color.WHITE, 0.60f) and 0xFFFFFF)
        val age = if (prefill) rnd.nextFloat() * ((span + size * 2f) / vy) * 0.95f else 0f
        return Heart(
            x0 = size + rnd.nextFloat() * (width - size * 2f).coerceAtLeast(1f),
            y0 = height + size * 1.3f,
            size = size, vy = vy,
            phase = rnd.nextFloat() * 6.28f,
            freq = 0.9f + rnd.nextFloat() * 1.2f,
            sway = (8f + rnd.nextFloat() * 18f) * d,
            rotAmp = 8f + rnd.nextFloat() * 14f,
            age = age, col = col, dark = dark, light = light,
            alpha = 0.78f + rnd.nextFloat() * 0.22f
        )
    }

    private fun easeOutBack(t: Float): Float {
        val k = t - 1f
        return 1f + 2.70158f * k * k * k + 1.70158f * k * k
    }

    private fun drawHeartShape(canvas: Canvas, h: Heart, x: Float, y: Float, a: Float) {
        val pop = easeOutBack(minOf(1f, h.age / 0.4f))
        val s = h.size * pop
        if (s < 0.5f) return

        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(sin(h.phase * 1.7f + h.age * h.freq * 0.9f) * h.rotAmp)
        canvas.scale(s, s)

        // soft glow
        canvas.save()
        canvas.scale(2.1f, 2.1f)
        glowP.shader = soft(h.col)
        glowP.alpha = (a * 70f).toInt().coerceIn(0, 255)
        canvas.drawCircle(0f, 0f, 1f, glowP)
        canvas.restore()

        val layered = a < 0.97f
        if (layered) canvas.saveLayerAlpha(heartBounds, (a * 255f).toInt().coerceIn(0, 255))

        // body (darker rim)
        heartP.color = h.dark
        canvas.drawPath(heartPath, heartP)
        // main colour, shifted up-left so the darker rim shows bottom-right
        canvas.save()
        canvas.translate(-0.05f, -0.07f)
        canvas.scale(0.86f, 0.86f)
        heartP.color = h.col
        canvas.drawPath(heartPath, heartP)
        canvas.restore()
        // inner light patch
        canvas.save()
        canvas.translate(-0.22f, -0.28f)
        canvas.scale(0.46f, 0.46f)
        heartP.color = h.light
        canvas.drawPath(heartPath, heartP)
        canvas.restore()
        // specular gleam
        canvas.save()
        canvas.rotate(-35f, -0.52f, -0.50f)
        heartP.color = 0xC8FFFFFF.toInt()
        canvas.drawOval(specRect, heartP)
        canvas.restore()

        if (layered) canvas.restore()
        canvas.restore()
    }

    private fun drawHearts(canvas: Canvas, dt: Float) {
        val span = (height - topLimit).coerceAtLeast(1f)
        if (!heartsFilled) {
            repeat(maxOf(6, base / 4)) { hearts.add(newHeart(true)) }
            heartsFilled = true
        }
        heartAcc += dt * base * 0.11f
        while (heartAcc >= 1f) {
            heartAcc -= 1f
            if (hearts.size < base * 2) hearts.add(newHeart(false))
        }

        val iter = hearts.iterator()
        while (iter.hasNext()) {
            val h = iter.next()
            h.age += dt
            val y = h.y0 - h.vy * h.age
            if (y + h.size * 1.3f < topLimit) { iter.remove(); continue }
            val x = h.x0 + sin(h.phase + h.age * h.freq) * h.sway
            val fadeTop = ((y - topLimit) / (span * 0.3f)).coerceIn(0f, 1f)
            val a = fadeTop * minOf(1f, h.age / 0.2f) * h.alpha
            if (a <= 0.01f) continue
            drawHeartShape(canvas, h, x, y, a)
        }
        glowP.shader = null
    }

    // =====================================================================
    //  RAIN – slanted streaks in 2 depth layers, gusting wind, and little
    //  splash rings with droplets where the near drops hit the bottom.
    // =====================================================================
    private class Streak(var x: Float, var y: Float, var len: Float, var spd: Float, var w: Float, var a: Int, var ci: Int)
    private class Splash(val x: Float, val y: Float, var age: Float, val life: Float, val r: Float, val a: Int, val ci: Int)

    private val streaks = ArrayList<Streak>()
    private val splashes = ArrayList<Splash>()
    private var rainFilled = false
    private val rainNat = 0xFFA9CCEE.toInt()
    private val rainRect = RectF()
    private val rainP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rainFillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun resetStreak(s: Streak, initial: Boolean) {
        val span = (height - topLimit).coerceAtLeast(1f)
        val depth = rnd.nextFloat()
        s.len = (10f + 26f * depth) * d
        s.spd = (520f + 380f * depth) * d
        s.w = (0.7f + 1.1f * depth) * d
        s.a = 60 + (120 * depth).toInt()
        s.ci = rnd.nextInt(1000)
        s.y = if (initial) topLimit + rnd.nextFloat() * span
        else topLimit - s.len - rnd.nextFloat() * 60f * d
        s.x = rnd.nextFloat() * (width + span * 0.25f) - span * 0.25f
    }

    private fun addSplash(x: Float, a: Int, ci: Int) {
        if (splashes.size >= 60) return
        splashes.add(
            Splash(
                x, height - (1f + rnd.nextFloat() * 5f) * d, 0f,
                0.35f + rnd.nextFloat() * 0.2f, (5f + rnd.nextFloat() * 6f) * d, a, ci
            )
        )
    }

    private fun drawRain(canvas: Canvas, dt: Float) {
        clock += dt
        val target = base * 3
        while (streaks.size < target) {
            val s = Streak(0f, 0f, 0f, 0f, 0f, 0, 0)
            resetStreak(s, !rainFilled)
            streaks.add(s)
        }
        while (streaks.size > target) streaks.removeAt(streaks.size - 1)
        rainFilled = true

        val ang = 0.2f + sin(clock * 0.4f) * 0.07f      // wind gusts
        val ux = sin(ang)
        val uy = cos(ang)
        val cols = palette()

        for (s in streaks) {
            s.x += ux * s.spd * dt
            s.y += uy * s.spd * dt
            if (s.y >= height - 2f * d) {
                if (s.a > 110 && rnd.nextFloat() < 0.6f) addSplash(s.x, s.a, s.ci)
                resetStreak(s, false)
                continue
            }
            rainP.color = if (natural) rainNat else cols[s.ci % cols.size]
            rainP.alpha = s.a
            rainP.strokeWidth = s.w
            canvas.drawLine(s.x, s.y, s.x - ux * s.len, s.y - uy * s.len, rainP)
        }

        val iter = splashes.iterator()
        while (iter.hasNext()) {
            val sp = iter.next()
            sp.age += dt
            val t = sp.age / sp.life
            if (t >= 1f) { iter.remove(); continue }
            val col = if (natural) rainNat else cols[sp.ci % cols.size]
            val rx = sp.r * (0.25f + 0.85f * t)
            val ry = rx * 0.35f
            rainP.color = col
            rainP.strokeWidth = 1.1f * d
            rainP.alpha = (sp.a * 0.8f * (1f - t)).toInt().coerceIn(0, 255)
            rainRect.set(sp.x - rx, sp.y - ry, sp.x + rx, sp.y + ry)
            canvas.drawOval(rainRect, rainP)

            rainFillP.color = col
            rainFillP.alpha = (sp.a * (1f - t)).toInt().coerceIn(0, 255)
            val up = sin(t * 3.1416f) * sp.r * 0.9f
            canvas.drawCircle(sp.x - sp.r * 0.9f * t, sp.y - up, 0.9f * d, rainFillP)
            canvas.drawCircle(sp.x + sp.r * 1.1f * t, sp.y - up * 0.8f, 0.8f * d, rainFillP)
            canvas.drawCircle(sp.x + sp.r * 0.2f * t, sp.y - up * 1.2f, 0.7f * d, rainFillP)
        }
    }

    // =====================================================================
    //  FIREFLIES – soft yellow-green lights that wander slowly and blink
    //  (additive glow + bright core).
    // =====================================================================
    private class Fly(
        var x: Float, var y: Float, var ang: Float, val spd: Float, var turn: Float,
        val phase: Float, val freq: Float, val gr: Float, val col: Int, var boost: Float
    )

    private val flies = ArrayList<Fly>()
    private val flyCols = intArrayOf(0xFFCDFF4D.toInt(), 0xFFE6FF70.toInt(), 0xFFFFE45C.toInt(), 0xFFB6F542.toInt())
    private val flyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    private fun newFly(): Fly {
        val span = (height - topLimit).coerceAtLeast(1f)
        val pal = palette()
        return Fly(
            x = rnd.nextFloat() * width,
            y = topLimit + rnd.nextFloat() * span,
            ang = rnd.nextFloat() * 6.28f,
            spd = (8f + rnd.nextFloat() * 18f) * d,
            turn = 0f,
            phase = rnd.nextFloat() * 6.28f,
            freq = 1.2f + rnd.nextFloat() * 2.2f,
            gr = (10f + rnd.nextFloat() * 12f) * d,
            col = if (natural) flyCols[rnd.nextInt(flyCols.size)] else pal[rnd.nextInt(pal.size)],
            boost = 0f
        )
    }

    private fun drawFireflies(canvas: Canvas, dt: Float) {
        clock += dt
        val target = maxOf(8, base / 3)
        while (flies.size < target) flies.add(newFly())
        while (flies.size > target) flies.removeAt(flies.size - 1)

        for (f in flies) {
            f.turn += (rnd.nextFloat() - 0.5f) * 9f * dt
            f.turn -= f.turn * 1.2f * dt
            f.ang += f.turn * dt
            f.x += cos(f.ang) * f.spd * dt
            f.y += sin(f.ang) * f.spd * dt + sin(clock * 1.3f + f.phase) * 7f * d * dt
            val m = f.gr
            if (f.x < -m) f.x = width + m else if (f.x > width + m) f.x = -m
            if (f.y < topLimit - m) f.y = height + m else if (f.y > height + m) f.y = topLimit - m
            f.boost = maxOf(0f, f.boost - dt * 0.8f)

            val sn = sin(clock * f.freq + f.phase)
            val b = minOf(1f, (if (sn > 0f) sn * sn * sn else 0.04f) + f.boost)
            if (b < 0.03f) continue

            flyPaint.shader = soft(quant(f.col))
            flyPaint.alpha = (b * 230f).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(f.x, f.y)
            canvas.scale(f.gr, f.gr)
            canvas.drawCircle(0f, 0f, 1f, flyPaint)
            canvas.restore()

            flyPaint.shader = null
            flyPaint.color = mixRgb(f.col, Color.WHITE, 0.75f)
            flyPaint.alpha = (b * 255f).toInt().coerceIn(0, 255)
            canvas.drawCircle(f.x, f.y, 1.7f * d, flyPaint)
        }
        flyPaint.shader = null
    }

    // =====================================================================
    //  BUBBLES – soap bubbles: transparent body, iridescent rim, window
    //  reflections; they wobble, drift up and pop at random heights.
    // =====================================================================
    private class Bubble(
        val x0: Float, var y: Float, val r: Float, val vy: Float,
        val phase: Float, val freq: Float, val sway: Float,
        val popY: Float, var popT: Float, val rot: Float, val col: Int, val a: Float, var age: Float
    )

    private val bubbles = ArrayList<Bubble>()
    private var bubblesFilled = false
    private var bubbleAcc = 0f
    private val bubbleRect = RectF(-0.7f, -0.7f, 0.7f, 0.7f)

    private val bubbleBody = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(0x00FFFFFF, 0x18FFFFFF, 0x66FFFFFF),
        floatArrayOf(0f, 0.78f, 1f), Shader.TileMode.CLAMP
    )
    private val bubbleRim = SweepGradient(
        0f, 0f,
        intArrayOf(
            0xAAFF6EC7.toInt(), 0xAA7CC8FF.toInt(), 0xAA7CFFB2.toInt(),
            0xAAFFE066.toInt(), 0xAAFF6EC7.toInt()
        ),
        null
    )
    private val bubbleBodyP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bubbleRimP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.07f
    }
    private val bubbleHiP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.07f
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    private fun newBubble(prefill: Boolean): Bubble {
        val span = (height - topLimit).coerceAtLeast(1f)
        val r = (8f + rnd.nextFloat() * 16f) * d
        val y = if (prefill) topLimit + span * (0.2f + rnd.nextFloat() * 0.8f) else height + r
        val pal = palette()
        return Bubble(
            x0 = r + rnd.nextFloat() * (width - r * 2f).coerceAtLeast(1f),
            y = y, r = r,
            vy = (18f + rnd.nextFloat() * 26f) * d,
            phase = rnd.nextFloat() * 6.28f,
            freq = 0.7f + rnd.nextFloat() * 0.9f,
            sway = (6f + rnd.nextFloat() * 14f) * d,
            popY = topLimit + (y - topLimit) * (0.05f + rnd.nextFloat() * 0.70f),
            popT = -1f,
            rot = rnd.nextFloat() * 360f,
            col = pal[rnd.nextInt(pal.size)] or (0xFF shl 24),
            a = 0.75f + rnd.nextFloat() * 0.25f,
            age = 0f
        )
    }

    private fun drawBubbles(canvas: Canvas, dt: Float) {
        if (!bubblesFilled) {
            repeat(maxOf(5, base / 4)) { bubbles.add(newBubble(true)) }
            bubblesFilled = true
        }
        bubbleAcc += dt * base * 0.12f
        while (bubbleAcc >= 1f) {
            bubbleAcc -= 1f
            if (bubbles.size < base * 3 / 2) bubbles.add(newBubble(false))
        }

        val iter = bubbles.iterator()
        while (iter.hasNext()) {
            val b = iter.next()
            b.age += dt
            b.y -= b.vy * dt
            if (b.popT < 0f && b.y < b.popY) b.popT = 0f
            if (b.popT >= 0f) {
                b.popT += dt / 0.18f
                if (b.popT >= 1f) { iter.remove(); continue }
            }
            if (b.y + b.r < topLimit) { iter.remove(); continue }

            val x = b.x0 + sin(b.phase + b.age * b.freq) * b.sway
            val popping = b.popT >= 0f
            val pop = if (popping) 1f + 0.25f * b.popT else 1f
            val a = b.a * (if (popping) 1f - b.popT else minOf(1f, b.age / 0.3f))
            val w = sin(b.age * 3f + b.phase) * 0.05f

            canvas.save()
            canvas.translate(x, b.y)
            canvas.scale(b.r * pop * (1f + w), b.r * pop * (1f - w))

            bubbleBodyP.shader = bubbleBody
            bubbleBodyP.alpha = (a * 255f).toInt().coerceIn(0, 255)
            canvas.drawCircle(0f, 0f, 1f, bubbleBodyP)

            canvas.save()
            canvas.rotate(b.rot)
            if (natural) {
                bubbleRimP.shader = bubbleRim
                bubbleRimP.alpha = (a * 255f).toInt().coerceIn(0, 255)
            } else {
                bubbleRimP.shader = null
                bubbleRimP.color = b.col
                bubbleRimP.alpha = (a * 200f).toInt().coerceIn(0, 255)
            }
            canvas.drawCircle(0f, 0f, 0.95f, bubbleRimP)
            canvas.restore()

            bubbleHiP.alpha = (a * 230f).toInt().coerceIn(0, 255)
            canvas.drawArc(bubbleRect, 200f, 55f, false, bubbleHiP)
            bubbleHiP.alpha = (a * 90f).toInt().coerceIn(0, 255)
            canvas.drawArc(bubbleRect, 20f, 40f, false, bubbleHiP)
            canvas.restore()
        }
        bubbleBodyP.shader = null
        bubbleRimP.shader = null
    }

    // =====================================================================
    //  STARS – twinkling stars (big ones get a 4-point sparkle) and an
    //  occasional shooting star with a fading tail.
    // =====================================================================
    private class Star(
        val fx: Float, val fy: Float, val r: Float,
        val phase: Float, val freq: Float, val col: Int, var flash: Float
    )

    private class Shoot(
        var x: Float, var y: Float, val ux: Float, val uy: Float,
        val spd: Float, var age: Float, val life: Float
    )

    private val stars = ArrayList<Star>()
    private val shoots = ArrayList<Shoot>()
    private var starsFilled = false
    private var shootIn = 3f
    private val starCols = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFFFF4C2.toInt(), 0xFFCFE4FF.toInt(), 0xFFFFE0B0.toInt()
    )
    private val starPath = Path().apply {
        moveTo(0f, -1f)
        quadTo(0f, 0f, 1f, 0f)
        quadTo(0f, 0f, 0f, 1f)
        quadTo(0f, 0f, -1f, 0f)
        quadTo(0f, 0f, 0f, -1f)
        close()
    }
    private val starP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun newStar(): Star {
        val pal = palette()
        return Star(
            fx = rnd.nextFloat(), fy = rnd.nextFloat(),
            r = (0.8f + rnd.nextFloat() * rnd.nextFloat() * 2.6f) * d,
            phase = rnd.nextFloat() * 6.28f,
            freq = 0.8f + rnd.nextFloat() * 2.4f,
            col = (if (natural) starCols[rnd.nextInt(starCols.size)] else pal[rnd.nextInt(pal.size)]) or (0xFF shl 24),
            flash = 0f
        )
    }

    private fun drawStars(canvas: Canvas, dt: Float) {
        clock += dt
        val span = (height - topLimit).coerceAtLeast(1f)
        if (!starsFilled) {
            repeat(maxOf(24, (base * 1.1f).toInt())) { stars.add(newStar()) }
            starsFilled = true
        }

        for (s in stars) {
            s.flash = maxOf(0f, s.flash - dt * 1.4f)
            val tw = 0.5f + 0.5f * sin(clock * s.freq + s.phase)
            val b = minOf(1f, 0.30f + 0.70f * tw + s.flash)
            val x = s.fx * width
            val y = topLimit + s.fy * span
            val big = s.r > 1.9f * d

            // soft glow
            val gr = s.r * (if (big) 3.2f else 2.4f)
            starP.shader = soft(quant(s.col))
            starP.alpha = ((if (big) 0.35f else 0.55f) * b * 255f).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(gr, gr)
            canvas.drawCircle(0f, 0f, 1f, starP)
            canvas.restore()
            starP.shader = null

            starP.color = s.col
            if (big || s.flash > 0.1f) {
                val sc = s.r * (1.5f + s.flash * 0.8f)
                starP.alpha = (b * 255f).toInt().coerceIn(0, 255)
                canvas.save()
                canvas.translate(x, y)
                canvas.scale(sc, sc)
                canvas.drawPath(starPath, starP)
                canvas.rotate(45f)
                canvas.scale(0.55f, 0.55f)
                starP.alpha = (b * 150f).toInt().coerceIn(0, 255)
                canvas.drawPath(starPath, starP)
                canvas.restore()
            } else {
                starP.alpha = (b * 255f).toInt().coerceIn(0, 255)
                canvas.drawCircle(x, y, s.r * 0.6f, starP)
            }
        }

        // shooting stars
        shootIn -= dt
        if (shootIn <= 0f) {
            shootIn = 2.5f + rnd.nextFloat() * 3.5f
            if (shoots.size < 2) {
                val dir = if (rnd.nextBoolean()) 1f else -1f
                val a = 0.45f + rnd.nextFloat() * 0.35f
                shoots.add(
                    Shoot(
                        x = if (dir > 0f) rnd.nextFloat() * width * 0.5f else width - rnd.nextFloat() * width * 0.5f,
                        y = topLimit + rnd.nextFloat() * span * 0.35f,
                        ux = dir * cos(a), uy = sin(a),
                        spd = (380f + rnd.nextFloat() * 180f) * d,
                        age = 0f, life = 0.7f + rnd.nextFloat() * 0.4f
                    )
                )
            }
        }
        val iter = shoots.iterator()
        while (iter.hasNext()) {
            val sh = iter.next()
            sh.age += dt
            val t = sh.age / sh.life
            if (t >= 1f) { iter.remove(); continue }
            sh.x += sh.ux * sh.spd * dt
            sh.y += sh.uy * sh.spd * dt
            val fade = minOf(1f, t / 0.12f, (1f - t) / 0.35f).coerceIn(0f, 1f)
            starP.shader = soft(0xE8F1FF)
            for (i in 0 until 14) {
                val k = 1f - i / 14f
                val rad = maxOf(0.6f, 2.6f - i * 0.14f) * d * 2.2f
                starP.alpha = (k * fade * 230f).toInt().coerceIn(0, 255)
                canvas.save()
                canvas.translate(sh.x - sh.ux * i * 7f * d, sh.y - sh.uy * i * 7f * d)
                canvas.scale(rad, rad)
                canvas.drawCircle(0f, 0f, 1f, starP)
                canvas.restore()
            }
            starP.shader = null
        }
    }
}
