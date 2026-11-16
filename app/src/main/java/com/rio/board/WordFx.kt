package com.rio.board

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Word animations: type "love" and a big heart pops up, small hearts burst out and float up.
 * Transparent overlay on top of the keyboard, does not take touches.
 * (Turn the snow mode to "full screen" and it plays over the whole screen, not only on the keyboard.)
 */
class WordFx(context: Context) : View(context) {

    /** [main] = the big emoji, [bits] = the small ones that burst out, [rise] = also let some float up from the bottom. */
    class Fx(val main: String, val bits: List<String>, val rise: Boolean = true)

    private class P(
        val x: Float, val y: Float, val vx: Float, val vy: Float, val g: Float,
        val size: Float, val rot: Float, val vr: Float, val t0: Long, val life: Long,
        val e: String, val sway: Float
    )

    private val ps = ArrayList<P>()
    private var mainE: String? = null
    private var mainT0 = 0L
    private var lastPlay = 0L
    private val d = resources.displayMetrics.density
    private val rnd = Random()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val mainMs = 1800L

    fun play(fx: Fx) {
        val now = SystemClock.uptimeMillis()
        if (width == 0 || height == 0 || now - lastPlay < 900) return
        lastPlay = now
        mainE = fx.main
        mainT0 = now
        val cx = width / 2f
        val cy = height / 2f
        val bits = if (fx.bits.isEmpty()) listOf(fx.main) else fx.bits

        // burst from the middle
        repeat(22) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val sp = (160f + rnd.nextFloat() * 420f) * d
            ps.add(
                P(
                    cx, cy, cos(a) * sp, sin(a) * sp - 120f * d, 380f * d,
                    (16f + rnd.nextFloat() * 20f) * d, rnd.nextFloat() * 60f - 30f, rnd.nextFloat() * 240f - 120f,
                    now + 120, 1100L + rnd.nextInt(700), bits[rnd.nextInt(bits.size)], 0f
                )
            )
        }
        // floating up from the bottom (like live-video hearts)
        if (fx.rise) {
            repeat(16) { i ->
                ps.add(
                    P(
                        width * (0.08f + rnd.nextFloat() * 0.84f), height + 20f * d,
                        0f, -(150f + rnd.nextFloat() * 260f) * d, 0f,
                        (18f + rnd.nextFloat() * 22f) * d, rnd.nextFloat() * 30f - 15f, rnd.nextFloat() * 60f - 30f,
                        now + 250L + i * 70L, 1700L + rnd.nextInt(900), bits[rnd.nextInt(bits.size)],
                        (6f + rnd.nextFloat() * 14f) * d
                    )
                )
            }
        }
        postInvalidateOnAnimation()
    }

    private fun backOut(t: Float): Float {      // grows past 1 and settles: the "pop"
        val c1 = 2.2f
        val c3 = c1 + 1f
        val x = t - 1f
        return 1f + c3 * x * x * x + c1 * x * x
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        var alive = false

        val me = mainE
        if (me != null) {
            val t = now - mainT0
            if (t >= mainMs) {
                mainE = null
            } else {
                alive = true
                val scale = if (t < 420) backOut(t / 420f).coerceAtLeast(0f)
                else 1f + 0.07f * sin((t - 420) / 110f)             // heartbeat
                val alpha = if (t > mainMs - 500) ((mainMs - t) / 500f).coerceIn(0f, 1f) else 1f
                paint.textSize = 120f * d * scale
                paint.alpha = (alpha * 255).toInt()
                canvas.drawText(me, width / 2f, height / 2f + paint.textSize * 0.35f, paint)
            }
        }

        val it = ps.iterator()
        while (it.hasNext()) {
            val p = it.next()
            val age = now - p.t0
            if (age < 0) {
                alive = true
                continue
            }
            if (age >= p.life) {
                it.remove()
                continue
            }
            alive = true
            val s = age / 1000f
            val frac = age.toFloat() / p.life
            val x = p.x + p.vx * s + p.sway * sin(age / 280f)
            val y = p.y + p.vy * s + 0.5f * p.g * s * s
            val a = if (frac < 0.6f) 1f else (1f - (frac - 0.6f) / 0.4f)
            paint.textSize = p.size
            paint.alpha = (a.coerceIn(0f, 1f) * 255).toInt()
            canvas.save()
            canvas.rotate(p.rot + p.vr * s, x, y)
            canvas.drawText(p.e, x, y + p.size * 0.35f, paint)
            canvas.restore()
        }
        if (alive) postInvalidateOnAnimation()
    }
}

