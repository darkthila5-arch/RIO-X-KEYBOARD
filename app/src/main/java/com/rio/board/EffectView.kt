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
import android.os.SystemClock
import android.view.View
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Transparent overlay with 7 effects: SNOW, DROPS (water drops sliding down glass),
 * LEAVES (tumbling leaves), FLOWERS (falling petals), SMOKE (rising plumes),
 * FIRE (flickering flames + sparks), HEARTS (floating glossy hearts).
 * Replaces SnowView (same API + [type] + [natural]). Does not consume touches.
 */
class EffectView(context: Context) : View(context) {

    enum class Type { SNOW, DROPS, LEAVES, FLOWERS, SMOKE, FIRE, HEARTS }

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
    //  FLOWERS – realistic falling flower petals (sakura / rose / custom).
    //
    //  Each petal is a smooth teardrop shape drawn with cubicTo bezier
    //  curves.  Physics: gravity + gentle air-drag + horizontal wind drift
    //  + sinusoidal sway.  3-D feel: petal flips around its long axis
    //  (cos-scaled scaleX), back-face is a darker tint.  A soft radial
    //  gradient gives the petal a slightly lighter centre so it looks
    //  paper-thin rather than flat.  A tiny white specular dot sits at the
    //  upper-left for a slightly waxy / glossy petal look.
    // =====================================================================

    /** One falling petal. All angles in radians unless noted. */
    private class Petal(
        var x: Float,          // screen X (updated each frame)
        var y: Float,          // screen Y
        val w: Float,          // half-width of petal (pre-scaled to dp)
        val h: Float,          // half-height of petal
        val vy: Float,         // base downward speed (dp/s)
        var vx: Float,         // lateral speed (dp/s, can be negative)
        var phase: Float,      // sway oscillation phase
        val swayAmp: Float,    // horizontal sway amplitude (dp)
        val swayFreq: Float,   // sway frequency (rad/s)
        var tilt: Float,       // current 3-D flip angle (radians)
        val tiltSpd: Float,    // flip speed (rad/s)
        var spin: Float,       // in-plane rotation angle (degrees)
        val spinSpd: Float,    // in-plane rotation speed (deg/s)
        val baseColor: Int,    // face colour
        val darkColor: Int,    // back-face / shadow colour
        val alpha: Int         // 160-255
    )

    private val petals = ArrayList<Petal>()
    private var petalsFilled = false

    // natural sakura palette – soft pinks and near-whites
    private val sakura = intArrayOf(
        0xFFFFB7C5.toInt(), // sakura pink
        0xFFFF8FAB.toInt(), // deeper rose
        0xFFFFC0CB.toInt(), // classic pink
        0xFFFFD1DC.toInt(), // blush
        0xFFFFECF1.toInt(), // near white petal
        0xFFE91E8C.toInt(), // vivid pink accent
        0xFFFF6F91.toInt(), // coral petal
        0xFFF8BBD0.toInt()  // pale rose
    )

    // unit petal path: tip at (0, -1), base at (0, 1), max width ~±0.65 at y≈0
    // Looks like a rounded teardrop / rose petal.
    private val petalPath = Path().apply {
        moveTo(0f, -1f)
        cubicTo( 0.70f, -0.55f,  0.68f,  0.45f, 0f,  1f)
        cubicTo(-0.68f,  0.45f, -0.70f, -0.55f, 0f, -1f)
        close()
    }

    // Right half darker for depth
    private val petalRight = Path().apply {
        moveTo(0f, -1f)
        cubicTo(0.70f, -0.55f, 0.68f, 0.45f, 0f, 1f)
        close()
    }

