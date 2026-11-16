package com.rio.board

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var snipList: LinearLayout
    private lateinit var styleList: LinearLayout
    private lateinit var toolPicker: LinearLayout
    private lateinit var setupBox: LinearLayout
    private lateinit var updateBox: LinearLayout
    private var density = 1f
    private var statusInfo: TextView? = null
    private var vidHolder: LinearLayout? = null
    private var effectPreview: EffectView? = null
    private val effectNames = listOf(
        "Snow \u2744", "Water drops \uD83D\uDCA7", "Leaves \uD83C\uDF43", "Flowers \uD83C\uDF38",
        "Smoke \uD83D\uDCA8", "Fire \uD83D\uDD25", "Hearts \u2764", "Rain \uD83C\uDF27",
        "Fireflies \u2728", "Bubbles \u25CB", "Stars \u2B50"
    )
    private val colourNames = listOf("Auto", "White", "Blue", "Pink", "Yellow", "Green", "Rainbow")
    private val colourHex = listOf("", "#FFFFFF", "#7CC8FF", "#FF8AC8", "#FFD54A", "#7CFFB2", "")
    private val pages = ArrayList<View>()
    private val tabs = ArrayList<TextView>()

    private val BG = Color.parseColor("#0F1014")
    private val NAV = Color.parseColor("#16171D")
    private val CARD = Color.parseColor("#1A1C23")
    private val FIELD = Color.parseColor("#262932")
    private val SUB = Color.parseColor("#9AA3C0")
    private val PINK = Color.parseColor("#FF4D8D")
    private val PURPLE = Color.parseColor("#B84DFF")
    private val GREEN = Color.parseColor("#2E9E63")

    private fun dp(v: Int) = (v * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Rio)          // leave the logo splash theme, show the normal screen
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("rio_board", MODE_PRIVATE)
        density = resources.displayMetrics.density
        RemoteConfig.cached(prefs)           // admin videos / hidden languages must be known before the pages are built

        pages.add(homePage())
        pages.add(typePage())
        pages.add(lookPage())
        pages.add(morePage())

        val frame = FrameLayout(this)
        pages.forEach { frame.addView(it, FrameLayout.LayoutParams(MATCH, MATCH)) }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(NAV)
            setPadding(0, dp(6), 0, dp(6))
        }
        listOf("\uD83C\uDFE0" to "Home", "\u2328\uFE0F" to "Typing", "\uD83C\uDFA8" to "Look", "\u2699\uFE0F" to "More")
            .forEachIndexed { i, pair ->
                val label = SpannableString(pair.first + "\n" + pair.second)
                label.setSpan(RelativeSizeSpan(1.7f), 0, pair.first.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val t = TextView(this).apply {
                    text = label
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(0, dp(6), 0, dp(6))
                    layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                    setOnClickListener { selectTab(i) }
                }
                tabs.add(t)
                bar.addView(t)
            }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        root.addView(frame, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
        setContentView(root)

        selectTab(0)
        refreshSnips()
        refreshStyles()

        // soft fade-in after the logo splash
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(350).start()
    }

    // ------------------------------------------------------------------ tabs

    private fun selectTab(i: Int) {
        pages.forEachIndexed { k, v -> v.visibility = if (k == i) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { k, t -> t.setTextColor(if (k == i) PINK else SUB) }
        pages[i].alpha = 0f
        pages[i].animate().alpha(1f).setDuration(220).start()
    }

    private fun pageOf(vararg views: View): ScrollView {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(22), dp(16), dp(24))
        }
        views.forEach { col.addView(it) }
        return ScrollView(this).apply {
            isFillViewport = true
            addView(col)
        }
    }

    // ------------------------------------------------------------------ HOME

    private fun homePage(): View {
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = lp(bottom = 18)
        }
        head.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_round)
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
        })
        val names = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        names.addView(tv("RIO BOARD", 26f, Color.WHITE, true))
        names.addView(tv("සිංහල + English Keyboard", 14f, SUB))
        head.addView(names)

        updateBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setupBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(CARD, 18)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = lp(bottom = 12)
        }
        refreshSetup()
        refreshUpdate()

        val test = card(
            title("\u270D\uFE0F  Try it here"),
            note("Tap the box and type to test your keyboard."),
            field("Type here to test...")
        )
        val tip = card(
            title("\uD83D\uDCA1  Tip"),
            note(
                "Turn on Singlish with the  A→අ  button in the keyboard toolbar, then type  mama  and you get  මම.\n" +
                    "More help is in the Typing tab."
            )
        )
        return pageOf(head, updateBox, setupBox, test, tip)
    }

    private fun isEnabled(): Boolean = try {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .enabledInputMethodList.any { it.packageName == packageName }
    } catch (e: Exception) {
        false
    }

    private fun isSelected(): Boolean = try {
        (Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "")
            .startsWith("$packageName/")
    } catch (e: Exception) {
        false
    }

    private fun refreshSetup() {
        setupBox.removeAllViews()
        val en = isEnabled()
        val sel = isSelected()
        if (en && sel) {
            setupBox.addView(title("\u2705  RIO BOARD is ready"))
            setupBox.addView(note("It is your active keyboard. Open any app and start typing."))
            return
        }
        setupBox.addView(title("\uD83D\uDE80  Get started"))
        setupBox.addView(note("Two quick steps and you are done."))
        setupBox.addView(stepRow(1, "Enable RIO BOARD", "Switch it on in the keyboard list.", en, "Enable") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        setupBox.addView(stepRow(2, "Select RIO BOARD", "Choose it as your current keyboard.", sel, "Select") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
    }

    @Suppress("DEPRECATION")
    private fun installedCode(): Int = try {
        packageManager.getPackageInfo(packageName, 0).versionCode
    } catch (e: Exception) {
        Int.MAX_VALUE
    }

    private fun refreshUpdate() {
        if (!::updateBox.isInitialized) return
        updateBox.removeAllViews()
        val cfg = RemoteConfig.cached(prefs)
        cfg.notice?.let { n ->
            updateBox.addView(card(title("\u26A0\uFE0F  Notice"), note(n.message)).apply {
                background = bg(Color.parseColor("#33281A"), 18, Color.parseColor("#FFB020"))
            })
        }
        val required = cfg.minVersion > installedCode()
        val up = cfg.update ?: return
        if (up.code <= installedCode() || !up.url.startsWith("https://")) return
        val head = if (up.name.isNotEmpty()) "New version ${up.name}" else "New version"
        val c = card(
            title(if (required) "\u26A0\uFE0F  Update required ($head)" else "\uD83C\uDF89  $head is ready"),
            note(if (up.message.isNotEmpty()) up.message else "Tap to download and install."),
            btn("Update now") {
                startActivity(Intent(this@MainActivity, UpdateActivity::class.java).putExtra("url", up.url))
            }
        )
        c.background = if (required) bg(Color.parseColor("#33181A"), 18, Color.parseColor("#FF5C5C"))
        else bg(Color.parseColor("#2A1A33"), 18, PURPLE)
        updateBox.addView(c)
    }

    private fun stepRow(n: Int, t: String, d: String, done: Boolean, label: String, action: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = lp(top = 10)
        }
        row.addView(TextView(this).apply {
            text = if (done) "\u2713" else n.toString()
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (done) GREEN else FIELD)
            }
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
        })
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        col.addView(tv(t, 16f, Color.WHITE, true))
        col.addView(tv(d, 13f, SUB))
        row.addView(col)
        if (!done) row.addView(smallBtn(label, true, action))
        return row
    }

    // ------------------------------------------------------------------ TYPING

    private fun typePage(): View {
        // ----- Singlish -----
        val ex = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf(
            "mama" to "මම", "ammaa" to "අම්මා", "oyaa" to "ඔයා",
            "kohomadha" to "කොහොමද", "sthuthiyi" to "ස්තුතියි", "shRii" to "ශ්‍රී"
        ).forEach { ex.addView(exampleRow(it.first, it.second)) }

        val chart = note(
            "k ක · kh ඛ · g ග · c/ch ච · j ජ · t ට · th ත · d ඩ · dh ද · n න · N ණ · p ප · b බ · m ම · y ය · r ර · l ල · w/v ව · s ස · sh ශ · h හ\n" +
                "Vowels: a aa ae aae i ii u uu e ee ai o oo au\n" +
                "Capital T D N L S = ඨ ඪ ණ ළ ෂ · R = ්‍ර · Y = ්‍ය · M = ං · H = ඃ"
        ).apply { visibility = View.GONE }
        lateinit var toggle: Button
        toggle = btn("Letter chart  \u25BE", false) {
            val open = chart.visibility != View.VISIBLE
            chart.visibility = if (open) View.VISIBLE else View.GONE
            toggle.text = if (open) "Hide letter chart  \u25B4" else "Letter chart  \u25BE"
        }
        val singlish = card(
            title("\uD83D\uDD24  Singlish typing"),
            note(
                "Turn it on with the  A→අ  button in the keyboard toolbar (or long-press the සිං key). " +
                    "Then type English letters and they turn into Sinhala."
            ),
            ex, toggle, chart
        )

        // ----- shortcuts -----
        snipList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val keyEt = field("Shortcut (letters only, e.g. gm)")
        val valEt = field("Full text (e.g. Good morning)", false)
        val shortcuts = card(
            title("\u26A1  Shortcuts"),
            note("Type the shortcut and press Space in any app, and it turns into the full text."),
            snipList, keyEt, valEt,
            btn("Add shortcut") {
                val k = keyEt.text.toString().trim().lowercase()
                val v = valEt.text.toString().trim()
                if (k.isEmpty() || k.length > 12 || !k.all { it.isLetter() } || v.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "Shortcut: letters only (max 12), and the full text can't be empty",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val o = Snips.load(prefs)
                    o.put(k, v)
                    Snips.save(prefs, o)
                    keyEt.setText("")
                    valEt.setText("")
                    refreshSnips()
                }
            }
        )

        // ----- RIO styles -----
        styleList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val styleEt = field("Your style, e.g. 《💗🍓🎧─⬣")
        val styles = card(
            title("\u2728  RIO STYLES"),
            note(
                "A style is put in front of every word you type. Pick one with the Aa button in the keyboard toolbar. " +
                    "Write or paste your own decoration below and it becomes the next RIO STYLE."
            ),
            styleList, styleEt,
            btn("Add RIO STYLE") {
                val v = styleEt.text.toString().trim()
                if (v.isEmpty() || v.length > RioStyles.MAX_LEN) {
                    Toast.makeText(
                        this@MainActivity,
                        "Style can't be empty or longer than ${RioStyles.MAX_LEN} characters",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val list = RioStyles.custom(prefs)
                    if (!list.contains(v) && !RioStyles.builtIn.contains(v)) list.add(v)
                    RioStyles.saveCustom(prefs, list)
                    styleEt.setText("")
                    refreshStyles()
                }
            }
        )
        return pageOf(pageHeader("Typing", "Languages, toolbar, Singlish, animations, shortcuts and styles"), languagesCard(), toolbarCard(), singlish, singlishGuideCard(), wordFxCard(), shortcuts, styles)
    }

    // ------------------------------------------------------------------ word animations

    private fun wordFxCard(): View {
        val sw = Switch(this).apply {
            text = "Play an animation when I type a word"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            isChecked = prefs.getBoolean("wordfx", true)
            layoutParams = lp(top = 6)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("wordfx", on).apply() }
        }
        val mine = field("birthday = \uD83C\uDF82\nbaba = \uD83D\uDC76", false).apply {
            minLines = 3
            gravity = Gravity.TOP
            setText(prefs.getString("wfx_custom", ""))
        }
        return card(
            title("\uD83D\uDC96  Word animations"),
            note(
                "Type a word and press space: love gives a big heart with small hearts floating up, haha gives laughing faces, " +
                    "birthday a cake and balloons. Also: kiss, fire, thanks, sorry, good night, good morning, wow, congrats, rio and the Sinhala ආදරේ. " +
                    "For the animation to fly over the whole screen (not only the keyboard) set the snow to full screen in the keyboard settings."
            ),
            sw,
            tv("Your own words (one per line: word = emoji)", 13f, SUB, true).apply { setPadding(dp(4), dp(14), 0, dp(2)) },
            mine,
            btn("Save my words", false) {
                prefs.edit().putString("wfx_custom", mine.text.toString().trim()).apply()
                Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // ------------------------------------------------------------------ Singlish guide

    private fun guideChips(pairs: List<Pair<String, String>>): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pairs.chunked(4).forEach { part ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = lp(top = 6)
            }
            part.forEach { (keys, sinhala) ->
                row.addView(TextView(this).apply {
                    text = "$keys  $sinhala"
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    background = bg(FIELD, 10)
                    setPadding(dp(2), dp(9), dp(2), dp(9))
                    layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(3), 0, dp(3), 0) }
                })
            }
            repeat(4 - part.size) { row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }) }
            col.addView(row)
        }
        return col
    }

    private fun guideLabel(s: String): TextView = tv(s, 13f, SUB, true).apply { setPadding(dp(4), dp(14), 0, dp(2)) }

    private fun singlishGuideCard(): View {
        // try it: type Singlish with any keyboard and see the Sinhala straight away
        val result = tv("සිංහල මෙතන පෙනේ", 20f, Color.WHITE, true).apply {
            background = bg(FIELD, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = lp(top = 8)
            setTextIsSelectable(true)
        }
        val input = field("Try it: mama gedhara yanawaa", false).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS      // no auto capital letters: they matter in Singlish
            minLines = 2
            gravity = Gravity.TOP
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val t = s?.toString().orEmpty()
                    result.text = if (t.isEmpty()) "සිංහල මෙතන පෙනේ" else Singlish.fromKeys(t)
                }
            })
        }

        val vowels = listOf(
            "a" to "අ", "aa" to "ආ", "ae" to "ඇ", "aae" to "ඈ", "i" to "ඉ", "ii" to "ඊ", "u" to "උ", "uu" to "ඌ",
            "e" to "එ", "ee" to "ඒ", "o" to "ඔ", "oo" to "ඕ", "ai" to "ඓ", "au" to "ඖ"
        )
        val cons = listOf(
            "k" to "ක", "kh" to "ඛ", "g" to "ග", "gh" to "ඝ", "ng" to "ඟ", "nG" to "ඞ", "c" to "ච", "Ch" to "ඡ",
            "j" to "ජ", "jh" to "ඣ", "ny" to "ඤ", "nj" to "ඦ", "Gn" to "ඥ", "t" to "ට", "T" to "ඨ", "d" to "ඩ",
            "D" to "ඪ", "N" to "ණ", "nD" to "ඬ", "th" to "ත", "Th" to "ථ", "dh" to "ද", "Dh" to "ධ", "n" to "න",
            "nd" to "ඳ", "p" to "ප", "ph" to "ඵ", "b" to "බ", "bh" to "භ", "m" to "ම", "mb" to "ඹ", "y" to "ය",
            "r" to "ර", "l" to "ල", "L" to "ළ", "w" to "ව", "s" to "ස", "sh" to "ශ", "S" to "ෂ", "h" to "හ", "f" to "ෆ"
        )
        val special = listOf(
            "q" to "ං", "H" to "ඃ", "x" to "්‍ය", "z" to "්‍ර"
        )
        return card(
            title("\uD83D\uDCD6  Singlish guide"),
            note(
                "Type English letters and the keyboard writes Sinhala (turn Singlish on with the A→අ tool or the Typing setting). " +
                    "Small letters are the light sounds, CAPITAL letters or an h are the heavy ones: t = ට, T = ඨ, th = ත, d = ඩ, dh = ද. " +
                    "A letter alone gives the consonant with a, add aa for ආ: ka = ක, kaa = කා, ki = කි."
            ),
            input, result,
            guideLabel("Vowels"), guideChips(vowels),
            guideLabel("Consonants"), guideChips(cons),
            guideLabel("Special keys"), guideChips(special),
            note(
                "q = bindu (ං), x = yansaya (type after a letter: kx = ක්‍ය), z = rakaransaya (kz = ක්‍ර), H = visarga (ඃ). For bindu after a letter write the vowel first: kaq = කං. Capital R after a letter also gives rakaransaya (shR = ශ්‍ර). " +
                    "Hold a key on the keyboard to see more letters (hold n for ණ ඞ ඤ, hold g for ඝ ඟ ඥ)."
            ).apply { setPadding(dp(4), dp(10), 0, 0) },
            guideLabel("Examples"),
            note("mama gedhara yanawaa  →  මම ගෙදර යනවා\nshRii laqkaawa  →  ශ්‍රී ලංකාව\nkx  →  ක්‍ය      kz  →  ක්‍ර      kaq  →  කං")
        )
    }

    // ------------------------------------------------------------------ keyboard toolbar picker

    private fun toolbarCard(): View {
        toolPicker = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        refreshToolPicker()
        val all = Switch(this).apply {
            text = "Show the other tools at the end of the bar"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            isChecked = ToolCatalog.showAll(prefs)
            layoutParams = lp(top = 10)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("bar_all", on).apply() }
        }
        return card(
            title("\uD83E\uDDF0  Keyboard toolbar"),
            note(
                "Tap the tools you want on the top bar of the keyboard. The number shows the order. " +
                    "Tap again to remove. Swipe the bar sideways on the keyboard to see them all. Changes work the next time the keyboard opens."
            ),
            toolPicker, all,
            btn("Reset to default", false) {
                ToolCatalog.save(prefs, ToolCatalog.defaults)
                refreshToolPicker()
            }
        )
    }

    private fun refreshToolPicker() {
        val chosen = ToolCatalog.load(prefs)
        toolPicker.removeAllViews()
        ToolCatalog.groups.forEach { (name, items) ->
            toolPicker.addView(tv(name, 12f, SUB, true).apply { setPadding(dp(4), dp(10), 0, dp(2)) })
            items.chunked(3).forEach { part ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = lp(top = 6)
                }
                part.forEach { it0 ->
                    val pos = chosen.indexOf(it0.id)
                    val on = pos >= 0
                    row.addView(TextView(this).apply {
                        text = (if (on) "${pos + 1}  " else "") + it0.glyph + "\n" + it0.label
                        gravity = Gravity.CENTER
                        maxLines = 2
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        if (on) typeface = Typeface.DEFAULT_BOLD
                        setPadding(dp(4), dp(10), dp(4), dp(10))
                        background = if (on) buttonBg(true, 12) else bg(FIELD, 12)
                        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(3), 0, dp(3), 0) }
                        setOnClickListener {
                            val now = ToolCatalog.load(prefs)
                            if (now.contains(it0.id)) {
                                if (now.size <= 1) {
                                    Toast.makeText(this@MainActivity, "Keep at least one tool on the bar", Toast.LENGTH_SHORT).show()
                                    return@setOnClickListener
                                }
                                now.remove(it0.id)
                            } else if (now.size >= 30) {
                                Toast.makeText(this@MainActivity, "Maximum 30 tools", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            } else now.add(it0.id)
                            ToolCatalog.save(prefs, now)
                            refreshToolPicker()
                        }
                    })
                }
                repeat(3 - part.size) {
                    row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
                }
                toolPicker.addView(row)
            }
        }
    }

    // ------------------------------------------------------------------ LOOK

    private fun lookPage(): View {
        val bgCard = card(
            title("\uD83D\uDDBC\uFE0F  Keyboard background"),
            note("Pick a photo from your gallery to use behind the keys."),
            btn("Choose photo") {
                startActivity(Intent(this@MainActivity, PickImageActivity::class.java))
            },
            note("Or pick one of the built-in backgrounds:").apply { setPadding(0, dp(14), 0, dp(4)) },
            DefaultBackgrounds.strip(this),
            btn("Remove photo", false) {
                prefs.edit().putBoolean("bgon", false).remove("bgname").apply()
                try {
                    File(filesDir, "kb_bg.jpg").delete()
                } catch (e: Exception) {
                }
                RioBoardService.instance?.reloadBackground()
                Toast.makeText(this@MainActivity, "Background removed", Toast.LENGTH_SHORT).show()
            }
        )
        val themes = card(
            title("\uD83C\uDFA8  Themes"),
            note("Change the keyboard colours from the keyboard itself: open the keyboard and tap the theme button in the toolbar. New themes from the RIO team appear there automatically.")
        )
        return pageOf(pageHeader("Look", "Make the keyboard yours"), effectsCard(), bgCard, videoCard(), themes)
    }

    // ------------------------------------------------------------------ MORE

    private fun morePage(): View {
        val si = note("")
        statusInfo = si
        val wa = card(
            title("\uD83D\uDCAC  WhatsApp status saver"),
            note(
                "1. Tap a button below and press \"Use this folder\" (the .Statuses folder is shown first).\n" +
                    "2. Watch a status in WhatsApp.\n" +
                    "3. Open the status gallery here (or the keyboard's status icon), tap a status for the full size preview, then Save, Share or Delete.\n" +
                    "Please only save statuses people are happy for you to keep."
            ),
            btn("Allow WhatsApp statuses") { pickStatusFolder(false) },
            btn("Allow WhatsApp Business statuses", false) { pickStatusFolder(true) },
            btn("Open status gallery", false) {
                startActivity(Intent(this@MainActivity, StatusActivity::class.java))
            },
            si
        )
        refreshStatusInfo()

        val fonts = card(
            title("\uD83C\uDD70\uFE0F  Fonts & voice"),
            note(
                "Aa button = stylish letters (Bold, Script, Bubble ...).\n" +
                    "\uD83C\uDFA4 button = voice typing (Sinhala when Singlish or the Sinhala layout is on)."
            )
        )

        @Suppress("DEPRECATION")
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        val about = card(
            title("\u2139\uFE0F  About"),
            note("RIO BOARD  v$ver"),
            btn("Check for updates", false) {
                Toast.makeText(this@MainActivity, "Checking...", Toast.LENGTH_SHORT).show()
                RemoteConfig.refresh(prefs, true) { refreshUpdate() }
                Handler(Looper.getMainLooper()).postDelayed({
                    refreshUpdate()
                    val up = RemoteConfig.cached(prefs).update
                    val msg = if (up != null && up.code > installedCode())
                        "New version available - see the Home tab"
                    else
                        "You are up to date"
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                }, 2500)
            }
        )
        return pageOf(pageHeader("More", "Tools and info"), wa, fonts, about)
    }

    private fun refreshStatusInfo() {
        statusInfo?.text = if (prefs.getString(StatusSaver.PREF_TREE, null) != null)
            "Status folder allowed \u2713"
        else
            "No folder allowed yet"
    }

    private fun pickStatusFolder(business: Boolean) {
        if (Build.VERSION.SDK_INT in 23..28) {
            requestPermissions(arrayOf("android.permission.WRITE_EXTERNAL_STORAGE"), 22)
        }
        val pkg = if (business) "com.whatsapp.w4b" else "com.whatsapp"
        val app = if (business) "WhatsApp%20Business" else "WhatsApp"
        val path = if (Build.VERSION.SDK_INT >= 30) "Android%2Fmedia%2F$pkg%2F$app%2FMedia%2F.Statuses"
        else "$app%2FMedia%2F.Statuses"
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        i.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
        if (Build.VERSION.SDK_INT >= 26) {
            i.putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                Uri.parse("content://com.android.externalstorage.documents/document/primary%3A$path")
            )
        }
        try {
            startActivityForResult(i, 21)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open the folder picker", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 31 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            Toast.makeText(this, "Adding video...", Toast.LENGTH_SHORT).show()
            AnimatedBackgrounds.import(this, uri) { id, msg ->
                if (id != null) {
                    AnimatedBackgrounds.use(this, id)
                    Toast.makeText(this, "Video background set", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
                refreshVideoStrip()
            }
        }
        if (requestCode == 21 && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e2: Exception) {
                    }
                }
                prefs.edit().putString(StatusSaver.PREF_TREE, uri.toString()).apply()
                refreshStatusInfo()
                Toast.makeText(this, "Status folder allowed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ------------------------------------------------------------------ video backgrounds

    private fun videoCard(): View {
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        vidHolder = holder
        refreshVideoStrip()
        return card(
            title("\uD83C\uDFAC  Video backgrounds"),
            note("Tap a clip to play it behind the keys. Add your own short video (max 60 MB, it is muted and loops). Tap \u2715 on your own clip to remove it."),
            holder,
            btn("Add my video") {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "video/*"
                }
                try {
                    startActivityForResult(i, 31)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Could not open the video picker", Toast.LENGTH_LONG).show()
                }
            },
            btn("Stop video background", false) {
                val svc = RioBoardService.instance
                if (svc != null) svc.removeBackground() else prefs.edit().remove("vbg").apply()
                Toast.makeText(this@MainActivity, "Video background off", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun refreshVideoStrip() {
        val h = vidHolder ?: return
        h.removeAllViews()
        h.addView(AnimatedBackgrounds.strip(this, {}, { refreshVideoStrip() }))
    }

    // ------------------------------------------------------------------ effects

    private fun applyEffects() {
        RioBoardService.instance?.reloadEffects()
        updateEffectPreview()
    }

    private fun updateEffectPreview() {
        val p = effectPreview ?: return
        val eff = prefs.getInt("effect", 0).coerceIn(0, EffectView.Type.values().size - 1)
        val col = prefs.getInt("snowcol", 0).coerceIn(0, colourNames.size - 1)
        p.type = EffectView.Type.values()[eff]
        p.colors = when (col) {
            0 -> intArrayOf(Color.WHITE)
            colourNames.size - 1 -> intArrayOf(
                Color.parseColor("#FF5C5C"), Color.parseColor("#FFD54A"), Color.parseColor("#7CFFB2"),
                Color.parseColor("#7CC8FF"), Color.parseColor("#C58CFF")
            )
            else -> intArrayOf(Color.parseColor(colourHex[col]))
        }
        p.natural = col == 0 && eff != 0
        p.base = 40
        p.running = prefs.getInt("snow", 2) != 0
    }

    /** Rows of chips; the picked one is highlighted. */
    private fun chipGrid(labels: List<String>, perRow: Int, selected: () -> Int, onPick: (Int) -> Unit): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun build() {
            box.removeAllViews()
            labels.chunked(perRow).forEachIndexed { r, part ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = lp(top = 6)
                }
                part.forEachIndexed { c, t ->
                    val idx = r * perRow + c
                    val on = idx == selected()
                    row.addView(TextView(this).apply {
                        text = t
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        if (on) typeface = Typeface.DEFAULT_BOLD
                        setPadding(dp(4), dp(10), dp(4), dp(10))
                        background = if (on) buttonBg(true, 12) else bg(FIELD, 12)
                        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(3), 0, dp(3), 0) }
                        setOnClickListener {
                            onPick(idx)
                            build()
                        }
                    })
                }
                repeat(perRow - part.size) {
                    row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
                }
                box.addView(row)
            }
        }
        build()
        return box
    }

    private fun effectsCard(): View {
        val preview = EffectView(this)
        effectPreview = preview
        val pBox = FrameLayout(this).apply {
            background = bg(Color.parseColor("#0A0B10"), 14)
            clipToOutline = true
            layoutParams = lp(top = 4, bottom = 6).apply { height = dp(120) }
            addView(preview, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        updateEffectPreview()
        return card(
            title("\u2744\uFE0F  Keyboard effects"),
            note("Pick the falling effect shown over the keyboard. The preview below shows how it looks."),
            pBox,
            tv("Effect", 14f, Color.WHITE, true).apply { setPadding(0, dp(6), 0, 0) },
            chipGrid(effectNames, 3, { prefs.getInt("effect", 0) }) {
                prefs.edit().putInt("effect", it).apply()
                applyEffects()
            },
            tv("Where", 14f, Color.WHITE, true).apply { setPadding(0, dp(12), 0, 0) },
            chipGrid(listOf("Off", "Keyboard", "Full screen"), 3, { prefs.getInt("snow", 2) }) {
                val e = prefs.edit().putInt("snow", it)
                if (it != 0) e.putInt("lastsnow", it)
                e.apply()
                applyEffects()
            },
            tv("Colour", 14f, Color.WHITE, true).apply { setPadding(0, dp(12), 0, 0) },
            chipGrid(colourNames, 4, { prefs.getInt("snowcol", 0) }) {
                prefs.edit().putInt("snowcol", it).apply()
                applyEffects()
            },
            note("Auto = the natural look of the effect.").apply { setPadding(0, dp(8), 0, 0) }
        )
    }

    // ------------------------------------------------------------------ languages

    private fun languagesCard(): View {
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Languages.entries.forEach { e ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(8), dp(10), dp(8))
                background = bg(FIELD, 12)
                layoutParams = lp(top = 6)
            }
            row.addView(tv("${e.native}   ", 17f, Color.WHITE, true))
            row.addView(tv(e.english, 13f, SUB).apply { layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) })
            row.addView(Switch(this).apply {
                isChecked = Languages.isOn(prefs, e.id)
                setOnCheckedChangeListener { _, on ->
                    if (on != Languages.isOn(prefs, e.id)) Languages.toggle(prefs, e.id)
                }
            })
            rows.addView(row)
        }
        return card(
            title("\uD83C\uDF10  Languages"),
            note("Choose the languages the language button on the keyboard switches between. English is always there. Changes work straight away."),
            rows
        )
    }

    // ------------------------------------------------------------------ lists

    private fun refreshSnips() {
        snipList.removeAllViews()
        val o = Snips.load(prefs)
        val keys = o.keys().asSequence().toList().sorted()
        if (keys.isEmpty()) snipList.addView(note("No shortcuts yet."))
        for (k in keys) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(6), dp(6), dp(6))
                background = bg(FIELD, 10)
                layoutParams = lp(bottom = 6)
            }
            row.addView(tv("$k  →  ${o.optString(k)}", 15f, Color.WHITE).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            row.addView(smallBtn("✕", false) {
                val cur = Snips.load(prefs)
                cur.remove(k)
                Snips.save(prefs, cur)
                refreshSnips()
            })
            snipList.addView(row)
        }
    }

    private fun refreshStyles() {
        styleList.removeAllViews()
        val builtInCount = RioStyles.builtIn.size
        val all = RioStyles.all(prefs)
        all.forEachIndexed { i, deco ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(6), dp(8))
                background = bg(FIELD, 10)
                layoutParams = lp(bottom = 6)
            }
            row.addView(tv("${RioStyles.name(i)}\n${deco}hello ${deco}world", 15f, Color.WHITE).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            if (i >= builtInCount) {
                row.addView(smallBtn("✕", false) {
                    val cur = RioStyles.custom(prefs)
                    val idx = i - builtInCount
                    if (idx in cur.indices) cur.removeAt(idx)
                    RioStyles.saveCustom(prefs, cur)
                    // keep the selected style pointing at the right entry
                    val sel = RioStyles.selected(prefs)
                    if (sel == i) RioStyles.select(prefs, -1)
                    else if (sel > i) RioStyles.select(prefs, sel - 1)
                    refreshStyles()
                })
            }
            styleList.addView(row)
        }
    }

    private val thumbCb: () -> Unit = { refreshVideoStrip() }

    override fun onPause() {
        effectPreview?.running = false
        AnimatedBackgrounds.thumbListeners.remove(thumbCb)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        AnimatedBackgrounds.thumbListeners.add(thumbCb)
        updateEffectPreview()
        if (::setupBox.isInitialized) refreshSetup()
        if (::snipList.isInitialized) refreshSnips()
        if (::styleList.isInitialized) refreshStyles()
        refreshStatusInfo()
        refreshUpdate()
        RemoteConfig.refresh(prefs, false) { refreshUpdate() }
    }

    // ------------------------------------------------------------------ small UI helpers

    private fun lp(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(top), 0, dp(bottom)) }

    private fun bg(color: Int, radius: Int, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun ripple(d: Drawable): Drawable = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), d, null)

    private fun tv(s: String, sp: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun title(s: String): TextView = tv(s, 18f, Color.WHITE, true).apply { setPadding(0, 0, 0, dp(6)) }

    private fun note(s: String): TextView = tv(s, 14f, SUB).apply {
        setPadding(0, 0, 0, dp(8))
        setLineSpacing(0f, 1.15f)
    }

    private fun pageHeader(t: String, sub: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = lp(bottom = 16)
        addView(tv(t, 28f, Color.WHITE, true))
        addView(tv(sub, 14f, SUB))
    }

    private fun card(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = bg(CARD, 18)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = lp(bottom = 12)
        views.forEach { addView(it) }
    }

    private fun field(h: String, single: Boolean = true): EditText = EditText(this).apply {
        hint = h
        setHintTextColor(Color.GRAY)
        setTextColor(Color.WHITE)
        setSingleLine(single)
        background = bg(FIELD, 12)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = lp(top = 8)
    }

    private fun btn(t: String, primary: Boolean = true, onClick: () -> Unit): Button = Button(this).apply {
        text = t
        isAllCaps = false
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.DEFAULT_BOLD
        stateListAnimator = null
        background = ripple(buttonBg(primary, 14))
        setPadding(dp(16), dp(12), dp(16), dp(12))
        layoutParams = lp(top = 8)
        setOnClickListener { onClick() }
    }

    private fun smallBtn(t: String, primary: Boolean, onClick: () -> Unit): Button = Button(this).apply {
        text = t
        isAllCaps = false
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.DEFAULT_BOLD
        stateListAnimator = null
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        background = ripple(buttonBg(primary, 12))
        setPadding(dp(14), dp(8), dp(14), dp(8))
        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
        setOnClickListener { onClick() }
    }

    private fun buttonBg(primary: Boolean, radius: Int): GradientDrawable =
        if (primary) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(PINK, PURPLE)).apply {
            cornerRadius = dp(radius).toFloat()
        } else bg(Color.parseColor("#2F3340"), radius)

    private fun exampleRow(a: String, b: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = bg(FIELD, 10)
        layoutParams = lp(top = 6)
        addView(tv(a, 15f, SUB).apply { layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) })
        addView(tv("→", 15f, PINK, true).apply { setPadding(dp(8), 0, dp(8), 0) })
        addView(tv(b, 18f, Color.WHITE, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            gravity = Gravity.END
        })
    }
}
