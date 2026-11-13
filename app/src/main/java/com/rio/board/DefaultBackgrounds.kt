package com.rio.board

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import java.io.File

/** Wallpapers that ship inside the app (assets/backgrounds) so there is always something to pick. */
object DefaultBackgrounds {
    val items: List<Pair<String, String>> = listOf(
        "Panda glow" to "panda_glow.jpg",
        "Bamboo panda" to "panda_bamboo.jpg",
        "Bears" to "bears.jpg",
        "Cats" to "cats.jpg",
        "Miku" to "miku.jpg",
        "Zero Two" to "zero_two.jpg",
        "Psycho" to "psycho.jpg",
        "Butterfly" to "butterfly.jpg"
    )

    private val thumbs = HashMap<String, Bitmap>()

    private fun thumb(ctx: Context, file: String): Bitmap? {
        thumbs[file]?.let { return it }
        return try {
            val o = BitmapFactory.Options().apply { inSampleSize = 3 }
            val b = ctx.assets.open("backgrounds/$file").use { BitmapFactory.decodeStream(it, null, o) }
            if (b != null) thumbs[file] = b
            b
        } catch (e: Exception) {
            null
        }
    }

    /** Copies the chosen wallpaper in as the keyboard background and tells the keyboard to reload. */
    fun setAsBackground(ctx: Context, file: String): Boolean {
        return try {
            ctx.assets.open("backgrounds/$file").use { input ->
                File(ctx.filesDir, "kb_bg.jpg").outputStream().use { out -> input.copyTo(out) }
            }
            ctx.getSharedPreferences("rio_board", Context.MODE_PRIVATE).edit().putBoolean("bgon", true).apply()
            RioBoardService.instance?.reloadBackground()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** A sideways-scrolling row of thumbnails; tap one to use it. */
    fun strip(ctx: Context, onPicked: () -> Unit = {}): View {
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }
        items.forEach { (name, file) ->
            val iv = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#2B2D35"))
                thumb(ctx, file)?.let { setImageBitmap(it) }
                contentDescription = name
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
                    }
                }
                layoutParams = LinearLayout.LayoutParams(dp(110), dp(62)).apply { setMargins(dp(4), 0, dp(4), 0) }
                setOnClickListener {
                    val ok = setAsBackground(ctx, file)
                    Toast.makeText(ctx, if (ok) "$name set" else "Could not set background", Toast.LENGTH_SHORT).show()
                    if (ok) onPicked()
                }
            }
            row.addView(iv)
        }
        return HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(row)
        }
    }
}
