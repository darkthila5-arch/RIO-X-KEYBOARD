package com.rio.board

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Transparent overlay with 3 effects: SNOW, DROPS (water drops sliding down glass), LEAVES (tumbling leaves).
 * Replaces SnowView (same API + [type] + [natural]). Does not consume touches.
 */
class EffectView(context: Context) : View(context) {

    enum class Type { SNOW, DROPS, LEAVES }

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

    /** true = use the built-in natural look (autumn leaves / clear water) instead of [colors]. */
    var natural: Boolean = false

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
        }
    }

    // ---------- shared ----------
    private val rnd = Random()
    private val d = resources.displayMetrics.density
    private var last = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun clearAll() {
        flakes.clear(); drops.clear(); trails.clear(); leaves.clear(); leavesFilled = false
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
}
