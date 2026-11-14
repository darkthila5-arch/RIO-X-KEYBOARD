package com.rio.board

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** Live animated preview on a checkerboard. */
class StickerPreview(ctx: Context, private val spec: StickerSpec) : View(ctx) {
    var animated = true
        set(v) {
            field = v
            invalidate()
        }

    private val r = StickerRenderer()
    private val p = Paint()
    private val d = resources.displayMetrics.density

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height)
        if (s <= 0) return
        val cell = 12f * d
        var y = 0f
        var row = 0
        while (y < s) {
            var x = 0f
            var col = 0
            while (x < s) {
                p.color = if ((row + col) % 2 == 0) 0xFFEDEDF2.toInt() else 0xFFD3D3DC.toInt()
                c.drawRect(x, y, minOf(x + cell, s.toFloat()), minOf(y + cell, s.toFloat()), p)
                x += cell
                col++
            }
            y += cell
            row++
        }
        val t = if (animated) (SystemClock.uptimeMillis() % 1000L) / 1000f else 0f
        r.draw(c, s, spec, t)
        if (animated) postInvalidateOnAnimation()
    }
}

/**
 * Full-screen studio: caption + emoji + style + motion -> sticker (PNG) or animated GIF.
 * Results go to the keyboard's sticker library (tap them in the keyboard to send),
 * and can also be shared or saved to the gallery.
 */
class MakerActivity : Activity() {

    private val BG = 0xFF14141A.toInt()
    private val CHIP = 0xFF2A2A35.toInt()
    private val ACCENT = 0xFF7C4DFF.toInt()

    private val spec = StickerSpec()
    private var gifMode = true
    private var busy = false
    private lateinit var preview: StickerPreview
    private lateinit var emojiField: EditText
    private val ui = Handler(Looper.getMainLooper())
    private val d by lazy { resources.displayMetrics.density }

    private fun dp(v: Int) = (v * d).toInt()

    private val emojis = listOf(
        "😀", "😂", "🤣", "😍", "🥰", "😘", "😎", "🤩", "🥳", "😜", "🤪", "😭", "😡", "🥺", "😱", "🤔",
        "🙄", "😴", "🤗", "🤭", "😇", "😈", "👻", "💀", "👽", "🤖", "💩", "🙈", "🙉", "🙊", "❤️", "🧡",
        "💛", "💚", "💙", "💜", "🖤", "💖", "💕", "💔", "🔥", "✨", "⭐", "🌟", "💥", "💫", "🎉", "🎊",
        "🎈", "🎁", "🏆", "👑", "💎", "🌹", "🌸", "🌈", "☀️", "🌙", "⚡", "❄️", "💧", "🍕", "🍔", "🍟",
        "🍩", "🍦", "🍺", "☕", "⚽", "🎮", "🎵", "🎤", "🚀", "👍", "👎", "👏", "🙌", "🙏", "💪", "👋",
        "✌️", "🤞", "👀", "🐶", "🐱", "🐼", "🦄", "🐸", "🐵"
    )