    private val petalFillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val petalShadeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val petalSpecP  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.WHITE
    }
    private val petalVeinP  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private fun maxPetals() = maxOf(20, base)

    private fun petalColor(): Pair<Int, Int> {
        val col: Int = if (natural) {
            sakura[rnd.nextInt(sakura.size)]
        } else {
            val pal = palette()
            val c0 = pal[rnd.nextInt(pal.size)]
            Color.colorToHSV(c0, hsv)
            // slightly randomize brightness so not all petals look identical
            hsv[1] = (hsv[1] * (0.6f + rnd.nextFloat() * 0.4f)).coerceIn(0f, 1f)
            hsv[2] = (hsv[2] * (0.75f + rnd.nextFloat() * 0.35f)).coerceIn(0.25f, 1f)
            Color.HSVToColor(hsv)
        }
        // darker variant for back-face and right-half shadow
        Color.colorToHSV(col, hsv)
        hsv[2] = (hsv[2] * 0.58f).coerceAtLeast(0.05f)
        hsv[1] = (hsv[1] * 1.15f).coerceIn(0f, 1f)
        val dark = Color.HSVToColor(hsv)
        return col to dark
    }

    private fun newPetal(fromTop: Boolean): Petal {
        val size = (5f + rnd.nextFloat() * 9f) * d  // 5-14 dp half-height
        val aspect = 0.52f + rnd.nextFloat() * 0.26f // width/height ratio 0.52-0.78
        val span = (height - topLimit).coerceAtLeast(1f)
        val y = if (fromTop) topLimit - size * 2f - rnd.nextFloat() * 60f * d
                else         topLimit + rnd.nextFloat() * span

        val (col, dark) = petalColor()

        return Petal(
            x          = rnd.nextFloat() * width,
            y          = y,
            w          = size * aspect,
            h          = size,
            vy         = (22f + rnd.nextFloat() * 38f) * d,   // 22-60 dp/s fall speed
            vx         = (rnd.nextFloat() - 0.5f) * 18f * d,  // initial lateral drift
            phase      = rnd.nextFloat() * 6.28f,
            swayAmp    = (12f + rnd.nextFloat() * 28f) * d,   // 12-40 dp sway
            swayFreq   = 0.7f + rnd.nextFloat() * 1.1f,       // sway cycles/s
            tilt       = rnd.nextFloat() * 6.28f,
            tiltSpd    = (0.9f + rnd.nextFloat() * 1.8f) * (if (rnd.nextBoolean()) 1f else -1f),
            spin       = rnd.nextFloat() * 360f,
            spinSpd    = (8f + rnd.nextFloat() * 25f) * (if (rnd.nextBoolean()) 1f else -1f),
            baseColor  = col,
            darkColor  = dark,
            alpha      = (165 + rnd.nextInt(80))               // 165-245
        )
    }

    private fun drawFlowers(canvas: Canvas, dt: Float) {
        val target = maxOf(12, base / 3)
        while (petals.size < target) petals.add(newPetal(petalsFilled))
        petalsFilled = true

        val iter = petals.iterator()
        while (iter.hasNext()) {
            val p = iter.next()

            // --- physics ---
            p.phase += p.swayFreq * dt
            // sinusoidal sway changes lateral velocity smoothly
            p.vx += (sin(p.phase) * p.swayAmp * p.swayFreq * 2.5f - p.vx * 0.4f) * dt
            // gentle air drag on downward speed
            p.y  += (p.vy + cos(p.tilt) * p.vy * 0.12f) * dt
            p.x  += p.vx * dt
            p.tilt += p.tiltSpd * dt
            p.spin += p.spinSpd * dt

            // wrap X (petals drift off screen, respawn on other side)
            if (p.x < -p.w * 3f)  p.x = width  + p.w * 2f
            if (p.x > width + p.w * 3f) p.x = -p.w * 2f

            // remove once fully below screen
            if (p.y - p.h * 2f > height) {
                iter.remove()
                continue
            }

            // --- 3-D flip: cos of tilt gives apparent width scale ---
            val flipScale = cos(p.tilt)
            val absFlip  = maxOf(0.08f, abs(flipScale))  // never fully flat
            val facingFront = flipScale >= 0f

            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.spin)
            canvas.scale(p.w * absFlip, p.h)       // non-uniform: width flips, height stable

            // --- back face (shown when flipped, slightly darker) ---
            if (!facingFront) {
                petalFillP.color = p.darkColor
                petalFillP.alpha = (p.alpha * 0.80f).toInt()
                canvas.drawPath(petalPath, petalFillP)
            } else {
                // --- front face ---
                petalFillP.color = p.baseColor
                petalFillP.alpha = p.alpha
                canvas.drawPath(petalPath, petalFillP)

                // right-half shadow for petal curl / depth
                petalShadeP.color = p.darkColor
                petalShadeP.alpha = (p.alpha * 0.28f).toInt()
                canvas.drawPath(petalRight, petalShadeP)

                // central vein  (midrib from tip to base)
                Color.colorToHSV(p.darkColor, hsv)
                hsv[2] = (hsv[2] * 0.75f).coerceAtLeast(0.03f)
                petalVeinP.color = Color.HSVToColor(hsv)
                petalVeinP.alpha = (p.alpha * 0.55f).toInt()
                petalVeinP.strokeWidth = 0.055f   // in petal-local units
                canvas.drawLine(0f, -0.82f, 0f, 0.78f, petalVeinP)

                // two side veins
                petalVeinP.alpha = (p.alpha * 0.30f).toInt()
                petalVeinP.strokeWidth = 0.035f
                canvas.drawLine(0f, -0.35f,  0.38f, 0.25f, petalVeinP)
                canvas.drawLine(0f, -0.35f, -0.38f, 0.25f, petalVeinP)
                canvas.drawLine(0f,  0.15f,  0.28f, 0.65f, petalVeinP)
                canvas.drawLine(0f,  0.15f, -0.28f, 0.65f, petalVeinP)

                // small specular glint near tip
                petalSpecP.alpha = (p.alpha * 0.55f).toInt()
                canvas.drawCircle(-0.22f, -0.60f, 0.10f, petalSpecP)
            }

            canvas.restore()
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
}
