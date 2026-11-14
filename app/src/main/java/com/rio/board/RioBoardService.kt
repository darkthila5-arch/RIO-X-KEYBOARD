package com.rio.board

import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RioBoardService : InputMethodService() {

    companion object {
        /** Lets VoiceActivity / PickImageActivity talk to the running keyboard. */
        @Volatile
        var instance: RioBoardService? = null
    }

    private enum class Style { NORMAL, SPECIAL, ACCENT, ACTIVE }
    private enum class Panel { NONE, CLIPBOARD, THEME, SETTINGS, EMOJI, FONTS, SNIPS, BACKGROUND, BOOM, STATUS, MAKER, TOOLS }

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
        activeS: String, pressedS: String, textS: String, hintS: String, snowS: String,
        val live: LiveKind? = null
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
        Theme("Light", "#E8EAF0", "#FFFFFF", "#C9CEDB", "#3B82F6", "#7C6CE0", "#B8BFD3", "#111111", "#5B6382", "#8FB8FF"),
        // ---- live (animated) themes ----
        Theme("Aurora", "#07111F", "#99101C33", "#B01A2A48", "#4DFFB5", "#8E6BFF", "#CC2E4A78", "#FFFFFF", "#A8C7E8", "#CFFFEF", LiveKind.AURORA),
        Theme("Galaxy", "#0B0720", "#99181033", "#B0261A4D", "#B388FF", "#FF6EC7", "#CC4A3A8F", "#FFFFFF", "#C3B5F0", "#FFFFFF", LiveKind.GALAXY),
        Theme("Lava", "#1A0606", "#99360E0A", "#B0501810", "#FF7A18", "#FF3D71", "#CC8A3A20", "#FFFFFF", "#FFC9A8", "#FFE0C0", LiveKind.LAVA),
        Theme("Neon", "#070617", "#990B0A2A", "#B0161448", "#00E5FF", "#FF2EE6", "#CC3A38A8", "#FFFFFF", "#A8B8FF", "#CFF8FF", LiveKind.NEON),
        Theme("Waves", "#04202E", "#990A3446", "#B0104A63", "#26E0D0", "#4DA3FF", "#CC2A7A99", "#FFFFFF", "#9AD7EA", "#E6FBFF", LiveKind.WAVES),
        Theme("Matrix", "#020A04", "#99061A0C", "#B0092A12", "#00FF66", "#B6FF3B", "#CC127A33", "#D6FFE0", "#6BE08A", "#7CFFB2", LiveKind.MATRIX),
        Theme("RGB", "#101018", "#99181822", "#B0242434", "#FF4D8D", "#4DA3FF", "#CC4A4A66", "#FFFFFF", "#C8C8E0", "#FFFFFF", LiveKind.RGB)
    )

    // snow colours: auto (theme), then fixed colours, last = rainbow
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
        "😀" to ("😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 🤑 🤠 😈 👿 👹 👺 🤡 💩 👻 💀 ☠️ 👽 👾 🤖 🎃 😺 😸 😹 😻 😼 😽 🙀 😿 😾 🙈 🙉 🙊").split(" "),
        "👋" to ("👍 👎 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 🤝 👏 🙌 👐 🤲 🙏 ✍️ 💅 🤳 💪 👀 👁️ 👅 👄 💋 🧠 👂 👃 👣 👶 👦 👧 👨 👩 👴 👵 👱 👮 💂 🕵️ 👷 🤴 👸 🤵 👰 🤰 🎅 🤶 🧙 🧚 🧛 🧜 🧝 🧞 🧟 🙍 🙎 🙅 🙆 💁 🙋 🤦 🤷 🙇 💆 💇 🚶 🏃 💃 🕺 👯 🧘").split(" "),
        "❤️" to ("❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ☮️ ✝️ ☪️ 🕉️ ☸️ ✡️ ☯️ 🔥 ✨ ⭐ 🌟 💫 💥 💢 💦 💨 💤 💯 ✅ ❎ ❌ ❓ ❔ ❕ ❗ ‼️ ⁉️ ⚠️ 🚫 ⛔ ♻️ ➕ ➖ ✖️ ➗ ♾️ 💲 ™️ ©️ ®️ 🔝 🔙 🔜 🔛 🔚 🆗 🆒 🆕 🆓 🆙 🔴 🔵 ⚫ ⚪ 🔶 🔷 🔸 🔹 ▶️ ⏸️ ⏹️ ⏩ ⏪ 🔀 🔁 🔄 ➡️ ⬅️ ⬆️ ⬇️ ↗️ ↘️ ↙️ ↖️ ↔️ ↕️ 🔔 🔕 📣 📢 💬 💭 🗯️ ♠️ ♥️ ♦️ ♣️ 🃏 🎴").split(" "),
        "🐶" to ("🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐽 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🕷️ 🦂 🐢 🐍 🦎 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦏 🐪 🐫 🦒 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🐐 🦌 🐕 🐩 🐈 🐓 🦃 🕊️ 🐇 🐁 🐀 🐿️ 🦔 🐾 🐉 🌵 🎄 🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🎍 🎋 🍃 🍂 🍁 🍄 🌾 💐 🌷 🌹 🥀 🌺 🌸 🌼 🌻 🌞 🌝 🌛 🌜 🌚 🌕 🌖 🌗 🌘 🌑 🌒 🌓 🌔 🌙 🌎 🌍 🌏 ☄️ 🌪️ 🌈 ☀️ 🌤️ ⛅ 🌥️ ☁️ 🌦️ 🌧️ ⛈️ 🌩️ 🌨️ ❄️ ☃️ ⛄ 🌬️ 💧 ☔ 🌊").split(" "),
        "🍔" to ("🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍈 🍒 🍑 🍍 🥭 🥥 🥝 🍅 🍆 🥑 🥦 🥒 🌶️ 🌽 🥕 🥔 🍠 🥐 🍞 🥖 🥨 🧀 🥚 🍳 🥞 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🥘 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍘 🍥 🥠 🍢 🍡 🍧 🍨 🍦 🥧 🍰 🎂 🧁 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🌰 🥜 🍯 🥛 🍼 ☕ 🍵 🥤 🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🍾 🥄 🍴 🍽️ 🥢").split(" "),
        "🚀" to ("⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥅 🏒 🏑 🏏 ⛳ 🏹 🎣 🥊 🥋 ⛸️ 🎿 🏂 🏋️ 🤸 🤺 🤾 🏌️ 🏇 🏊 🚴 🏆 🥇 🥈 🥉 🏅 🎖️ 🎗️ 🎫 🎟️ 🎪 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🎲 🎯 🎳 🎮 🎰 🧩 🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🚚 🚛 🚜 🏍️ 🚲 🛴 🚨 🚔 🚍 🚘 🚖 🚡 🚠 🚟 🚃 🚋 🚞 🚝 🚄 🚅 🚈 🚂 🚆 🚇 🚊 🚉 ✈️ 🛫 🛬 🚁 🚀 🛸 🛰️ 🚢 ⛵ 🚤 🛥️ ⚓ 🗺️ 🗽 🗼 🏰 🏯 🏟️ 🎡 🎢 🎠 ⛲ ⛱️ 🏖️ 🏝️ 🏜️ 🌋 ⛰️ 🏔️ 🏕️ 🏠 🏡 🏢 🏥 🏦 🏨 🏪 🏫 ⛪ 🕌 🕍 ⛩️ 🌃 🌆 🌇 🌉").split(" "),
        "💡" to ("⌚ 📱 📲 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💽 💾 💿 📀 📷 📸 📹 🎥 📽️ 📞 ☎️ 📟 📠 📺 📻 🎙️ ⏰ ⏱️ ⏲️ 🕰️ ⌛ ⏳ 📡 🔋 🔌 💡 🔦 🕯️ 🗑️ 💸 💵 💴 💶 💷 💰 💳 💎 ⚖️ 🔧 🔨 ⚒️ 🛠️ ⛏️ 🔩 ⚙️ 🔫 💣 🔪 🗡️ 🛡️ 🚬 ⚰️ 🔮 📿 🔭 🔬 💊 💉 🌡️ 🚽 🚿 🛁 🔑 🗝️ 🚪 🛋️ 🛏️ 🧸 🖼️ 🛍️ 🛒 🎁 🎈 🎏 🎀 🎊 🎉 🎎 🏮 ✉️ 📩 📨 📧 📦 📫 📮 📝 📁 📂 📅 📆 📈 📉 📊 📋 📌 📍 📎 🖇️ 📏 📐 ✂️ 🗃️ 🗄️ 🔒 🔓 🔏 🔐 📚 📖 📕 📗 📘 📙 📓 📔 📒 📃 📜 📄 📰 🔖 🏷️ ✏️ ✒️ 🖊️ 🖋️ 🖌️ 🖍️ 🔍 🔎").split(" "),
        "🚩" to ("🇱🇰 🇮🇳 🇵🇰 🇧🇩 🇳🇵 🇲🇻 🇧🇹 🇦🇫 🇨🇳 🇯🇵 🇰🇷 🇹🇭 🇸🇬 🇲🇾 🇮🇩 🇵🇭 🇻🇳 🇦🇪 🇸🇦 🇶🇦 🇰🇼 🇴🇲 🇧🇭 🇮🇷 🇮🇶 🇹🇷 🇮🇱 🇪🇬 🇿🇦 🇳🇬 🇰🇪 🇬🇧 🇮🇪 🇫🇷 🇩🇪 🇮🇹 🇪🇸 🇵🇹 🇳🇱 🇧🇪 🇨🇭 🇦🇹 🇸🇪 🇳🇴 🇩🇰 🇫🇮 🇵🇱 🇷🇺 🇺🇦 🇬🇷 🇺🇸 🇨🇦 🇲🇽 🇧🇷 🇦🇷 🇨🇴 🇨🇱 🇦🇺 🇳🇿 🇪🇺 🇺🇳 🏳️ 🏴 🏁 🚩 🏳️‍🌈").split(" ")
    )
    private var emojiCat = 0

    // ---------- state ----------
    private var siMode = false        // false = English, true = Sinhala
    private var shift = 0             // 0 off, 1 once, 2 caps lock
    private var symbols = false
    private var siLayer = 0           // 0 consonants, 1 vowels, 2 pili/signs
    private var lastShiftTap = 0L
    private var themeIdx = 0
    private var snowMode = 2          // 0 off, 1 keyboard area, 2 full screen
    private var lastSnow = 2
    private var snowColorIdx = 0
    private var effectIdx = 0         // 0 snow, 1 drops, 2 leaves, 3 flowers, 4 smoke, 5 fire, 6 hearts, 7 rain, 8 fireflies, 9 bubbles, 10 stars
    private var haptic = true
    private val boomCounts = intArrayOf(5, 10, 20, 50)
    private val boomDelays = longArrayOf(300L, 600L, 1000L)
    private var boomCountIdx = 1
    private var boomDelayIdx = 1
    private var boomRunning = false
    private var statusItems: List<StatusSaver.Item>? = null
    private var statusLoading = false
    private var statusError = false
    private val statusThumbs = HashMap<String, Bitmap>()
    private val statusSaved = HashSet<String>()
    private var boomLeft = 0
    private var boomTotal = 0
    private var boomMsg = ""
    private var boomStatusText = ""
    private var boomStatus: TextView? = null
    private val boomStep = Runnable { boomTick() }
    private var keyPreview = true     // iPhone-style key pop-up bubble
    private var previewPopup: android.widget.PopupWindow? = null
    private var previewTv: TextView? = null
    private var keyHIdx = 1
    private var panel = Panel.NONE
    private var toolbarX = 0
    private var clearArmed = false
    private var toolsPinned = false
    private var suggestions: List<String> = emptyList()
    private val clips = ArrayList<String>()
    private val learned = HashMap<String, Int>()
    private var singlishOn = false     // English keys -> Sinhala letters
    private val sbuf = StringBuilder() // raw Singlish letters being composed
    private var lastComposeMs = 0L
    private var fontStyle = 0          // 0 = normal, see Fonts.names
    private var rioStyle = -1          // -1 = off, else index into rioList
    private var rioList: List<String> = emptyList()
    private var snips = JSONObject()
    private var bgOn = false
    private var bgBitmap: Bitmap? = null
    private var pendingVoice: String? = null
    private var pendingVoiceAt = 0L

    private val keyHs = intArrayOf(44, 50, 56)
    private val keyH: Int get() = keyHs[keyHIdx]

    private lateinit var prefs: SharedPreferences
    private lateinit var cm: ClipboardManager
    private lateinit var frame: FrameLayout
    private lateinit var root: LinearLayout
    private lateinit var barHolder: LinearLayout
    private lateinit var snow: EffectView
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
        snowColorIdx = prefs.getInt("snowcol", 0).coerceIn(0, snowPalette.size - 1)
        effectIdx = prefs.getInt("effect", 0).coerceIn(0, EffectView.Type.values().size - 1)
        loadPinned()
        haptic = prefs.getBoolean("haptic", true)
        keyPreview = prefs.getBoolean("preview", true)
        boomCountIdx = prefs.getInt("boom_count", 1).coerceIn(0, boomCounts.size - 1)
        boomDelayIdx = prefs.getInt("boom_delay", 1).coerceIn(0, boomDelays.size - 1)
        keyHIdx = prefs.getInt("keyh", 1).coerceIn(0, 2)
        singlishOn = prefs.getBoolean("singlish", false)
        snips = Snips.load(prefs)
        loadRioStyles()
        loadBackground()
        instance = this
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
        cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        boomRunning = false
        handler.removeCallbacks(boomStep)
        hidePreview()
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
        snow = EffectView(this)
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
        if (boomRunning && restarting != true) {
            boomRunning = false
            handler.removeCallbacks(boomStep)
            boomStatusText = "Stopped"
        }
        toolsPinned = false
        sbuf.clear()
        snips = Snips.load(prefs)
        loadRioStyles()
        captureClip()
        flushPendingVoice()
        suggestions = computeSuggestions()
        render()
        applySnow()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // the user moved the cursor away from the Singlish word -> stop composing it
        if (sbuf.isNotEmpty()) {
            val moved = if (candidatesStart >= 0) {
                newSelStart != candidatesEnd || newSelEnd != candidatesEnd
            } else {
                SystemClock.uptimeMillis() - lastComposeMs > 400
            }
            if (moved) {
                sbuf.clear()
                currentInputConnection?.finishComposingText()
            }
        }
        updateSuggestions()
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
    private fun sysDimen(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun applySnow() {
        if (!::snow.isInitialized || !::frame.isInitialized) return
        snow.colors = when (snowColorIdx) {
            0 -> intArrayOf(th.snow)
            snowPalette.size - 1 -> rainbow
            else -> intArrayOf(Color.parseColor(snowPalette[snowColorIdx]))
        }
        snow.type = EffectView.Type.values()[effectIdx]
        snow.natural = snowColorIdx == 0 && effectIdx != 0
        snow.base = if (snowMode == 2) 75 else 40
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
        hidePreview()
        root.removeAllViews()
        applyRootBg()
        root.addView(barHolder)
        refreshBar()
        when (panel) {
            Panel.CLIPBOARD -> renderClipboard()
            Panel.THEME -> renderTheme()
            Panel.SETTINGS -> renderSettings()
            Panel.EMOJI -> renderEmoji()
            Panel.FONTS -> renderFonts()
            Panel.SNIPS -> renderSnips()
            Panel.BACKGROUND -> renderBackgrounds()
            Panel.BOOM -> renderBoom()
            Panel.STATUS -> renderStatus()
            Panel.MAKER -> renderMaker()
            Panel.TOOLS -> renderTools()
            Panel.NONE -> {
                root.addView(row(chars("1234567890")))
                if (siMode) renderSinhala() else renderEnglish()
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

    // =====================================================================
    //  Top bar (6 pinned tools + expand button) and the "all tools" grid.
    //  Hold a tool in the grid and drop it on a top-bar slot to pin it.
    // =====================================================================
    private class Tool(
        val id: String, val label: String, val group: Int,
        val kind: IconView.Kind?, val text: String?, val textSp: Float,
        val active: () -> Boolean, val action: () -> Unit
    )

    private val toolGroups = listOf("Input", "Text", "Edit", "Cursor", "Look & extras")
    private val defaultPinned = listOf("singlish", "emoji", "sticker", "clip", "fonts", "voice")
    private var pinned = ArrayList<String>(defaultPinned)

    private val toolList: List<Tool> by lazy {
        fun ic(id: String, label: String, g: Int, k: IconView.Kind, active: () -> Boolean = { false }, a: () -> Unit) =
            Tool(id, label, g, k, null, 0f, active, a)
        fun tx(id: String, label: String, g: Int, t: String, sp: Float, active: () -> Boolean = { false }, a: () -> Unit) =
            Tool(id, label, g, null, t, sp, active, a)
        listOf(
            // 0 input
            ic("paste", "Paste", 0, IconView.Kind.CLIPBOARD) { pasteNow() },
            ic("clip", "Clipboard", 0, IconView.Kind.HISTORY, { panel == Panel.CLIPBOARD }) { togglePanel(Panel.CLIPBOARD) },
            ic("emoji", "Emoji", 0, IconView.Kind.EMOJI, { panel == Panel.EMOJI }) { togglePanel(Panel.EMOJI) },
            ic("sticker", "Stickers & GIFs", 0, IconView.Kind.STICKER, { panel == Panel.MAKER }) { togglePanel(Panel.MAKER) },
            ic("lang", "Language", 0, IconView.Kind.GLOBE) {
                flushSinglish(); siMode = !siMode; symbols = false; panel = Panel.NONE; render()
            },
            tx("singlish", "Singlish", 0, "A→අ", 13f, { singlishOn }) { setSinglish(!singlishOn) },
            ic("voice", "Voice typing", 0, IconView.Kind.MIC) { startVoice() },
            // 1 text
            tx("fonts", "Fonts", 1, "Aa", 16f, { fontStyle != 0 || rioStyle >= 0 || panel == Panel.FONTS }) { togglePanel(Panel.FONTS) },
            ic("snips", "Shortcuts", 1, IconView.Kind.BOLT, { panel == Panel.SNIPS }) { togglePanel(Panel.SNIPS) },
            tx("case", "Change case", 1, "a⇄A", 13f) { changeCase() },
            ic("date", "Date & time", 1, IconView.Kind.CALENDAR) { insertDateTime() },
            // 2 edit
            ic("selall", "Select all", 2, IconView.Kind.SELECT_ALL) { menuAction(android.R.id.selectAll) },
            ic("copy", "Copy", 2, IconView.Kind.COPY) { menuAction(android.R.id.copy) },
            ic("cut", "Cut", 2, IconView.Kind.CUT) { menuAction(android.R.id.cut) },
            ic("undo", "Undo", 2, IconView.Kind.UNDO) { menuAction(android.R.id.undo) },
            ic("redo", "Redo", 2, IconView.Kind.REDO) { menuAction(android.R.id.redo) },
            ic("clear", "Clear all", 2, IconView.Kind.TRASH, { clearArmed }) { clearAllText() },
            // 3 cursor
            ic("home", "Home", 3, IconView.Kind.HOME) { sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_HOME) },
            ic("left", "Left", 3, IconView.Kind.LEFT) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT) },
            ic("up", "Up", 3, IconView.Kind.UP) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP) },
            ic("down", "Down", 3, IconView.Kind.DOWN) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN) },
            ic("right", "Right", 3, IconView.Kind.RIGHT) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT) },
            ic("end", "End", 3, IconView.Kind.END) { sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_END) },
            // 4 look & extras
            ic("bg", "Background", 4, IconView.Kind.IMAGE, { panel == Panel.BACKGROUND || bgOn }) { togglePanel(Panel.BACKGROUND) },
            ic("effects", "Effects", 4, IconView.Kind.SNOW, { snowMode != 0 }) { setSnow(if (snowMode != 0) 0 else lastSnow) },
            ic("theme", "Theme", 4, IconView.Kind.THEME, { panel == Panel.THEME }) { togglePanel(Panel.THEME) },
            ic("boom", "Boom", 4, IconView.Kind.BOMB, { panel == Panel.BOOM || boomRunning }) { togglePanel(Panel.BOOM) },
            ic("status", "Statuses", 4, IconView.Kind.STATUS, { panel == Panel.STATUS }) { statusItems = null; togglePanel(Panel.STATUS) },
            ic("settings", "Settings", 4, IconView.Kind.SETTINGS, { panel == Panel.SETTINGS }) { togglePanel(Panel.SETTINGS) }
        )
    }

    private fun toolById(id: String): Tool? = toolList.firstOrNull { it.id == id }

    private fun loadPinned() {
        val raw = prefs.getString("pinned", null)?.split(",")
            ?.filter { toolById(it) != null }?.distinct()?.toMutableList() ?: mutableListOf()
        for (d in defaultPinned) if (raw.size < 6 && !raw.contains(d)) raw.add(d)
        pinned = ArrayList(raw.take(6))
    }

    private fun savePinned() {
        prefs.edit().putString("pinned", pinned.joinToString(",")).apply()
    }

    private fun ovalBg(color: Int) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(color)
    }

    /** Round tool button (icon or text) like the Gboard top bar. */
    private fun toolCircle(t: Tool, sizeDp: Int, active: Boolean): View {
        val fl = FrameLayout(this)
        fl.background = ovalBg(th.key)
        if (t.kind != null) {
            val iv = IconView(this, t.kind)
            iv.tint = if (active) th.accent else th.text
            val sz = dp((sizeDp * 0.56f).toInt())
            fl.addView(iv, FrameLayout.LayoutParams(sz, sz, Gravity.CENTER))
        } else {
            fl.addView(TextView(this).apply {
                text = t.text
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, t.textSp * (0.6f + sizeDp / 95f))
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (active) th.accent else th.text)
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            ))
        }
        return fl
    }

    /** Closes the all-tools grid (if open) and runs the tool. */
    private fun runTool(t: Tool) {
        if (panel == Panel.TOOLS) {
            panel = Panel.NONE
            render()
        }
        t.action()
    }

    private fun startToolDrag(v: View, id: String, fromSlot: Int): Boolean {
        return try {
            val clip = android.content.ClipData.newPlainText("tool", id)
            v.startDragAndDrop(clip, View.DragShadowBuilder(v), fromSlot, 0)
        } catch (e: Exception) {
            false
        }
    }

    private fun dropOnSlot(slot: Int, id: String) {
        if (toolById(id) == null || slot !in pinned.indices) return
        val j = pinned.indexOf(id)
        if (j >= 0 && j != slot) pinned[j] = pinned[slot]   // already pinned elsewhere -> swap
        pinned[slot] = id
        savePinned()
        render()
    }

    private fun toolbar(): View {
        val editing = panel == Panel.TOOLS
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(dp(4), 0, dp(4), 0)
        }
        val circle = 38
        pinned.forEachIndexed { i, id ->
            val t = toolById(id) ?: return@forEachIndexed
            val slot = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                isClickable = true
                setOnClickListener {
                    feedback(it)
                    runTool(t)
                }
            }
            slot.addView(toolCircle(t, circle, t.active()), FrameLayout.LayoutParams(dp(circle), dp(circle), Gravity.CENTER))
            if (editing) {
                slot.setOnLongClickListener { startToolDrag(slot, t.id, i) }
                slot.setOnDragListener { v, ev ->
                    when (ev.action) {
                        android.view.DragEvent.ACTION_DRAG_STARTED -> true
                        android.view.DragEvent.ACTION_DRAG_ENTERED -> { v.alpha = 0.4f; true }
                        android.view.DragEvent.ACTION_DRAG_EXITED, android.view.DragEvent.ACTION_DRAG_ENDED -> { v.alpha = 1f; true }
                        android.view.DragEvent.ACTION_DROP -> {
                            v.alpha = 1f
                            val dropped = ev.clipData?.getItemAt(0)?.text?.toString()
                            if (dropped != null) dropOnSlot(i, dropped)
                            true
                        }
                        else -> false
                    }
                }
            }
            bar.addView(slot)
        }

        // expand / close button
        val toggle = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            isClickable = true
            setOnClickListener {
                feedback(it)
                flushSinglish()
                panel = if (panel == Panel.TOOLS) Panel.NONE else Panel.TOOLS
                render()
            }
        }
        val tc = FrameLayout(this)
        tc.background = ovalBg(th.key)
        val iv = IconView(this, if (editing) IconView.Kind.CLOSE else IconView.Kind.DOWN)
        iv.tint = th.text
        val sz = dp((circle * 0.56f).toInt())
        tc.addView(iv, FrameLayout.LayoutParams(sz, sz, Gravity.CENTER))
        toggle.addView(tc, FrameLayout.LayoutParams(dp(circle), dp(circle), Gravity.CENTER))
        bar.addView(toggle)
        return bar
    }

    private fun toolCell(t: Tool): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(2), dp(4), dp(2), dp(4))
            }
            isClickable = true
            setOnClickListener {
                feedback(it)
                runTool(t)
            }
            setOnLongClickListener { startToolDrag(it, t.id, -1) }
        }
        col.addView(toolCircle(t, 54, t.active()), LinearLayout.LayoutParams(dp(54), dp(54)))
        col.addView(TextView(this).apply {
            text = t.label
            setTextColor(th.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, dp(4), 0, 0)
        })
        return col
    }

    private fun renderTools() {
        root.addView(TextView(this).apply {
            text = "Hold and drag to customize top bar"
            setTextColor(th.hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
        })
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), 0, dp(6), dp(12))
        }
        toolGroups.forEachIndexed { g, name ->
            box.addView(TextView(this).apply {
                text = name
                setTextColor(th.hint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(8), dp(10), dp(8), dp(2))
            })
            toolList.filter { it.group == g }.chunked(4).forEach { rowTools ->
                val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                rowTools.forEach { r.addView(toolCell(it)) }
                repeat(4 - rowTools.size) {
                    r.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
                }
                box.addView(r)
            }
        }
        box.addView(TextView(this).apply {
            text = "Reset top bar"
            gravity = Gravity.CENTER
            setTextColor(th.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(8), dp(14), dp(8), dp(6))
            isClickable = true
            setOnClickListener {
                pinned = ArrayList(defaultPinned)
                savePinned()
                render()
            }
        })
        root.addView(panelBody(box))
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
        flushSinglish()
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
            setColor(kc(c))
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
    private fun renderEmoji() {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(keyH))
        }
        header.addView(backButton())
        emojiCats.forEachIndexed { i, cat ->
            header.addView(TextView(this).apply {
                text = cat.first
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                background = roundBg(if (i == emojiCat) th.accent else Color.TRANSPARENT, 12)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(dp(3), dp(5), dp(3), dp(5))
                }
                setOnClickListener {
                    feedback(it)
                    emojiCat = i
                    render()
                }
            })
        }
        root.addView(header)

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(8))
        }
        emojiCats[emojiCat].second.chunked(8).forEach { items ->
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

    // ----- background panel (pick a wallpaper without leaving the keyboard) -----
    private fun renderBackgrounds() {
        root.addView(panelHeader("Backgrounds", IconView.Kind.TRASH) { removeBackground() })
        val current = if (bgOn) prefs.getString("bgname", "") ?: "" else ""
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        fun tile(selected: Boolean, content: View, onClick: () -> Unit): View {
            return FrameLayout(this).apply {
                setPadding(dp(2), dp(2), dp(2), dp(2))
                background = roundBg(if (selected) th.accent else Color.TRANSPARENT, 12)
                layoutParams = LinearLayout.LayoutParams(0, dp(72), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
                isClickable = true
                addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                setOnClickListener {
                    feedback(it)
                    onClick()
                }
            }
        }
        val tiles = ArrayList<View>()
        tiles.add(tile(false, TextView(this).apply {
            text = "\uD83D\uDCC1\nGallery"
            gravity = Gravity.CENTER
            setTextColor(th.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = roundBg(th.special, 10)
        }) {
            try {
                startActivity(Intent(this@RioBoardService, PickImageActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
            }
        })
        DefaultBackgrounds.items.forEach { (_, file) ->
            val iv = android.widget.ImageView(this).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(th.key)
                DefaultBackgrounds.thumb(this@RioBoardService, file)?.let { setImageBitmap(it) }
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
                    }
                }
            }
            tiles.add(tile(file == current, iv) { DefaultBackgrounds.setAsBackground(this, file) })
        }
        tiles.chunked(3).forEach { rowTiles ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowTiles.forEach { r.addView(it) }
            repeat(3 - rowTiles.size) {
                r.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
            }
            grid.addView(r)
        }
        root.addView(panelBody(grid))
    }

    // ----- message boom: repeat the message that is in the text box -----
    private fun boomSetStatus(t: String) {
        boomStatusText = t
        boomStatus?.text = t
    }

    private fun boomStart() {
        flushSinglish()
        val ic = currentInputConnection ?: return
        var msg = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)?.text?.toString() ?: ""
        if (msg.isBlank()) {
            msg = (ic.getTextBeforeCursor(500, 0)?.toString() ?: "") + (ic.getTextAfterCursor(500, 0)?.toString() ?: "")
        }
        if (msg.isBlank()) {
            boomSetStatus("Type a message in the chat box first")
            return
        }
        boomMsg = msg
        boomTotal = boomCounts[boomCountIdx]
        boomLeft = boomTotal
        boomRunning = true
        boomSetStatus("Sending...")
        handler.post(boomStep)
        render()
    }

    private fun boomTick() {
        if (!boomRunning) return
        if (boomLeft <= 0) {
            boomStop("Done: $boomTotal sent")
            return
        }
        val ic = currentInputConnection
        if (ic == null) {
            boomStop("Stopped")
            return
        }
        val cur = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)?.text?.toString() ?: ""
        if (cur.isEmpty()) ic.commitText(boomMsg, 1)
        pressEnter()
        boomLeft--
        boomSetStatus("Sent ${boomTotal - boomLeft}/$boomTotal")
        handler.postDelayed(boomStep, boomDelays[boomDelayIdx])
    }

    private fun boomStop(msg: String) {
        boomRunning = false
        handler.removeCallbacks(boomStep)
        boomStatusText = msg
        if (::root.isInitialized && panel == Panel.BOOM) render() else boomStatus?.text = msg
    }

    private fun renderBoom() {
        root.addView(panelHeader("Message boom"))
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(10))
        }
        box.addView(hintText("Type your message in the chat box, pick how many times, then press Start. Needs Enter to send in that app."))
        box.addView(settingLabel("How many times"))
        box.addView(chipRow(boomCounts.map { "${it}x" }, boomCountIdx) {
            boomCountIdx = it
            prefs.edit().putInt("boom_count", it).apply()
            render()
        })
        box.addView(settingLabel("Speed"))
        box.addView(chipRow(listOf("Fast", "Normal", "Slow"), boomDelayIdx) {
            boomDelayIdx = it
            prefs.edit().putInt("boom_delay", it).apply()
            render()
        })
        box.addView(TextView(this).apply {
            text = if (boomRunning) "Stop" else "Start"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            background = roundBg(if (boomRunning) Color.parseColor("#E5484D") else th.accent, 12)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply {
                setMargins(dp(3), dp(12), dp(3), dp(4))
            }
            isClickable = true
            setOnClickListener {
                feedback(it)
                if (boomRunning) boomStop("Stopped") else boomStart()
            }
        })
        boomStatus = TextView(this).apply {
            text = boomStatusText
            gravity = Gravity.CENTER
            setTextColor(th.hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(6), 0, dp(4))
        }
        box.addView(boomStatus)
        root.addView(panelBody(box))
    }

    // ----- WhatsApp status saver panel -----
    private fun startStatusLoad(tree: android.net.Uri) {
        if (statusLoading) return
        statusLoading = true
        statusError = false
        Thread {
            var items: List<StatusSaver.Item> = emptyList()
            var failed = false
            val thumbs = HashMap<String, Bitmap>()
            try {
                items = StatusSaver.list(this, tree).take(30)
                items.forEach { it2 ->
                    StatusSaver.thumb(this, it2)?.let { b -> thumbs[it2.uri.toString()] = b }
                }
            } catch (e: Exception) {
                failed = true
            }
            handler.post {
                statusThumbs.clear()
                statusThumbs.putAll(thumbs)
                statusItems = items
                statusError = failed
                statusLoading = false
                if (::root.isInitialized && panel == Panel.STATUS) render()
            }
        }.start()
    }

    private fun saveStatus(item: StatusSaver.Item) {
        Thread {
            val ok = StatusSaver.saveToGallery(this, item)
            handler.post {
                if (ok) statusSaved.add(item.uri.toString())
                android.widget.Toast.makeText(
                    this,
                    if (ok) "Saved to gallery" else "Could not save (storage permission?)",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                if (::root.isInitialized && panel == Panel.STATUS) render()
            }
        }.start()
    }

    private fun renderStatus() {
        root.addView(panelHeader("WhatsApp statuses", IconView.Kind.HISTORY) {
            statusItems = null
            render()
        })
        val tree = StatusSaver.treeUri(prefs)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(10))
        }
        if (tree == null) {
            box.addView(hintText("Open the RIO BOARD app and allow the WhatsApp statuses folder once. Then watch a status in WhatsApp and come back here."))
            box.addView(TextView(this).apply {
                text = "Open RIO BOARD app"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                background = roundBg(th.accent, 12)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                    setMargins(dp(3), dp(10), dp(3), dp(4))
                }
                isClickable = true
                setOnClickListener { openApp() }
            })
            root.addView(panelBody(box))
            return
        }
        val items = statusItems
        if (items == null) {
            startStatusLoad(tree)
            box.addView(hintText("Loading statuses..."))
            root.addView(panelBody(box))
            return
        }
        if (items.isEmpty()) {
            box.addView(
                hintText(
                    if (statusError) "Could not read the folder. Allow it again in the RIO BOARD app."
                    else "No statuses yet. Watch a status in WhatsApp, then press refresh."
                )
            )
            root.addView(panelBody(box))
            return
        }
        box.addView(hintText("Tap a status to save it to your gallery."))
        fun tile(item: StatusSaver.Item): View {
            val key = item.uri.toString()
            return FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(96), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
                isClickable = true
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
                    }
                }
                setBackgroundColor(th.key)
                val iv = android.widget.ImageView(this@RioBoardService).apply {
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    statusThumbs[key]?.let { setImageBitmap(it) }
                }
                addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                if (item.isVideo) {
                    addView(TextView(this@RioBoardService).apply {
                        text = "\u25B6"
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setShadowLayer(4f, 0f, 0f, Color.BLACK)
                        setPadding(dp(6), 0, 0, dp(4))
                    }, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START
                    ))
                }
                if (statusSaved.contains(key)) {
                    addView(TextView(this@RioBoardService).apply {
                        text = "\u2713 Saved"
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        typeface = Typeface.DEFAULT_BOLD
                        setBackgroundColor(Color.parseColor("#99000000"))
                    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
                setOnClickListener {
                    feedback(it)
                    saveStatus(item)
                }
            }
        }
        items.chunked(3).forEach { rowItems ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { r.addView(tile(it)) }
            repeat(3 - rowItems.size) {
                r.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
            }
            box.addView(r)
        }
        root.addView(panelBody(box))
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
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        fun section(title: String, items: List<IndexedValue<Theme>>) {
            grid.addView(TextView(this).apply {
                text = title
                setTextColor(th.hint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(8), dp(8), dp(8), 0)
            })
            items.chunked(4).forEach { rowItems ->
                val r = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                rowItems.forEach { (i, t) -> r.addView(swatch(i, t)) }
                repeat(4 - rowItems.size) {
                    r.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
                }
                grid.addView(r)
            }
        }
        val all = themes.withIndex().toList()
        section("Classic", all.filter { it.value.live == null })
        section("Live themes (animated)", all.filter { it.value.live != null })
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
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (t.live == null) t.key else Color.TRANSPARENT)
            setStroke(dp(if (selected) 4 else 2), if (selected) t.accent else t.special)
        }
        val circle: View = if (t.live == null) {
            View(this).apply {
                background = ring
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
            }
        } else {
            // small animated preview of the live theme inside a ring
            FrameLayout(this).apply {
                background = ring
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
                addView(View(this@RioBoardService).apply {
                    background = LiveBgDrawable(t.live, resources.displayMetrics.density)
                    clipToOutline = true
                    outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: android.graphics.Outline) {
                            outline.setOval(0, 0, view.width, view.height)
                        }
                    }
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                })
            }
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
        box.addView(settingLabel("Effect"))
        val pickEffect: (Int) -> Unit = { idx ->
            effectIdx = idx
            prefs.edit().putInt("effect", effectIdx).apply()
            if (snowMode == 0) setSnow(lastSnow) else { applySnow(); render() }
        }
        val effRows = listOf(
            listOf("Snow ❄", "Water drops 💧", "Leaves 🍃", "Flowers 🌸"),
            listOf("Smoke 💨", "Fire 🔥", "Hearts ❤"),
            listOf("Rain 🌧", "Fireflies ✨", "Bubbles ○", "Stars ⭐")
        )
        var effOff = 0
        for (row in effRows) {
            val o = effOff
            box.addView(chipRow(row, if (effectIdx >= o && effectIdx < o + row.size) effectIdx - o else -1) {
                pickEffect(it + o)
            })
            effOff += row.size
        }
        box.addView(settingLabel("Effect area"))
        box.addView(chipRow(listOf("Off", "Keyboard", "Full screen"), snowMode) { setSnow(it) })
        box.addView(settingLabel("Effect colour (auto = natural look)"))
        box.addView(colorRow())
        box.addView(settingLabel("Vibration"))
        box.addView(chipRow(listOf("On", "Off"), if (haptic) 0 else 1) {
            haptic = it == 0
            prefs.edit().putBoolean("haptic", haptic).apply()
            render()
        })
        box.addView(settingLabel("Key pop-up bubble"))
        box.addView(chipRow(listOf("On", "Off"), if (keyPreview) 0 else 1) {
            keyPreview = it == 0
            prefs.edit().putBoolean("preview", keyPreview).apply()
            render()
        })
        box.addView(settingLabel("Key height"))
        box.addView(chipRow(listOf("Small", "Normal", "Large"), keyHIdx) {
            keyHIdx = it
            prefs.edit().putInt("keyh", keyHIdx).apply()
            render()
        })
        box.addView(settingLabel("Typing"))
        box.addView(chipRow(listOf("English", "Singlish → සිංහල"), if (singlishOn) 1 else 0) { setSinglish(it == 1) })
        box.addView(settingLabel("Background photo"))
        box.addView(chipRow(listOf("Choose photo", "Remove"), -1) {
            if (it == 0) {
                try {
                    startActivity(Intent(this@RioBoardService, PickImageActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                }
            } else {
                removeBackground()
            }
        })
        box.addView(settingLabel("Default backgrounds"))
        box.addView(DefaultBackgrounds.strip(this))
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
                setTextColor(th.text)
                text = if (i == 0) "A" else ""
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    when (name) {
                        "auto" -> setColor(th.snow)
                        "rainbow" -> {
                            setGradientType(GradientDrawable.SWEEP_GRADIENT)
                            setColors(rainbow)
                        }
                        else -> setColor(Color.parseColor(name))
                    }
                    setStroke(dp(if (sel) 3 else 1), if (sel) th.accent else th.special)
                }
                if (i == 0) setTextColor(Color.parseColor("#333333"))
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            }
            holder.addView(dot)
            r.addView(holder)
        }
        return r
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
                    typeLetter(label)
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
                        Key("ABC", 1.5f, Style.SPECIAL, 14f) { flushSinglish(); symbols = false; render() }
                    else
                        Key("?123", 1.5f, Style.SPECIAL, 14f) { flushSinglish(); symbols = true; render() },
                    Key("සිං", 1.3f, Style.SPECIAL, 14f, onLong = { setSinglish(!singlishOn) }) {
                        flushSinglish(); siMode = true; symbols = false; render()
                    },
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
                Key(label, textSp = 22f) { decorateWordStart(s); type(s) }
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
        spaceLabel(), weight, Style.NORMAL, 12f, space = true,
        onLong = {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
    ) { type(" ") }

    private fun chars(s: String) = s.map { c -> Key(c.toString()) { type(styled(c.toString())) } }

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

    /** Tiny squeeze when a key is touched, springs back on release. */
    private fun pressAnim(v: View, down: Boolean) {
        v.animate().cancel()
        if (down) v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(40).start()
        else v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
    }

    /** Light keyboard "tick" on key touch. */
    private fun keyTick(v: View) {
        if (haptic) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** iPhone-style bubble that shows the letter above the key while it is held. */
    private fun showPreview(anchor: View, label: String) {
        if (!keyPreview || !::frame.isInitialized) return
        try {
            val w = (anchor.width * 1.35f).toInt().coerceAtLeast(dp(46))
            val h = (anchor.height * 1.45f).toInt().coerceAtLeast(dp(56))
            val loc = IntArray(2)
            anchor.getLocationOnScreen(loc)
            val x = loc[0] + anchor.width / 2 - w / 2
            val y = loc[1] - h
            val tv = previewTv ?: TextView(this).apply {
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            }.also { previewTv = it }
            val p = previewPopup ?: android.widget.PopupWindow(tv, w, h).apply {
                isTouchable = false
                isFocusable = false
                isClippingEnabled = false
                inputMethodMode = android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED
                elevation = dp(6).toFloat()
            }.also { previewPopup = it }
            tv.text = label
            tv.setTextColor(th.text)
            tv.background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (th.bg == th.key) th.special else th.key or (0xFF shl 24))
                setStroke(dp(1), th.accent)
            }
            if (p.isShowing) p.update(x, y, w, h) else p.showAtLocation(frame, Gravity.NO_GRAVITY, x, y)
        } catch (e: Exception) {
        }
    }

    private fun hidePreview() {
        try {
            previewPopup?.dismiss()
        } catch (e: Exception) {
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
                k.space -> th.text
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
                        pressAnim(view, true)
                        keyTick(view)
                        k.onPress()
                        handler.postDelayed(r, 400)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        pressAnim(view, false)
                        handler.removeCallbacks(r)
                    }
                }
                true
            }
        } else {
            // Keys fire the moment the finger touches (no waiting for release), so fast typing
            // feels instant. A key that also has a long-press action fires on release instead.
            val long = k.onLong
            val previewable = !k.space && k.style == Style.NORMAL && k.label.isNotEmpty() &&
                k.label.codePointCount(0, k.label.length) <= 2
            var longDone = false
            val longRun = Runnable {
                longDone = true
                hidePreview()
                long?.invoke()
            }
            tv.setOnTouchListener { view, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.isPressed = true
                        pressAnim(view, true)
                        keyTick(view)
                        longDone = false
                        if (long != null) {
                            handler.postDelayed(longRun, 380)
                        } else {
                            if (previewable) showPreview(view, k.label)
                            k.onPress()
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        view.isPressed = false
                        pressAnim(view, false)
                        handler.removeCallbacks(longRun)
                        hidePreview()
                        if (long != null && !longDone) k.onPress()
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        pressAnim(view, false)
                        handler.removeCallbacks(longRun)
                        hidePreview()
                    }
                }
                true
            }
        }
        return tv
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
        if (fontStyle != 0) return emptyList()
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
        flushSinglish()
        val prefix = currentPrefix()
        ic.beginBatchEdit()
        ic.deleteSurroundingText(prefix.length, 0)
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
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

    private fun toast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    /** Clear everything in the field. First tap arms it, second tap (within 2 s) clears. */
    private fun clearAllText() {
        val ic = currentInputConnection ?: return
        flushSinglish()
        if (!clearArmed) {
            clearArmed = true
            toast("Tap again to clear all text")
            refreshBar()
            handler.postDelayed({ clearArmed = false; refreshBar() }, 2000)
            return
        }
        clearArmed = false
        ic.beginBatchEdit()
        val sel = ic.getSelectedText(0)
        if (!sel.isNullOrEmpty()) ic.commitText("", 1)
        val before = ic.getTextBeforeCursor(100000, 0)?.length ?: 0
        val after = ic.getTextAfterCursor(100000, 0)?.length ?: 0
        ic.deleteSurroundingText(before, after)
        ic.endBatchEdit()
        refreshBar()
    }

    /** Selected text (or the word before the cursor): UPPER -> lower -> Title -> UPPER ... */
    private fun changeCase() {
        val ic = currentInputConnection ?: return
        flushSinglish()
        val sel = ic.getSelectedText(0)?.toString()
        val hasSel = !sel.isNullOrEmpty()
        val src: String = if (hasSel) {
            sel!!
        } else {
            val b = ic.getTextBeforeCursor(40, 0)?.toString() ?: return
            var i = b.length
            while (i > 0 && isWordChar(b[i - 1])) i--
            b.substring(i)
        }
        if (src.isEmpty()) {
            toast("Select some text first")
            return
        }
        val out = when {
            src == src.uppercase() && src != src.lowercase() -> src.lowercase()
            src == src.lowercase() -> src.split(" ").joinToString(" ") { w ->
                w.lowercase().replaceFirstChar { it.uppercase() }
            }
            else -> src.uppercase()
        }
        ic.beginBatchEdit()
        if (!hasSel) ic.deleteSurroundingText(src.length, 0)
        ic.commitText(out, 1)
        if (hasSel) {
            // keep the text selected so the button can be tapped again to cycle
            val end = ic.getTextBeforeCursor(100000, 0)?.length ?: 0
            ic.setSelection(end - out.length, end)
        }
        ic.endBatchEdit()
    }

    private fun insertDateTime() {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd  HH:mm", java.util.Locale.getDefault())
        type(f.format(java.util.Date()))
    }

    private fun menuAction(id: Int) {
        currentInputConnection?.performContextMenuAction(id)
        if (id == android.R.id.copy || id == android.R.id.cut) {
            handler.postDelayed({ captureClip() }, 150)
        }
    }

    // ---------- input actions ----------
    private fun type(s: String) {
        val ic = currentInputConnection
        toolsPinned = false
        if (sbuf.isNotEmpty()) {
            // Singlish word in progress: a shortcut can still match its raw letters
            if (s == " " && expandSnip(sbuf.toString(), true)) {
                sbuf.clear()
                return
            }
            flushSinglish()
        } else if (s == " " && fontStyle == 0 && expandSnip(currentPrefix(), false)) {
            return
        }
        // a space/punctuation ends a word -> remember it for suggestions
        if (s.length == 1 && !isWordChar(s[0])) {
            val w = currentPrefix()
            if (w.length >= 3) learn(w)
        }
        ic?.commitText(s, 1)
        if (::snow.isInitialized) snow.burst()
    }

    private fun deleteOne() {
        val ic = currentInputConnection ?: return
        if (sbuf.isNotEmpty()) {
            sbuf.deleteCharAt(sbuf.length - 1)
            lastComposeMs = SystemClock.uptimeMillis()
            if (sbuf.isEmpty()) {
                ic.setComposingText("", 1)
                ic.finishComposingText()
            } else {
                ic.setComposingText(Singlish.convert(sbuf.toString()), 1)
            }
            return
        }
        val sel = ic.getSelectedText(0)
        if (!sel.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        // a RIO STYLE decoration is removed in one tap
        if (rioStyle in rioList.indices) {
            val deco = rioList[rioStyle]
            val b = ic.getTextBeforeCursor(deco.length, 0)
            if (b != null && b.toString() == deco) {
                ic.deleteSurroundingText(deco.length, 0)
                return
            }
        }
        val before = ic.getTextBeforeCursor(2, 0)
        if (before.isNullOrEmpty()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        } else {
            // emoji and styled letters are 2 UTF-16 units: delete them whole
            val n = if (before.length >= 2 &&
                Character.isLowSurrogate(before[before.length - 1]) &&
                Character.isHighSurrogate(before[before.length - 2])
            ) 2 else 1
            ic.deleteSurroundingText(n, 0)
        }
    }

    private fun pressEnter() {
        flushSinglish()
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

    // ---------- Singlish: English keys -> Sinhala letters ----------
    private fun setSinglish(on: Boolean) {
        flushSinglish()
        singlishOn = on
        if (on) {
            siMode = false
            symbols = false
            fontStyle = 0
        }
        prefs.edit().putBoolean("singlish", on).apply()
        suggestions = emptyList()
        render()
    }

    private fun typeLetter(label: String) {
        if (sbuf.isEmpty()) decorateWordStart(label)
        if (singlishOn) {
            toolsPinned = false
            sbuf.append(label)
            lastComposeMs = SystemClock.uptimeMillis()
            currentInputConnection?.setComposingText(Singlish.convert(sbuf.toString()), 1)
            if (::snow.isInitialized) snow.burst()
        } else {
            type(styled(label))
        }
    }

    /** Commit the Singlish word that is still being composed. */
    private fun flushSinglish() {
        if (sbuf.isEmpty()) return
        currentInputConnection?.commitText(Singlish.convert(sbuf.toString()), 1)
        sbuf.clear()
    }

    private fun styled(s: String): String =
        if (fontStyle == 0 || singlishOn || s.length != 1) s else Fonts.convert(s[0], fontStyle)

    private fun spaceLabel(): String = "RIO BOARD"

    // ---------- RIO STYLES (decoration in front of every word) ----------
    private fun loadRioStyles() {
        rioList = RioStyles.all(prefs)
        val sel = RioStyles.selected(prefs)
        rioStyle = if (sel in rioList.indices) sel else -1
    }

    private fun setRioStyle(i: Int) {
        flushSinglish()
        rioStyle = if (i in rioList.indices) i else -1
        RioStyles.select(prefs, rioStyle)
    }

    /** Puts the active RIO STYLE decoration in front of a word that is just starting. */
    private fun decorateWordStart(first: String) {
        if (rioStyle !in rioList.indices || first.isEmpty()) return
        if (!Character.isLetterOrDigit(first.codePointAt(0))) return
        val ic = currentInputConnection ?: return
        val deco = rioList[rioStyle]
        val before = ic.getTextBeforeCursor(deco.length + 2, 0)?.toString() ?: ""
        if (before.endsWith(deco)) return
        if (before.isNotEmpty()) {
            val cp = before.codePointBefore(before.length)
            val t = Character.getType(cp)
            val startsNew = Character.isWhitespace(cp) || (cp != '\''.code && (
                t == Character.CONNECTOR_PUNCTUATION.toInt() ||
                    t == Character.DASH_PUNCTUATION.toInt() ||
                    t == Character.START_PUNCTUATION.toInt() ||
                    t == Character.END_PUNCTUATION.toInt() ||
                    t == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
                    t == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
                    t == Character.OTHER_PUNCTUATION.toInt()
                ))
            if (!startsNew) return
        }
        ic.commitText(deco, 1)
    }

    // ---------- shortcuts ("gm" + space -> "Good morning") ----------
    private fun expandSnip(key: String, composing: Boolean): Boolean {
        if (key.isEmpty()) return false
        val v = snips.optString(key.lowercase(), "")
        if (v.isEmpty()) return false
        val ic = currentInputConnection ?: return false
        ic.beginBatchEdit()
        if (!composing) ic.deleteSurroundingText(key.length, 0)
        ic.commitText("$v ", 1)
        ic.endBatchEdit()
        if (::snow.isInitialized) snow.burst()
        return true
    }

    // ----- sticker / GIF maker panel -----
    private var makerDelete = false
    private val makerThumbs = HashMap<String, Bitmap>()

    private fun makerThumb(f: File): Bitmap? {
        val key = f.name + f.lastModified()
        makerThumbs[key]?.let { return it }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 200) sample *= 2
            val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
            if (bmp != null) {
                if (makerThumbs.size > 80) makerThumbs.clear()
                makerThumbs[key] = bmp
            }
            bmp
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun openMaker() {
        flushSinglish()
        try {
            startActivity(Intent(this, MakerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }

    private fun renderMaker() {
        root.addView(panelHeader(if (makerDelete) "Tap a sticker to delete" else "Stickers & GIFs", IconView.Kind.TRASH) {
            makerDelete = !makerDelete
            render()
        })
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(10))
        }
        val files = StickerStore.list(this)
        box.addView(
            hintText(
                when {
                    makerDelete -> "Delete mode: tap a sticker to remove it. Tap the bin again to stop."
                    files.isEmpty() -> "Make your first emoji sticker or GIF with ＋ Create."
                    else -> "Tap a sticker to send it"
                }
            )
        )

        fun cell(f: File?): View {
            val lp = LinearLayout.LayoutParams(0, dp(78), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
            if (f == null) {
                return TextView(this).apply {
                    text = "＋\nCreate"
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    typeface = Typeface.DEFAULT_BOLD
                    background = roundBg(th.accent, 12)
                    layoutParams = lp
                    isClickable = true
                    setOnClickListener {
                        feedback(it)
                        openMaker()
                    }
                }
            }
            return FrameLayout(this).apply {
                layoutParams = lp
                background = roundBg(if (makerDelete) Color.parseColor("#66FF5252") else th.key, 12)
                isClickable = true
                val iv = android.widget.ImageView(this@RioBoardService).apply {
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    makerThumb(f)?.let { setImageBitmap(it) }
                }
                addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                if (f.name.endsWith(".gif")) {
                    addView(TextView(this@RioBoardService).apply {
                        text = "GIF"
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(dp(4), dp(1), dp(4), dp(1))
                        background = roundBg(Color.parseColor("#AA000000"), 6)
                    }, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.START
                    ).apply { setMargins(dp(4), 0, 0, dp(4)) })
                }
                setOnClickListener {
                    feedback(it)
                    if (makerDelete) {
                        f.delete()
                        render()
                    } else {
                        sendSticker(f)
                    }
                }
            }
        }

        val cells = ArrayList<File?>()
        cells.add(null)
        cells.addAll(files)
        cells.chunked(4).forEach { rowItems ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { r.addView(cell(it)) }
            repeat(4 - rowItems.size) {
                r.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
            }
            box.addView(r)
        }
        root.addView(panelBody(box))
    }

    /** Sends a sticker/GIF into the text box (commitContent). Apps that refuse get the share sheet instead. */
    private fun sendSticker(f: File) {
        if (!f.exists()) {
            render()
            return
        }
        val mime = StickerStore.mimeOf(f)
        val uri = RioFileProvider.uriFor(this, f)
        var ok = false
        val ic = currentInputConnection
        if (ic != null && android.os.Build.VERSION.SDK_INT >= 25) {
            try {
                val info = android.view.inputmethod.InputContentInfo(
                    uri, android.content.ClipDescription(f.name, arrayOf(mime)), null
                )
                ok = ic.commitContent(info, android.view.inputmethod.InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
            } catch (e: Exception) {
                ok = false
            }
        }
        if (ok) {
            if (::snow.isInitialized) snow.burst()
            return
        }
        toast("This app can't take stickers directly. Pick where to send it.")
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Send sticker").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }

    private fun openApp() {
        try {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }

    private fun renderSnips() {
        root.addView(panelHeader("Shortcuts", IconView.Kind.SETTINGS) { openApp() })
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        val keys = snips.keys().asSequence().toList().sorted()
        keys.forEach { k ->
            val v = snips.optString(k, "")
            list.addView(TextView(this).apply {
                text = "$k  →  $v"
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(th.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(8), dp(14), dp(8))
                minHeight = dp(46)
                background = roundBg(th.key, 12)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(4), dp(3), dp(4), dp(3)) }
                setOnClickListener {
                    feedback(it)
                    type(v)
                    panel = Panel.NONE
                    render()
                }
                setOnLongClickListener {
                    snips.remove(k)
                    Snips.save(prefs, snips)
                    render()
                    true
                }
            })
        }
        list.addView(
            hintText(
                if (keys.isEmpty()) "No shortcuts yet. Tap ⚙ above to add some."
                else "Type the shortcut + Space to expand it.\nTap = insert  ·  Long press = delete  ·  ⚙ = add new"
            )
        )
        root.addView(panelBody(list))
    }

    private fun styleRow(name: String, sample: String, on: Boolean, onPick: () -> Unit): View {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = roundBg(if (on) th.accent else th.key, 12)
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(4), dp(3), dp(4), dp(3))
            }
            setOnClickListener {
                feedback(it)
                onPick()
                suggestions = emptyList()
                panel = Panel.NONE
                render()
            }
        }
        r.addView(TextView(this).apply {
            text = name
            setTextColor(if (on) Color.WHITE else th.hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        })
        if (sample.isNotEmpty()) {
            r.addView(TextView(this).apply {
                text = sample
                setTextColor(if (on) Color.WHITE else th.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        return r
    }

    // ---------- fancy fonts ----------
    private fun renderFonts() {
        root.addView(panelHeader("Fonts"))
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        Fonts.names.forEachIndexed { i, name ->
            val on = i == fontStyle
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(14), 0)
                background = roundBg(if (on) th.accent else th.key, 12)
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply {
                    setMargins(dp(4), dp(3), dp(4), dp(3))
                }
                setOnClickListener {
                    feedback(it)
                    fontStyle = i
                    if (i != 0 && singlishOn) {
                        singlishOn = false
                        prefs.edit().putBoolean("singlish", false).apply()
                    }
                    suggestions = emptyList()
                    panel = Panel.NONE
                    render()
                }
            }
            r.addView(TextView(this).apply {
                text = name
                setTextColor(if (on) Color.WHITE else th.hint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            r.addView(TextView(this).apply {
                text = Fonts.sample(i)
                setTextColor(if (on) Color.WHITE else th.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                maxLines = 1
            })
            list.addView(r)
        }
        list.addView(hintText("Works for English letters and numbers. Pick Normal to turn it off."))

        // ----- RIO STYLES -----
        list.addView(TextView(this).apply {
            text = "RIO STYLES"
            setTextColor(th.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(10), dp(10), dp(4))
        })
        list.addView(styleRow("Off", "", rioStyle < 0) { setRioStyle(-1) })
        rioList.forEachIndexed { i, deco ->
            list.addView(styleRow(RioStyles.name(i), deco + "hello " + deco + "world", i == rioStyle) { setRioStyle(i) })
        }
        list.addView(hintText("A style goes in front of every word you type. Add your own in the RIO BOARD app."))
        root.addView(panelBody(list))
    }

    // ---------- voice typing ----------
    private fun startVoice() {
        flushSinglish()
        try {
            val i = Intent(this, VoiceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("si", siMode || singlishOn)
            startActivity(i)
        } catch (e: Exception) {
        }
    }

    /** Called by VoiceActivity. The text goes in once the keyboard is back in the app's text box. */
    fun voiceResult(text: String) {
        pendingVoice = text
        pendingVoiceAt = SystemClock.uptimeMillis()
        handler.postDelayed({ flushPendingVoice() }, 400)
    }

    private fun flushPendingVoice() {
        val t = pendingVoice ?: return
        if (SystemClock.uptimeMillis() - pendingVoiceAt > 20000) {
            pendingVoice = null
            return
        }
        val ic = currentInputConnection ?: return
        pendingVoice = null
        ic.commitText("$t ", 1)
    }

    // ---------- background photo ----------
    private fun loadBackground() {
        bgOn = prefs.getBoolean("bgon", false)
        bgBitmap = null
        if (bgOn) {
            try {
                val f = File(filesDir, "kb_bg.jpg")
                if (f.exists()) bgBitmap = BitmapFactory.decodeFile(f.path)
            } catch (e: Exception) {
            } catch (e: OutOfMemoryError) {
            }
            if (bgBitmap == null) bgOn = false
        }
    }

    /** Called by PickImageActivity after a new photo was saved. */
    fun reloadBackground() {
        loadBackground()
        if (::root.isInitialized) render()
    }

    private fun removeBackground() {
        prefs.edit().putBoolean("bgon", false).remove("bgname").apply()
        try {
            File(filesDir, "kb_bg.jpg").delete()
        } catch (e: Exception) {
        }
        reloadBackground()
    }

    private var liveBg: LiveBgDrawable? = null
    private var liveBgKind: LiveKind? = null

    private fun applyRootBg() {
        val live = th.live
        if (live != null) {
            if (liveBg == null || liveBgKind != live) {
                liveBg = LiveBgDrawable(live, resources.displayMetrics.density)
                liveBgKind = live
            }
            root.background = liveBg
            return
        }
        val bmp = bgBitmap
        if (bgOn && bmp != null) {
            // photo + a see-through layer of the theme colour so keys stay readable
            val scrim = ColorDrawable((th.bg and 0x00FFFFFF) or (0x88 shl 24))
            root.background = LayerDrawable(arrayOf<Drawable>(CropDrawable(bmp), scrim))
        } else {
            root.setBackgroundColor(th.bg)
        }
    }

    /** Make opaque key colours slightly see-through when a photo is behind them. */
    private fun kc(c: Int): Int =
        if (bgOn && bgBitmap != null && Color.alpha(c) == 255) (c and 0x00FFFFFF) or (0xCC shl 24) else c
}