    private val bgColors = intArrayOf(
        0xFFFFC107.toInt(), 0xFFFF5252.toInt(), 0xFFFF4081.toInt(), 0xFF7C4DFF.toInt(), 0xFF448AFF.toInt(),
        0xFF18FFFF.toInt(), 0xFF69F0AE.toInt(), 0xFFB2FF59.toInt(), 0xFFFFFFFF.toInt(), 0xFF212121.toInt()
    )
    private val textColors = intArrayOf(
        Color.WHITE, Color.BLACK, 0xFFFFEB3B.toInt(), 0xFFFF5252.toInt(),
        0xFFFF80AB.toInt(), 0xFF40C4FF.toInt(), 0xFF69F0AE.toInt()
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isVerticalScrollBarEnabled = false
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(24))
        }
        scroll.addView(col)

        // ---- title
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MakerActivity).apply {
                text = "←"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setPadding(dp(4), dp(4), dp(14), dp(4))
                setOnClickListener { finish() }
            })
            addView(TextView(this@MakerActivity).apply {
                text = "Emoji · Sticker · GIF maker"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                typeface = Typeface.DEFAULT_BOLD
            })
        })

        // ---- preview
        preview = StickerPreview(this, spec)
        col.addView(preview, LinearLayout.LayoutParams(dp(260), dp(260)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, dp(12), 0, dp(10))
        })

        // ---- caption
        col.addView(label("Caption (optional)"))
        col.addView(editText("Type something…", 40, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) {
            spec.text = it
            refresh()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))

        // ---- emoji
        col.addView(label("Emoji  (tap one, or type / paste your own – even several)"))
        val emojiRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        emojiField = editText("", 16, InputType.TYPE_CLASS_TEXT) {
            spec.emoji = it.trim()
            refresh()
        }
        emojiField.setText(spec.emoji)
        emojiField.gravity = Gravity.CENTER
        emojiField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        emojiRow.addView(emojiField, LinearLayout.LayoutParams(dp(84), dp(50)).apply { setMargins(0, 0, dp(8), 0) })
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        emojis.forEach { e ->
            strip.addView(TextView(this).apply {
                text = e
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                setOnClickListener { emojiField.setText(e) }
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(50))
            })
        }
        emojiRow.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(strip)
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        col.addView(emojiRow)

        // ---- style
        col.addView(label("Background"))
        col.addView(chips(StickerRenderer.bgs, spec.bgStyle) { spec.bgStyle = it; refresh() })
        col.addView(swatches(bgColors, 0) { spec.bgColor = it; refresh() })

        col.addView(label("Text colour"))
        col.addView(swatches(textColors, 0) { spec.textColor = it; refresh() })

        // ---- animation
        col.addView(label("Emoji motion"))
        col.addView(chips(StickerRenderer.motions, spec.motion) { spec.motion = it; refresh() })
        col.addView(label("Text effect"))
        col.addView(chips(StickerRenderer.textFxs, spec.textFx) { spec.textFx = it; refresh() })
        col.addView(label("Floating particles"))
        col.addView(chips(StickerRenderer.overlays, spec.overlay) { spec.overlay = it; refresh() })

        // ---- format
        col.addView(label("Make"))
        col.addView(chips(listOf("Sticker (PNG)", "Animated GIF"), 1, perRow = 2) {
            gifMode = it == 1
            preview.animated = gifMode
        })

        // ---- actions
        col.addView(button("Save to keyboard", ACCENT) { make { f ->
            toast("Saved! Open the sticker tool on the keyboard to send it.")
        } }, btnLp(dp(10)))
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(button("Share…", CHIP) { make { f -> share(f) } },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, dp(8), dp(4), 0) })
        row2.addView(button("Save to gallery", CHIP) {
            make { f ->
                Thread {
                    val ok = StickerStore.saveToGallery(this, f)
                    ui.post { toast(if (ok) "Saved to Pictures/RIO Stickers" else "Could not save to gallery") }
                }.start()
            }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(4), dp(8), 0, 0) })
        col.addView(row2)

        setContentView(scroll)
        refresh()
    }

    private fun refresh() {
        if (::preview.isInitialized) preview.invalidate()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    /** Builds the file on a background thread (GIF encoding takes a moment), then calls [done] on the UI thread. */
    private fun make(done: (File) -> Unit) {
        if (busy) return
        if (spec.emoji.isEmpty() && spec.text.isBlank()) {
            toast("Add an emoji or a caption first")
            return
        }
        busy = true
        toast(if (gifMode) "Making GIF…" else "Making sticker…")
        val snap = spec.copy()
        val gif = gifMode
        Thread {
            var out: File? = null
            try {
                out = StickerStore.make(this, snap, gif)
            } catch (e: Exception) {
            } catch (e: OutOfMemoryError) {
            }
            ui.post {
                busy = false
                val f = out
                if (f != null) done(f) else toast("Something went wrong, try again")
            }
        }.start()
    }

    private fun share(f: File) {
        try {
            val uri = RioFileProvider.uriFor(this, f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = StickerStore.mimeOf(f)
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share sticker"))
        } catch (e: Exception) {
            toast("Could not open the share sheet")
        }
    }

    // ------------------------------------------------------------------ small UI helpers
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(color)
    }

    private fun label(s: String) = TextView(this).apply {
        text = s
        setTextColor(0xFFB8B8C8.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        setPadding(dp(2), dp(14), 0, dp(5))
    }

    private fun editText(hint: String, max: Int, type: Int, onChange: (String) -> Unit) = EditText(this).apply {
        this.hint = hint
        setHintTextColor(0xFF77778A.toInt())
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        inputType = type
        isSingleLine = true
        filters = arrayOf(InputFilter.LengthFilter(max))
        background = rounded(CHIP, 12)
        setPadding(dp(12), 0, dp(12), 0)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = onChange(s?.toString() ?: "")
        })
    }

    private fun button(text: String, color: Int, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(color, 14)
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun btnLp(top: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply {
        setMargins(0, dp(18) + top - dp(10), 0, 0)
    }

    private fun chips(options: List<String>, initial: Int, perRow: Int = 4, onSelect: (Int) -> Unit): LinearLayout {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val all = ArrayList<TextView>()
        fun restyle(sel: Int) {
            all.forEachIndexed { i, tv ->
                tv.background = rounded(if (i == sel) ACCENT else CHIP, 12)
            }
        }
        options.chunked(perRow).forEachIndexed { r, rowOpts ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowOpts.forEachIndexed { j, label ->
                val idx = r * perRow + j
                val tv = TextView(this).apply {
                    text = label
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    maxLines = 1
                    isClickable = true
                    layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
                    setOnClickListener {
                        restyle(idx)
                        onSelect(idx)
                    }
                }
                all.add(tv)
                row.addView(tv)
            }
            repeat(perRow - rowOpts.size) {
                row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
            }
            wrap.addView(row)
        }
        restyle(initial)
        return wrap
    }

    private fun swatches(colors: IntArray, initial: Int, onPick: (Int) -> Unit): HorizontalScrollView {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val dots = ArrayList<View>()
        fun restyle(sel: Int) {
            dots.forEachIndexed { i, v ->
                v.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colors[i])
                    setStroke(dp(if (i == sel) 3 else 1), if (i == sel) Color.WHITE else 0xFF55556A.toInt())
                }
            }
        }
        colors.forEachIndexed { i, c ->
            val v = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
                setOnClickListener {
                    restyle(i)
                    onPick(c)
                }
            }
            dots.add(v)
            row.addView(v)
        }
        restyle(initial)
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }
}
