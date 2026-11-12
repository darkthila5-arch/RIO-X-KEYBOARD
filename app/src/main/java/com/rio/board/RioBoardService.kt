package com.rio.board

import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
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
        Theme("Light", "#E8EAF0", "#FFFFFF", "#C9CEDB", "#3B82F6", "#7C6CE0", "#B8BFD3", "#111111", "#5B6382", "#8FB8FF")
    )

    // ---------- state ----------
    private var siMode = false        // false = English, true = Sinhala
    private var shift = 0             // 0 off, 1 once, 2 caps lock
    private var symbols = false
    private var siLayer = 0           // 0 consonants, 1 vowels, 2 pili/signs
    private var lastShiftTap = 0L
    private var themeIdx = 0
    private var snowMode = 1          // 0 off, 1 keyboard area, 2 full screen
    private var clipOpen = false
    private val clips = ArrayList<String>()

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
        snowMode = prefs.getInt("snow", 1).coerceIn(0, 2)
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
        render()
        applySnow()
        return frame
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!::root.isInitialized) return
        if (shift == 1) shift = 0
        clipOpen = false
        captureClip()
        render()
        applySnow()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    // In full-screen snow mode only the keyboard part is touchable;
    // touches on the snow area pass through to the app below.
    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        if (snowMode == 2 && ::root.isInitialized && root.height > 0) {
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
    }

    // ---------- rendering ----------
    private fun render() {
        root.removeAllViews()
        root.setBackgroundColor(th.bg)
        root.addView(toolbar())
        if (clipOpen) {
            renderClipboard()
            return
        }
        root.addView(row(chars("1234567890")))
        if (siMode) renderSinhala() else renderEnglish()
    }

    private fun toolbar(): LinearLayout {
        val snowKey = Key(
            if (snowMode == 2) "❄+" else "❄", 1f,
            when (snowMode) {
                0 -> Style.SPECIAL
                1 -> Style.ACTIVE
                else -> Style.ACCENT
            }, 15f
        ) {
            snowMode = (snowMode + 1) % 3
            prefs.edit().putInt("snow", snowMode).apply()
            applySnow()
            render()
        }
        val keys = listOf(
            Key("📋", 1f, Style.SPECIAL, 16f) { pasteNow() },
            Key("🕘", 1f, Style.SPECIAL, 16f) { clipOpen = true; render() },
            Key("All", 1f, Style.SPECIAL, 13f) { menuAction(android.R.id.selectAll) },
            Key("Copy", 1.2f, Style.SPECIAL, 12f) { menuAction(android.R.id.copy) },
            Key("Cut", 1f, Style.SPECIAL, 13f) { menuAction(android.R.id.cut) },
            snowKey,
            Key("🎨", 1f, Style.SPECIAL, 16f) {
                themeIdx = (themeIdx + 1) % themes.size
                prefs.edit().putInt("theme", themeIdx).apply()
                applySnow()
                render()
            },
            Key("⚙", 1f, Style.SPECIAL, 16f) {
                val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
            }
        )
        return row(keys, h = 42)
    }

    private fun renderClipboard() {
        root.addView(
            row(
                listOf(
                    Key("←", 1f, Style.SPECIAL) { clipOpen = false; render() },
                    Key("Clipboard", 4f, Style.NORMAL, 15f) { },
                    Key("Clear", 1.6f, Style.SPECIAL, 14f) {
                        clips.clear()
                        saveClips()
                        render()
                    }
                )
            )
        )
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200))
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (clips.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Clipboard හිස්. Text එකක් copy කළාම මෙතන පෙනෙනවා."
                setTextColor(th.hint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(dp(12), dp(16), dp(12), dp(16))
            })
        } else {
            clips.toList().forEach { list.addView(clipItem(it)) }
        }
        scroll.addView(list)
        root.addView(scroll)
    }

    private fun clipItem(text: String): View {
        val tv = TextView(this)
        tv.text = text
        tv.maxLines = 2
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.setTextColor(th.text)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        tv.setPadding(dp(12), dp(10), dp(12), dp(10))
        tv.background = bg(th.key)
        tv.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
        tv.setOnClickListener {
            type(text)
            clipOpen = false
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
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
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

    private fun row(keys: List<Key>, pad: Float = 0f, h: Int = 50): LinearLayout {
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

    private fun bg(color: Int): StateListDrawable {
        fun shape(c: Int) = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(c)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(th.pressed))
            addState(intArrayOf(), shape(color))
        }
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
        tv.background = bg(
            when (k.style) {
                Style.NORMAL -> th.key
                Style.SPECIAL -> th.special
                Style.ACCENT -> th.accent
                Style.ACTIVE -> th.active
            }
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
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
