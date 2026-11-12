package com.rio.board

import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
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
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RioBoardService : InputMethodService() {

    companion object {
        var instance: RioBoardService? = null
    }

    private enum class Style { NORMAL, SPECIAL, ACCENT, ACTIVE }
    private enum class Panel { NONE, CLIPBOARD, THEME, SETTINGS, EMOJI, FONT }

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

    // custom theme (index = themes.size)
    private val customBgs = listOf("#101010", "#0B1A2E", "#1A0B2E", "#2E0B16", "#0B2E1A", "#2E260B", "#1C1C28", "#000000")
    private val customAccents = listOf("#3B82F6", "#22C55E", "#EF4444", "#F59E0B", "#A855F7", "#EC4899", "#14B8A6", "#FFFFFF")
    private var customBgIdx = 0
    private var customAccIdx = 0
    private var customCache: Theme? = null

    // effects
    private val effectNames = listOf("Snow", "Rain", "Hearts", "Stars", "Confetti", "Bubbles")
    private val snowPalette = listOf("auto", "#FFFFFF", "#7CC8FF", "#FF8AC8", "#FFD54A", "#7CFFB2", "rainbow")
    private val rainbow = intArrayOf(
        0xFFFF5252.toInt(), 0xFFFFA726.toInt(), 0xFFFFEE58.toInt(), 0xFF66BB6A.toInt(),
        0xFF29B6F6.toInt(), 0xFF7E57C2.toInt(), 0xFFEC407A.toInt()
    )

    // ---------- word lists for suggestions ----------
    private val enWords = ("the be to of and a in that have I it for not on with he as you do at this but his by from they we say her she or an will my one all would there their what so up out if about who get which go me when make can like time no just him know take people into year your good some could them see other than then now look only come its over think also back after use two how our work first well way even new want because any these give day most us is are was were been has had did does hello hi thanks thank please sorry yes okay ok where why home love friend friends today tomorrow yesterday night morning evening happy birthday welcome great nice beautiful really very much many here still call phone message send share video song music photo picture game school class exam study money food water bus train going coming doing being having something anything everything nothing someone everyone always never sometimes maybe again already before tonight together should might must need needs wait waiting enough little big small long short right left help keep let begin seem talk turn start show hear play run move live believe bring happen write provide sit stand lose pay meet include continue set learn change lead understand watch follow stop create speak read allow add spend grow open walk win offer remember consider appear buy serve expect build stay fall cut reach remain people person world life hand part child eye woman place week case point government company number group problem fact").split(" ")

    private val siWords = ("ඔයා මම අපි ඔබ කොහොමද කොහොමෙයි ස්තූතියි හරි ඉතින් මොකද මොකක්ද මොනවද කොහෙද නැහැ ඔව් කරන්න යන්න එන්න බලන්න දෙන්න ගන්න ලස්සනයි ආයුබෝවන් සුභ රාත්‍රියක් උදෑසනක් දැන් ඊයේ අද හෙට ගෙදර යාලුවා යාලුවෝ අම්මා තාත්තා මල්ලි අක්කා අයියා නංගි ආදරේ සතුටුයි දුකයි කමක් නැහැ පුළුවන් බැහැ ඕන නෑ තියෙනවා මොකෝ ඇයි කවද්ද කීයද කවුද මෙහෙ එහෙ ඔහොම මේක ඒක ඕක").split(" ")

    private val dict: List<String> by lazy { (enWords + siWords).filter { it.isNotBlank() } }

    // ---------- emoji ----------
    private val emojiCats: List<Pair<String, List<String>>> = listOf(
        "😀" to ("😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 😉 😍 🥰 😘 😋 😛 😜 🤪 😎 🤩 🥳 😏 😒 😞 😔 😢 😭 😤 😡 🤬 😱 😨 😰 😓 🤗 🤔 🤭 🤫 😶 😐 🙄 😴 🤤 😷 🤒 🥵 🥶 🤯 😈 💀 👻 🤡 💩").split(" "),
        "👍" to ("👍 👎 👌 ✌️ 🤞 🤟 🤘 👏 🙌 🙏 💪 👋 🤝 ✊ 👊 ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 💔 💕 💞 💓 💗 💖 💘 💝 🔥 ✨ ⭐ 🌟 💯 ✅ ❌ ⚡ 💥 🎉 🎊 💫 💦 👀 👑").split(" "),
        "🐶" to ("🐶 🐱 🐭 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🦅 🐢 🐍 🐙 🐬 🦋 🌹 🌸 🌴 🌈 ☀️ 🌙 ❄️ 🍎 🍌 🍉 🍇 🍓 🍕 🍔 🍟 🌭 🍩 🍪 🎂 🍫 ☕ 🍺 🥤 🍜").split(" "),
        "🚀" to ("📱 💻 🎧 🎮 📷 🎬 🎵 🎶 🚗 ✈️ 🚀 🏠 ⚽ 🏏 🏆 🎁 💰 💎 🔑 🔒 📌 📎 ✏️ 📚 💡 🔔 ⏰ 📞 🇱🇰 🚌 🏍️ 🎯 🎈 🛒 📝 📍 ⚙️ 🔋 📶").split(" ")
    )
    private val recent = ArrayList<String>()
    private var emojiTab = 1

    // ---------- state ----------
    private var lang = 0              // 0 English, 1 Sinhala keys, 2 Singlish
    private var shift = 0             // 0 off, 1 once, 2 caps lock
    private var symbols = false
    private var siLayer = 0           // 0 consonants, 1 vowels, 2 pili/signs
    private var lastShiftTap = 0L
    private var themeIdx = 0
    private var snowMode = 2          // 0 off, 1 keyboard area, 2 full screen
    private var lastSnow = 2
    private var effectIdx = 0
    private var snowColorIdx = 0
    private var haptic = true
    private var soundOn = false
    private var oneHand = 0           // 0 off, 1 left, 2 right
    private var keyHIdx = 1
    private var fontStyle = 0
    private var panel = Panel.NONE
    private var toolbarX = 0
    private var toolsPinned = false
    private var suggestions: List<String> = emptyList()
    private val clips = ArrayList<String>()
    private val learned = HashMap<String, Int>()
    private var bgBitmap: Bitmap? = null
    private var pendingVoice: String? = null

    // Singlish composing: raw latin letters + what is currently shown on screen
    private val sgBuf = StringBuilder()
    private var sgShown = ""

    private val keyHs = intArrayOf(44, 50, 56)
    private val keyH: Int get() = keyHs[keyHIdx]
    private val bgOn: Boolean get() = bgBitmap != null

    private lateinit var prefs: SharedPreferences
    private lateinit var cm: ClipboardManager
    private lateinit var frame: FrameLayout
    private lateinit var kbBox: FrameLayout
    private lateinit var root: LinearLayout
    private lateinit var barHolder: LinearLayout
    private lateinit var bgImage: ImageView
    private lateinit var bgDim: View
    private lateinit var snow: SnowView
    private val handler = Handler(Looper.getMainLooper())

    private val th: Theme
        get() = if (themeIdx >= themes.size) customThemeObj() else themes[themeIdx]

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
        instance = this
        prefs = getSharedPreferences("rio_board", MODE_PRIVATE)
        themeIdx = prefs.getInt("theme", 0).coerceIn(0, themes.size)
        snowMode = prefs.getInt("snow", 2).coerceIn(0, 2)
        lastSnow = prefs.getInt("lastsnow", 2).coerceIn(1, 2)
        effectIdx = prefs.getInt("effect", 0).coerceIn(0, effectNames.size - 1)
        snowColorIdx = prefs.getInt("snowcol", 0).coerceIn(0, snowPalette.size - 1)
        haptic = prefs.getBoolean("haptic", true)
        soundOn = prefs.getBoolean("sound", false)
        oneHand = prefs.getInt("onehand", 0).coerceIn(0, 2)
        keyHIdx = prefs.getInt("keyh", 1).coerceIn(0, 2)
        fontStyle = prefs.getInt("font", 0).coerceIn(0, Fonts.names.size - 1)
        customBgIdx = prefs.getInt("cbg", 0).coerceIn(0, customBgs.size - 1)
        customAccIdx = prefs.getInt("cacc", 0).coerceIn(0, customAccents.size - 1)
        try {
            val arr = JSONArray(prefs.getString("clips", "[]"))
            for (i in 0 until arr.length()) clips.add(arr.getString(i))
        } catch (e: Exception) {
        }
        try {
            val o = JSONObject(prefs.getString("learned", "{}"))
            o.keys().forEach { learned[it] = o.getInt(it) }
        } catch (e: Exception) {
        }
        val rec = prefs.getString("recent", "") ?: ""
        rec.split(" ").filter { it.isNotBlank() }.forEach { recent.add(it) }
        loadBg()
        cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        cm.removePrimaryClipChangedListener(clipListener)
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        makeWindowTransparent()
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(4), dp(3), dp(6))
        }
        barHolder = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
        }
        bgImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        bgDim = View(this)
        kbBox = FrameLayout(this)
        kbBox.addView(
            bgImage,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        kbBox.addView(
            bgDim,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        kbBox.addView(
            root,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        snow = SnowView(this)
        frame = FrameLayout(this)
        frame.addView(
            kbBox,
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
        applyLayout()
        applySnow()
        return frame
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!::root.isInitialized) return
        makeWindowTransparent()
        if (shift == 1) shift = 0
        panel = Panel.NONE
        toolsPinned = false
        sgReset()
        captureClip()
        suggestions = computeSuggestions()
        render()
        applyLayout()
        applySnow()
        if (pendingVoice != null) handler.postDelayed({ flushVoice() }, 350)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateSuggestions()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun makeWindowTransparent() {
        try {
            window?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        } catch (e: Exception) {
        }
    }

    // Only the keyboard itself is touchable when the window is taller than the keyboard
    // (effects area) or when one-handed mode leaves empty space on one side.
    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        if (::kbBox.isInitialized && ::frame.isInitialized && kbBox.height > 0 &&
            (frame.height - kbBox.height > dp(40) || oneHand != 0)
        ) {
            val loc = IntArray(2)
            kbBox.getLocationInWindow(loc)
            val top = loc[1]
            outInsets.contentTopInsets = top
            outInsets.visibleTopInsets = top
            outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.set(loc[0], top, loc[0] + kbBox.width, top + kbBox.height)
        }
    }

    // ---------- voice ----------
    private fun startVoice() {
        sgReset()
        val i = Intent(this, VoiceActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.putExtra("si", lang != 0)
        startActivity(i)
    }

    fun voiceResult(text: String) {
        pendingVoice = text
        handler.postDelayed({ flushVoice() }, 500)
    }

    private fun flushVoice() {
        val t = pendingVoice ?: return
        val ic = currentInputConnection ?: return
        ic.commitText("$t ", 1)
        pendingVoice = null
    }

    // ---------- background photo ----------
    fun reloadBackground() {
        handler.post {
            loadBg()
            if (::root.isInitialized) {
                render()
                applyLayout()
            }
        }
    }

    private fun loadBg() {
        val f = File(filesDir, "kb_bg.jpg")
        bgBitmap = if (prefs.getBoolean("bgon", false) && f.exists()) {
            try {
                BitmapFactory.decodeFile(f.path)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    private fun removeBackground() {
        prefs.edit().putBoolean("bgon", false).apply()
        try {
            File(filesDir, "kb_bg.jpg").delete()
        } catch (e: Exception) {
        }
        loadBg()
        render()
        applyLayout()
    }

    /** Key colours become a bit see-through when a photo is the background. */
    private fun kc(color: Int): Int =
        if (bgOn && Color.alpha(color) == 255) (color and 0x00FFFFFF) or (0xB8 shl 24) else color

    // ---------- layout / effects ----------
    private fun applyLayout() {
        if (!::kbBox.isInitialized) return
        val lp = kbBox.layoutParams as? FrameLayout.LayoutParams
        if (lp != null) {
            val sw = resources.displayMetrics.widthPixels
            val w = if (oneHand == 0) ViewGroup.LayoutParams.MATCH_PARENT else (sw * 0.82f).toInt()
            val g = Gravity.BOTTOM or when (oneHand) {
                1 -> Gravity.START
                2 -> Gravity.END
                else -> Gravity.NO_GRAVITY
            }
            if (lp.width != w || lp.gravity != g) {
                lp.width = w
                lp.gravity = g
                kbBox.layoutParams = lp
            }
        }
        applyBackground()
    }

    private fun applyBackground() {
        if (!::bgImage.isInitialized) return
        if (bgOn) {
            bgImage.setImageBitmap(bgBitmap)
            bgImage.visibility = View.VISIBLE
            bgDim.setBackgroundColor((th.bg and 0x00FFFFFF) or 0x66000000)
        } else {
            bgImage.setImageDrawable(null)
            bgImage.visibility = View.GONE
            bgDim.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun sysDimen(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun effectColors(): IntArray {
        if (snowColorIdx == snowPalette.size - 1) return rainbow
        if (snowColorIdx != 0) return intArrayOf(Color.parseColor(snowPalette[snowColorIdx]))
        return when (effectIdx) {
            0 -> intArrayOf(th.snow)
            1 -> intArrayOf(Color.parseColor("#8FD3FF"))
            2 -> intArrayOf(Color.parseColor("#FF5C8A"))
            3 -> intArrayOf(Color.parseColor("#FFE066"))
            4 -> rainbow
            else -> intArrayOf(Color.parseColor("#BFE9FF"))
        }
    }

    private fun applySnow() {
        if (!::snow.isInitialized || !::frame.isInitialized) return
        snow.effect = effectIdx
        snow.colors = effectColors()
        snow.base = if (snowMode == 2) 70 else 38
        snow.running = snowMode != 0
        val lp = frame.layoutParams
        if (lp != null) {
            // keep the window inside the usable screen so the keyboard is not pushed down
            val full = resources.displayMetrics.heightPixels - sysDimen("status_bar_height") -
                    sysDimen("navigation_bar_height") - dp(2)
            val h = if (snowMode == 2) full else ViewGroup.LayoutParams.WRAP_CONTENT
            if (lp.height != h) {
                lp.height = h
                frame.layoutParams = lp
            }
        }
        updateSnowTop()
    }

    private fun updateSnowTop() {
        if (!::snow.isInitialized || !::kbBox.isInitialized) return
        snow.topLimit = if (snowMode == 1) kbBox.top.toFloat() else 0f
    }

    private fun setSnow(mode: Int) {
        snowMode = mode
        if (mode != 0) lastSnow = mode
        prefs.edit().putInt("snow", snowMode).putInt("lastsnow", lastSnow).apply()
        applySnow()
        render()
    }

    private fun setEffect(i: Int) {
        effectIdx = i
        prefs.edit().putInt("effect", i).apply()
        if (snowMode == 0) {
            snowMode = lastSnow
            prefs.edit().putInt("snow", snowMode).apply()
        }
        applySnow()
        render()
    }

    private fun setOneHand(i: Int) {
        oneHand = i
        prefs.edit().putInt("onehand", i).apply()
        applyLayout()
        render()
    }

    private fun setLang(l: Int) {
        sgReset()
        lang = l
        symbols = false
        panel = Panel.NONE
        render()
    }

    private fun nextLang(): Int = (lang + 1) % 3

    private fun langLabel(l: Int): String = when (l) {
        0 -> "EN"
        1 -> "සිං"
        else -> "Sg"
    }

    // ---------- custom theme ----------
    private fun hex(c: Int): String = String.format("#%06X", c and 0xFFFFFF)

    private fun mix(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a) * (1f - t) + Color.red(b) * t).toInt()
        val g = (Color.green(a) * (1f - t) + Color.green(b) * t).toInt()
        val bl = (Color.blue(a) * (1f - t) + Color.blue(b) * t).toInt()
        return Color.rgb(r, g, bl)
    }

    private fun customThemeObj(): Theme {
        var c = customCache
        if (c == null) {
            val bg = Color.parseColor(customBgs[customBgIdx])
            val acc = Color.parseColor(customAccents[customAccIdx])
            val white = Color.WHITE
            c = Theme(
                "Custom", hex(bg), hex(mix(bg, white, 0.10f)), hex(mix(bg, white, 0.20f)),
                hex(acc), hex(mix(acc, bg, 0.45f)), hex(mix(bg, white, 0.32f)),
                "#FFFFFF", hex(mix(bg, white, 0.65f)), "#FFFFFF"
            )
            customCache = c
        }
        return c
    }

    private fun saveCustom() {
        customCache = null
        prefs.edit().putInt("cbg", customBgIdx).putInt("cacc", customAccIdx).apply()
        applySnow()
        render()
    }

    // ---------- rendering ----------
    private fun render() {
        root.removeAllViews()
        applyBackground()
        root.setBackgroundColor(if (bgOn) Color.TRANSPARENT else th.bg)
        root.addView(barHolder)
        refreshBar()
        when (panel) {
            Panel.CLIPBOARD -> renderClipboard()
            Panel.THEME -> renderTheme()
            Panel.SETTINGS -> renderSettings()
            Panel.EMOJI -> renderEmoji()
            Panel.FONT -> renderFont()
            Panel.NONE -> {
                root.addView(row(chars("1234567890")))
                if (lang == 1) renderSinhala() else renderEnglish()
            }
        }
    }

    /** Top bar: word suggestions while typing, tools otherwise. */
    private fun refreshBar() {
        if (!::barHolder.isInitialized) return
        barHolder.removeAllViews()
        val showSug = suggestions.isNotEmpty() && panel == Panel.NONE && !toolsPinned
        barHolder.addView(if (showSug) suggestionStrip() else toolbar())
    }

    // ----- toolbar (flat line icons, scrolls sideways) -----
    private fun toolbar(): View {
        val hsv = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun add(kind: IconView.Kind, active: Boolean = false, onClick: () -> Unit) {
            val b = iconButton(kind, active, onClick)
            b.layoutParams = LinearLayout.LayoutParams(dp(46), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(dp(1), dp(2), dp(1), dp(2))
            }
            bar.addView(b)
        }
        add(IconView.Kind.CLIPBOARD) { pasteNow() }
        add(IconView.Kind.HISTORY, panel == Panel.CLIPBOARD) { togglePanel(Panel.CLIPBOARD) }
        add(IconView.Kind.EMOJI, panel == Panel.EMOJI) { togglePanel(Panel.EMOJI) }
        add(IconView.Kind.MIC) { startVoice() }
        add(IconView.Kind.FONT, panel == Panel.FONT || fontStyle != 0) { togglePanel(Panel.FONT) }
        add(IconView.Kind.GLOBE) { setLang(nextLang()) }
        add(IconView.Kind.UNDO) { menuAction(android.R.id.undo) }
        add(IconView.Kind.REDO) { menuAction(android.R.id.redo) }
        add(IconView.Kind.LEFT) { moveCursor(-1) }
        add(IconView.Kind.RIGHT) { moveCursor(1) }
        add(IconView.Kind.SELECT_ALL) { menuAction(android.R.id.selectAll) }
        add(IconView.Kind.COPY) { menuAction(android.R.id.copy) }
        add(IconView.Kind.CUT) { menuAction(android.R.id.cut) }
        add(IconView.Kind.SNOW, snowMode != 0) { setSnow(if (snowMode != 0) 0 else lastSnow) }
        add(IconView.Kind.THEME, panel == Panel.THEME) { togglePanel(Panel.THEME) }
        add(IconView.Kind.SETTINGS, panel == Panel.SETTINGS) { togglePanel(Panel.SETTINGS) }
        hsv.addView(
            bar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        hsv.setOnScrollChangeListener { _, x, _, _, _ -> toolbarX = x }
        hsv.post { hsv.scrollTo(toolbarX, 0) }
        return hsv
    }

    private fun suggestionStrip(): View {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val tools = iconButton(IconView.Kind.GRID, false) {
            toolsPinned = true
            refreshBar()
        }
        tools.layoutParams = LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
        r.addView(tools)
        suggestions.forEachIndexed { i, w ->
            if (i > 0) {
                r.addView(View(this).apply {
                    setBackgroundColor(th.special)
                    layoutParams = LinearLayout.LayoutParams(dp(1), dp(22))
                })
            }
            r.addView(TextView(this).apply {
                text = w
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(th.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                if (i == 0) typeface = Typeface.DEFAULT_BOLD
                background = flatBg()
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
                setOnClickListener {
                    feedback(it)
                    pickSuggestion(w)
                }
            })
        }
        return r
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
            addState(intArrayOf(), shape(kc(color)))
        }
    }

    private fun playKey(fx: Int) {
        if (!soundOn) return
        try {
            (getSystemService(AUDIO_SERVICE) as AudioManager).playSoundEffect(fx, 0.6f)
        } catch (e: Exception) {
        }
    }

    private fun feedback(v: View, fx: Int = AudioManager.FX_KEYPRESS_STANDARD) {
        if (haptic) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        playKey(fx)
    }

    // ----- panels -----
    private fun backButton(): View {
        val b = iconButton(IconView.Kind.BACK, false) { panel = Panel.NONE; render() }
        b.layoutParams = LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        }
        return b
    }

    private fun panelHeader(title: String, actionKind: IconView.Kind? = null, action: (() -> Unit)? = null): LinearLayout {
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(keyH))
        }
        h.addView(backButton())
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
            val b = iconButton(actionKind, false, action)
            b.layoutParams = LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(dp(2), dp(3), dp(2), dp(3))
            }
            h.addView(b)
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

    // ----- emoji panel -----
    private fun emojiList(tab: Int): List<String> = if (tab == 0) recent else emojiCats[tab - 1].second

    private fun addRecent(e: String) {
        recent.remove(e)
        recent.add(0, e)
        while (recent.size > 24) recent.removeAt(recent.size - 1)
        prefs.edit().putString("recent", recent.joinToString(" ")).apply()
    }

    private fun renderEmoji() {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(keyH))
        }
        header.addView(backButton())
        val tabs = listOf("🕘") + emojiCats.map { it.first }
        tabs.forEachIndexed { i, label ->
            header.addView(TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                background = roundBg(if (i == emojiTab) th.accent else Color.TRANSPARENT, 12)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(dp(3), dp(5), dp(3), dp(5))
                }
                setOnClickListener {
                    feedback(it)
                    emojiTab = i
                    render()
                }
            })
        }
        root.addView(header)

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(8))
        }
        val list = emojiList(emojiTab)
        if (list.isEmpty()) {
            grid.addView(hintText("Recently used emojis will show here"))
        }
        list.chunked(8).forEach { items ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46))
            }
            items.forEach { e ->
                r.addView(TextView(this).apply {
                    text = e
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                    background = flatBg()
                    isClickable = true
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    setOnClickListener {
                        feedback(it)
                        type(e)
                        addRecent(e)
                    }
                })
            }
            repeat(8 - items.size) {
                r.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
            }
            grid.addView(r)
        }
        root.addView(panelBody(grid))
    }

    // ----- fonts panel (stylish text) -----
    private fun renderFont() {
        root.addView(panelHeader("Stylish text"))
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        Fonts.names.chunked(2).withIndex().forEach { (rowIdx, pair) ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            pair.forEachIndexed { j, name ->
                val idx = rowIdx * 2 + j
                val on = idx == fontStyle
                r.addView(TextView(this).apply {
                    text = Fonts.sample(idx) + "\n" + name
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(if (on) Color.WHITE else th.text)
                    background = roundBg(if (on) th.accent else th.key, 12)
                    setPadding(dp(6), dp(10), dp(6), dp(10))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    }
                    setOnClickListener {
                        feedback(it)
                        fontStyle = idx
                        prefs.edit().putInt("font", idx).apply()
                        render()
                    }
                })
            }
            list.addView(r)
        }
        list.addView(hintText("Applies to English typing (EN mode)"))
        root.addView(panelBody(list))
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
        val all = themes + customThemeObj()
        all.withIndex().toList().chunked(5).forEach { items ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            items.forEach { (i, t) -> r.addView(swatch(i, t)) }
            repeat(5 - items.size) {
                r.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
            }
            grid.addView(r)
        }
        if (themeIdx == themes.size) {
            grid.addView(settingLabel("Custom background"))
            grid.addView(paletteRow(customBgs, customBgIdx) { customBgIdx = it; saveCustom() })
            grid.addView(settingLabel("Custom accent"))
            grid.addView(paletteRow(customAccents, customAccIdx) { customAccIdx = it; saveCustom() })
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
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        }
        val label = TextView(this).apply {
            text = t.name
            setTextColor(th.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
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

    private fun paletteRow(colors: List<String>, selected: Int, onPick: (Int) -> Unit): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46))
        }
        colors.forEachIndexed { i, hexColor ->
            val sel = i == selected
            val holder = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener {
                    feedback(it)
                    onPick(i)
                }
            }
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(hexColor))
                    setStroke(dp(if (sel) 3 else 1), if (sel) th.accent else th.special)
                }
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
            }
            holder.addView(dot)
            r.addView(holder)
        }
        return r
    }

    // ----- settings panel -----
    private fun renderSettings() {
        root.addView(panelHeader("Settings"))
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(10))
        }
        box.addView(settingLabel("Effect"))
        box.addView(chipRow(effectNames.subList(0, 3), effectIdx, 0) { setEffect(it) })
        box.addView(chipRow(effectNames.subList(3, 6), effectIdx, 3) { setEffect(it) })
        box.addView(settingLabel("Effect area"))
        box.addView(chipRow(listOf("Off", "Keyboard", "Full screen"), snowMode, 0) { setSnow(it) })
        box.addView(settingLabel("Effect colour"))
        box.addView(colorRow())
        box.addView(settingLabel("Photo background"))
        box.addView(twoButtons("Choose photo", {
            val i = Intent(this, PickImageActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        }, "Remove photo", { removeBackground() }))
        box.addView(settingLabel("One-handed mode"))
        box.addView(chipRow(listOf("Off", "Left", "Right"), oneHand, 0) { setOneHand(it) })
        box.addView(settingLabel("Vibration"))
        box.addView(chipRow(listOf("On", "Off"), if (haptic) 0 else 1, 0) {
            haptic = it == 0
            prefs.edit().putBoolean("haptic", haptic).apply()
            render()
        })
        box.addView(settingLabel("Key sound (needs Touch sounds ON in phone settings)"))
        box.addView(chipRow(listOf("Off", "On"), if (soundOn) 1 else 0, 0) {
            soundOn = it == 1
            prefs.edit().putBoolean("sound", soundOn).apply()
            render()
        })
        box.addView(settingLabel("Key height"))
        box.addView(chipRow(listOf("Small", "Normal", "Large"), keyHIdx, 0) {
            keyHIdx = it
            prefs.edit().putInt("keyh", keyHIdx).apply()
            render()
        })
        box.addView(TextView(this).apply {
            text = "Open RIO BOARD app (text shortcuts)"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = roundBg(th.accent, 12)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                setMargins(dp(3), dp(14), dp(3), dp(4))
            }
            setOnClickListener {
                val i = Intent(this@RioBoardService, MainActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
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

    private fun twoButtons(l1: String, a1: () -> Unit, l2: String, a2: () -> Unit): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42))
        }
        val pairs = listOf(Pair(l1, a1), Pair(l2, a2))
        pairs.forEach { (label, action) ->
            r.addView(TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(th.text)
                background = roundBg(th.key, 12)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(dp(3), dp(2), dp(3), dp(2))
                }
                setOnClickListener {
                    feedback(it)
                    action()
                }
            })
        }
        return r
    }

    private fun colorRow(): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46))
        }
        snowPalette.forEachIndexed { i, name ->
            val sel = i == snowColorIdx
            val holder = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener {
                    feedback(it)
                    snowColorIdx = i
                    prefs.edit().putInt("snowcol", i).apply()
                    applySnow()
                    render()
                }
            }
            val dot = TextView(this).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                text = if (i == 0) "A" else ""
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    when (name) {
                        "auto" -> setColor(Color.parseColor("#DDDDDD"))
                        "rainbow" -> {
                            setGradientType(GradientDrawable.SWEEP_GRADIENT)
                            setColors(rainbow)
                        }
                        else -> setColor(Color.parseColor(name))
                    }
                    setStroke(dp(if (sel) 3 else 1), if (sel) th.accent else th.special)
                }
                setTextColor(Color.parseColor("#333333"))
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            }
            holder.addView(dot)
            r.addView(holder)
        }
        return r
    }

    private fun chipRow(options: List<String>, selected: Int, offset: Int, onSelect: (Int) -> Unit): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42))
        }
        options.forEachIndexed { i, label ->
            val on = (i + offset) == selected
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
                    onSelect(i + offset)
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
                    if (lang == 2) sgType(label) else type(Fonts.convert(label[0], fontStyle))
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
                    Key(langLabel(nextLang()), 1.3f, Style.SPECIAL, 14f) { setLang(nextLang()) },
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
                    Key("ABC", 1.3f, Style.SPECIAL, 14f) { setLang(0) },
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
        if (lang == 2) "RIO BOARD · Singlish" else "RIO BOARD", weight, Style.NORMAL, 12f, space = true,
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

        val fx = when {
            k.space -> AudioManager.FX_KEYPRESS_SPACEBAR
            k.label == "⌫" -> AudioManager.FX_KEYPRESS_DELETE
            k.label == "⏎" -> AudioManager.FX_KEYPRESS_RETURN
            else -> AudioManager.FX_KEYPRESS_STANDARD
        }

        if (k.space) {
            // tap = space, long press = keyboard picker, slide left/right = move cursor
            val stepPx = dp(16).toFloat()
            val pos = FloatArray(2)            // 0 = down x, 1 = last step x
            val flags = BooleanArray(2)        // 0 = moved, 1 = long press fired
            val longR = Runnable {
                if (!flags[0]) {
                    flags[1] = true
                    k.onLong?.invoke()
                }
            }
            tv.setOnTouchListener { view, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        pos[0] = ev.x
                        pos[1] = ev.x
                        flags[0] = false
                        flags[1] = false
                        view.isPressed = true
                        handler.postDelayed(longR, 550)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!flags[0] && Math.abs(ev.x - pos[0]) > stepPx) {
                            flags[0] = true
                            handler.removeCallbacks(longR)
                        }
                        if (flags[0]) {
                            while (ev.x - pos[1] >= stepPx) {
                                pos[1] = pos[1] + stepPx
                                moveCursor(1)
                            }
                            while (pos[1] - ev.x >= stepPx) {
                                pos[1] = pos[1] - stepPx
                                moveCursor(-1)
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        view.isPressed = false
                        handler.removeCallbacks(longR)
                        if (!flags[0] && !flags[1]) {
                            feedback(view, fx)
                            k.onPress()
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        handler.removeCallbacks(longR)
                    }
                }
                true
            }
        } else if (k.repeat) {
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
                        feedback(view, fx)
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
                feedback(it, fx)
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

    // ---------- Singlish composing ----------
    private fun sgReset() {
        sgBuf.setLength(0)
        sgShown = ""
    }

    private fun sgStillThere(ic: InputConnection): Boolean {
        if (sgShown.isEmpty()) return true
        val before = ic.getTextBeforeCursor(sgShown.length, 0)?.toString() ?: return false
        return before == sgShown
    }

    /** Replace what is shown for the current Singlish word with the conversion of [newRaw]. */
    private fun sgReplace(ic: InputConnection, newRaw: String) {
        val conv = Singlish.convert(newRaw)
        ic.beginBatchEdit()
        if (sgShown.isNotEmpty()) ic.deleteSurroundingText(sgShown.length, 0)
        if (conv.isNotEmpty()) ic.commitText(conv, 1)
        ic.endBatchEdit()
        sgBuf.setLength(0)
        sgBuf.append(newRaw)
        sgShown = conv
    }

    private fun sgType(ch: String) {
        val ic = currentInputConnection ?: return
        toolsPinned = false
        if (sgBuf.isNotEmpty() && !sgStillThere(ic)) sgReset()
        sgReplace(ic, sgBuf.toString() + ch)
        if (::snow.isInitialized) snow.burst()
    }

    // ---------- word suggestions ----------
    private fun isWordChar(c: Char): Boolean =
        c.isLetter() || c == '\'' || c == '\u200D' || c in '\u0D80'..'\u0DFF'

    private fun currentPrefix(): String {
        val before = currentInputConnection?.getTextBeforeCursor(24, 0)?.toString() ?: return ""
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        return before.substring(i)
    }

    private fun computeSuggestions(): List<String> {
        val prefix = currentPrefix()
        if (prefix.isEmpty()) return emptyList()
        val lower = prefix.lowercase()
        val out = LinkedHashSet<String>()
        learned.entries
            .filter { it.key.startsWith(lower) && it.key != lower }
            .sortedByDescending { it.value }
            .forEach { out.add(it.key) }
        for (w in dict) {
            if (out.size >= 6) break
            if (w.startsWith(lower) && w != lower) out.add(w)
        }
        val cap = prefix[0].isUpperCase()
        return out.take(3).map { w -> if (cap) w.replaceFirstChar { it.uppercase() } else w }
    }

    private fun updateSuggestions() {
        if (!::barHolder.isInitialized) return
        val list = computeSuggestions()
        if (list != suggestions) {
            suggestions = list
            if (panel == Panel.NONE) refreshBar()
        }
    }

    private fun pickSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val prefix = currentPrefix()
        ic.beginBatchEdit()
        ic.deleteSurroundingText(prefix.length, 0)
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
        sgReset()
        learn(word)
        suggestions = emptyList()
        refreshBar()
        if (::snow.isInitialized) snow.burst()
    }

    private fun learn(word: String) {
        val k = word.lowercase().trim()
        if (k.length < 2) return
        learned[k] = (learned[k] ?: 0) + 1
        if (learned.size > 600) {
            learned.entries.sortedBy { it.value }.take(100).map { it.key }.forEach { learned.remove(it) }
        }
        prefs.edit().putString("learned", JSONObject(learned.toMap()).toString()).apply()
    }

    private fun shortcutFor(word: String): String? {
        val o = Snips.load(prefs)
        val k = word.lowercase()
        return if (o.has(k)) o.optString(k, "") else null
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
        sgReset()
        currentInputConnection?.performContextMenuAction(id)
        if (id == android.R.id.copy || id == android.R.id.cut) {
            handler.postDelayed({ captureClip() }, 150)
        }
    }

    private fun moveCursor(dir: Int) {
        sgReset()
        sendDownUpKeyEvents(if (dir > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
    }

    // ---------- input actions ----------
    private fun type(s: String) {
        sgReset()
        toolsPinned = false
        val ic = currentInputConnection
        // a space/punctuation ends a word: remember it, and expand text shortcuts
        if (s.length == 1 && !isWordChar(s[0])) {
            val w = currentPrefix()
            if (w.length >= 3) learn(w)
            if (s == " " && w.isNotEmpty() && ic != null) {
                val ex = shortcutFor(w)
                if (!ex.isNullOrEmpty()) {
                    ic.beginBatchEdit()
                    ic.deleteSurroundingText(w.length, 0)
                    ic.commitText("$ex ", 1)
                    ic.endBatchEdit()
                    if (::snow.isInitialized) snow.burst()
                    return
                }
            }
        }
        ic?.commitText(s, 1)
        if (::snow.isInitialized) snow.burst()
    }

    private fun deleteOne() {
        val ic = currentInputConnection ?: return
        if (sgBuf.isNotEmpty()) {
            if (sgStillThere(ic)) {
                sgReplace(ic, sgBuf.substring(0, sgBuf.length - 1))
                return
            }
            sgReset()
        }
        val sel = ic.getSelectedText(0)
        if (!sel.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(2, 0)
        if (before.isNullOrEmpty()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        val n = if (before.length == 2 && Character.isHighSurrogate(before[0]) && Character.isLowSurrogate(before[1])) 2 else 1
        ic.deleteSurroundingText(n, 0)
    }

    private fun pressEnter() {
        sgReset()
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
