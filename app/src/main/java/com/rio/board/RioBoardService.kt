package com.rio.board

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView

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

    // ---------- state ----------
    private var siMode = false        // false = English, true = Sinhala
    private var shift = 0             // 0 off, 1 once, 2 caps lock
    private var symbols = false
    private var siLayer = 0           // 0 consonants, 1 vowels, 2 pili/signs
    private var lastShiftTap = 0L
    private lateinit var root: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    // ---------- colors ----------
    private val cBg = Color.parseColor("#17181C")
    private val cKey = Color.parseColor("#2B2D35")
    private val cSpecial = Color.parseColor("#3A3D49")
    private val cAccent = Color.parseColor("#3B82F6")
    private val cActive = Color.parseColor("#6D5BD0")
    private val cPressed = Color.parseColor("#5A5F73")

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
    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cBg)
            setPadding(dp(3), dp(6), dp(3), dp(6))
        }
        render()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!::root.isInitialized) return
        if (shift == 1) shift = 0
        render()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    // ---------- rendering ----------
    private fun render() {
        root.removeAllViews()
        root.addView(row(chars("1234567890")))
        if (siMode) renderSinhala() else renderEnglish()
    }

    private fun renderEnglish() {
        if (symbols) {
            root.addView(row(chars("@#$%&*-+()")))
            root.addView(row(chars("!\"':;/?_=~")))
            root.addView(row(chars("<>[]{}\\|^`") + Key("⌫", 1.3f, Style.SPECIAL, repeat = true) { deleteOne() }))
        } else {
            val upper = shift != 0
            fun letters(s: String) = s.map { c ->
                val t = if (upper) c.uppercaseChar().toString() else c.toString()
                Key(t) {
                    type(t)
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

    private fun row(keys: List<Key>, pad: Float = 0f): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
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
            addState(intArrayOf(android.R.attr.state_pressed), shape(cPressed))
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
        tv.setTextColor(if (k.space) Color.parseColor("#9AA3C0") else Color.WHITE)
        if (k.space) {
            tv.letterSpacing = 0.2f
            tv.typeface = Typeface.DEFAULT_BOLD
        }
        tv.background = bg(
            when (k.style) {
                Style.NORMAL -> cKey
                Style.SPECIAL -> cSpecial
                Style.ACCENT -> cAccent
                Style.ACTIVE -> cActive
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

    // ---------- input actions ----------
    private fun type(s: String) {
        currentInputConnection?.commitText(s, 1)
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
