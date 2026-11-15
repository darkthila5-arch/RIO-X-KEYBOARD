package com.rio.board

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Looping video backgrounds that ship inside the app (assets/videos/NAME.mp4 + NAME.jpg preview).
 * Played muted behind the keys; paused automatically when the keyboard is hidden.
 */
object AnimatedBackgrounds {
    val items: List<Pair<String, String>> = listOf(
        "Aurora" to "aurora",
        "Neon waves" to "neon_waves",
        "Starfield" to "starfield",
        "Lava" to "lava",
        "Synthwave" to "synthwave",
        "Bokeh" to "bokeh",
        "Galaxy" to "galaxy",
        "Plasma" to "plasma"
    )

    private val thumbs = HashMap<String, Bitmap>()

    fun thumb(ctx: Context, id: String): Bitmap? {
        thumbs[id]?.let { return it }
        return try {
            val b = ctx.assets.open("videos/$id.jpg").use { BitmapFactory.decodeStream(it) }
            if (b != null) thumbs[id] = b
            b
        } catch (e: Exception) {
            null
        }
    }

    /** A sideways-scrolling row of previews; tap one to use it as the keyboard background. */
    fun strip(ctx: Context, onPicked: () -> Unit = {}): View {
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }
        items.filter { it.second !in RemoteConfig.current.hiddenVideos }.forEach { (name, id) ->
            val cell = FrameLayout(ctx).apply {
                setBackgroundColor(Color.parseColor("#2B2D35"))
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
                    }
                }
                layoutParams = LinearLayout.LayoutParams(dp(110), dp(62)).apply { setMargins(dp(4), 0, dp(4), 0) }
                addView(ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    thumb(ctx, id)?.let { setImageBitmap(it) }
                    contentDescription = name
                })
                addView(TextView(ctx).apply {
                    text = "▶"
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END))
                setOnClickListener {
                    val svc = RioBoardService.instance
                    if (svc != null) {
                        svc.setVideoBackground(id)
                        Toast.makeText(ctx, "$name set", Toast.LENGTH_SHORT).show()
                        onPicked()
                    }
                }
            }
            row.addView(cell)
        }
        return HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(row)
        }
    }
}
