package com.rio.board

import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray

class RioBoardService : InputMethodService() {

    private enum class Style { NORMAL, SPECIAL, ACCENT, ACTIVE }
    private enum class Panel { NONE, CLIPBOARD, THEME, SETTINGS }

    private class Key(
        val label: String,
        val weight: Float = 1f,
        val style: Style = Style.NORMAL,
        val textSp: Float = 20f,
        val repeat: Boolean = false,
        val space: Boolean = false,
        val onLong: (() -> Unit)? = null,
        val onPress: () -> Unit
    )

    private class Theme(
        val name: String,
        bgS: String, keyS: String, specialS: String, accentS: String,
        activeS: String, pressedS: String, textS: String, hintS: String, snowS: String
    ) {
        val bg = Color.parseColor(bgS)
        val key = Color.parseColor(keyS)
        val special = Color.parseColor(specialS)
        val accent = Color.parseColor(accentS)
        val active = Color.parseColor(activeS)
        val pressed = Color.parseColor(pressedS)
        val text = Color.parseColor(textS)
        val hint = Color.parseColor(hintS)
        val snow = Color.parseColor(snowS)
    }

    private val themes = listOf(
        Theme("Dark", "#17181C", "#2B2D35", "#3A3D49", "#3B82F6", "#6D5BD0", "#5A5F73", "#FFFFFF", "#9AA3C0", "#FFFFFF"),
        Theme("Midnight", "#0B1026", "#18204A", "#232C63", "#00B4D8", "#7B2CBF", "#3A4690", "#FFFFFF", "#8FA3FF", "#DDEBFF"),
        Theme("Forest", "#0F1F17", "#1C3A2A", "#26503A", "#2ECC71", "#F39C12", "#3F7A58", "#FFFFFF", "#9BD6B0", "#FFFFFF"),
        Theme("Rose", "#2A1220", "#4A2038", "#63304D", "#FF4D8D", "#B84DFF", "#8A3F67", "#FFFFFF", "#F5A9CC", "#FFE8F2"),
        Theme("Ocean", "#06202B", "#0E3A4D", "#16526B", "#00C2A8", "#3B82F6", "#2A7A99", "#FFFFFF", "#8CD3E6", "#E6FBFF"),
        Theme("Sunset", "#2B1408", "#4D2A12", "#6A3A1A", "#FF7A18", "#E8478B", "#8F5A30", "#FFFFFF", "#F2B88A", "#FFF1E0"),
        Theme("Purple", "#1B1030", "#2F1D54", "#432A78", "#A855F7", "#EC4899", "#6A4DA8", "#FFFFFF", "#C4A8F0", "#F3E8FF"),
        Theme("Light", "#E8EAF0", "#FFFFFF", "#C9CEDB", "#3B82F6", "#7C6CE0", "#B8BFD3", "#111111", "#5B6382", "#8FB8FF")
    )

    // ---------- state ----------
    private var siMode = false        // false = English, true = Sinhala
    private var shift = 0             // 0 off, 1 once, 2 caps lock
    private var symbols = false
    private var siLayer = 0           // 0 consonants, 1 vowels, 2 pili/signs
    private var lastShiftTap = 0L
    private var themeIdx = 0
    private var snowMode = 2          // 0 off, 1 keyboard area, 2 full screen
    private var lastSnow = 2
    private var haptic = true
    private var keyHIdx = 1
    private var panel = Panel.NONE
    private val clips = ArrayList<String>()

    private val keyHs = intArrayOf(44, 50, 56)
    private val keyH: Int get() = keyHs[keyHIdx]