/** Which words play which animation. Your own words (added in the app) win over the built-in ones. */
object WordFxCatalog {

    private fun fx(main: String, vararg bits: String, rise: Boolean = true) = WordFx.Fx(main, bits.toList(), rise)

    private val heart = fx("\u2764\uFE0F", "\u2764\uFE0F", "\uD83D\uDC95", "\uD83D\uDC96", "\uD83D\uDC97", "\uD83D\uDC98")
    private val birthday = fx("\uD83C\uDF82", "\uD83C\uDF89", "\uD83C\uDF88", "\uD83C\uDF8A", "\uD83E\uDD73")
    private val cheer = fx("\uD83C\uDF89", "\uD83C\uDF8A", "\u2728", "\uD83D\uDC4F", "\uD83E\uDD73")
    private val laugh = fx("\uD83D\uDE02", "\uD83D\uDE02", "\uD83E\uDD23", "\uD83D\uDE39")
    private val fire = fx("\uD83D\uDD25", "\uD83D\uDD25", "\u2728", "\uD83D\uDCA5")
    private val kiss = fx("\uD83D\uDE18", "\uD83D\uDC8B", "\uD83D\uDE18", "\uD83D\uDC95")
    private val thanks = fx("\uD83D\uDE4F", "\uD83D\uDE4F", "\uD83D\uDC90", "\u2728")
    private val sorry = fx("\uD83E\uDD7A", "\uD83E\uDD7A", "\uD83D\uDC94", "\uD83D\uDE22", rise = false)
    private val night = fx("\uD83C\uDF19", "\u2B50", "\u2728", "\uD83D\uDE34", "\uD83C\uDF19")
    private val morning = fx("\uD83C\uDF1E", "\u2600\uFE0F", "\uD83C\uDF3C", "\u2728")
    private val wow = fx("\uD83E\uDD29", "\u2728", "\uD83E\uDD29", "\uD83C\uDF1F")
    private val rio = fx("\uD83D\uDC30", "\uD83D\uDC30", "\u2328\uFE0F", "\u2728")

    private val words: Map<String, WordFx.Fx> = mapOf(
        "love" to heart, "luv" to heart, "love you" to heart, "adare" to heart, "aadare" to heart,
        "ආදරේ" to heart, "ආදරෙයි" to heart, "ආදරය" to heart,
        "birthday" to birthday, "bday" to birthday, "happy birthday" to birthday,
        "congrats" to cheer, "congratulations" to cheer, "party" to cheer, "yay" to cheer,
        "haha" to laugh, "hahaha" to laugh, "hehe" to laugh, "lol" to laugh, "lmao" to laugh,
        "fire" to fire, "kiss" to kiss, "muah" to kiss,
        "thanks" to thanks, "thank you" to thanks, "thankyou" to thanks, "ස්තුතියි" to thanks,
        "sorry" to sorry, "සමාවෙන්න" to sorry,
        "good night" to night, "goodnight" to night, "gn" to night,
        "good morning" to morning, "gm" to morning,
        "wow" to wow, "rio" to rio
    )

    /** Your own words, one per line: word = emoji */
    fun custom(prefs: SharedPreferences): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        (prefs.getString("wfx_custom", "") ?: "").lines().forEach { line ->
            val parts = line.split("=", limit = 2).let { if (it.size == 2) it else line.trim().split(Regex("\\s+"), 2) }
            if (parts.size == 2) {
                val w = parts[0].trim().lowercase()
                val e = parts[1].trim()
                if (w.isNotEmpty() && e.isNotEmpty()) out[w] = e
            }
        }
        return out
    }

    fun find(prev: String, word: String, custom: Map<String, String>): WordFx.Fx? {
        val two = "$prev $word"
        custom[two]?.let { return WordFx.Fx(it, listOf(it), true) }
        custom[word]?.let { return WordFx.Fx(it, listOf(it), true) }
        return words[two] ?: words[word]
    }
}
