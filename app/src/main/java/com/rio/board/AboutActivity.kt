package com.rio.board

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// ---- Fill these in and the "Contact us" card shows up on the About page. Leave a line empty to hide it. ----
private const val CONTACT_WHATSAPP = ""      // digits with the country code, e.g. "94771234567"
private const val CONTACT_EMAIL = ""         // e.g. "hello@example.com"
private const val LINK_FACEBOOK = ""         // full link, e.g. "https://facebook.com/yourpage"
private const val LINK_TELEGRAM = ""         // full link, e.g. "https://t.me/yourchannel"
private const val LINK_WEBSITE = ""          // full link, e.g. "https://yourname.github.io/RIO-X-KEYBOARD"

/** About RIO BOARD: what it is, what it can do, what it does with your data, and how to reach us. */
class AboutActivity : Activity() {

    private val BG = Color.parseColor("#0F1014")
    private val CARD = Color.parseColor("#1A1C23")
    private val SUB = Color.parseColor("#9AA3C0")
    private val PINK = Color.parseColor("#FF4D8D")
    private val PURPLE = Color.parseColor("#B84DFF")
    private val d by lazy { resources.displayMetrics.density }

    private fun dp(v: Int) = (v * d).toInt()

    private fun tv(s: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun lp(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, dp(top), 0, dp(bottom)) }

    private fun card(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(CARD)
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), 0x1AFFFFFF)
        }
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = lp(bottom = 12)
        addView(View(this@AboutActivity).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(PINK, PURPLE))
                .apply { cornerRadius = dp(2).toFloat() }
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(4)).apply { bottomMargin = dp(10) }
        })
        views.forEach { addView(it) }
    }

    private fun title(s: String) = tv(s, 18f, Color.WHITE, true).apply { setPadding(0, 0, 0, dp(6)) }

    private fun body(s: String) = tv(s, 14f, SUB).apply {
        setPadding(0, 0, 0, dp(6))
        setLineSpacing(0f, 1.2f)
    }

    private fun point(emoji: String, text: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = lp(top = 6)
        addView(tv(emoji, 16f, Color.WHITE).apply { setPadding(0, 0, dp(10), 0) })
        addView(tv(text, 14f, 0xFFD5DAEA.toInt()).apply {
            setLineSpacing(0f, 1.15f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
    }

    private fun linkButton(label: String, onClick: () -> Unit) = tv(label, 15f, Color.WHITE, true).apply {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#2F3340"))
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), 0x22FFFFFF)
        }
        setPadding(dp(16), dp(12), dp(16), dp(12))
        layoutParams = lp(top = 8)
        setOnClickListener { onClick() }
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open it", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        @Suppress("DEPRECATION")
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(28))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isVerticalScrollBarEnabled = false
            addView(col)
        }

        // top row: back arrow
        col.addView(tv("\u2190", 26f, Color.WHITE).apply {
            setPadding(dp(4), dp(4), dp(14), dp(10))
            setOnClickListener { finish() }
        })

        // hero
        val hero = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF2B1A6B.toInt(), 0xFF6A2BD9.toInt(), 0xFFD63A8A.toInt())
            ).apply { cornerRadius = dp(28).toFloat() }
            layoutParams = lp(bottom = 16)
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(26), dp(18), dp(24))
        }
        inner.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_round)
            layoutParams = LinearLayout.LayoutParams(dp(92), dp(92))
        })
        inner.addView(tv("RIO BOARD", 28f, Color.WHITE, true).apply {
            letterSpacing = 0.06f
            setPadding(0, dp(12), 0, 0)
        })
        inner.addView(tv("සිංහල  \u00B7  Singlish  \u00B7  English", 14f, 0xE6FFFFFF.toInt()))
        if (ver.isNotEmpty()) {
            inner.addView(tv("Version $ver", 12f, 0xCCFFFFFF.toInt()).apply {
                background = GradientDrawable().apply { setColor(0x40000000); cornerRadius = dp(14).toFloat() }
                setPadding(dp(12), dp(5), dp(12), dp(5))
                layoutParams = lp(top = 12).apply { width = ViewGroup.LayoutParams.WRAP_CONTENT }
            })
        }
        hero.addView(inner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(hero)

        col.addView(
            card(
                title("What is RIO BOARD?"),
                body(
                    "RIO BOARD is a keyboard for typing Sinhala, Singlish and English in one place, without switching apps. " +
                        "Type English letters and get Sinhala, swipe across the keys, and use handy tools right from the keyboard."
                )
            )
        )

        col.addView(
            card(
                title("What you get"),
                point("\u2328\uFE0F", "Sinhala, Singlish, English and Tamil layouts, swipe typing and word suggestions"),
                point("\uD83E\uDDF0", "A toolbar you choose yourself: read more messages, QR codes, voice changer, Google search and translate, word count and more"),
                point("\uD83C\uDFA8", "Themes, photo and video backgrounds, falling effects and key styles"),
                point("\uD83C\uDFF7\uFE0F", "Sticker maker and a WhatsApp status saver"),
                point("\uD83D\uDC96", "Word animations: type love and watch the hearts")
            )
        )

        col.addView(
            card(
                title("Your privacy"),
                point("\uD83D\uDCF1", "What you type, the words the keyboard learns and your shortcuts stay on your phone. Password fields are never learned."),
                point("\uD83C\uDF10", "The keyboard reads the RIO config page now and then for news, themes and update notices. Nothing you type is sent for that."),
                point("\uD83D\uDD0E", "Only when you tap them: Google search opens google.com inside the keyboard, Google Translate sends the text you chose to Google to translate it, and the camera QR scanner is Google's own scanner."),
                point("\uD83C\uDFA4", "Voice typing uses your phone's speech service, which may use the internet. The voice changer and reading a QR from a picture work on the phone. The microphone is asked for only when you record.")
            )
        )

        val contact = ArrayList<View>()
        if (CONTACT_WHATSAPP.isNotEmpty()) contact.add(linkButton("\uD83D\uDCAC  WhatsApp") { open("https://wa.me/$CONTACT_WHATSAPP") })
        if (CONTACT_EMAIL.isNotEmpty()) contact.add(linkButton("\u2709\uFE0F  Email") { open("mailto:$CONTACT_EMAIL") })
        if (LINK_FACEBOOK.isNotEmpty()) contact.add(linkButton("Facebook") { open(LINK_FACEBOOK) })
        if (LINK_TELEGRAM.isNotEmpty()) contact.add(linkButton("Telegram") { open(LINK_TELEGRAM) })
        if (LINK_WEBSITE.isNotEmpty()) contact.add(linkButton("\uD83C\uDF10  Website") { open(LINK_WEBSITE) })
        if (contact.isNotEmpty()) {
            col.addView(card(title("Contact us"), body("Questions, ideas or a problem? Tell us."), *contact.toTypedArray()))
        }

        col.addView(tv("Thank you for using RIO BOARD \u2764", 13f, SUB).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        })
        setContentView(scroll)
    }
}