    private lateinit var prefs: SharedPreferences
    private lateinit var cm: ClipboardManager
    private lateinit var frame: FrameLayout
    private lateinit var root: LinearLayout
    private lateinit var snow: SnowView
    private val handler = Handler(Looper.getMainLooper())
    private val th: Theme get() = themes[themeIdx]

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }

    // ---------- Sinhala layers ----------
    private fun split(vararg rows: String): List<List<String>> =
        rows.map { r -> r.map { it.toString() } }

    private val si1 = split("කගචජටඩතදපබ", "මයරලවසහනණළ", "ඛඝඡඣඨඪථධඵභ")
    private val si2 = split("අආඇඈඉඊඋඌඑඒ", "ඔඕඓඖඍඤඞඬඳඹ", "ශෂෆඥඟඦඎඏඐ")
    private val si3 = listOf(
        listOf("\u0DCF", "\u0DD0", "\u0DD1", "\u0DD2", "\u0DD3", "\u0DD4", "\u0DD6", "\u0DD8", "\u0DF2", "\u0DCA"),
        listOf("\u0DD9", "\u0DDA", "\u0DDB", "\u0DDC", "\u0DDD", "\u0DDE", "\u0D82", "\u0D83", "\u0DCA\u200D\u0DBB", "\u0DCA\u200D\u0DBA")
    )

    // ---------- lifecycle ----------
    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("rio_board", MODE_PRIVATE)
        themeIdx = prefs.getInt("theme", 0).coerceIn(0, themes.size - 1)
        snowMode = prefs.getInt("snow", 2).coerceIn(0, 2)
        lastSnow = prefs.getInt("lastsnow", 2).coerceIn(1, 2)
        haptic = prefs.getBoolean("haptic", true)
        keyHIdx = prefs.getInt("keyh", 1).coerceIn(0, 2)
        try {
            val arr = JSONArray(prefs.getString("clips", "[]"))
            for (i in 0 until arr.length()) clips.add(arr.getString(i))
        } catch (e: Exception) {
        }
        cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        cm.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        makeWindowTransparent()
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(4), dp(3), dp(6))
        }
        snow = SnowView(this)
        frame = FrameLayout(this)
        frame.addView(
            root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
        frame.addView(
            snow,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateSnowTop() }
        render()
        applySnow()
        return frame
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!::root.isInitialized) return
        makeWindowTransparent()
        if (shift == 1) shift = 0
        panel = Panel.NONE
        captureClip()
        render()
        applySnow()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun makeWindowTransparent() {
        try {
            window?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        } catch (e: Exception) {
        }
    }

    // When the window is taller than the keyboard (snow area above it),
    // only the keyboard part is touchable; the rest passes through to the app.
    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        if (::root.isInitialized && ::frame.isInitialized &&
            root.height > 0 && frame.height - root.height > dp(40)
        ) {
            val loc = IntArray(2)
            root.getLocationInWindow(loc)
            val top = loc[1]
            outInsets.contentTopInsets = top
            outInsets.visibleTopInsets = top
            outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.set(loc[0], top, loc[0] + root.width, top + root.height)
        }
    }

    // ---------- snow ----------
    private fun applySnow() {
        if (!::snow.isInitialized || !::frame.isInitialized) return
        snow.color = th.snow
        snow.base = if (snowMode == 2) 75 else 40
        snow.running = snowMode != 0
        val lp = frame.layoutParams
        if (lp != null) {
            val h = if (snowMode == 2) resources.displayMetrics.heightPixels
            else ViewGroup.LayoutParams.WRAP_CONTENT
            if (lp.height != h) {
                lp.height = h
                frame.layoutParams = lp
            }
        }
        updateSnowTop()
    }

    private fun updateSnowTop() {
        if (!::snow.isInitialized || !::root.isInitialized) return
        snow.topLimit = if (snowMode == 1) root.top.toFloat() else 0f
    }

    private fun setSnow(mode: Int) {
        snowMode = mode
        if (mode != 0) lastSnow = mode
        prefs.edit().putInt("snow", snowMode).putInt("lastsnow", lastSnow).apply()
        applySnow()
        render()
    }

    // ---------- rendering ----------
    private fun render() {
        root.removeAllViews()
        root.setBackgroundColor(th.bg)
        root.addView(toolbar())
        when (panel) {
            Panel.CLIPBOARD -> renderClipboard()
            Panel.THEME -> renderTheme()
            Panel.SETTINGS -> renderSettings()
            Panel.NONE -> {
                root.addView(row(chars("1234567890")))
                if (siMode) renderSinhala() else renderEnglish()
            }
        }
    }

    // ----- toolbar (flat line icons, like Gboard) -----
    private fun toolbar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
        }
        fun add(kind: IconView.Kind, active: Boolean = false, onClick: () -> Unit) {
            bar.addView(iconButton(kind, active, onClick))
        }
        add(IconView.Kind.CLIPBOARD) { pasteNow() }
        add(IconView.Kind.HISTORY, panel == Panel.CLIPBOARD) { togglePanel(Panel.CLIPBOARD) }
        add(IconView.Kind.SELECT_ALL) { menuAction(android.R.id.selectAll) }
        add(IconView.Kind.COPY) { menuAction(android.R.id.copy) }
        add(IconView.Kind.CUT) { menuAction(android.R.id.cut) }
        add(IconView.Kind.SNOW, snowMode != 0) { setSnow(if (snowMode != 0) 0 else lastSnow) }
        add(IconView.Kind.THEME, panel == Panel.THEME) { togglePanel(Panel.THEME) }
        add(IconView.Kind.SETTINGS, panel == Panel.SETTINGS) { togglePanel(Panel.SETTINGS) }
        return bar
    }

    private fun togglePanel(p: Panel) {
        panel = if (panel == p) Panel.NONE else p
        render()
    }

    private fun iconButton(kind: IconView.Kind, active: Boolean, onClick: () -> Unit): View {
        val v = IconView(this, kind)
        v.tint = if (active) th.accent else th.text
        v.alpha = if (active) 1f else 0.85f
        v.background = flatBg()
        v.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(dp(1), dp(2), dp(1), dp(2))
        }
        v.isClickable = true
        v.setOnClickListener {
            feedback(it)
            onClick()
        }
        return v
    }

    private fun flatBg(): StateListDrawable {
        fun shape(c: Int) = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(c)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(th.pressed))
            addState(intArrayOf(), shape(Color.TRANSPARENT))
        }
    }

    private fun roundBg(color: Int, radiusDp: Int): StateListDrawable {
        fun shape(c: Int) = GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat()
            setColor(c)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(th.pressed))
            addState(intArrayOf(), shape(color))
        }
    }

    private fun feedback(v: View) {
        if (haptic) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    // ----- panels -----
    private fun panelHeader(title: String, actionKind: IconView.Kind? = null, action: (() -> Unit)? = null): LinearLayout {
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(keyH))
        }
        fun side(kind: IconView.Kind, onClick: () -> Unit): View {
            val b = iconButton(kind, false, onClick)
            b.layoutParams = LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(dp(2), dp(3), dp(2), dp(3))
            }
            return b
        }
        h.addView(side(IconView.Kind.BACK) { panel = Panel.NONE; render() })
        h.addView(TextView(this).apply {
            text = title
            setTextColor(th.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                leftMargin = dp(6)
            }
        })
        if (actionKind != null && action != null) {
            h.addView(side(actionKind, action))
        } else {
            h.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(52), 1) })
        }
        return h
    }

    private fun panelBody(content: View): ScrollView {
        return ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(keyH * 4))
            isVerticalScrollBarEnabled = false
            addView(content)
        }
    }

    private fun hintText(s: String): TextView = TextView(this).apply {
        text = s
        setTextColor(th.hint)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(dp(8), dp(10), dp(8), dp(4))
        gravity = Gravity.CENTER
    }

    // ----- clipboard panel (card grid, like Gboard) -----
    private fun renderClipboard() {
        root.addView(panelHeader("Clipboard", IconView.Kind.TRASH) {
            clips.clear()
            saveClips()
            render()
        })
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        if (clips.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Clipboard හිස්.\nText එකක් copy කළාම මෙතන පෙනෙනවා."
                setTextColor(th.hint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(40), dp(12), dp(16))
            })
        } else {
            clips.toList().chunked(2).forEach { pair ->
                val r = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                pair.forEach { r.addView(clipCard(it)) }
                if (pair.size == 1) {
                    r.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
                }
                list.addView(r)
            }
            list.addView(hintText("Tap = paste  ·  Long press = delete"))
        }
        root.addView(panelBody(list))
    }

    private fun clipCard(text: String): View {
        val tv = TextView(this)
        tv.text = text
        tv.maxLines = 4
        tv.minHeight = dp(64)
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.gravity = Gravity.TOP or Gravity.START
        tv.setTextColor(th.text)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        tv.setPadding(dp(12), dp(10), dp(12), dp(10))
        tv.background = roundBg(th.key, 14)
        tv.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }
        tv.setOnClickListener {
            feedback(it)
            type(text)
            panel = Panel.NONE
            render()
        }
        tv.setOnLongClickListener {
            clips.remove(text)
            saveClips()
            render()
            true
        }
        return tv
    }

    // ----- theme panel -----
    private fun renderTheme() {
        root.addView(panelHeader("Theme"))
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(8))
        }
        themes.withIndex().toList().chunked(4).forEach { items ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            items.forEach { (i, t) -> r.addView(swatch(i, t)) }
            grid.addView(r)
        }
        root.addView(panelBody(grid))
    }

    private fun swatch(index: Int, t: Theme): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(2), dp(6), dp(2), dp(6))
            }
        }
        val selected = index == themeIdx
        val circle = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(t.key)
                setStroke(dp(if (selected) 4 else 2), if (selected) t.accent else t.special)
            }
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
        }
        val dot = TextView(this).apply {
            text = if (selected) "✓" else ""
            setTextColor(t.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        val label = TextView(this).apply {
            text = t.name
            setTextColor(th.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }
        box.addView(circle)
        box.addView(label)
        box.setOnClickListener {
            feedback(it)
            themeIdx = index
            prefs.edit().putInt("theme", themeIdx).apply()
            applySnow()
            render()
        }
        return box
    }

    // ----- settings panel -----
    private fun renderSettings() {
        root.addView(panelHeader("Settings"))
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(10))
        }
        box.addView(settingLabel("Snow ❄"))
        box.addView(chipRow(listOf("Off", "Keyboard", "Full screen"), snowMode) { setSnow(it) })
        box.addView(settingLabel("Vibration"))
        box.addView(chipRow(listOf("On", "Off"), if (haptic) 0 else 1) {
            haptic = it == 0
            prefs.edit().putBoolean("haptic", haptic).apply()
            render()
        })
        box.addView(settingLabel("Key height"))
        box.addView(chipRow(listOf("Small", "Normal", "Large"), keyHIdx) {
            keyHIdx = it
            prefs.edit().putInt("keyh", keyHIdx).apply()
            render()
        })
        box.addView(TextView(this).apply {
            text = "Open RIO BOARD app"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = roundBg(th.accent, 12)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                setMargins(dp(3), dp(14), dp(3), dp(4))
            }
            setOnClickListener {
                startActivity(Intent(this@RioBoardService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        })
        root.addView(panelBody(box))
    }

    private fun settingLabel(s: String): TextView = TextView(this).apply {
        text = s
        setTextColor(th.hint)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(dp(4), dp(10), dp(4), dp(4))
    }

    private fun chipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42))
        }
        options.forEachIndexed { i, label ->
            val on = i == selected
            r.addView(TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(if (on) Color.WHITE else th.text)
                background = roundBg(if (on) th.accent else th.key, 12)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(dp(3), dp(2), dp(3), dp(2))
                }
                setOnClickListener {
                    feedback(it)
                    onSelect(i)
                }
            })
        }
        return r
    }

    // ----- typing layouts -----
    private fun renderEnglish() {
        if (symbols) {
            root.addView(row(chars("@#$%&*-+()")))
            root.addView(row(chars("!\"':;/?_=~")))
            root.addView(row(chars("<>[]{}\\|^`") + Key("⌫", 1.3f, Style.SPECIAL, repeat = true) { deleteOne() }))
        } else {
            val upper = shift != 0
            fun letters(s: String) = s.map { c ->
                val label = if (upper) c.uppercaseChar().toString() else c.toString()
                Key(label) {
                    type(label)
                    if (shift == 1) {
                        shift = 0
                        render()
                    }
                }
            }
            root.addView(row(letters("qwertyuiop")))
            root.addView(row(letters("asdfghjkl"), pad = 0.5f))
            val shiftKey = Key(
                if (shift == 2) "⇪" else "⇧", 1.5f,
                if (shift == 0) Style.SPECIAL else Style.ACTIVE
            ) { toggleShift() }
            val bs = Key("⌫", 1.5f, Style.SPECIAL, repeat = true) { deleteOne() }
            root.addView(row(listOf(shiftKey) + letters("zxcvbnm") + bs))
        }
        root.addView(
            row(
                listOf(
                    if (symbols)
                        Key("ABC", 1.5f, Style.SPECIAL, 14f) { symbols = false; render() }
                    else
                        Key("?123", 1.5f, Style.SPECIAL, 14f) { symbols = true; render() },
                    Key("සිං", 1.3f, Style.SPECIAL, 14f) { siMode = true; symbols = false; render() },
                    Key(",") { type(",") },
                    spaceKey(4.5f),
                    Key(".") { type(".") },
                    Key("⏎", 1.5f, Style.ACCENT) { pressEnter() }
                )
            )
        )
    }

    private fun renderSinhala() {
        val layer = when (siLayer) {
            0 -> si1
            1 -> si2
            else -> si3
        }
        layer.forEach { keys ->
            root.addView(row(keys.map { s ->
                val label = if (siLayer == 2) "\u25CC$s" else s
                Key(label, textSp = 22f) { type(s) }
            }))
        }
        repeat(3 - layer.size) {
            root.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(keyH))
            })
        }
        val nextLabel = when ((siLayer + 1) % 3) {
            0 -> "ක"
            1 -> "අ"
            else -> "\u25CC\u0DCF"
        }
        root.addView(
            row(
                listOf(
                    Key("ABC", 1.3f, Style.SPECIAL, 14f) { siMode = false; render() },
                    Key(nextLabel, 1.3f, Style.ACTIVE, 20f) { siLayer = (siLayer + 1) % 3; render() },
                    Key(",") { type(",") },
                    spaceKey(4f),
                    Key(".") { type(".") },
                    Key("⌫", 1.3f, Style.SPECIAL, repeat = true) { deleteOne() },
                    Key("⏎", 1.5f, Style.ACCENT) { pressEnter() }
                )
            )
        )
    }

    private fun spaceKey(weight: Float) = Key(
        "RIO BOARD", weight, Style.NORMAL, 12f, space = true,
        onLong = {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
    ) { type(" ") }

    private fun chars(s: String) = s.map { c -> Key(c.toString()) { type(c.toString()) } }

    // ---------- view helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun row(keys: List<Key>, pad: Float = 0f, h: Int = keyH): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(h))
        }
        if (pad > 0f) r.addView(spacer(pad))
        keys.forEach { r.addView(keyView(it)) }
        if (pad > 0f) r.addView(spacer(pad))
        return r
    }

    private fun spacer(w: Float) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w)
    }

    private fun keyView(k: Key): View {
        val tv = TextView(this)
        tv.text = k.label
        tv.gravity = Gravity.CENTER
        tv.maxLines = 1
        tv.includeFontPadding = false
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, k.textSp)
        tv.setTextColor(
            when {
                k.space -> th.hint
                k.style == Style.ACCENT || k.style == Style.ACTIVE -> Color.WHITE
                else -> th.text
            }
        )
        if (k.space) {
            tv.letterSpacing = 0.2f
            tv.typeface = Typeface.DEFAULT_BOLD
        }
        tv.background = roundBg(
            when (k.style) {
                Style.NORMAL -> th.key
                Style.SPECIAL -> th.special
                Style.ACCENT -> th.accent
                Style.ACTIVE -> th.active
            }, 8
        )
        tv.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, k.weight).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        }
        tv.isClickable = true

        if (k.repeat) {
            val r = object : Runnable {
                override fun run() {
                    k.onPress()
                    handler.postDelayed(this, 50)
                }
            }
            tv.setOnTouchListener { view, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.isPressed = true
                        feedback(view)
                        k.onPress()
                        handler.postDelayed(r, 400)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        handler.removeCallbacks(r)
                    }
                }
                true
            }
        } else {
            tv.setOnClickListener {
                feedback(it)
                k.onPress()
            }
            val long = k.onLong
            if (long != null) {
                tv.setOnLongClickListener {
                    long()
                    true
                }
            }
        }
        return tv
    }

    // ---------- clipboard ----------
    private fun captureClip() {
        try {
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
            if (text.isBlank()) return
            if (clips.isNotEmpty() && clips[0] == text) return
            clips.remove(text)
            clips.add(0, text)
            while (clips.size > 20) clips.removeAt(clips.size - 1)
            saveClips()
        } catch (e: Exception) {
        }
    }

    private fun saveClips() {
        val arr = JSONArray()
        clips.forEach { arr.put(it) }
        prefs.edit().putString("clips", arr.toString()).apply()
    }

    private fun pasteNow() {
        captureClip()
        val text = try {
            cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        } catch (e: Exception) {
            null
        }
        val s = if (!text.isNullOrEmpty()) text else clips.firstOrNull()
        if (!s.isNullOrEmpty()) type(s)
    }

    private fun menuAction(id: Int) {
        currentInputConnection?.performContextMenuAction(id)
        if (id == android.R.id.copy || id == android.R.id.cut) {
            handler.postDelayed({ captureClip() }, 150)
        }
    }

    // ---------- input actions ----------
    private fun type(s: String) {
        currentInputConnection?.commitText(s, 1)
        if (::snow.isInitialized) snow.burst()
    }

    private fun deleteOne() {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        if (!sel.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(1, 0)
        if (before.isNullOrEmpty()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun pressEnter() {
        val ei = currentInputEditorInfo
        if (ei == null) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            return
        }
        val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
        if ((ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
            action == EditorInfo.IME_ACTION_NONE ||
            action == EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        } else {
            currentInputConnection?.performEditorAction(action)
        }
    }

    private fun toggleShift() {
        val now = SystemClock.uptimeMillis()
        shift = when {
            shift == 2 -> 0
            shift == 1 && now - lastShiftTap < 400 -> 2
            shift == 1 -> 0
            else -> 1
        }
        lastShiftTap = now
        render()
    }
}
