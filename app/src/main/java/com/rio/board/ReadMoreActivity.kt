package com.rio.board

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * WhatsApp "Read more" message maker.
 *   1) type the part people see first
 *   2) type the part hidden behind "Read more"
 *   3) Put in chat (or Put in chat & send)
 * The two parts are joined with a long run of invisible characters, which is what makes WhatsApp show "Read more".
 */
class ReadMoreActivity : Activity() {

    companion object {
        const val PAD = 4000          // invisible characters (U+200B) between the two parts
    }

    private val BG = 0xFF14141A.toInt()
    private val CHIP = 0xFF2A2A35.toInt()
    private val ACCENT = 0xFF7C4DFF.toInt()
    private lateinit var visibleField: EditText
    private lateinit var hiddenField: EditText
    private val d by lazy { resources.displayMetrics.density }

    private fun dp(v: Int) = (v * d).toInt()

    private fun box(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        val whole = intent.getBooleanExtra("whole", false)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isVerticalScrollBarEnabled = false
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(28))
        }
        scroll.addView(col)

        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@ReadMoreActivity).apply {
                text = "\u2190"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setPadding(dp(4), dp(4), dp(14), dp(4))
                setOnClickListener { finish() }
            })
            addView(TextView(this@ReadMoreActivity).apply {
                text = "Read more message"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = Typeface.DEFAULT_BOLD
            })
        })

        fun label(t: String) = TextView(this).apply {
            text = t
            setTextColor(0xFF9AA3C0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(16), 0, dp(6))
        }

        fun field(hint: String, text: String, lines: Int) = EditText(this).apply {
            this.hint = hint
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setText(text)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = lines
            gravity = Gravity.TOP
            background = box(CHIP, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        col.addView(label("1.  The part people see first"))
        visibleField = field("Type the first part here", intent.getStringExtra("visible") ?: "", 2)
        col.addView(visibleField)

        col.addView(label("2.  The part hidden behind \"Read more\""))
        hiddenField = field("Type the rest of the message here", intent.getStringExtra("hidden") ?: "", 6)
        col.addView(hiddenField)

        fun button(t: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
            text = t
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            background = box(if (primary) ACCENT else CHIP, 14)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            setOnClickListener { onClick() }
        }

        col.addView(button("3.  Put in chat", false) { done(whole, false) })
        col.addView(button("Put in chat & send", true) { done(whole, true) })
        col.addView(TextView(this).apply {
            text = "People will see the first part and a \"Read more\" link. Tap the Read more tool on the keyboard again to turn a message back into normal text."
            setTextColor(0xFF9AA3C0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(4), dp(14), dp(4), 0)
        })
        setContentView(scroll)

        // start typing in the first empty box
        (if (visibleField.text.isNullOrBlank()) visibleField else hiddenField).requestFocus()
    }

    private fun done(whole: Boolean, send: Boolean) {
        val vis = visibleField.text.toString().trim().replace(Regex("\\s*\\n\\s*"), " ")
        val hid = hiddenField.text.toString().trim('\n', ' ')
        if (vis.isEmpty()) {
            Toast.makeText(this, "Type the first part (the part people see)", Toast.LENGTH_SHORT).show()
            return
        }
        if (hid.isEmpty()) {
            Toast.makeText(this, "Type the hidden part (after Read more)", Toast.LENGTH_SHORT).show()
            return
        }
        val out = vis + "\u200B".repeat(PAD) + "\n" + hid
        RioBoardService.instance?.readMoreInsert(out, whole, send)
        finish()
    }
}
