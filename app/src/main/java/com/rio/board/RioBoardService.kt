package com.rio.board

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
    private enum class Panel { NONE, CLIPBOARD, THEME, SETTINGS, EMOJI, FONTS, SNIPS, BACKGROUND, BOOM, STATUS, MAKER, TOOLS, CRYPTO, CALC, KEYSTYLE, NEWS, ART, GSEARCH, TRANSLATE }

    private class Key(
        val label: String,
        val weight: Float = 1f,
        val style: Style = Style.NORMAL,
        val textSp: Float = 20f,
        val repeat: Boolean = false,
        val space: Boolean = false,
        val onLong: (() -> Unit)? = null,
        val alts: List<String> = emptyList(),   // long-press popup choices
        val letter: Char? = null,               // set on English letter keys (swipe typing)
        val hint: String? = null,               // small Sinhala letter shown above the key in Singlish mode
        val onPress: () -> Unit
    )

    /** A key label that can also draw a small hint (the Sinhala letter) above itself. */
    private class HintTextView(ctx: Context) : TextView(ctx) {
        var hintText: String? = null
        var hintColor: Int = Color.GRAY
        var hintSizePx: Float = 24f
        private val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val h = hintText ?: return
            hp.color = hintColor
            hp.textSize = hintSizePx
            canvas.drawText(h, width / 2f, hintSizePx + height * 0.06f, hp)
        }
    }

    /** Thin line that follows the finger while swipe-typing. */
    private class TrailView(ctx: Context) : View(ctx) {
        private val pts = ArrayList<PointF>()
        private val path = Path()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 9f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        var color: Int = Color.WHITE

        fun add(rawX: Float, rawY: Float) {
            val l = IntArray(2)
            getLocationOnScreen(l)
            pts.add(PointF(rawX - l[0], rawY - l[1]))
            if (pts.size > 80) pts.removeAt(0)
            invalidate()
        }

        fun clear() {
            if (pts.isNotEmpty()) {
                pts.clear()
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            if (pts.size < 2) return
            path.reset()
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
            paint.color = color
            canvas.drawPath(path, paint)
        }
    }

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

    private val baseThemes = listOf(
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
        Theme("RGB", "#101018", "#99181822", "#B0242434", "#FF4D8D", "#4DA3FF", "#CC4A4A66", "#FFFFFF", "#C8C8E0", "#FFFFFF", LiveKind.RGB),
        // ---- follow the phone (colours come from SystemTheme.kt) ----
        Theme("Auto", "#17181C", "#2B2D35", "#3A3D49", "#3B82F6", "#6D5BD0", "#5A5F73", "#FFFFFF", "#9AA3C0", "#FFFFFF"),
        Theme("Material You", "#1C1B1F", "#2B2930", "#49454F", "#6750A4", "#7D5260", "#625B71", "#FFFFFF", "#CAC4D0", "#EADDFF")
    )

    // base themes + the ones you publish from the admin web (RemoteConfig.kt)
    private var themes: List<Theme> = baseThemes
    private var remoteCfg: RemoteConfig.Data = RemoteConfig.empty

    private fun rebuildThemes() {
        themes = baseThemes + remoteCfg.themes.map {
            Theme(it.name, it.c[0], it.c[1], it.c[2], it.c[3], it.c[4], it.c[5], it.c[6], it.c[7], it.c[8])
        }
        val rid = prefs.getString("theme_remote", "") ?: ""
        themeIdx = if (rid.isNotEmpty()) {
            val i = remoteCfg.themes.indexOfFirst { it.id == rid }
            if (i >= 0) baseThemes.size + i else 0
        } else prefs.getInt("theme", 0).coerceIn(0, baseThemes.size - 1)
        RemoteConfig.current = remoteCfg
        themeCache = null
    }

    // snow colours: auto (theme), then fixed colours, last = rainbow
    private val snowPalette = listOf("auto", "#FFFFFF", "#7CC8FF", "#FF8AC8", "#FFD54A", "#7CFFB2", "rainbow")
    private val rainbow = intArrayOf(
        0xFFFF5252.toInt(), 0xFFFFA726.toInt(), 0xFFFFEE58.toInt(), 0xFF66BB6A.toInt(),
        0xFF29B6F6.toInt(), 0xFF7E57C2.toInt(), 0xFFEC407A.toInt()
    )

    // ---------- word lists for suggestions ----------
    private val enWords = ("the be to of and a in that have I it for not on with he as you do at this but his by from they we say her she or an will my one all would there their what so up out if about who get which go me when make can like time no just him know take people into year your good some could them see other than then now look only come its over think also back after use two how our work first well way even new want because any these give day most us is are was were been has had did does hello hi thanks thank please sorry yes okay ok where why home love friend friends today tomorrow yesterday night morning evening happy birthday welcome great nice beautiful really very much many here still call phone message send share video song music photo picture game school class exam study money food water bus train going coming doing being having something anything everything nothing someone everyone always never sometimes maybe again already before tonight together should might must need needs wait waiting enough little big small long short right left help keep let begin seem talk turn start show hear play run move live believe bring happen write provide sit stand lose pay meet include continue set learn change lead understand watch follow stop create speak read allow add spend grow open walk win offer remember consider appear buy serve expect build stay fall cut reach remain people person world life hand part child eye woman place week case point government company number group problem fact").split(" ")

    private val siWords = ("ඔයා මම අපි ඔබ කොහොමද කොහොමෙයි ස්තූතියි හරි ඉතින් මොකද මොකක්ද මොනවද කොහෙද නැහැ ඔව් කරන්න යන්න එන්න බලන්න දෙන්න ගන්න ලස්සනයි ආයුබෝවන් සුභ රාත්‍රියක් උදෑසනක් දැන් ඊයේ අද හෙට ගෙදර යාලුවා යාලුවෝ අම්මා තාත්තා මල්ලි අක්කා අයියා නංගි ආදරේ සතුටුයි දුකයි කමක් නැහැ පුළුවන් බැහැ ඕන නෑ තියෙනවා මොකෝ ඇයි කවද්ද කීයද කවුද මෙහෙ එහෙ ඔහොම මේක ඒක ඕක").split(" ")

    private val dict: List<String> by lazy { (enWords + SinhalaDict.englishExtra + siWords + SinhalaDict.sinhala).filter { it.isNotBlank() } }

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
    private var hapticLevel = 2       // 0 off, 1 light, 2 medium, 3 strong
    private var pressLevel = 2        // key squeeze: 0 off, 1 soft, 2 normal, 3 strong
    private val boomCounts = intArrayOf(5, 10, 20, 50, 100)
    private val boomDelays = longArrayOf(500L, 1000L, 2000L)
    private var boomCount = 10            // how many times to send (any number 1..500)
    private var boomMethod = 0            // how "send" is pressed: 0 Enter/action, 1 Send action, 2 Ctrl+Enter
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
    private var boomKey = ""              // which chat box the boom was started in (package/field id)
    private var boomChecking = false      // we pressed send last tick: check that the box was really emptied
    private var boomFails = 0
    private var keyPreview = true     // iPhone-style key pop-up bubble
    private var previewPopup: android.widget.PopupWindow? = null
    private var previewTv: TextView? = null
    private var keyHIdx = 1
    private var swipeOn = true        // glide across letters to type a word
    private var autoCorrect = true    // Singlish typo fixing
    private var nextWordOn = true     // next-word suggestions
    private var numberRow = true      // digits row on top
    private var popupsOn = true       // long-press popups (a -> á ä ...)
    private var oneHand = 0           // 0 off, 1 left, 2 right
    private var widthPct = 100
    private var heightPct = 100
    private var layoutKind = 0        // 0 Sinhala grid, 1 Wijesekara, 2 Tamil
    private var soundLevel = 0        // 0 off, 1 low, 2 medium, 3 high
    private var autoCap = true        // capital after . ? ! and "  " -> ". "
    private var emojiSug = true       // emoji next to a fully typed word
    private var lastSpaceMs = 0L
    private var undoCorr: Pair<String, String>? = null   // (corrected+separator, what was typed)
    private var swipeCommitted: String? = null
    private var swipeAlts: List<String> = emptyList()
    private val bigrams = HashMap<String, HashMap<String, Int>>()
    private val learnedRoman = HashMap<String, Int>()
    private val swipeKeys = ArrayList<Pair<Char, View>>()
    private var panel = Panel.NONE
    private var toolbarX = 0
    private var clearArmed = false
    private var toolsPinned = false
    private var barScrollX = 0            // remembered scroll position of the swipeable tool bar
    private var barMsg: String? = null     // short message shown in the top bar (word count etc.)
    private var suggestions: List<String> = emptyList()
    private val clips = ArrayList<String>()
    private val learned = HashMap<String, Int>()
    private var singlishOn = false     // English keys -> Sinhala letters
    private var singlishHints = true   // show the Sinhala letter above each key while Singlish is on
    private val sbuf = StringBuilder() // raw Singlish letters being composed
    private var lastComposeMs = 0L
    private var fontStyle = 0          // 0 = normal, see Fonts.names
    private var rioStyle = -1          // -1 = off, else index into rioList
    private var rioList: List<String> = emptyList()
    private var snips = JSONObject()
    private var bgOn = false
    private var bgBitmap: Bitmap? = null
    private var pendingVoice: String? = null
    private var keyTpl = 0
    private var keyDecor = 0
    private var passwordField = false
    private var pendingInsert: String? = null
    private var pendingInsertAt = 0L
    private var pendingReplaceAll = false
    private var pendingSend = false
    private var pendingVoiceAt = 0L

    private val keyHs = intArrayOf(44, 50, 56)
    private val keyH: Int get() = keyHs[keyHIdx] * heightPct / 100

    private lateinit var prefs: SharedPreferences
    private lateinit var cm: ClipboardManager
    private lateinit var frame: FrameLayout
    private lateinit var root: LinearLayout
    private lateinit var barHolder: LinearLayout
    private lateinit var snow: EffectView
    private lateinit var wordFx: WordFx
    private var fxPrev = ""            // previous word, for two-word triggers (good night, happy birthday)
    private val handler = Handler(Looper.getMainLooper())
    private var themeCache: Theme? = null
    private var themeCacheIdx = -1
    private val th: Theme get() {
        val raw = themes[themeIdx]
        if (!isSystemTheme(raw)) return raw
        val c = themeCache
        if (c != null && themeCacheIdx == themeIdx) return c
        return resolveTheme(raw).also { themeCache = it; themeCacheIdx = themeIdx }
    }

    private fun isSystemTheme(t: Theme) = t.name == "Auto" || t.name == "Material You"

    /** Auto / Material You: real colours for the phone's dark mode and wallpaper right now. */
    private fun resolveTheme(t: Theme): Theme {
        val night = SystemTheme.isNight(this)
        val c = (if (t.name == "Material You") SystemTheme.material(this, night) else null) ?: SystemTheme.auto(night)
        return Theme(t.name, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8])
    }

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

    // admin video previews finished downloading -> redraw the open panel
    private val thumbListener: () -> Unit = {
        if (::root.isInitialized && (panel == Panel.BACKGROUND || panel == Panel.SETTINGS)) render()
    }

    // ---------- lifecycle ----------
    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("rio_board", MODE_PRIVATE)
        remoteCfg = RemoteConfig.cached(prefs)
        RemoteConfig.applyDefaults(prefs, remoteCfg)
        AnimatedBackgrounds.thumbListeners.add(thumbListener)
        rebuildThemes()
        snowMode = prefs.getInt("snow", 2).coerceIn(0, 2)
        lastSnow = prefs.getInt("lastsnow", 2).coerceIn(1, 2)
        snowColorIdx = prefs.getInt("snowcol", 0).coerceIn(0, snowPalette.size - 1)
        effectIdx = prefs.getInt("effect", 0).coerceIn(0, EffectView.Type.values().size - 1)
        loadPinned()
        keyTpl = prefs.getInt("ktpl", 0).let { if (it in KeyStyles.codes) it else 0 }   // removed styles fall back to Classic
        keyDecor = prefs.getInt("kdecor", 0).coerceIn(0, KeyStyles.decors.size - 1)
        haptic = prefs.getBoolean("haptic", true)
        hapticLevel = prefs.getInt("hapticlvl", if (haptic) 2 else 0).coerceIn(0, 3)
        haptic = hapticLevel > 0
        pressLevel = prefs.getInt("pressanim", 2).coerceIn(0, 3)
        keyPreview = prefs.getBoolean("preview", true)
        boomCount = prefs.getInt("boom_n", boomCounts[prefs.getInt("boom_count", 1).coerceIn(0, 3)]).coerceIn(1, 500)
        boomDelayIdx = prefs.getInt("boom_delay", 1).coerceIn(0, boomDelays.size - 1)
        keyHIdx = prefs.getInt("keyh", 1).coerceIn(0, 2)
        singlishOn = prefs.getBoolean("singlish", false)
        singlishHints = prefs.getBoolean("singlish_hints", true)
        swipeOn = prefs.getBoolean("swipe", true)
        autoCorrect = prefs.getBoolean("autocorrect", true)
        nextWordOn = prefs.getBoolean("nextword", true)
        numberRow = prefs.getBoolean("numrow", true)
        popupsOn = prefs.getBoolean("popups", true)
        oneHand = prefs.getInt("onehand", 0).coerceIn(0, 2)
        widthPct = prefs.getInt("widthpct", 100).coerceIn(50, 100)
        heightPct = prefs.getInt("heightpct", 100).coerceIn(70, 140)
        layoutKind = prefs.getInt("langlayout", 0).coerceIn(0, 3 + Languages.extra.size)
        soundLevel = prefs.getInt("sound", 0).coerceIn(0, 3)
        IconView.neo = prefs.getBoolean("neoicons", true)
        autoCap = prefs.getBoolean("autocap", true)
        emojiSug = prefs.getBoolean("emojisug", true)
        snips = Snips.load(prefs)
        loadRioStyles()
        videoName = prefs.getString("vbg", null)
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
        try {
            val o = JSONObject(prefs.getString("bigrams", "{}"))
            o.keys().forEach { prev ->
                val inner = o.getJSONObject(prev)
                val m = HashMap<String, Int>()
                inner.keys().forEach { nx -> m[nx] = inner.getInt(nx) }
                bigrams[prev] = m
            }
        } catch (e: Exception) {
        }
        try {
            val o = JSONObject(prefs.getString("learned_roman", "{}"))
            o.keys().forEach { learnedRoman[it] = o.getInt(it) }
        } catch (e: Exception) {
        }
        cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        try { gWeb?.destroy() } catch (e: Exception) { }
        gWeb = null
        boomRunning = false
        handler.removeCallbacks(boomStep)
        hidePreview()
        hideAlts()
        releasePlayer()
        cm.removePrimaryClipChangedListener(clipListener)
        AnimatedBackgrounds.thumbListeners.remove(thumbListener)
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
        wordFx = WordFx(this)
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
        frame.addView(
            wordFx,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        videoView = android.view.TextureView(this).apply {
            isClickable = false
            visibility = View.GONE
            surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                    videoSurface = android.view.Surface(st)
                    startVideo()
                }

                override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                    fitVideo()
                }

                override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                    releasePlayer()
                    videoSurface?.release()
                    videoSurface = null
                    return true
                }

                override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
            }
        }
        frame.addView(videoView, 0, FrameLayout.LayoutParams(1, 1))
        trail = TrailView(this).also {
            it.isClickable = false
            frame.addView(
                it,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateSnowTop(); syncVideo() }
        applySize()
        render()
        applySnow()
        return frame
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!::root.isInitialized) return
        themeCache = null          // Auto / Material You: pick up dark mode and wallpaper changes
        makeWindowTransparent()
        if (shift == 1) shift = 0
        panel = Panel.NONE
        if (boomRunning && boomKeyOf(info) != boomKey) {      // same chat box (apps restart it after every send): keep going
            boomRunning = false
            handler.removeCallbacks(boomStep)
            boomStatusText = "Stopped"
        }
        toolsPinned = false
        loadPinned()               // picks up tools chosen in the app
        fxPrev = ""
        sbuf.clear()
        undoCorr = null
        swipeCommitted = null
        snips = Snips.load(prefs)
        loadRioStyles()
        val it0 = info?.inputType ?: 0
        val cls = it0 and android.text.InputType.TYPE_MASK_CLASS
        val variation = it0 and android.text.InputType.TYPE_MASK_VARIATION
        passwordField = (cls == android.text.InputType.TYPE_CLASS_TEXT && (
            variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == android.text.InputType.TYPE_CLASS_NUMBER &&
                variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        if (passwordField) {
            // passwords are typed exactly as pressed: no Singlish, no styles, no suggestions, no learning
            siMode = false
            sbuf.clear()
        }
        captureClip()
        flushPendingVoice()
        flushPendingInsert()
        suggestions = computeSuggestions()
        updateAutoCap(redraw = false)
        render()
        applySnow()
        announceNews()
        RemoteConfig.refresh(prefs, false) { d ->
            remoteCfg = d
            rebuildThemes()
            if (RemoteConfig.applyDefaults(prefs, d)) {
                snowMode = prefs.getInt("snow", 2).coerceIn(0, 2)
                lastSnow = prefs.getInt("lastsnow", 2).coerceIn(1, 2)
                snowColorIdx = prefs.getInt("snowcol", 0).coerceIn(0, snowPalette.size - 1)
                effectIdx = prefs.getInt("effect", 0).coerceIn(0, EffectView.Type.values().size - 1)
            }
            loadPinned()
            if (::root.isInitialized) {
                applySnow()
                render()
                announceNews()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        themeCache = null
        if (::root.isInitialized && isSystemTheme(themes[themeIdx])) {
            applySnow()
            render()
        }
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

    override fun onWindowShown() {
        super.onWindowShown()
        windowVisible = true
        try {
            player?.start()
        } catch (e: Exception) {
        }
        slideIn()
    }

    /**
     * Phone Back button / back gesture: inside a panel (Settings, Tools, Emoji, Fonts ...) it goes back to the
     * keyboard itself, instead of hiding the keyboard. On the normal keyboard it works as usual.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && panel != Panel.NONE) {
            event?.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && panel != Panel.NONE && event?.isCanceled != true) {
            flushSinglish()
            panel = Panel.NONE
            render()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        windowVisible = false
        try { gWeb?.onPause() } catch (e: Exception) { }
        try {
            player?.pause()
        } catch (e: Exception) {
        }
    }

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
            root.height > 0 &&
            (frame.height - root.height > dp(40) || frame.width - root.width > dp(24))
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
        hideAlts()
        swipeKeys.clear()
        applySize()
        applyVideoBg()
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
            Panel.CRYPTO -> renderCrypto()
            Panel.CALC -> renderCalc()
            Panel.KEYSTYLE -> renderKeyStyle()
            Panel.NEWS -> renderNews()
            Panel.ART -> renderArt()
            Panel.GSEARCH -> renderGSearch()
            Panel.TRANSLATE -> renderTranslate()
            Panel.NONE -> {
                if (numberRow) root.addView(row(numberKeys()))
                if (siMode) renderSinhala() else renderEnglish()
            }
        }
        if (panel != lastPanelShown) {
            lastPanelShown = panel
            animatePanelIn()
        }
    }

    // ---------- smooth motion helpers ----------
    private var lastPanelShown = Panel.NONE
    private var lastShowSug = false

    /** Whole keyboard glides up and fades in when it appears. */
    private fun slideIn() {
        if (!::root.isInitialized) return
        root.animate().cancel()
        root.alpha = 0f
        root.translationY = dp(20).toFloat()
        root.animate().alpha(1f).translationY(0f).setDuration(220)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f)).start()
    }

    /** Panel content (below the top bar) slides up softly when you open or close a panel. */
    private fun animatePanelIn() {
        for (i in 1 until root.childCount) {
            val c = root.getChildAt(i)
            c.alpha = 0f
            c.translationY = dp(14).toFloat()
            c.animate().alpha(1f).translationY(0f).setDuration(190)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f)).start()
        }
    }

    /** Little springy pop used when a tool is tapped. */
    private fun bounce(v: View) {
        v.animate().cancel()
        v.scaleX = 0.84f
        v.scaleY = 0.84f
        v.animate().scaleX(1f).scaleY(1f).setDuration(240)
            .setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()
    }

    /** Tools in the grid pop in one after another. */
    private fun popIn(v: View, index: Int) {
        v.alpha = 0f
        v.scaleX = 0.6f
        v.scaleY = 0.6f
        v.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(minOf(index * 16L, 380L)).setDuration(240)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.6f)).start()
    }

    /** Top bar: word suggestions while typing, tools otherwise. */
    private fun refreshBar() {
        if (!::barHolder.isInitialized) return
        barHolder.removeAllViews()
        val msg = barMsg
        val showSug = msg == null && suggestions.isNotEmpty() && panel == Panel.NONE && !toolsPinned
        val bar = if (msg != null) messageStrip(msg) else if (showSug) suggestionStrip() else toolbar()
        barHolder.addView(bar)
        if (showSug != lastShowSug) {            // soft cross-fade only when the bar changes kind
            lastShowSug = showSug
            bar.alpha = 0.35f
            bar.animate().alpha(1f).setDuration(150).start()
        }
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

    private val toolGroups = listOf("Input", "Text & tools", "Edit", "Cursor", "Look & extras")
    private val defaultPinned = listOf("singlish", "emoji", "sticker", "clip", "fonts", "voice")
    private var pinned = ArrayList<String>(defaultPinned)

    private val allTools: List<Tool> by lazy {
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
            ic("lang", "Language (tap to change)", 0, IconView.Kind.GLOBE) { cycleLanguage() },
            tx("singlish", "Singlish", 0, "A→අ", 13f, { singlishOn }) { setSinglish(!singlishOn) },
            ic("voice", "Voice typing", 0, IconView.Kind.MIC) { startVoice() },
            tx("numrow", "Number row", 0, "123", 13f, { numberRow }) { toggleNumberRow() },
            tx("swipe", "Swipe typing", 0, "〰", 16f, { swipeOn }) { toggleSwipe() },
            tx("layout", "Sinhala / Tamil layout", 0, "සි/த", 12f) { cycleLayout() },
            tx("onehand", "One-handed mode", 0, "☝", 16f, { oneHand != 0 }) { setOneHand((oneHand + 1) % 3) },
            // 1 text
            tx("fonts", "Fonts", 1, "Aa", 16f, { fontStyle != 0 || rioStyle >= 0 || panel == Panel.FONTS }) { togglePanel(Panel.FONTS) },
            ic("snips", "Shortcuts", 1, IconView.Kind.BOLT, { panel == Panel.SNIPS }) { togglePanel(Panel.SNIPS) },
            tx("case", "Change case", 1, "a⇄A", 13f) { changeCase() },
            tx("readmore", "Read more message", 1, "\u22EF", 20f) { makeReadMore() },
            ic("date", "Date & time", 1, IconView.Kind.CALENDAR) { insertDateTime() },
            ic("calc", "Calculator", 1, IconView.Kind.CALC, { panel == Panel.CALC }) { openCalc() },
            ic("lock", "Secret message", 1, IconView.Kind.LOCK, { panel == Panel.CRYPTO }) { togglePanel(Panel.CRYPTO) },
            tx("wcount", "Word count", 1, "#W", 13f) { wordCount() },
            tx("gsearch", "Google search", 1, "G", 18f) { googleSearch() },
            tx("gtrans", "Google Translate", 1, "\u6587A", 14f) { googleTranslate() },
            tx("ytsearch", "YouTube search", 1, "\u25B6", 18f) { youtubeSearch() },
            tx("gvoice", "Google voice search", 1, "G\uD83C\uDFA4", 12f) { googleVoice() },
            tx("qrmake", "QR code maker", 1, "QR", 15f) { qrMake() },
            tx("qrread", "QR reader", 1, "QR\uD83D\uDCF7", 11f) { qrRead() },
            tx("voicefx", "Voice changer", 1, "\uD83C\uDFA4\u2728", 14f) { voiceChanger() },
            tx("mock", "sPoNgE text", 1, "aBc", 13f) { mockText() },
            tx("flip", "Upside down", 1, "\u0250q", 14f) { flipText() },
            tx("spaced", "S P A C E D", 1, "A B", 13f) { spacedText() },
            tx("clap", "Clap text", 1, "\uD83D\uDC4F", 16f) { clapText() },
            tx("blank", "Blank message", 1, "[ ]", 13f) { type("\u2800") },
            tx("divider", "Fancy art (dividers, frames, faces, symbols)", 1, "\u2501\u2726\u2501", 12f, { panel == Panel.ART }) { togglePanel(Panel.ART) },
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
            ic("keystyle", "Key style", 4, IconView.Kind.KEYSTYLE, { panel == Panel.KEYSTYLE }) { togglePanel(Panel.KEYSTYLE) },
            ic("bg", "Background", 4, IconView.Kind.IMAGE, { panel == Panel.BACKGROUND || bgOn }) { togglePanel(Panel.BACKGROUND) },
            ic("effects", "Effects", 4, IconView.Kind.SNOW, { snowMode != 0 }) { setSnow(if (snowMode != 0) 0 else lastSnow) },
            ic("theme", "Theme", 4, IconView.Kind.THEME, { panel == Panel.THEME }) { togglePanel(Panel.THEME) },
            ic("boom", "Boom", 4, IconView.Kind.BOMB, { panel == Panel.BOOM || boomRunning }) { togglePanel(Panel.BOOM) },
            ic("status", "Statuses", 4, IconView.Kind.STATUS, { panel == Panel.STATUS }) { statusItems = null; togglePanel(Panel.STATUS) },
            tx("news", "News", 4, "\uD83D\uDCF0", 16f, { panel == Panel.NEWS || RemoteConfig.unread(prefs, remoteCfg) > 0 }) { togglePanel(Panel.NEWS) },
            ic("settings", "Settings", 4, IconView.Kind.SETTINGS, { panel == Panel.SETTINGS }) { togglePanel(Panel.SETTINGS) }
        )
    }

    /** All tools except the ones the admin switched off (RemoteConfig flags.disabled). Settings/emoji/language can not be switched off. */
    private val toolList: List<Tool>
        get() = allTools.filter { it.id in alwaysOn || (it.id !in remoteCfg.disabled && it.id !in toolsOff) }
    private var toolsOff: Set<String> = emptySet()      // switched off by the user in the app (Typing -> Tools on / off)

    private val alwaysOn = setOf("settings", "emoji", "lang", "singlish", "news")

    private fun toolById(id: String): Tool? = toolList.firstOrNull { it.id == id }

    private fun loadPinned() {
        toolsOff = (prefs.getString("tools_off", "") ?: "").split(",").filter { it.isNotEmpty() }.toSet()
        val raw = prefs.getString("pinned", null)?.split(",")
            ?.filter { toolById(it) != null }?.distinct()?.toMutableList() ?: mutableListOf()
        if (raw.isEmpty()) raw.addAll(defaultPinned.filter { toolById(it) != null })
        if (raw.isEmpty()) raw.addAll(toolList.take(6).map { it.id })       // everything chosen is switched off: never leave the bar empty
        pinned = ArrayList(raw.take(30))      // chosen in the app (Typing -> Keyboard toolbar) or dragged here
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
        val fl = FrameLayout(this)      // no round background any more: only the icon is drawn
        if (t.kind != null) {
            val iv = IconView(this, t.kind)
            iv.tint = if (active) th.accent else th.text
            val sz = dp((sizeDp * 0.66f).toInt())
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
        if (!editing) return swipeToolbar(bar, circle)
        pinned.take(6).forEachIndexed { i, id ->
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
        val iv = IconView(this, if (editing) IconView.Kind.CLOSE else IconView.Kind.COLLAPSE)
        iv.tint = th.text
        val sz = dp((circle * 0.66f).toInt())
        tc.addView(iv, FrameLayout.LayoutParams(sz, sz, Gravity.CENTER))
        toggle.addView(tc, FrameLayout.LayoutParams(dp(circle), dp(circle), Gravity.CENTER))
        bar.addView(toggle)
        return bar
    }

    /** Short text in the top bar (used by Word count). Goes away by itself. */
    private fun showBarMsg(msg: String) {
        barMsg = msg
        refreshBar()
        handler.removeCallbacks(clearBarMsg)
        handler.postDelayed(clearBarMsg, 3500)
    }

    private val clearBarMsg = Runnable {
        if (barMsg != null) {
            barMsg = null
            refreshBar()
        }
    }

    private fun messageStrip(msg: String): View = TextView(this).apply {
        text = msg
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(th.accent)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.DEFAULT_BOLD
        isClickable = true
        setOnClickListener { barMsg = null; refreshBar() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    /**
     * Normal top bar: swipe sideways to reach EVERY tool (your pinned 6 come first),
     * the expand button stays fixed on the right.
     */
    private fun swipeToolbar(bar: LinearLayout, circle: Int): View {
        val total = if (::root.isInitialized && root.width > 0) root.width else resources.displayMetrics.widthPixels
        val slotW = (total - dp(8)) / 7
        val ordered = ArrayList<Tool>()
        pinned.forEach { id -> toolById(id)?.let { ordered.add(it) } }
        if (prefs.getBoolean("bar_all", true)) toolList.forEach { t -> if (ordered.none { it.id == t.id }) ordered.add(t) }

        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        ordered.forEach { t ->
            val slot = FrameLayout(this).apply {
                isClickable = true
                setOnClickListener {
                    feedback(it)
                    runTool(t)
                }
            }
            slot.addView(toolCircle(t, circle, t.active()), FrameLayout.LayoutParams(dp(circle), dp(circle), Gravity.CENTER))
            strip.addView(slot, LinearLayout.LayoutParams(slotW, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val sv = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = false
            addView(strip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setOnScrollChangeListener { _, x, _, _, _ -> barScrollX = x }
            post { scrollTo(barScrollX, 0) }
        }
        bar.addView(sv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 6f))

        val toggle = FrameLayout(this).apply {
            isClickable = true
            setOnClickListener {
                feedback(it)
                flushSinglish()
                panel = Panel.TOOLS
                render()
            }
        }
        // the app icon opens the all-tools grid (it turns into a close button while the grid is open)
        val iv = android.widget.ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_round)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        val sz = dp(32)
        toggle.addView(iv, FrameLayout.LayoutParams(sz, sz, Gravity.CENTER))
        bar.addView(toggle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
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
        var popIdx = 0
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
                rowTools.forEach { t ->
                    val cell = toolCell(t)
                    popIn(cell, popIdx++)
                    r.addView(cell)
                }
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

    /** Key background for the chosen key style (template + corner decoration). */
    private fun keyBg(color: Int, label: String): android.graphics.drawable.Drawable {
        if (keyTpl == 0 && keyDecor == 0) return roundBg(color, 8)
        val dens = resources.displayMetrics.density
        val seed = label.hashCode()
        // Frost / Sci-Fi / Neon take their outline colour from the background picture or video
        // (or from the theme when there is none)
        val ka = if (keyTpl == 1 || keyTpl == 3 || keyTpl == 6) keyAccent() else th.accent
        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                KeyDrawable(dens, keyTpl, kc(th.pressed), ka, true, keyDecor, seed)
            )
            addState(intArrayOf(), KeyDrawable(dens, keyTpl, kc(color), ka, false, keyDecor, seed))
        }
    }

    private var kaKey = ""
    private var kaVal = 0

    /** Bright, vivid colour of the current background photo/video; the theme accent when there is no background. */
    private fun keyAccent(): Int {
        val photo = if (bgOn) bgBitmap else null
        val bmp: Bitmap? = photo ?: videoName?.let { AnimatedBackgrounds.thumb(this, it) }
        if (bmp == null) return th.accent
        val key = if (photo != null) "p" + System.identityHashCode(photo) else "v" + videoName
        if (key == kaKey) return kaVal
        val v = try {
            vividColorOf(bmp)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
        if (v == null) return th.accent
        kaKey = key
        kaVal = v
        return v
    }

    /** Average of the most colourful pixels, pushed to a bright, saturated colour. Null for grey pictures. */
    private fun vividColorOf(b: Bitmap): Int? {
        val sm = Bitmap.createScaledBitmap(b, 40, 40, true)
        val px = IntArray(40 * 40)
        sm.getPixels(px, 0, 40, 0, 0, 40, 40)
        val hsv = FloatArray(3)
        var sr = 0.0
        var sg = 0.0
        var sb = 0.0
        var ws = 0.0
        for (p in px) {
            Color.colorToHSV(p, hsv)
            val w = (hsv[1] * hsv[1] * hsv[2]).toDouble()      // vivid and bright pixels count most
            sr += Color.red(p) * w
            sg += Color.green(p) * w
            sb += Color.blue(p) * w
            ws += w
        }
        if (ws < 3.0) return null
        Color.colorToHSV(Color.rgb((sr / ws).toInt(), (sg / ws).toInt(), (sb / ws).toInt()), hsv)
        hsv[1] = maxOf(hsv[1], 0.55f)
        hsv[2] = maxOf(hsv[2], 0.95f)
        return Color.HSVToColor(hsv)
    }

    // ----- fancy art panel: dividers, frames for your text, faces, symbols (+ the ones you used last) -----
    private val artTabs = listOf("Dividers", "Frames", "Faces", "Symbols", "Recent")
    private var artTab = 0

    private val artDividers = listOf(
        "━━━━ ✦ ━━━━", "──────  ✿  ──────", "┈┈┈┈┈ ❀ ┈┈┈┈┈", "•°•°•°•°•°•°•°•", "✦•······•✦•······•✦",
        "⋆｡°✩ ━━━━━ ✩°｡⋆", "▬▬▬▬ ஜ ▬▬▬▬", "═══════ ◈ ═══════", "୨୧ ┈┈┈┈┈┈┈ ୨୧", "°❀•°:🎀:°•❀°",
        "☆━━━━━━━☆", "♡ ───── ♡ ───── ♡", "⊹ ࣪ ˖ ───── ⊹ ࣪ ˖", "✧･ﾟ: *✧･ﾟ:*  *:･ﾟ✧*:･ﾟ✧",
        "▁▂▃▄▅▆▇█▇▆▅▄▃▂▁", "◤◢◤◢◤◢◤◢◤◢◤◢", "⋘ ─────── ⋙", "🌸━━━━━━━━🌸", "★彡 ━━━━━ 彡★",
        "·.·´¯`·.·★·.·´¯`·.·", "•·.·´¯`·.·• ❤ •·.·´¯`·.·•", "✿･ﾟ･✿･ﾟ･✿･ﾟ･✿", "┏━━━━━━━━━━━━━┓",
        "┗━━━━━━━━━━━━━┛", "╔═════════════╗", "╚═════════════╝", "- - - - - - - - - -", "~~~~~~~~~~~~~~~~",
        "❖ ── ✦ ── ❖", "🔥━━━━━🔥━━━━━🔥", "💫 ⋆ ✩ ⋆ 💫", "▰▰▰▰▰▰▰▰▰▰", "⎯⎯⎯⎯ ⌘ ⎯⎯⎯⎯", "𓆩♡𓆪 ┈┈┈┈┈ 𓆩♡𓆪"
    )

    /** {} = your text */
    private val artFrames = listOf(
        "『{}』", "【{}】", "《{}》", "「{}」", "✦ {} ✦", "❀ {} ❀", "•°• {} •°•", "꧁{}꧂",
        "✧･ﾟ: *{}*:･ﾟ✧", "♡ {} ♡", "★彡 {} 彡★", "୨୧ {} ୨୧", "⋆｡°✩ {} ✩°｡⋆", "┏━ {} ━┓",
        "▬▬ {} ▬▬", "✿ {} ✿", "🌸 {} 🌸", "🔥 {} 🔥", "⚡ {} ⚡", "𓆩 {} 𓆪", "｡･:*:･ﾟ★ {} ★ﾟ･:*:･｡", "❝{}❞"
    )

    private val artFaces = listOf(
        "(◕‿◕✿)", "(づ｡◕‿‿◕｡)づ", "ʕ•ᴥ•ʔ", "(≧◡≦)", "( ˘▽˘)っ♨", "(╯°□°）╯︵ ┻━┻", "¯\\_(ツ)_/¯", "(ಥ﹏ಥ)",
        "(◍•ᴗ•◍)❤", "٩(◕‿◕｡)۶", "(｡♥‿♥｡)", "ヽ(´▽`)/", "(⌐■_■)", "(¬‿¬)", "(｡•́︿•̀｡)", "(ノ◕ヮ◕)ノ*:･ﾟ✧",
        "(♡´▽`♡)", "ᕦ(ò_óˇ)ᕤ", "(ᵔᴥᵔ)", "૮₍ ˶ᵔ ᵕ ᵔ˶ ₎ა", "(｡•̀ᴗ-)✧", "(っ˘ω˘ς )", "(๑>◡<๑)", "(*^‿^*)"
    )

    private val artSymbols = ("★ ☆ ✦ ✧ ✩ ✪ ✫ ✬ ❀ ✿ ❁ ❃ ❋ ♡ ♥ ❤ ❥ ❣ ☘ ☾ ☽ ☀ ☁ ☂ ♪ ♫ ♬ ✔ ✘ ➤ ➜ ➔ ► ◄ ▲ ▼ ◆ ◇ ● ○ ◎ ■ □ " +
        "✂ ✈ ☎ ✉ ☮ ☯ ∞ ≈ ≠ ± × ÷ © ® ™ ° ‣ • ◦ ※ ⁂ ✱ ❖ ✾ ❂ ☻ ☺ ⚘ ⚜ ♔ ♕ ♚ ♛ ✡ ☪ ❝ ❞ « » ‹ › ⋆ ˚ ʚ ɞ ꒰ ꒱ ᵔ ᴗ ˃ ˂ ♢ ♤ ♧").split(" ")

    private fun artRecent(): List<String> =
        (prefs.getString("art_recent", "") ?: "").split("\u0001").filter { it.isNotEmpty() }

    private fun artRemember(s: String) {
        val l = ArrayList<String>(artRecent())
        l.remove(s)
        l.add(0, s)
        prefs.edit().putString("art_recent", l.take(14).joinToString("\u0001")).apply()
    }

    /** Puts art into the text box. Dividers get their own line. */
    private fun artInsert(s: String, ownLine: Boolean) {
        val ic = currentInputConnection ?: return
        flushSinglish()
        var out = s
        if (ownLine) {
            val b = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
            out = (if (b.isNotEmpty() && b != "\n") "\n" else "") + s + "\n"
        }
        ic.commitText(out, 1)
        artRemember(s)
    }

    /** Wraps the selected text (or the line before the cursor) with a frame; with nothing to wrap the cursor lands inside it. */
    private fun artWrap(frame: String) {
        val ic = currentInputConnection ?: return
        flushSinglish()
        val pre = frame.substringBefore("{}")
        val post = frame.substringAfter("{}")
        val sel = ic.getSelectedText(0)?.toString()
        if (!sel.isNullOrEmpty()) {
            ic.commitText(pre + sel + post, 1)
        } else {
            val line = (ic.getTextBeforeCursor(200, 0)?.toString() ?: "").substringAfterLast('\n')
            if (line.isNotBlank()) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(line.length, 0)
                ic.commitText(pre + line.trim() + post, 1)
                ic.endBatchEdit()
            } else {
                ic.beginBatchEdit()
                ic.commitText(pre, 1)
                ic.commitText(post, 0)       // cursor stays between the two halves
                ic.endBatchEdit()
            }
        }
        artRemember(frame)
    }

    private fun renderArt() {
        root.addView(panelHeader("Fancy art"))
        root.addView(chipRow(artTabs, artTab) {
            artTab = it
            render()
        })
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        fun grid(items: List<String>, perRow: Int, sp: Float, heightDp: Int, label: (String) -> String, tap: (String) -> Unit) {
            items.chunked(perRow).forEach { rowItems ->
                val r = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp))
                }
                rowItems.forEach { s ->
                    r.addView(TextView(this).apply {
                        text = label(s)
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setTextColor(th.text)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
                        background = roundBg(th.key, 10)
                        isClickable = true
                        setPadding(dp(4), 0, dp(4), 0)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                            setMargins(dp(3), dp(3), dp(3), dp(3))
                        }
                        setOnClickListener {
                            feedback(it)
                            tap(s)
                        }
                    })
                }
                repeat(perRow - rowItems.size) {
                    r.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
                }
                list.addView(r)
            }
        }
        when (artTab) {
            0 -> grid(artDividers, 1, 15f, 40, { it }) { artInsert(it, true) }
            1 -> {
                list.addView(hintText("Select text (or write a line), then tap a frame. With nothing written the frame opens empty."))
                grid(artFrames, 2, 14f, 42, { it.replace("{}", "text") }) { artWrap(it) }
            }
            2 -> grid(artFaces, 2, 16f, 42, { it }) { artInsert(it, false) }
            3 -> grid(artSymbols, 8, 20f, 42, { it }) { artInsert(it, false) }
            else -> {
                val rec = artRecent()
                if (rec.isEmpty()) list.addView(hintText("Nothing yet. The art you use shows up here."))
                else grid(rec, 2, 14f, 42, { it.replace("{}", "text") }) { if (it.contains("{}")) artWrap(it) else artInsert(it, artDividers.contains(it)) }
            }
        }
        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(keyH * 4) - dp(42))
            isVerticalScrollBarEnabled = false
            addView(list)
        })
    }

    // ----- key style panel: live sample keys + template and decoration pickers -----
    private fun renderKeyStyle() {
        root.addView(panelHeader("Key style"))
        val sample = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(keyH))
            setPadding(dp(4), 0, dp(4), 0)
        }
        sample.addView(keyView(Key("Q", 1f, Style.NORMAL, 19f) {}))
        sample.addView(keyView(Key("W", 1f, Style.NORMAL, 19f) {}))
        sample.addView(keyView(Key("E", 1f, Style.NORMAL, 19f) {}))
        sample.addView(keyView(Key("?123", 1.4f, Style.SPECIAL, 14f) {}))
        sample.addView(keyView(Key("RIO", 2f, Style.NORMAL, 13f, space = true) {}))
        sample.addView(keyView(Key("↵", 1.3f, Style.ACCENT, 20f) {}))
        root.addView(sample)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), 0, dp(6), dp(10))
        }
        fun pick(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
            options.chunked(4).forEachIndexed { r, rowOpts ->
                val base = r * 4
                box.addView(chipRow(rowOpts, selected - base) { onPick(base + it) })
            }
        }
        box.addView(settingLabel("Template"))
        pick(KeyStyles.templates, KeyStyles.codes.indexOf(keyTpl)) {
            keyTpl = KeyStyles.codes[it]
            prefs.edit().putInt("ktpl", keyTpl).apply()
            render()
        }
        box.addView(settingLabel("Decoration"))
        pick(KeyStyles.decors, keyDecor) {
            keyDecor = it
            prefs.edit().putInt("kdecor", keyDecor).apply()
            render()
        }
        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(keyH * 3))
            isVerticalScrollBarEnabled = false
            addView(box)
        })
    }

    private fun feedback(v: View) {
        if (haptic) Haptics.buzz(this, hapticLevel, v)
        bounce(v)
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

    // ----- emoji panel (built-in tabs + emoji / sticker packs from the admin) -----
    private class EmojiTab(val icon: String, val emojis: List<String>, val pack: RemoteConfig.Pack?)

    private fun emojiTabs(): List<EmojiTab> =
        emojiCats.map { EmojiTab(it.first, it.second, null) } +
            remoteCfg.packs.take(4).map { EmojiTab(it.icon, if (it.type == "emoji") it.items else emptyList(), it) }

    private val packBmps = HashMap<String, Bitmap>()
    private var packLoading = false

    private fun packFile(pack: RemoteConfig.Pack, path: String): File {
        val ext = if (path.endsWith(".gif")) "gif" else "png"
        // pk_ files are hidden from "my stickers" (StickerStore.list)
        return File(StickerStore.dir(this), "pk_" + pack.id + "_" + Integer.toHexString(path.hashCode()) + "." + ext)
    }

    /** Grid of sticker pictures; missing ones are downloaded once (max 600 KB each) and the panel redraws. */
    private fun stickerPackGrid(pack: RemoteConfig.Pack): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val missing = pack.items.filter { !packFile(pack, it).exists() }
        if (missing.isNotEmpty() && !packLoading) {
            packLoading = true
            Thread {
                missing.forEach { RemoteConfig.fetch(it, packFile(pack, it), 600 * 1024) }
                handler.post {
                    packLoading = false
                    if (::root.isInitialized && panel == Panel.EMOJI) render()
                }
            }.start()
        }
        pack.items.chunked(4).forEach { rowItems ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { path ->
                val f = packFile(pack, path)
                val key = f.name
                var bmp = packBmps[key]
                if (bmp == null && f.exists()) {
                    try {
                        bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 2 })
                        if (bmp != null) packBmps[key] = bmp
                    } catch (e: Exception) {
                    } catch (e: OutOfMemoryError) {
                    }
                }
                r.addView(FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(78), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
                    background = roundBg(th.key, 10)
                    isClickable = true
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    if (bmp != null) {
                        addView(android.widget.ImageView(this@RioBoardService).apply {
                            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                            setImageBitmap(bmp)
                        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    } else {
                        addView(TextView(this@RioBoardService).apply {
                            text = "\u2026"
                            gravity = Gravity.CENTER
                            setTextColor(th.hint)
                        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                    setOnClickListener {
                        feedback(it)
                        if (f.exists()) sendSticker(f) else toast("Loading...")
                    }
                })
            }
            repeat(4 - rowItems.size) {
                r.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
            }
            col.addView(r)
        }
        return col
    }

    private fun renderEmoji() {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(keyH))
        }
        header.addView(backButton())
        val tabs = emojiTabs()
        if (emojiCat >= tabs.size) emojiCat = 0
        tabs.forEachIndexed { i, cat ->
            header.addView(TextView(this).apply {
                text = cat.icon
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
        val curTab = tabs[emojiCat]
        curTab.pack?.let { p -> if (p.type == "sticker") grid.addView(stickerPackGrid(p)) }
        curTab.emojis.chunked(8).forEach { items ->
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
        DefaultBackgrounds.items.filter { it.second !in remoteCfg.hiddenBg }.forEach { (_, file) ->
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
        remoteCfg.backgrounds.forEach { b ->
            val iv = android.widget.ImageView(this).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(th.key)
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
                    }
                }
            }
            loadRemoteThumb(b, iv)
            tiles.add(tile(current == "remote:" + b.id, iv) { applyRemoteBg(b) })
        }
        fun addTiles(list: List<View>) {
            list.chunked(3).forEach { rowTiles ->
                val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                rowTiles.forEach { r.addView(it) }
                repeat(3 - rowTiles.size) {
                    r.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
                }
                grid.addView(r)
            }
        }
        addTiles(tiles)
        grid.addView(settingLabel("Animated ▶ (loops, mutes itself when the keyboard closes)"))
        val vtiles = AnimatedBackgrounds.all(this).map { (_, id) ->
            val cell = FrameLayout(this).apply {
                addView(android.widget.ImageView(this@RioBoardService).apply {
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(th.key)
                    AnimatedBackgrounds.thumb(this@RioBoardService, id)?.let { setImageBitmap(it) }
                    clipToOutline = true
                    outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: android.graphics.Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
                        }
                    }
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(TextView(this@RioBoardService).apply {
                    text = "▶"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END))
            }
            tile(id == videoName, cell) { AnimatedBackgrounds.use(this, id) }
        }
        addTiles(vtiles)
        root.addView(panelBody(grid))
    }

    // ----- message boom: repeat the message that is in the text box -----
    private fun boomSetStatus(t: String) {
        boomStatusText = t
        boomStatus?.text = t
    }

    private fun boomSend(ic: android.view.inputmethod.InputConnection) {
        when (boomMethod) {
            1 -> ic.performEditorAction(EditorInfo.IME_ACTION_SEND)
            2 -> {
                val t = SystemClock.uptimeMillis()
                ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0, KeyEvent.META_CTRL_ON))
                ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0, KeyEvent.META_CTRL_ON))
            }
            else -> pressEnter()
        }
    }

    private fun boomKeyOf(info: EditorInfo?): String = (info?.packageName ?: "") + "/" + (info?.fieldId ?: 0)

    private fun boomStart() {
        flushSinglish()
        val ic = currentInputConnection ?: return
        val msg = readField(ic).all.trim()
        if (msg.isBlank()) {
            boomSetStatus("Type a message in the chat box first")
            return
        }
        boomMsg = msg
        boomKey = boomKeyOf(currentInputEditorInfo)
        boomTotal = boomCount
        boomMethod = 0
        boomLeft = boomTotal
        boomChecking = false
        boomFails = 0
        boomRunning = true
        boomSetStatus("Sending...")
        handler.post(boomStep)
        render()
    }

    /** One round: make sure the chat box holds exactly the message, press send, check on the next round that it went. */
    private fun boomTick() {
        if (!boomRunning) return
        val ic = currentInputConnection
        if (ic == null) {
            boomStop("Stopped")
            return
        }
        val f = readField(ic)
        val cur = f.all
        if (boomChecking) {
            boomChecking = false
            if (cur.trim() == boomMsg) {
                // still sitting in the box: the app did not send it
                boomLeft++
                boomFails++
                if (boomFails >= 2) {
                    boomFails = 0
                    boomMethod++                 // try the next way of pressing send
                    if (boomMethod > 2) {
                        boomStop("Not sending. In WhatsApp turn on Settings > Chats > Enter is send, then try again")
                        return
                    }
                }
            } else {
                boomFails = 0
            }
        }
        if (boomLeft <= 0) {
            boomStop("Done: $boomTotal sent")
            return
        }
        if (cur != boomMsg) {
            // box is empty (after a send) or has something else: put exactly the message in it
            replaceWholeField(ic, f, boomMsg)
        }
        boomSend(ic)
        boomLeft--
        boomChecking = true
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
        box.addView(hintText("1) Type your message in the chat box   2) Pick how many times and the speed   3) Press Start. In WhatsApp turn on Settings > Chats > Enter is send."))
        box.addView(settingLabel("How many times"))
        box.addView(chipRow(boomCounts.map { "${it}x" }, boomCounts.indexOf(boomCount)) {
            boomCount = boomCounts[it]
            prefs.edit().putInt("boom_n", boomCount).apply()
            render()
        })
        box.addView(chipRow(listOf("-10", "-1", "$boomCount times", "+1", "+10"), -1) {
            val step = when (it) { 0 -> -10; 1 -> -1; 3 -> 1; 4 -> 10; else -> 0 }
            if (step != 0) {
                boomCount = (boomCount + step).coerceIn(1, 500)
                prefs.edit().putInt("boom_n", boomCount).apply()
                render()
            }
        })
        box.addView(settingLabel("Speed (time between messages)"))
        box.addView(chipRow(listOf("Fast 0.5s", "Normal 1s", "Slow 2s"), boomDelayIdx) {
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

    /** Opens the full size preview (Save / Share / Delete) for a status, or the whole gallery when [item] is null. */
    private fun openStatusViewer(item: StatusSaver.Item?) {
        try {
            val i = Intent(this, StatusActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (item != null) i.putExtra("open", item.uri.toString())
            startActivity(i)
        } catch (e: Exception) {
        }
    }

    /** Called by StatusActivity after something was saved or deleted. */
    fun statusChangedFromApp() {
        statusItems = null
        if (::root.isInitialized && panel == Panel.STATUS) render()
    }

    /** Called by the app (Look tab) after the effect, area or colour changed. */
    fun reloadEffects() {
        snowMode = prefs.getInt("snow", 2).coerceIn(0, 2)
        lastSnow = prefs.getInt("lastsnow", 2).coerceIn(1, 2)
        snowColorIdx = prefs.getInt("snowcol", 0).coerceIn(0, snowPalette.size - 1)
        effectIdx = prefs.getInt("effect", 0).coerceIn(0, EffectView.Type.values().size - 1)
        applySnow()
        if (::root.isInitialized) render()
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
        box.addView(hintText("Tap a status for the full size preview (Save · Share · Delete)."))
        box.addView(TextView(this).apply {
            text = "Open full gallery"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = roundBg(th.accent, 12)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)).apply {
                setMargins(dp(3), dp(2), dp(3), dp(6))
            }
            isClickable = true
            setOnClickListener { openStatusViewer(null) }
        })
        fun tile(item: StatusSaver.Item): View {
            val key = item.uri.toString()
            return FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(110), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
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
                if (statusSaved.contains(key) || StatusSaver.savedKeys.contains(key)) {
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
                    openStatusViewer(item)
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
        val nb = baseThemes.size
        val hid = remoteCfg.hiddenThemes
        section("Classic", all.filter { it.index < nb && it.value.live == null && it.value.name !in hid })
        section("Live themes (animated)", all.filter { it.index < nb && it.value.live != null && it.value.name !in hid })
        if (themes.size > nb) section("New from RIO", all.filter { it.index >= nb })
        root.addView(panelBody(grid))
    }

    private fun swatch(index: Int, t0: Theme): View {
        val t = if (isSystemTheme(t0)) resolveTheme(t0) else t0
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
            val rid = if (index >= baseThemes.size) remoteCfg.themes.getOrNull(index - baseThemes.size)?.id ?: "" else ""
            prefs.edit().putInt("theme", if (rid.isEmpty()) themeIdx else 0).putString("theme_remote", rid).apply()
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
        box.addView(settingLabel("Vibration strength"))
        box.addView(chipRow(Haptics.names, hapticLevel) {
            hapticLevel = it
            haptic = it > 0
            prefs.edit().putBoolean("haptic", haptic).putInt("hapticlvl", hapticLevel).apply()
            Haptics.buzz(this, hapticLevel, null)   // feel the new strength straight away
            render()
        })
        box.addView(settingLabel("Key press squeeze"))
        box.addView(chipRow(listOf("Off", "Soft", "Normal", "Strong"), pressLevel) {
            pressLevel = it
            prefs.edit().putInt("pressanim", pressLevel).apply()
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
        box.addView(settingLabel("Keyboard width"))
        val widths = intArrayOf(100, 90, 80, 70, 60)
        box.addView(chipRow(widths.map { "$it%" }, widths.indexOf(widthPct)) {
            widthPct = widths[it]
            prefs.edit().putInt("widthpct", widthPct).apply()
            render()
        })
        box.addView(settingLabel("Keyboard height (fine tune)"))
        val heights = intArrayOf(80, 90, 100, 110, 125)
        box.addView(chipRow(heights.map { "$it%" }, heights.indexOf(heightPct)) {
            heightPct = heights[it]
            prefs.edit().putInt("heightpct", heightPct).apply()
            render()
        })
        box.addView(settingLabel("One-handed mode"))
        box.addView(chipRow(listOf("Off", "◀ Left", "Right ▶"), oneHand) { setOneHand(it) })
        box.addView(settingLabel("Number row"))
        box.addView(chipRow(listOf("Show", "Hide"), if (numberRow) 0 else 1) {
            numberRow = it == 0
            prefs.edit().putBoolean("numrow", numberRow).apply()
            render()
        })
        box.addView(settingLabel("Long-press popups (a → á ä, 1 → !)"))
        box.addView(chipRow(listOf("On", "Off"), if (popupsOn) 0 else 1) {
            popupsOn = it == 0
            prefs.edit().putBoolean("popups", popupsOn).apply()
            render()
        })
        box.addView(settingLabel("Swipe typing"))
        box.addView(chipRow(listOf("On", "Off"), if (swipeOn) 0 else 1) {
            swipeOn = it == 0
            prefs.edit().putBoolean("swipe", swipeOn).apply()
            render()
        })
        box.addView(settingLabel("Singlish auto-correct"))
        box.addView(chipRow(listOf("On", "Off"), if (autoCorrect) 0 else 1) {
            autoCorrect = it == 0
            prefs.edit().putBoolean("autocorrect", autoCorrect).apply()
        })
        box.addView(settingLabel("Next-word suggestions"))
        box.addView(chipRow(listOf("On", "Off"), if (nextWordOn) 0 else 1) {
            nextWordOn = it == 0
            prefs.edit().putBoolean("nextword", nextWordOn).apply()
            suggestions = computeSuggestions()
            render()
        })
        box.addView(settingLabel("Toolbar icon style"))
        box.addView(chipRow(listOf("Neo (new)", "Classic"), if (IconView.neo) 0 else 1) {
            IconView.neo = it == 0
            prefs.edit().putBoolean("neoicons", IconView.neo).apply()
            render()
        })
        box.addView(settingLabel("Key sound (also needs touch sounds on in phone settings)"))
        box.addView(chipRow(listOf("Off", "Low", "Medium", "High"), soundLevel) {
            soundLevel = it
            prefs.edit().putInt("sound", soundLevel).apply()
        })
        box.addView(settingLabel("Auto-capital and double-space → \". \""))
        box.addView(chipRow(listOf("On", "Off"), if (autoCap) 0 else 1) {
            autoCap = it == 0
            prefs.edit().putBoolean("autocap", autoCap).apply()
        })
        box.addView(settingLabel("Emoji suggestions"))
        box.addView(chipRow(listOf("On", "Off"), if (emojiSug) 0 else 1) {
            emojiSug = it == 0
            prefs.edit().putBoolean("emojisug", emojiSug).apply()
        })
        box.addView(settingLabel("Backup learned words and shortcuts"))
        box.addView(chipRow(listOf("Copy backup", "Restore from clipboard"), -1) {
            if (it == 0) backupToClipboard() else restoreFromClipboard()
        })
        box.addView(settingLabel("Sinhala / Tamil layout"))
        box.addView(chipRow(listOf("Sinhala", "Wijesekara", "Tamil", "Easy"), layoutKind) { setLayout(it) })
        box.addView(settingLabel("Languages in the \uD83C\uDF10 button (tap to switch on / off)"))
        Languages.entries.chunked(3).forEach { part ->
            box.addView(chipRow(part.map { (if (Languages.isOn(prefs, it.id)) "\u2713 " else "") + it.native }, -1) { i ->
                Languages.toggle(prefs, part[i].id)
                render()
            })
        }
        box.addView(settingLabel("Typing"))
        box.addView(chipRow(listOf("English", "Singlish → සිංහල"), if (singlishOn) 1 else 0) { setSinglish(it == 1) })
        box.addView(settingLabel("Word animations (love, birthday, haha ...)"))
        box.addView(chipRow(listOf("On", "Off"), if (prefs.getBoolean("wordfx", true)) 0 else 1) {
            prefs.edit().putBoolean("wordfx", it == 0).apply()
            render()
        })
        box.addView(settingLabel("Singlish: show the Sinhala letter above each key"))
        box.addView(chipRow(listOf("Show", "Hide"), if (singlishHints) 0 else 1) {
            singlishHints = it == 0
            prefs.edit().putBoolean("singlish_hints", singlishHints).apply()
            render()
        })
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
        box.addView(settingLabel("Animated backgrounds"))
        box.addView(AnimatedBackgrounds.strip(this))
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
            if (!numberRow) root.addView(row(chars("1234567890")))
            root.addView(row(chars("@#$%&*-+()")))
            root.addView(row(chars("!\"':;/?_=~")))
            root.addView(row(chars("<>[]{}\\|^`") + Key("⌫", 1.3f, Style.SPECIAL, repeat = true) { deleteOne() }))
        } else {
            val upper = shift != 0
            fun letters(s: String, topRow: Boolean = false) = s.mapIndexed { i, c ->
                val label = if (upper) c.uppercaseChar().toString() else c.toString()
                val digit = if (topRow && !numberRow) "1234567890"[i] else null
                Key(label, alts = altsFor(c, digit, upper), letter = c, hint = singlishHintFor(c, upper)) {
                    typeLetter(singlishSend(c, label))
                    if (shift == 1) {
                        shift = 0
                        render()
                    }
                }
            }
            root.addView(row(letters("qwertyuiop", true)))
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
                    Key(Languages.keyLabel(layoutKind), 1.3f, Style.SPECIAL, 14f, onLong = { setSinglish(!singlishOn) }) {
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
        when (layoutKind) {
            1 -> renderWijesekara()
            2 -> renderTamil()
            3 -> renderEasy()
            in 4..99 -> renderExtra()
            else -> renderSinhalaGrid()
        }
    }

    private fun digitAlt(row: Int, col: Int): List<String> =
        if (row == 0 && !numberRow) listOf("1234567890"[col].toString()) else emptyList()

    private fun renderSinhalaGrid() {
        val layer = when (siLayer) {
            0 -> si1
            1 -> si2
            else -> si3
        }
        layer.forEachIndexed { r, keys ->
            root.addView(row(keys.mapIndexed { c, s ->
                Key(sinLabel(s), textSp = 22f, alts = digitAlt(r, c)) { decorateWordStart(s); type(s) }
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
        root.addView(indicBottomRow(Key(nextLabel, 1.3f, Style.ACTIVE, 20f) { siLayer = (siLayer + 1) % 3; render() }))
    }

    // ----- Easy Sinhala: consonants and the common vowel signs on ONE screen -----
    // type a consonant, then tap its sign right below - no layer switching for everyday words.
    // Long-press a consonant for ready-made syllables (ක -> කා කැ කි කී කු කූ කෙ කො).
    private val easyMain = listOf(
        listOf("ක", "ග", "ච", "ජ", "ට", "ඩ", "ත", "ද", "ප", "බ"),
        listOf("ම", "ය", "ර", "ල", "ව", "ස", "හ", "න", "ණ", "ළ"),
        listOf("\u0DCF", "\u0DD0", "\u0DD2", "\u0DD3", "\u0DD4", "\u0DD6", "\u0DD9", "\u0DDA", "\u0DDC", "\u0DCA")
    )
    private val easyPili = listOf("\u0DCF", "\u0DD0", "\u0DD2", "\u0DD3", "\u0DD4", "\u0DD6", "\u0DD9", "\u0DDC")

    private fun renderEasy() {
        val layer = when (siLayer) {
            0 -> easyMain
            1 -> si2                                   // vowels
            else -> listOf(si1[2], si3[0], si3[1])     // rarer consonants + rarer signs
        }
        layer.forEachIndexed { r, keys ->
            root.addView(row(keys.mapIndexed { c, s ->
                val combos = if (siLayer == 0 && r < 2 && popupsOn) easyPili.map { s + it } else emptyList()
                Key(sinLabel(s), textSp = 22f, alts = digitAlt(r, c) + combos) {
                    decorateWordStart(s)
                    easyType(s)
                }
            }))
        }
        val nextLabel = when ((siLayer + 1) % 3) {
            0 -> "ක"
            1 -> "අ"
            else -> "ඛ"
        }
        root.addView(indicBottomRow(Key(nextLabel, 1.3f, Style.ACTIVE, 20f) { siLayer = (siLayer + 1) % 3; render() }))
    }

    /** Like wijType, but tapping a different vowel sign right after one swaps it (fixes a wrong tap). */
    private fun easyType(s: String) {
        val ic = currentInputConnection
        if (ic != null && s.length == 1) {
            val c0 = s[0]
            val last = ic.getTextBeforeCursor(1, 0)?.toString()?.lastOrNull()
            fun isSign(c: Char?) = c != null && (c in '\u0DCF'..'\u0DDF' || c == '\u0DF2')
            val joins = (c0 == '\u0DCF' && last == '\u0DD9') || (c0 == '\u0DCA' && last == '\u0DDC') ||
                (c0 == '\u0DD9' && last == '\u0DD9') || (c0 == '\u0DDF' && last == '\u0DD9')
            if (!joins && isSign(c0) && isSign(last)) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText(s, 1)
                ic.endBatchEdit()
                suggestions = computeSuggestions()
                refreshBar()
                return
            }
        }
        wijType(s)
    }

    // ----- Tamil (3 layers: consonants / vowels / vowel signs) -----
    private val ta1 = listOf(
        listOf("க", "ங", "ச", "ஞ", "ட", "ண", "த", "ந", "ப", "ம"),
        listOf("ய", "ர", "ல", "வ", "ழ", "ள", "ற", "ன", "ஜ", "ஷ"),
        listOf("ஸ", "ஹ", "க்ஷ", "ஶ")
    )
    private val ta2 = listOf(
        listOf("அ", "ஆ", "இ", "ஈ", "உ", "ஊ", "எ", "ஏ", "ஐ", "ஒ"),
        listOf("ஓ", "ஔ", "ஃ")
    )
    private val ta3 = listOf(
        listOf("\u0BBE", "\u0BBF", "\u0BC0", "\u0BC1", "\u0BC2", "\u0BC6", "\u0BC7", "\u0BC8", "\u0BCA", "\u0BCB"),
        listOf("\u0BCC", "\u0BCD")
    )

    private fun renderTamil() {
        val layer = when (siLayer) {
            0 -> ta1
            1 -> ta2
            else -> ta3
        }
        layer.forEachIndexed { r, keys ->
            root.addView(row(keys.mapIndexed { c, s ->
                Key(sinLabel(s), textSp = 22f, alts = digitAlt(r, c)) { decorateWordStart(s); type(s) }
            }, pad = (10 - keys.size) / 2f))
        }
        repeat(3 - layer.size) {
            root.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(keyH))
            })
        }
        val nextLabel = when ((siLayer + 1) % 3) {
            0 -> "க"
            1 -> "அ"
            else -> "\u25CC\u0BBE"
        }
        root.addView(indicBottomRow(Key(nextLabel, 1.3f, Style.ACTIVE, 20f) { siLayer = (siLayer + 1) % 3; render() }))
    }

    // ----- Wijesekara (standard Sinhala typewriter layout, SLS 1134), key positions follow QWERTY -----
    private val wijN = listOf(
        listOf("\u0DD4", "අ", "\u0DD0", "ර", "එ", "හ", "ම", "ස", "ද", "ච"),
        listOf("\u0DCA", "\u0DD2", "\u0DCF", "\u0DD9", "ට", "ය", "ව", "න", "ක", "ත"),
        listOf("\u0D82", "ජ", "ඩ", "ඉ", "බ", "ප", "ල", "ග")
    )
    private val wijS = listOf(
        listOf("\u0DD6", "උ", "\u0DD1", "ඍ", "ඔ", "ශ", "ඹ", "ෂ", "ධ", "ඡ"),
        listOf("\u0DDF", "\u0DD3", "\u0DD8", "ෆ", "ඨ", "\u0DCA\u200D\u0DBA", "\u0DDB", "ණ", "ඛ", "ථ"),
        listOf("ඞ", "ඣ", "ඪ", "ඊ", "භ", "ඵ", "ළ", "ඝ")
    )
    /** AltGr letters of the standard layout, reached with a long-press on the key. */
    private val wijAlts: Map<String, List<String>> = mapOf(
        "ද" to listOf("ඳ"), "\u0DCA" to listOf("\u0DF3"), "\u0D82" to listOf("\u0D83"),
        "ජ" to listOf("ඦ"), "ඩ" to listOf("ඬ"), "ල" to listOf("ඏ"), "ග" to listOf("ඟ"),
        "ච" to listOf("ඤ", "ඥ")
    )
    private val RAKAR = "\u0DCA\u200D\u0DBB"
    private val REPAYA = "\u0DBB\u0DCA\u200D"

    private fun renderWijesekara() {
        val layer = if (shift != 0) wijS else wijN
        layer.forEachIndexed { r, keys ->
            val ks = keys.mapIndexed { c, s ->
                val extra = digitAlt(r, c) + (if (popupsOn) wijAlts[wijN[r][c]].orEmpty() else emptyList())
                Key(sinLabel(s), textSp = 22f, alts = extra) {
                    decorateWordStart(s)
                    wijType(s)
                    if (shift == 1) {
                        shift = 0
                        render()
                    }
                }
            }
            if (r == 2) {
                val shiftKey = Key(
                    if (shift == 2) "⇪" else "⇧", 1.3f,
                    if (shift == 0) Style.SPECIAL else Style.ACTIVE
                ) { toggleShift() }
                val bs = Key("⌫", 1.3f, Style.SPECIAL, repeat = true) { deleteOne() }
                root.addView(row(listOf(shiftKey) + ks + bs))
            } else {
                root.addView(row(ks))
            }
        }
        root.addView(
            row(
                listOf(
                    Key("ABC", 1.3f, Style.SPECIAL, 14f, onLong = { cycleLayout() }) { siMode = false; render() },
                    Key(sinLabel(RAKAR), 1.2f, Style.ACTIVE, 20f) { wijType(RAKAR) },
                    Key(",") { type(",") },
                    spaceKey(3.5f),
                    Key(".") { type(".") },
                    Key(REPAYA, 1.2f, Style.ACTIVE, 20f) { type(REPAYA) },
                    Key("⏎", 1.5f, Style.ACCENT) { pressEnter() }
                )
            )
        )
    }

    /**
     * Typewriter habit: the vowel sign ෙ / ෛ is typed BEFORE its consonant, and ො ෝ ෞ are built
     * from parts. This reorders and joins them so the text comes out as proper Unicode.
     */
    private fun wijType(s: String) {
        val ic = currentInputConnection
        if (ic == null || s.length != 1) {
            type(s)
            return
        }
        val c0 = s[0]
        val b = ic.getTextBeforeCursor(3, 0)?.toString() ?: ""
        val last = b.lastOrNull()
        val prev = if (b.length >= 2) b[b.length - 2] else null
        fun isCons(c: Char?) = c != null && c in '\u0D9A'..'\u0DC6'
        fun swap(n: Int, t: String) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(n, 0)
            ic.commitText(t, 1)
            ic.endBatchEdit()
            suggestions = computeSuggestions()
            refreshBar()
        }
        when {
            isCons(c0) && (last == '\u0DD9' || last == '\u0DDB') && !isCons(prev) -> swap(1, "$c0$last")
            c0 == '\u0DCF' && last == '\u0DD9' -> swap(1, "\u0DDC")   // ෙ + ා = ො
            c0 == '\u0DCA' && last == '\u0DDC' -> swap(1, "\u0DDD")   // ො + ් = ෝ
            c0 == '\u0DD9' && last == '\u0DD9' -> swap(1, "\u0DDB")   // ෙ + ෙ = ෛ
            c0 == '\u0DDF' && last == '\u0DD9' -> swap(1, "\u0DDE")   // ෙ + ෟ = ෞ
            else -> type(s)
        }
    }

    /** Sinhala/Tamil signs get a dotted circle so they are visible on their own key. */
    private fun sinLabel(s: String): String {
        val c = s[0]
        val sign = c == '\u0DCA' || c in '\u0DCF'..'\u0DDF' || c in '\u0DF2'..'\u0DF3' ||
            c == '\u0D82' || c == '\u0D83' || c in '\u0BBE'..'\u0BCD' || c == '\u0BD7'
        return if (sign) "\u25CC$s" else s
    }

    private fun indicBottomRow(layerKey: Key): LinearLayout = row(
        listOf(
            Key("ABC", 1.3f, Style.SPECIAL, 14f, onLong = { cycleLayout() }) { siMode = false; render() },
            layerKey,
            Key(",") { type(",") },
            spaceKey(4f),
            Key(".") { type(".") },
            Key("⌫", 1.3f, Style.SPECIAL, repeat = true) { deleteOne() },
            Key("⏎", 1.5f, Style.ACCENT) { pressEnter() }
        )
    )

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
        if (pressLevel == 0) {
            v.scaleX = 1f
            v.scaleY = 1f
            return
        }
        val sq = floatArrayOf(1f, 0.97f, 0.94f, 0.88f)[pressLevel]
        if (down) v.animate().scaleX(sq).scaleY(sq).setDuration(50)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        else v.animate().scaleX(1f).scaleY(1f).setDuration(180)
            .setInterpolator(android.view.animation.OvershootInterpolator(2.4f)).start()
    }

    /** Light keyboard "tick" on key touch. */
    private fun keyTick(v: View) {
        if (haptic) Haptics.buzz(this, hapticLevel, v)
        if (soundLevel > 0) {
            try {
                val vol = floatArrayOf(0f, 0.25f, 0.5f, 1f)[soundLevel]
                (getSystemService(AUDIO_SERVICE) as AudioManager)
                    .playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, vol)
            } catch (e: Exception) {
            }
        }
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
        val tv = HintTextView(this)
        tv.text = k.label
        if (k.hint != null) {
            tv.hintText = k.hint
            tv.hintColor = (th.hint and 0x00FFFFFF) or (0xCC shl 24)
            tv.hintSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9.5f, resources.displayMetrics)
        }
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
        tv.background = keyBg(
            when (k.style) {
                Style.NORMAL -> th.key
                Style.SPECIAL -> th.special
                Style.ACCENT -> th.accent
                Style.ACTIVE -> th.active
            }, k.label
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
            var bx = 0f
            tv.setOnTouchListener { view, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.isPressed = true
                        pressAnim(view, true)
                        keyTick(view)
                        bx = ev.rawX
                        k.onPress()
                        handler.postDelayed(r, 400)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // slide left on backspace: delete a word per step
                        if (bx - ev.rawX > dp(36)) {
                            handler.removeCallbacks(r)
                            deleteWord()
                            bx = ev.rawX
                        }
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
            // Plain keys fire the moment the finger touches (instant typing). A key that also has a
            // long-press action, popup letters or swipe typing fires on release instead.
            val long = k.onLong
            val hasAlts = k.alts.isNotEmpty()
            val swipeKey = swipeOn && k.letter != null && !passwordField && fontStyle == 0
            if (k.letter != null) swipeKeys.add(k.letter to tv)
            val deferred = long != null || hasAlts || swipeKey
            val previewable = !k.space && k.style == Style.NORMAL && k.label.isNotEmpty() &&
                k.label.codePointCount(0, k.label.length) <= 2
            var longDone = false
            var altShown = false
            var swiping = false
            var cursorMode = false
            var lastX = 0f
            var acc = 0f
            var downX = 0f
            var downY = 0f
            val longRun = Runnable {
                if (hasAlts) {
                    altShown = showAlts(tv, k.alts)
                } else {
                    longDone = true
                    hidePreview()
                    long?.invoke()
                }
            }
            tv.setOnTouchListener { view, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.isPressed = true
                        pressAnim(view, true)
                        keyTick(view)
                        longDone = false
                        altShown = false
                        swiping = false
                        downX = ev.rawX
                        downY = ev.rawY
                        lastX = ev.rawX
                        acc = 0f
                        cursorMode = false
                        if (previewable) showPreview(view, k.label)
                        if (!deferred) {
                            k.onPress()
                        } else if (long != null || hasAlts) {
                            handler.postDelayed(longRun, 380)
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (altShown) {
                            updateAlt(ev.rawX)
                        } else if (swipeKey) {
                            if (!swiping) {
                                val dx = ev.rawX - downX
                                val dy = ev.rawY - downY
                                if (dx * dx + dy * dy > dp(18) * dp(18)) {
                                    swiping = true
                                    handler.removeCallbacks(longRun)
                                    hidePreview()
                                    beginSwipe(downX, downY)
                                }
                            }
                            if (swiping) {
                                val ox = ev.rawX - ev.x
                                val oy = ev.rawY - ev.y
                                for (i in 0 until ev.historySize) {
                                    addSwipePoint(ev.getHistoricalX(i) + ox, ev.getHistoricalY(i) + oy)
                                }
                                addSwipePoint(ev.rawX, ev.rawY)
                            }
                        } else if (k.space) {
                            // slide on the space bar to move the cursor
                            if (!cursorMode && Math.abs(ev.rawX - downX) > dp(16)) {
                                cursorMode = true
                                handler.removeCallbacks(longRun)
                                flushSinglish()
                            }
                            if (cursorMode) {
                                acc += ev.rawX - lastX
                                val step = dp(14).toFloat()
                                while (acc >= step) {
                                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
                                    acc -= step
                                }
                                while (acc <= -step) {
                                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
                                    acc += step
                                }
                            }
                        }
                        lastX = ev.rawX
                    }
                    MotionEvent.ACTION_UP -> {
                        view.isPressed = false
                        pressAnim(view, false)
                        handler.removeCallbacks(longRun)
                        hidePreview()
                        when {
                            altShown -> {
                                altShown = false
                                val a = takeAlt()
                                if (a != null) commitAlt(a)
                            }
                            swiping -> {
                                swiping = false
                                endSwipe(true)
                            }
                            cursorMode -> cursorMode = false
                            deferred && !longDone -> k.onPress()
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        pressAnim(view, false)
                        handler.removeCallbacks(longRun)
                        hidePreview()
                        hideAlts()
                        if (swiping) endSwipe(false)
                        swiping = false
                        cursorMode = false
                        altShown = false
                    }
                }
                true
            }
        }
        return tv
    }

    // =====================================================================
    //  Number row, long-press popups, swipe typing, one-handed mode, layouts
    // =====================================================================
    private val digitAlts: Map<Char, List<String>> = mapOf(
        '1' to listOf("!", "¹"), '2' to listOf("@", "²"), '3' to listOf("#", "³"),
        '4' to listOf("\$", "€"), '5' to listOf("%", "‰"), '6' to listOf("^", "⁶"),
        '7' to listOf("&", "⁷"), '8' to listOf("*", "⁸"), '9' to listOf("(", "⁹"),
        '0' to listOf(")", "°", "⁰")
    )

    private val accents: Map<Char, String> = mapOf(
        'a' to "á ä à â ã å ā æ", 'c' to "ç ć č", 'd' to "ď", 'e' to "é è ê ë ē ė",
        'g' to "ğ", 'i' to "í ï ì î ī", 'l' to "ł", 'n' to "ñ ń", 'o' to "ó ö ò ô õ ø ō œ",
        'r' to "ř", 's' to "ß ś š", 't' to "ť", 'u' to "ú ü ù û ū", 'y' to "ý ÿ", 'z' to "ž ź ż"
    )

    /** With Singlish on, the popups give the capital-letter sounds (T=ඨ, N=ණ, L=ළ ...). */
    private val singlishAlts: Map<Char, List<String>> = mapOf(
        't' to listOf("T", "Th"), 'd' to listOf("D", "Dh"), 'n' to listOf("N", "nG", "ny"),
        'l' to listOf("L"), 's' to listOf("S", "sh"), 'm' to listOf("M"), 'h' to listOf("H"),
        'r' to listOf("R"), 'y' to listOf("Y"), 'c' to listOf("Ch"), 'g' to listOf("gh", "ng", "Gn"),
        'q' to listOf("H"), 'x' to listOf("ny", "Gn"), 'z' to listOf("nG", "nj"),
        'k' to listOf("kh"), 'p' to listOf("ph"), 'b' to listOf("bh"), 'j' to listOf("jh")
    )

    private fun numberKeys(): List<Key> = "1234567890".map { c ->
        Key(c.toString(), alts = if (popupsOn) digitAlts[c].orEmpty() else emptyList()) {
            type(styled(c.toString()))
        }
    }

    // Sinhala letter each English key gives in Singlish (small hint above the key)
    private val singlishHintLower: Map<Char, String> = mapOf(
        'a' to "අ", 'b' to "බ", 'c' to "ච", 'd' to "ඩ", 'e' to "එ", 'f' to "ෆ", 'g' to "ග", 'h' to "හ",
        'i' to "ඉ", 'j' to "ජ", 'k' to "ක", 'l' to "ල", 'm' to "ම", 'n' to "න", 'o' to "ඔ", 'p' to "ප",
        'r' to "ර", 's' to "ස", 't' to "ට", 'u' to "උ", 'v' to "ව", 'w' to "ව", 'y' to "ය",
        'q' to "ං", 'x' to "ක්\u200Dය", 'z' to "ක්\u200Dර"      // spare keys: bindu, yansaya, rakaransaya
    )
    private val singlishHintShift: Map<Char, String> = mapOf(
        't' to "ඨ", 'd' to "ඪ", 'n' to "ණ", 'l' to "ළ", 's' to "ෂ", 'm' to "ං", 'h' to "ඃ",
        'r' to "ක්\u200Dර", 'y' to "ක්\u200Dය"
    )

    /** In Singlish the keys that English words need but Sinhala does not (q, x, z) type bindu, yansaya and rakaransaya. */
    private fun singlishSend(c: Char, label: String): String {
        if (!singlishOn || passwordField) return label
        return when (c) {
            'q' -> "M"
            'x' -> "Y"
            'z' -> "R"
            else -> label
        }
    }

    private fun singlishHintFor(c: Char, upper: Boolean): String? {
        if (!singlishOn || !singlishHints || passwordField || fontStyle != 0) return null
        return (if (upper) singlishHintShift[c] else null) ?: singlishHintLower[c]
    }

    private fun altsFor(c: Char, digit: Char?, upper: Boolean): List<String> {
        val out = ArrayList<String>()
        if (digit != null) out.add(digit.toString())
        if (popupsOn) {
            if (singlishOn) {
                singlishAlts[c]?.let { out.addAll(it) }
            } else {
                accents[c]?.split(" ")?.forEach { out.add(if (upper) it.uppercase() else it) }
            }
        }
        return out
    }

    private fun commitAlt(alt: String) {
        val sing = singlishOn && !passwordField && alt.all { it in 'a'..'z' || it in 'A'..'Z' }
        if (sing) {
            alt.forEach { typeLetter(it.toString()) }
        } else {
            flushSinglish()
            type(styled(alt))
        }
        if (shift == 1) {
            shift = 0
            render()
        }
    }

    // ----- long-press popup -----
    private var altPopup: android.widget.PopupWindow? = null
    private var altCells: List<TextView> = emptyList()
    private var altValues: List<String> = emptyList()
    private var altLeft = 0
    private var altCellW = 1
    private var altSel = 0

    private fun showAlts(anchor: View, alts: List<String>): Boolean {
        if (!::frame.isInitialized || alts.isEmpty()) return false
        return try {
            hidePreview()
            hideAlts()
            val sw = resources.displayMetrics.widthPixels
            val cw = maxOf(anchor.width, dp(40))
            val ch = maxOf((anchor.height * 1.2f).toInt(), dp(52))
            val total = cw * alts.size
            val loc = IntArray(2)
            anchor.getLocationOnScreen(loc)
            val extendLeft = loc[0] + anchor.width / 2 > sw / 2
            val ordered = if (extendLeft) alts.reversed() else alts
            var x = if (extendLeft) loc[0] + anchor.width - total else loc[0]
            x = x.coerceIn(0, maxOf(0, sw - total))
            val y = loc[1] - ch - dp(6)
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (th.bg == th.key) th.special else (th.key or (0xFF shl 24)))
                    setStroke(dp(1), th.accent)
                }
            }
            val cells = ordered.map { a ->
                TextView(this).apply {
                    text = a
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                    layoutParams = LinearLayout.LayoutParams(cw, ch)
                }
            }
            cells.forEach { box.addView(it) }
            val p = android.widget.PopupWindow(box, total, ch).apply {
                isTouchable = false
                isFocusable = false
                isClippingEnabled = false
                inputMethodMode = android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED
                elevation = dp(8).toFloat()
            }
            p.showAtLocation(frame, Gravity.NO_GRAVITY, x, y)
            altPopup = p
            altCells = cells
            altValues = ordered
            altLeft = x
            altCellW = cw
            altSel = if (extendLeft) ordered.size - 1 else 0
            highlightAlt()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun highlightAlt() {
        altCells.forEachIndexed { i, tv ->
            val on = i == altSel
            tv.setTextColor(if (on) Color.WHITE else th.text)
            tv.background = if (on) GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(th.accent)
            } else null
        }
    }

    private fun updateAlt(rawX: Float) {
        if (altCells.isEmpty()) return
        val i = ((rawX - altLeft) / altCellW).toInt().coerceIn(0, altCells.size - 1)
        if (i != altSel) {
            altSel = i
            highlightAlt()
        }
    }

    private fun takeAlt(): String? {
        val a = altValues.getOrNull(altSel)
        hideAlts()
        return a
    }

    private fun hideAlts() {
        try {
            altPopup?.dismiss()
        } catch (e: Exception) {
        }
        altPopup = null
        altCells = emptyList()
        altValues = emptyList()
    }

    // ----- swipe typing -----
    private var swipeActive = false
    private val swipePts = ArrayList<Float>()
    private var swipeRects: List<Pair<Char, FloatArray>> = emptyList()
    private var trail: TrailView? = null

    private fun beginSwipe(x: Float, y: Float) {
        swipePts.clear()
        swipeRects = swipeKeys.map { (c, v) ->
            val l = IntArray(2)
            v.getLocationOnScreen(l)
            c to floatArrayOf(l[0].toFloat(), l[1].toFloat(), (l[0] + v.width).toFloat(), (l[1] + v.height).toFloat())
        }
        swipeActive = true
        trail?.color = th.accent
        trail?.clear()
        addSwipePoint(x, y)
    }

    private fun addSwipePoint(x: Float, y: Float) {
        if (!swipeActive) return
        swipePts.add(x)
        swipePts.add(y)
        trail?.add(x, y)
    }

    private fun endSwipe(commit: Boolean) {
        val rects = swipeRects
        swipeActive = false
        trail?.clear()
        if (!commit || rects.isEmpty()) return
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < swipePts.size) {
            val x = swipePts[i]
            val y = swipePts[i + 1]
            i += 2
            var best = ' '
            var bestD = Float.MAX_VALUE
            for ((c, r) in rects) {
                val dx = if (x < r[0]) r[0] - x else if (x > r[2]) x - r[2] else 0f
                val dy = if (y < r[1]) r[1] - y else if (y > r[3]) y - r[3] else 0f
                val d = dx * dx + dy * dy
                if (d < bestD) {
                    bestD = d
                    best = c
                }
            }
            if (best != ' ' && (sb.isEmpty() || sb[sb.length - 1] != best)) sb.append(best)
        }
        val words = swipeCandidates(sb.toString())
        if (words.isNotEmpty()) commitSwipe(words)
    }

    private fun swipeCandidates(path: String): List<String> {
        if (path.length < 2) return emptyList()
        val words = ArrayList<String>()
        return if (singlishOn) {
            learnedRoman.entries.sortedByDescending { it.value }.forEach { words.add(it.key) }
            words.addAll(SinhalaDict.roman)
            SwipeDecoder.rank(path, words, { learnedRoman[it] ?: 0 })
        } else {
            learned.entries.filter { e -> e.key.all { it in 'a'..'z' } }
                .sortedByDescending { it.value }.forEach { words.add(it.key) }
            words.addAll(enWords)
            words.addAll(SinhalaDict.englishExtra)
            SwipeDecoder.rank(path, words, { learned[it.lowercase()] ?: 0 })
        }
    }

    /** Types the best guess + a space; the other guesses go to the suggestion bar to swap in. */
    private fun commitSwipe(words: List<String>) {
        val ic = currentInputConnection ?: return
        flushSinglish()
        val cap = shift != 0
        val shown = words.map { w ->
            val t = if (singlishOn) Singlish.convert(w) else w
            if (cap && !singlishOn) t.replaceFirstChar { it.uppercase() } else t
        }
        val best = shown[0]
        decorateWordStart(best)
        val before = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
        val lead = if (before.isNotEmpty() && isWordChar(before[0])) " " else ""
        val prev = prevWordBefore(0)
        ic.commitText("$lead$best ", 1)
        learn(best)
        checkWordFx(best)
        if (prev != null && lead.isEmpty()) learnBigram(prev, best)
        if (singlishOn) learnRoman(words[0])
        undoCorr = null
        swipeCommitted = best
        swipeAlts = shown.drop(1)
        suggestions = swipeAlts
        toolsPinned = false
        if (shift == 1) {
            shift = 0
            render()
        } else {
            refreshBar()
        }
        if (::snow.isInitialized) snow.burst()
    }

    // ----- one-handed mode / keyboard size -----
    private fun applySize() {
        if (!::root.isInitialized) return
        val lp = root.layoutParams as? FrameLayout.LayoutParams ?: return
        val sw = resources.displayMetrics.widthPixels
        val pct = if (oneHand != 0) minOf(widthPct, 80) else widthPct
        val w = if (pct >= 100) ViewGroup.LayoutParams.MATCH_PARENT else sw * pct / 100
        val g = Gravity.BOTTOM or when (oneHand) {
            1 -> Gravity.START
            2 -> Gravity.END
            else -> Gravity.CENTER_HORIZONTAL
        }
        if (lp.width != w || lp.gravity != g) {
            lp.width = w
            lp.gravity = g
            root.layoutParams = lp
        }
    }

    private fun setOneHand(mode: Int) {
        oneHand = mode.coerceIn(0, 2)
        prefs.edit().putInt("onehand", oneHand).apply()
        panel = Panel.NONE
        render()
    }

    private fun toggleNumberRow() {
        numberRow = !numberRow
        prefs.edit().putBoolean("numrow", numberRow).apply()
        render()
    }

    private fun toggleSwipe() {
        swipeOn = !swipeOn
        prefs.edit().putBoolean("swipe", swipeOn).apply()
        toast(if (swipeOn) "Swipe typing on" else "Swipe typing off")
        render()
    }

    private fun setLayout(kind: Int) {
        flushSinglish()
        layoutKind = kind.coerceIn(0, 3 + Languages.extra.size)
        siLayer = 0
        shift = 0
        prefs.edit().putInt("langlayout", layoutKind).apply()
        if (layoutKind != 2 && layoutKind < 4) prefs.edit().putInt("silayout", layoutKind).apply()
        render()
    }

    /** Long-press ABC: next layout (Sinhala grid, Wijesekara, Easy, Tamil, Hindi ...). */
    private fun cycleLayout() {
        val stops = Languages.layoutStops(prefs)
        setLayout(stops[(stops.indexOf(layoutKind) + 1) % stops.size])
        toast(Languages.title(layoutKind))
    }

    // ---------- more languages (data lives in Languages.kt) ----------
    private fun currentLangId(): String = if (!siMode) "en" else Languages.idForKind(layoutKind)

    /** Language button: English -> Sinhala -> Tamil -> Hindi -> ... -> English. */
    private fun cycleLanguage() {
        flushSinglish()
        val order = Languages.cycle(prefs)
        setLanguage(order[(order.indexOf(currentLangId()) + 1) % order.size])
    }

    private fun setLanguage(id: String) {
        symbols = false
        panel = Panel.NONE
        shift = 0
        if (id == "en") {
            siMode = false
            render()
            toast(if (singlishOn) "Singlish" else "English")
        } else {
            siMode = true
            setLayout(Languages.kindFor(id, prefs.getInt("silayout", 0)))
            toast(Languages.title(layoutKind))
        }
    }

    /** Draws Hindi, Telugu, Malayalam, Kannada, Bengali, Arabic from the layers in Languages.kt. */
    private fun renderExtra() {
        val lang = Languages.extra.getOrNull(layoutKind - 4)
        if (lang == null) {
            renderSinhalaGrid()
            return
        }
        val n = lang.layers.size
        val li = siLayer % n
        val layer = lang.layers[li]
        layer.rows.forEachIndexed { r, keys ->
            root.addView(row(keys.mapIndexed { c, s ->
                val combos = if (layer.popups && popupsOn) lang.signs.map { s + it } else emptyList()
                Key(Languages.label(s), textSp = 22f, alts = digitAlt(r, c) + combos) {
                    decorateWordStart(s)
                    type(s)
                }
            }, pad = (10 - keys.size) / 2f))
        }
        repeat(3 - layer.rows.size) {
            root.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(keyH))
            })
        }
        val nextLabel = lang.layers[(li + 1) % n].label
        root.addView(indicBottomRow(Key(nextLabel, 1.3f, Style.ACTIVE, 20f) { siLayer = (li + 1) % n; render() }))
    }

    private fun deleteWord() {
        val ic = currentInputConnection ?: return
        undoCorr = null
        if (sbuf.isNotEmpty()) {
            sbuf.clear()
            ic.setComposingText("", 1)
            ic.finishComposingText()
            return
        }
        val b = ic.getTextBeforeCursor(60, 0)?.toString() ?: return
        if (b.isEmpty()) return
        var i = b.length
        while (i > 0 && b[i - 1].isWhitespace()) i--
        while (i > 0 && !b[i - 1].isWhitespace()) i--
        ic.deleteSurroundingText(b.length - i, 0)
    }

    /** Turn Shift on by itself at the start of a sentence (English layout only). */
    private fun updateAutoCap(redraw: Boolean = true) {
        if (!autoCap || siMode || symbols || singlishOn || passwordField || fontStyle != 0 || shift != 0) return
        val ei = currentInputEditorInfo ?: return
        val cls = ei.inputType and android.text.InputType.TYPE_MASK_CLASS
        val variation = ei.inputType and android.text.InputType.TYPE_MASK_VARIATION
        if (cls != android.text.InputType.TYPE_CLASS_TEXT) return
        if (variation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_URI ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        ) return
        val caps = currentInputConnection?.getCursorCapsMode(TextUtils.CAP_MODE_SENTENCES) ?: 0
        if (caps != 0) {
            shift = 1
            if (redraw) render()
        }
    }

    // ----- backup / restore of learned words, shortcuts (via the clipboard) -----
    private val backupKeys = listOf("learned", "bigrams", "learned_roman", "snips")

    private fun backupToClipboard() {
        try {
            val o = JSONObject()
            o.put("rio_backup", 1)
            for (k in backupKeys) prefs.getString(k, null)?.let { o.put(k, it) }
            cm.setPrimaryClip(android.content.ClipData.newPlainText("RIO BOARD backup", o.toString()))
            toast("Backup copied - paste it into Notes or Saved messages")
        } catch (e: Exception) {
            toast("Could not copy backup")
        }
    }

    private fun restoreFromClipboard() {
        try {
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
            val o = JSONObject(text)
            if (o.optInt("rio_backup", 0) != 1) {
                toast("Clipboard has no RIO BOARD backup")
                return
            }
            val e = prefs.edit()
            for (k in backupKeys) if (o.has(k)) e.putString(k, o.getString(k))
            e.apply()
            learned.clear()
            bigrams.clear()
            learnedRoman.clear()
            try {
                val l = JSONObject(prefs.getString("learned", "{}"))
                l.keys().forEach { learned[it] = l.getInt(it) }
                val b = JSONObject(prefs.getString("bigrams", "{}"))
                b.keys().forEach { prev ->
                    val inner = b.getJSONObject(prev)
                    val m = HashMap<String, Int>()
                    inner.keys().forEach { nx -> m[nx] = inner.getInt(nx) }
                    bigrams[prev] = m
                }
                val r = JSONObject(prefs.getString("learned_roman", "{}"))
                r.keys().forEach { learnedRoman[it] = r.getInt(it) }
            } catch (ex: Exception) {
            }
            snips = Snips.load(prefs)
            toast("Backup restored")
        } catch (e: Exception) {
            toast("Clipboard has no RIO BOARD backup")
        }
    }

    // ---------- word suggestions ----------
    private fun isWordChar(c: Char): Boolean =
        c.isLetter() || c == '\'' || c == '\u200D' || c in '\u0D80'..'\u0DFF' || c in '\u0B80'..'\u0BFF' ||
            Character.getType(c).let { it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() }

    private fun currentPrefix(): String {
        val before = currentInputConnection?.getTextBeforeCursor(24, 0)?.toString() ?: return ""
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        return before.substring(i)
    }

    private fun computeSuggestions(): List<String> {
        if (fontStyle != 0 || passwordField) return emptyList()
        val sc = swipeCommitted
        if (sc != null) {
            val b = currentInputConnection?.getTextBeforeCursor(sc.length + 2, 0)?.toString() ?: ""
            if (swipeAlts.isNotEmpty() && b.endsWith("$sc ")) return swipeAlts
            swipeCommitted = null
        }
        val prefix = currentPrefix()
        if (prefix.isEmpty()) return nextWords()
        val lower = prefix.lowercase()
        val emo = if (emojiSug) SinhalaDict.emojiFor(lower) else emptyList()
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
        val words = out.take(3).map { w -> if (cap) w.replaceFirstChar { it.uppercase() } else w }
        return (emo.take(2) + words).take(3)
    }

    /** Word before the cursor's current word (lower-case), or null. */
    private fun prevWordBefore(prefixLen: Int): String? {
        val b = currentInputConnection?.getTextBeforeCursor(80, 0)?.toString() ?: return null
        val head = b.dropLast(prefixLen).trimEnd()
        if (head.isEmpty()) return null
        var i = head.length
        while (i > 0 && isWordChar(head[i - 1])) i--
        val w = head.substring(i)
        return if (w.isEmpty()) null else w.lowercase()
    }

    /** After "word " suggest what usually follows: your own history first, then built-in pairs. */
    private fun nextWords(): List<String> {
        if (!nextWordOn) return emptyList()
        val b = currentInputConnection?.getTextBeforeCursor(60, 0)?.toString() ?: return emptyList()
        if (b.isEmpty() || !b.last().isWhitespace() || b.last() == '\n') return emptyList()
        val head = b.trimEnd()
        var i = head.length
        while (i > 0 && isWordChar(head[i - 1])) i--
        val prev = head.substring(i).lowercase()
        if (prev.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        bigrams[prev]?.entries?.sortedByDescending { it.value }?.forEach { out.add(it.key) }
        SinhalaDict.next(prev).forEach { out.add(it) }
        return out.take(3)
    }

    private fun learnBigram(prev: String, next: String) {
        if (passwordField) return
        val n = next.lowercase().trim()
        if (prev.length < 1 || n.length < 2) return
        val m = bigrams.getOrPut(prev) { HashMap() }
        m[n] = (m[n] ?: 0) + 1
        if (m.size > 8) m.entries.sortedBy { it.value }.take(m.size - 8).map { it.key }.forEach { m.remove(it) }
        if (bigrams.size > 400) bigrams.keys.toList().take(60).forEach { bigrams.remove(it) }
        val o = JSONObject()
        for ((p, inner) in bigrams) o.put(p, JSONObject(inner.toMap()))
        prefs.edit().putString("bigrams", o.toString()).apply()
    }

    /** A finished word can play an animation (love -> big heart, haha -> laughing faces ...). */
    private fun checkWordFx(word: String) {
        val w = word.trim().trim('.', ',', '!', '?', '"', '\'', ':', ';', '(', ')').lowercase()
        if (w.isEmpty()) return
        val prev = fxPrev
        fxPrev = w
        if (!::wordFx.isInitialized || passwordField || !prefs.getBoolean("wordfx", true)) return
        val fx = WordFxCatalog.find(prev, w, WordFxCatalog.custom(prefs)) ?: return
        wordFx.play(fx)
    }

    private fun learnRoman(raw: String) {
        if (passwordField || raw.length < 3) return
        learnedRoman[raw] = (learnedRoman[raw] ?: 0) + 1
        if (learnedRoman.size > 300) {
            learnedRoman.entries.sortedBy { it.value }.take(60).map { it.key }.forEach { learnedRoman.remove(it) }
        }
        prefs.edit().putString("learned_roman", JSONObject(learnedRoman.toMap()).toString()).apply()
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
        // an alternative for a word that was just swipe-typed replaces that word
        val sc = swipeCommitted
        if (sc != null) {
            val b = ic.getTextBeforeCursor(sc.length + 1, 0)?.toString()
            if (b == "$sc ") {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(sc.length + 1, 0)
                ic.commitText("$word ", 1)
                ic.endBatchEdit()
                learn(word)
                swipeCommitted = null
                swipeAlts = emptyList()
                suggestions = emptyList()
                refreshBar()
                return
            }
            swipeCommitted = null
        }
        flushSinglish()
        // an emoji is added after the word instead of replacing it
        if (word.isNotEmpty() && !Character.isLetterOrDigit(word.codePointAt(0))) {
            val p = currentPrefix()
            ic.commitText(if (p.isNotEmpty()) " $word " else "$word ", 1)
            suggestions = emptyList()
            refreshBar()
            if (::snow.isInitialized) snow.burst()
            return
        }
        val prefix = currentPrefix()
        val prev = prevWordBefore(prefix.length)
        ic.beginBatchEdit()
        ic.deleteSurroundingText(prefix.length, 0)
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
        learn(word)
        if (prev != null) learnBigram(prev, word)
        suggestions = emptyList()
        refreshBar()
        if (::snow.isInitialized) snow.burst()
    }

    private fun learn(word: String) {
        if (passwordField) return
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
            if (clip.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return
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

    private val READ_MORE_PAD = 4000     // invisible characters (U+200B) between the preview and the hidden text

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

    /**
     * WhatsApp "Read more" message. Line 1 stays visible; everything after it is pushed behind a
     * "Read more" link by a long run of invisible characters. Tap the tool again to undo.
     */
    /** Whole text of the field, split around the cursor. Works in apps that refuse huge getTextBeforeCursor requests. */
    private class FieldText(val before: String, val sel: String, val after: String) {
        val all: String get() = before + sel + after
    }

    private fun readField(ic: android.view.inputmethod.InputConnection): FieldText {
        // 1) ExtractedText: the app hands over the full text + selection in one go
        try {
            val req = android.view.inputmethod.ExtractedTextRequest().apply { hintMaxChars = 1000000; hintMaxLines = 100000 }
            val et = ic.getExtractedText(req, 0)
            val txt = et?.text?.toString()
            if (et != null && txt != null && et.startOffset == 0 && et.partialStartOffset <= 0 && txt.isNotEmpty()) {
                val a = minOf(maxOf(minOf(et.selectionStart, et.selectionEnd), 0), txt.length)
                val b = minOf(maxOf(maxOf(et.selectionStart, et.selectionEnd), 0), txt.length)
                return FieldText(txt.substring(0, a), txt.substring(a, b), txt.substring(b))
            }
        } catch (e: Exception) {
        }
        // 2) old way, but retry with smaller sizes because some apps return null for big requests
        fun grab(before: Boolean): String {
            for (n in intArrayOf(100000, 20000, 5000, 1000)) {
                val r = try {
                    (if (before) ic.getTextBeforeCursor(n, 0) else ic.getTextAfterCursor(n, 0))?.toString()
                } catch (e: Exception) {
                    null
                }
                if (r != null && (r.isNotEmpty() || n == 1000)) return r
            }
            return ""
        }
        val sel = try { ic.getSelectedText(0)?.toString() ?: "" } catch (e: Exception) { "" }
        return FieldText(grab(true), sel, grab(false))
    }

    /** Replaces the whole field with [text]; double-checks and falls back to select-all when the app ignores big deletes. */
    private fun replaceWholeField(ic: android.view.inputmethod.InputConnection, f: FieldText, text: String) {
        ic.beginBatchEdit()
        if (f.sel.isNotEmpty()) ic.commitText("", 1)
        ic.deleteSurroundingText(f.before.length, f.after.length)
        ic.endBatchEdit()
        val left = readField(ic).all
        if (left.isNotEmpty()) {
            ic.performContextMenuAction(android.R.id.selectAll)
            ic.commitText("", 1)
        }
        ic.commitText(text, 1)
    }

    /**
     * WhatsApp "Read more" message. Line 1 stays visible; everything after it is pushed behind a
     * "Read more" link by a long run of invisible characters. Tap the tool again to undo.
     */
    private fun makeReadMore() {
        val ic = currentInputConnection ?: return
        flushSinglish()
        val f = readField(ic)
        val all = f.all
        val zw = '\u200B'
        if (all.count { it == zw } >= 200) {
            // already a read-more message: turn it back into normal text
            replaceWholeField(ic, f, all.replace(zw.toString(), ""))
            toast("Read more removed")
            suggestions = computeSuggestions()
            render()
            return
        }
        // otherwise open the Read more maker: 1) part people see  2) hidden part  3) put in chat / send
        val nl = all.indexOf('\n')
        val visible = if (nl < 0) all.trim() else all.substring(0, nl).trim()
        val hidden = if (nl < 0) "" else all.substring(nl + 1).trim('\n', ' ')
        try {
            startActivity(
                Intent(this, ReadMoreActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("visible", visible)
                    .putExtra("hidden", hidden)
                    .putExtra("whole", all.isNotBlank())
            )
        } catch (e: Exception) {
            toast("Could not open the Read more maker")
        }
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

    /** Runs [fn] on the selected text, or on the current line before the cursor when nothing is selected. */
    private fun transformText(fn: (String) -> String) {
        val ic = currentInputConnection ?: return
        flushSinglish()
        val sel = ic.getSelectedText(0)?.toString()
        val hasSel = !sel.isNullOrEmpty()
        val src: String = if (hasSel) {
            sel!!
        } else {
            val b = ic.getTextBeforeCursor(500, 0)?.toString() ?: return
            b.substring(b.lastIndexOf('\n') + 1)
        }
        if (src.isEmpty()) {
            toast("Type or select some text first")
            return
        }
        val out = fn(src)
        ic.beginBatchEdit()
        if (!hasSel) ic.deleteSurroundingText(src.length, 0)
        ic.commitText(out, 1)
        ic.endBatchEdit()
    }

    private fun wordCount() {
        val ic = currentInputConnection ?: return
        flushSinglish()
        val f = readField(ic)
        val hasSel = f.sel.isNotEmpty()
        val s = if (hasSel) f.sel else f.all
        val words = s.replace('\u200B', ' ').trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        val chars = s.count { it != '\u200B' }
        val msg = "$words words \u00B7 $chars characters" + (if (hasSel) " (selected)" else "")
        toast(msg)
        showBarMsg(msg)
    }

    // ---------- Google tools ----------
    /** Selected text, else the whole text in the box (max 500 chars). */
    private fun googleQuery(max: Int = 500): String {
        val ic = currentInputConnection ?: return ""
        flushSinglish()
        val f = readField(ic)
        val s = if (f.sel.isNotEmpty()) f.sel else f.all
        return s.replace('\u200B', ' ').trim().take(max)
    }

    private fun launch(i: Intent): Boolean {
        return try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ----- Google search and translate, inside the keyboard -----
    private var gWeb: android.webkit.WebView? = null
    private var webKind = 0              // 0 = Google, 1 = YouTube (same panel, same web view)
    private var trSource = ""
    private var trResult = ""
    private var trStatus = ""
    private var trTl = "si"
    private var trWasSel = false
    private var trReq = 0
    private val trLangs = listOf(
        "si" to "සිංහල", "en" to "English", "ta" to "தமிழ்", "hi" to "हिन्दी",
        "fr" to "Français", "de" to "Deutsch", "es" to "Español", "ar" to "العربية"
    )

    private fun googleSearch() {
        val q = googleQuery()
        if (q.isEmpty()) {
            toast("Type or select what to search first")
            return
        }
        try {
            val w = gWebView()
            webKind = 0
            w.loadUrl("https://www.google.com/search?q=" + java.net.URLEncoder.encode(q, "UTF-8"))
            panel = Panel.GSEARCH
            render()
        } catch (e: Exception) {
            val web = Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, q)
            if (!launch(web)) toast("Could not open Google")
        }
    }

    /** Selected text (or the whole text box) is searched on YouTube, inside the keyboard like Google. */
    private fun youtubeSearch() {
        val q = googleQuery()
        if (q.isEmpty()) {
            toast("Type or select what to search on YouTube first")
            return
        }
        try {
            val w = gWebView()
            webKind = 1
            w.loadUrl("https://m.youtube.com/results?search_query=" + java.net.URLEncoder.encode(q, "UTF-8"))
            panel = Panel.GSEARCH
            render()
        } catch (e: Exception) {
            val url = "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(q, "UTF-8")
            if (!launch(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))) toast("Could not open YouTube")
        }
    }

    /** A video page becomes the short youtu.be link when it is put in the chat. */
    private fun shareUrl(u: String): String {
        if (webKind != 1 || u.isEmpty()) return u
        val uri = android.net.Uri.parse(u)
        val v = if (uri.host?.contains("youtube.com") == true) uri.getQueryParameter("v") else null
        return if (!v.isNullOrEmpty()) "https://youtu.be/$v" else u
    }

    private fun gWebView(): android.webkit.WebView {
        gWeb?.let { return it }
        val w = android.webkit.WebView(this)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.setSupportZoom(true)
        w.webViewClient = object : android.webkit.WebViewClient() {}
        gWeb = w
        return w
    }

    private fun renderGSearch() {
        root.addView(panelHeader(if (webKind == 1) "YouTube" else "Google"))
        val w = try { gWebView() } catch (e: Exception) { null }
        if (w == null) {
            root.addView(hintText("The web view is not available on this phone."))
            return
        }
        val tall = prefs.getBoolean("gtall", true)
        root.addView(chipRow(listOf("\u25C0 Back", "\u21BB", "Link", "Open", if (tall) "\u25BC Small" else "\u25B2 Big"), -1) { i ->
            when (i) {
                0 -> if (w.canGoBack()) w.goBack() else { panel = Panel.NONE; render() }
                1 -> w.reload()
                2 -> {
                    val url = shareUrl(w.url ?: "")
                    if (url.isNotEmpty()) {
                        currentInputConnection?.commitText(url, 1)
                        panel = Panel.NONE
                        render()
                    }
                }
                3 -> {
                    val url = w.url ?: ""
                    if (url.isNotEmpty() && !launch(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))) toast("Could not open it")
                }
                4 -> {                       // taller keyboard for reading results, or back to the normal size
                    prefs.edit().putBoolean("gtall", !tall).apply()
                    render()
                }
            }
        })
        (w.parent as? ViewGroup)?.removeView(w)
        // tall: the keyboard grows upwards to about half of the screen so the results are easy to read
        val webH = if (tall) (resources.displayMetrics.heightPixels * 0.5f).toInt() else dp(keyH * 4) - dp(42)
        w.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, webH)
        try { w.onResume() } catch (e: Exception) { }
        root.addView(w)
    }

    /** Selected text (or the whole text box) is translated and shown in the keyboard; Replace / Add below / Copy. */
    private fun googleTranslate() {
        val ic = currentInputConnection ?: return
        flushSinglish()
        val f = readField(ic)
        val src = (if (f.sel.isNotEmpty()) f.sel else f.all).replace('\u200B', ' ').trim().take(1500)
        if (src.isEmpty()) {
            toast("Type or select the text to translate first")
            return
        }
        trSource = src
        trWasSel = f.sel.isNotEmpty()
        trResult = ""
        trTl = if (src.any { it in '\u0D80'..'\u0DFF' }) "en" else "si"     // Sinhala -> English, anything else -> Sinhala
        panel = Panel.TRANSLATE
        render()
        trRun()
    }

    private fun trRun() {
        val id = ++trReq
        val text = trSource
        val tl = trTl
        trResult = ""
        trStatus = "Translating..."
        if (panel == Panel.TRANSLATE) render()
        Thread {
            var out = ""
            var err = ""
            try {
                val url = java.net.URL(
                    "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" + tl +
                        "&dt=t&q=" + java.net.URLEncoder.encode(text, "UTF-8")
                )
                val c = url.openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.setRequestProperty("User-Agent", "Mozilla/5.0")
                val body = c.inputStream.bufferedReader().use { it.readText() }
                val parts = JSONArray(body).getJSONArray(0)
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    val p = parts.optJSONArray(i) ?: continue
                    if (!p.isNull(0)) sb.append(p.getString(0))
                }
                out = sb.toString()
                if (out.isEmpty()) err = "No translation"
            } catch (e: Exception) {
                err = "No internet, or Google did not answer"
            }
            handler.post {
                if (id == trReq) {
                    trResult = out
                    trStatus = err
                    if (panel == Panel.TRANSLATE) render()
                }
            }
        }.start()
    }

    private fun renderTranslate() {
        root.addView(panelHeader("Translate"))
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(10))
        }
        trLangs.chunked(4).forEach { part ->
            box.addView(chipRow(part.map { it.second }, part.indexOfFirst { it.first == trTl }) { i ->
                trTl = part[i].first
                trRun()
            })
        }
        box.addView(TextView(this).apply {
            text = trSource
            setTextColor(th.hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(6), dp(10), dp(6), dp(2))
        })
        box.addView(TextView(this).apply {
            text = if (trResult.isNotEmpty()) trResult else trStatus
            setTextColor(th.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dp(6), dp(4), dp(6), dp(10))
        })
        if (trResult.isNotEmpty()) {
            box.addView(chipRow(listOf("Replace", "Add below", "Copy"), -1) { i ->
                val ic = currentInputConnection
                when (i) {
                    0 -> if (ic != null) {
                        val f = readField(ic)
                        if (trWasSel && f.sel == trSource) ic.commitText(trResult, 1) else replaceWholeField(ic, f, trResult)
                        panel = Panel.NONE
                        render()
                    }
                    1 -> if (ic != null) {
                        val f = readField(ic)
                        val end = f.before.length + f.sel.length
                        ic.setSelection(end, end)
                        ic.commitText("\n" + trResult, 1)
                        panel = Panel.NONE
                        render()
                    }
                    2 -> {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("RIO", trResult))
                        toast("Copied")
                    }
                }
            })
        }
        root.addView(panelBody(box))
    }

    /** Speak a search to Google (opens Google's voice search screen). */
    private fun googleVoice() {
        val i = Intent(android.speech.RecognizerIntent.ACTION_WEB_SEARCH)
        if (!launch(i)) {
            if (!launch(Intent(Intent.ACTION_VOICE_COMMAND))) toast("Google voice search is not available on this phone")
        }
    }

    // ---------- QR tools ----------
    /** Makes a QR code of the selected text (or the whole text box) with the app icon in the middle and sends it as an image. */
    private fun qrMake() {
        val q = googleQuery(1200)
        if (q.isEmpty()) {
            toast("Type or select the text for the QR code first")
            return
        }
        val bmp = try { QrKit.make(this, q) } catch (e: Exception) { null }
        if (bmp == null) {
            toast("That text is too long for a QR code")
            return
        }
        try {
            val dir = StickerStore.dir(this)
            dir.listFiles { f -> f.isFile && f.name.startsWith("pk_qr_") }
                ?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
            val f = java.io.File(dir, "pk_qr_" + System.currentTimeMillis() + ".png")
            java.io.FileOutputStream(f).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            sendSticker(f)
        } catch (e: Exception) {
            toast("Could not make the QR code")
        }
    }

    private fun voiceChanger() {
        flushSinglish()
        try {
            startActivity(Intent(this, VoiceChangerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("Could not open the voice changer")
        }
    }

    private fun qrRead() {
        flushSinglish()
        try {
            startActivity(Intent(this, QrReadActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }

    private fun mockText() = transformText { src ->
        var upper = true
        val sb = StringBuilder()
        for (c in src) {
            if (c.isLetter()) {
                sb.append(if (upper) c.uppercaseChar() else c.lowercaseChar())
                upper = !upper
            } else sb.append(c)
        }
        sb.toString()
    }

    private fun flipText() = transformText { src ->
        val from = "abcdefghijklmnopqrstuvwxyz"
        val to = "\u0250q\u0254p\u01DD\u025F\u0183\u0265\u1D09\u027E\u029El\u026Fuodb\u0279s\u0287n\u028C\u028Dx\u028Ez"
        val sb = StringBuilder()
        for (c in src.lowercase()) {
            val i = from.indexOf(c)
            sb.append(if (i >= 0) to[i] else c)
        }
        sb.reverse().toString()
    }

    private fun spacedText() = transformText { src ->
        src.split(" ").joinToString("   ") { w -> w.toCharArray().joinToString(" ") }
    }

    private fun clapText() = transformText { src ->
        src.trim().split(Regex("\\s+")).joinToString(" \uD83D\uDC4F ") + " \uD83D\uDC4F"
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
        if (barMsg != null) { barMsg = null }
        undoCorr = null
        swipeCommitted = null
        val isSep = s.length == 1 && !isWordChar(s[0])
        // two quick spaces after a word -> ". "
        if (s == " " && sbuf.isEmpty() && autoCap && !passwordField && fontStyle == 0 && ic != null) {
            val now = SystemClock.uptimeMillis()
            val b = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (b.length == 2 && b[1] == ' ' && b[0].isLetterOrDigit() && now - lastSpaceMs < 600) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                ic.endBatchEdit()
                lastSpaceMs = 0L
                updateAutoCap()
                return
            }
        }
        if (sbuf.isNotEmpty()) {
            val raw = sbuf.toString()
            // Singlish word in progress: a shortcut can still match its raw letters
            if (s == " " && expandSnip(raw, true)) {
                sbuf.clear()
                return
            }
            // typo fix: "kohomada " -> කොහොමද (backspace right after undoes it)
            if (isSep && autoCorrect && !passwordField && ic != null) {
                val fixed = SinhalaDict.correct(raw)
                if (fixed != null) {
                    val typed = Singlish.convert(raw)
                    val prev = prevWordBefore(typed.length)
                    ic.beginBatchEdit()
                    ic.commitText(fixed, 1)
                    ic.commitText(s, 1)
                    ic.endBatchEdit()
                    sbuf.clear()
                    learn(fixed)
                    if (prev != null) learnBigram(prev, fixed)
                    undoCorr = Pair(fixed + s, typed)
                    if (::snow.isInitialized) snow.burst()
                    return
                }
            }
            if (isSep) {
                learnRoman(raw)
                checkWordFx(raw)
            }
            flushSinglish()
        } else if (s == " " && fontStyle == 0 && expandSnip(currentPrefix(), false)) {
            return
        }
        // a space/punctuation ends a word -> remember it (and what followed what) for suggestions
        if (isSep) {
            val w = currentPrefix()
            checkWordFx(w)
            if (w.length >= 3) learn(w)
            if (w.length >= 2) {
                val prev = prevWordBefore(w.length)
                if (prev != null) learnBigram(prev, w)
            }
        }
        ic?.commitText(s, 1)
        if (s == " ") lastSpaceMs = SystemClock.uptimeMillis()
        if (isSep) updateAutoCap()
        if (::snow.isInitialized) snow.burst()
    }

    private fun deleteOne() {
        val ic = currentInputConnection ?: return
        // backspace straight after an auto-correct brings back what was typed
        undoCorr?.let { (done, orig) ->
            undoCorr = null
            val b = ic.getTextBeforeCursor(done.length, 0)?.toString()
            if (b == done) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(done.length, 0)
                ic.commitText(orig, 1)
                ic.endBatchEdit()
                return
            }
        }
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
            ) 2
            else if (before.length >= 2 && before[before.length - 1] in '\u0300'..'\u036F') 2  // letter + combining mark (strikethrough, underline ...)
            else 1
            ic.deleteSurroundingText(n, 0)
        }
    }

    private fun pressEnter() {
        flushSinglish()
        handler.postDelayed({ updateAutoCap() }, 120)
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
        undoCorr = null
        swipeCommitted = null
        if (sbuf.isEmpty() && !passwordField) decorateWordStart(label)
        if (singlishOn && !passwordField) {
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
        if (fontStyle == 0 || singlishOn || passwordField || s.length != 1) s else Fonts.convert(s[0], fontStyle)

    private fun spaceLabel(): String = if (siMode) Languages.nativeName(layoutKind) + " \u00B7 RIO BOARD" else "RIO BOARD"

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

    // ---------- news + update notice from the admin web (RemoteConfig.kt) ----------
    private fun launchUpdate(url: String) {
        try {
            startActivity(Intent(this, UpdateActivity::class.java).putExtra("url", url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }

    private fun loadRemoteThumb(b: RemoteConfig.RBg, iv: android.widget.ImageView) {
        Thread {
            val f = RemoteConfig.thumbFile(this, b)
            if (!f.exists()) RemoteConfig.download(RemoteConfig.baseUrl() + b.thumb, f, 400_000)
            val bmp = try {
                if (f.exists()) BitmapFactory.decodeFile(f.path) else null
            } catch (e: Exception) {
                null
            }
            if (bmp != null) iv.post { iv.setImageBitmap(bmp) }
        }.start()
    }

    private fun applyRemoteBg(b: RemoteConfig.RBg) {
        toast("Downloading\u2026")
        Thread {
            val tmp = File(cacheDir, "rbg_full.jpg")
            val ok = RemoteConfig.download(RemoteConfig.baseUrl() + b.file, tmp, 6_000_000)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (ok) {
                    try {
                        tmp.copyTo(File(filesDir, "kb_bg.jpg"), overwrite = true)
                        prefs.edit().putBoolean("bgon", true).putString("bgname", "remote:" + b.id).apply()
                        reloadBackground()
                    } catch (e: Exception) {
                        toast("Could not use this image")
                    }
                } else {
                    toast("Could not download (no internet?)")
                }
            }
        }.start()
    }

    private fun announceNews() {
        RemoteConfig.toastWorthy(prefs, remoteCfg)?.let { toast("\uD83D\uDCF0 " + it.title) }
        remoteCfg.notice?.let { n ->
            if (prefs.getString("notice_seen", "") != n.id) {
                prefs.edit().putString("notice_seen", n.id).apply()
                toast("\u26A0\uFE0F " + n.message)
            }
        }
        if (remoteCfg.minVersion > installedVersionCode() &&
            prefs.getInt("minver_seen", 0) != remoteCfg.minVersion
        ) {
            prefs.edit().putInt("minver_seen", remoteCfg.minVersion).apply()
            toast("\u26A0\uFE0F Update required - see the News panel")
        }
    }

    @Suppress("DEPRECATION")
    private fun installedVersionCode(): Int = try {
        packageManager.getPackageInfo(packageName, 0).versionCode
    } catch (e: Exception) {
        Int.MAX_VALUE
    }

    private fun newsCard(
        title: String, body: String, date: String, link: String, highlight: Boolean,
        action: (() -> Unit)? = null, actionLabel: String = ""
    ): View {
        val fg = if (highlight) Color.WHITE else th.text
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundBg(if (highlight) th.accent else th.key, 12)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(4), dp(3), dp(4), dp(3)) }
        }
        card.addView(TextView(this).apply {
            text = title
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
        })
        if (date.isNotEmpty()) card.addView(TextView(this).apply {
            text = date
            setTextColor(if (highlight) Color.WHITE else th.hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        })
        if (body.isNotEmpty()) card.addView(TextView(this).apply {
            text = body
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(4), 0, 0)
        })
        if (action != null) {
            card.addView(TextView(this).apply {
                text = actionLabel
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(8), 0, 0)
            })
            card.setOnClickListener {
                feedback(it)
                action()
            }
        } else if (link.isNotEmpty()) {
            card.addView(TextView(this).apply {
                text = "Tap to open"
                setTextColor(if (highlight) Color.WHITE else th.accent)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(6), 0, 0)
            })
            card.setOnClickListener {
                feedback(it)
                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {
                }
            }
        }
        return card
    }

    private fun renderNews() {
        root.addView(panelHeader("News"))
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        val up = remoteCfg.update
        remoteCfg.notice?.let { n -> list.addView(newsCard("\u26A0\uFE0F Notice", n.message, "", "", true)) }
        if (up != null && up.code > installedVersionCode()) {
            val head = if (up.name.isNotEmpty()) "Update " + up.name + " is ready" else "Update is ready"
            if (up.url.endsWith(".apk", ignoreCase = true)) {
                list.addView(newsCard(head, up.message, "", "", true, { launchUpdate(up.url) }, "Tap to download and install"))
            } else {
                list.addView(newsCard(head, up.message, "", up.url, true))
            }
        }
        remoteCfg.news.forEach { list.addView(newsCard((if (it.pinned) "\uD83D\uDCCC " else "") + it.title, it.body, it.date, it.link, false)) }
        if (remoteCfg.news.isEmpty() && (up == null || up.code <= installedVersionCode())) {
            list.addView(hintText("No news yet."))
        }
        root.addView(panelBody(list))
        RemoteConfig.markSeen(prefs, remoteCfg)
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
                .putExtra("loc", if (siMode) Languages.voiceLocale(layoutKind) else "")
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

    /** Called by CryptoActivity: the locked text goes into the chat box when the keyboard comes back. */
    fun cryptoInsert(text: String, replaceAll: Boolean) {
        pendingInsert = text
        pendingReplaceAll = replaceAll
        pendingSend = false
        pendingInsertAt = SystemClock.uptimeMillis()
    }

    /** Called by ReadMoreActivity: the finished message goes into the chat box (and is sent when [send] is true). */
    fun readMoreInsert(text: String, replaceAll: Boolean, send: Boolean) {
        pendingInsert = text
        pendingReplaceAll = replaceAll
        pendingSend = send
        pendingInsertAt = SystemClock.uptimeMillis()
    }

    private fun flushPendingInsert() {
        val t = pendingInsert ?: return
        if (SystemClock.uptimeMillis() - pendingInsertAt > 30000) {
            pendingInsert = null
            pendingSend = false
            return
        }
        val ic = currentInputConnection ?: return
        pendingInsert = null
        ic.beginBatchEdit()
        if (pendingReplaceAll) {
            val f = readField(ic)
            if (f.sel.isNotEmpty()) ic.commitText("", 1)
            ic.deleteSurroundingText(f.before.length, f.after.length)
        }
        ic.commitText(t, 1)
        ic.endBatchEdit()
        if (pendingSend) {
            pendingSend = false
            handler.postDelayed({ pressEnter() }, 350)
        }
    }

    private fun clipText(): String = try {
        cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun selectedText(): String = currentInputConnection?.getSelectedText(0)?.toString() ?: ""

    private fun openCrypto(encrypt: Boolean) {
        flushSinglish()
        var text = ""
        var whole = false
        if (encrypt) {
            text = selectedText()
            if (text.isEmpty()) {
                // nothing selected: lock everything that was typed in the chat box
                val ic = currentInputConnection
                val before = ic?.getTextBeforeCursor(100000, 0)?.toString() ?: ""
                val after = ic?.getTextAfterCursor(100000, 0)?.toString() ?: ""
                text = before + after
                whole = text.isNotEmpty()
            }
        } else {
            val sel = selectedText()
            text = if (RioCrypto.findToken(sel) != null) sel else clipText()
        }
        try {
            startActivity(
                Intent(this, CryptoActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("mode", if (encrypt) "enc" else "dec")
                    .putExtra("text", text)
                    .putExtra("whole", whole)
            )
        } catch (e: Exception) {
        }
    }

    private fun renderCrypto() {
        root.addView(panelHeader("Secret messages"))
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(10))
        }
        val sel = selectedText()
        val hasLocked = RioCrypto.findToken(clipText()) != null || RioCrypto.findToken(sel) != null

        fun card(title: String, sub: String, highlight: Boolean, onClick: () -> Unit): View {
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundBg(if (highlight) th.accent else th.key, 14)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(2), dp(5), dp(2), dp(5)) }
                isClickable = true
                setOnClickListener {
                    feedback(it)
                    onClick()
                }
            }
            c.addView(TextView(this).apply {
                text = title
                setTextColor(if (highlight) Color.WHITE else th.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                typeface = Typeface.DEFAULT_BOLD
            })
            c.addView(TextView(this).apply {
                text = sub
                setTextColor(if (highlight) Color.WHITE else th.hint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setPadding(0, dp(3), 0, 0)
            })
            return c
        }

        box.addView(
            card(
                "🔒 Lock message",
                if (sel.isNotEmpty()) "The selected text will be locked with a password."
                else "Type your message in the chat box (or select part of it), then tap here.",
                false
            ) { openCrypto(true) }
        )
        box.addView(
            card(
                "🔓 Unlock message",
                if (hasLocked) "✓ A locked message is ready. Tap to read it with the password."
                else "Copy the locked message you received (long-press it → Copy), then tap here.",
                hasLocked
            ) { openCrypto(false) }
        )
        box.addView(hintText("Locked text starts with 🔒RIO: and only opens with the password. The person you send it to needs the RIO BOARD keyboard."))
        root.addView(panelBody(box))
    }

    // =====================================================================
    //  Calculator panel: result/expression in the header, 5 x 5 key pad below
    // =====================================================================
    private val calcExpr = StringBuilder()
    private var calcHist = ""
    private var calcDone = false
    private var calcErr = false
    private var calcTop: TextView? = null
    private var calcBot: TextView? = null

    private val calcRows = listOf(
        listOf("C", "⌫", "(", ")", "÷"),
        listOf("7", "8", "9", "√", "×"),
        listOf("4", "5", "6", "^", "−"),
        listOf("1", "2", "3", "%", "+"),
        listOf("0", ".", "=", "INS")
    )

    private fun openCalc() {
        if (panel != Panel.CALC && calcExpr.isEmpty()) {
            // a selected sum such as 12*5+3 is loaded straight into the calculator
            val sel = selectedText().trim()
            if (sel.isNotEmpty() && sel.length < 60 &&
                Regex("^[0-9+\\-*/×÷−().% ^√]+$").matches(sel) && sel.any { it.isDigit() }
            ) {
                calcExpr.append(sel.replace(" ", "").replace('*', '×').replace('/', '÷').replace('-', '−'))
                calcDone = false
            }
        }
        togglePanel(Panel.CALC)
    }

    private fun isCalcOp(c: Char) = c == '+' || c == '−' || c == '×' || c == '÷' || c == '^'

    private fun calcPress(k: String) {
        if (k == "INS") {
            calcInsert(false)
            return
        }
        calcErr = false
        when (k) {
            "C" -> {
                calcExpr.setLength(0)
                calcHist = ""
                calcDone = false
            }
            "⌫" -> {
                if (calcDone) {
                    calcDone = false
                    calcHist = ""
                }
                if (calcExpr.isNotEmpty()) calcExpr.setLength(calcExpr.length - 1)
            }
            "=" -> calcEquals()
            else -> calcAppend(k[0])
        }
        calcRefresh()
    }

    private fun calcAppend(k: Char) {
        val startsValue = k.isDigit() || k == '.' || k == '(' || k == '√'
        if (calcDone) {
            if (startsValue) calcExpr.setLength(0)      // new sum; an operator carries on with the result
            calcDone = false
            calcHist = ""
        }
        val last = if (calcExpr.isNotEmpty()) calcExpr[calcExpr.length - 1] else ' '
        when {
            k.isDigit() -> {
                if (last == ')' || last == '%') calcExpr.append('×')
                calcExpr.append(k)
            }
            k == '.' -> {
                var i = calcExpr.length - 1
                while (i >= 0 && (calcExpr[i].isDigit() || calcExpr[i] == '.')) i--
                val seg = calcExpr.substring(i + 1)
                if (seg.contains('.')) return
                if (seg.isEmpty()) {
                    if (last == ')' || last == '%') calcExpr.append('×')
                    calcExpr.append('0')
                }
                calcExpr.append('.')
            }
            k == '(' || k == '√' -> {
                if (last == ')' || last == '%') calcExpr.append('×')
                calcExpr.append(k)
            }
            k == ')' -> {
                val open = calcExpr.count { it == '(' } - calcExpr.count { it == ')' }
                if (open > 0 && (last.isDigit() || last == ')' || last == '%')) calcExpr.append(')')
            }
            k == '%' -> {
                if (last.isDigit() || last == ')') calcExpr.append('%')
            }
            isCalcOp(k) -> {
                if (calcExpr.isEmpty() || last == '(' || last == '√') {
                    if (k == '−') calcExpr.append(k)          // a leading minus is allowed
                } else if (isCalcOp(last)) {
                    // 5×−3 is fine, otherwise the new operator replaces the old one
                    if (k == '−' && (last == '×' || last == '÷' || last == '^')) calcExpr.append(k)
                    else {
                        calcExpr.setLength(calcExpr.length - 1)
                        if (calcExpr.isNotEmpty()) calcExpr.append(k)
                    }
                } else {
                    calcExpr.append(k)
                }
            }
        }
    }

    private fun calcEquals() {
        val s = calcExpr.toString()
        if (s.isEmpty() || calcDone) return
        val v = Calc.eval(s)
        if (v == null) {
            calcErr = true
            return
        }
        calcHist = "$s ="
        calcExpr.setLength(0)
        calcExpr.append(Calc.format(v).replace('-', '−'))
        calcDone = true
    }

    /** Types "12×5+3 = 63" into the chat (long-press: just the result). */
    private fun calcInsert(resultOnly: Boolean) {
        val text: String
        if (calcDone) {
            val result = calcExpr.toString()
            val expr = calcHist.removeSuffix(" =")
            text = if (resultOnly) result else "$expr = $result"
        } else {
            val s = calcExpr.toString()
            val v = Calc.eval(s) ?: return
            val result = Calc.format(v).replace('-', '−')
            val plainNumber = s.all { it.isDigit() || it == '.' }
            text = if (resultOnly || plainNumber) result else "$s = $result"
        }
        flushSinglish()
        currentInputConnection?.commitText(text, 1)
        if (::snow.isInitialized) snow.burst()
    }

    private fun calcRefresh() {
        val top = calcTop ?: return
        val bot = calcBot ?: return
        if (calcDone) {
            top.text = calcExpr.toString()
            bot.text = calcHist
            bot.setTextColor(th.hint)
        } else {
            top.text = calcExpr.toString().ifEmpty { "0" }
            val live = if (calcErr) null else Calc.eval(calcExpr.toString())
            val liveText = if (live != null) Calc.format(live).replace('-', '−') else ""
            when {
                calcErr -> {
                    bot.text = "Error"
                    bot.setTextColor(0xFFFF5252.toInt())
                }
                liveText.isNotEmpty() && liveText != calcExpr.toString() -> {
                    bot.text = "= $liveText"
                    bot.setTextColor(th.accent)
                }
                else -> bot.text = ""
            }
        }
    }

    private fun renderCalc() {
        // header: back button + expression/result display
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(keyH))
        }
        h.addView(backButton())
        val disp = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(dp(6), 0, dp(14), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        val top = TextView(this).apply {
            setTextColor(th.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.START
        }
        val bot = TextView(this).apply {
            setTextColor(th.hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            gravity = Gravity.END
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.START
        }
        disp.addView(top)
        disp.addView(bot)
        h.addView(disp)
        calcTop = top
        calcBot = bot
        root.addView(h)

        // key pad
        val pad = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(keyH * 4))
            setPadding(dp(3), 0, dp(3), dp(3))
        }
        for (rowKeys in calcRows) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            for (k in rowKeys) {
                val digit = k.length == 1 && (k[0].isDigit() || k[0] == '.')
                val bg = when {
                    k == "=" -> th.accent
                    k == "INS" -> th.active
                    digit -> th.key
                    else -> th.special
                }
                val label = if (k == "INS") "Insert ↵" else k
                val tv = TextView(this).apply {
                    text = label
                    gravity = Gravity.CENTER
                    setTextColor(if (k == "=" || k == "INS") Color.WHITE else th.text)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (k == "INS") 14f else 19f)
                    typeface = if (digit) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
                    background = roundBg(bg, 10)
                    isClickable = true
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, if (k == "INS") 2f else 1f)
                        .apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
                    setOnClickListener {
                        feedback(it)
                        calcPress(k)
                    }
                    if (k == "INS") setOnLongClickListener {
                        calcInsert(true)
                        true
                    } else if (k == "⌫") setOnLongClickListener {
                        calcPress("C")
                        true
                    }
                }
                r.addView(tv)
            }
            pad.addView(r)
        }
        root.addView(pad)
        calcRefresh()
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
        // a photo and a video background exclude each other
        if (bgOn && videoName != null) {
            videoName = null
            prefs.edit().remove("vbg").apply()
        }
    }

    // ---------- animated (video) background ----------
    private var videoName: String? = null
    private var videoView: android.view.TextureView? = null
    private var videoSurface: android.view.Surface? = null
    private var player: android.media.MediaPlayer? = null
    private var videoW = 0
    private var videoH = 0
    private var windowVisible = true

    private fun videoActive(): Boolean = videoName != null && th.live == null

    fun setVideoBackground(name: String) {
        videoName = name
        prefs.edit().putString("vbg", name).putBoolean("bgon", false).remove("bgname").apply()
        try {
            File(filesDir, "kb_bg.jpg").delete()
        } catch (e: Exception) {
        }
        bgOn = false
        bgBitmap = null
        if (videoSurface != null) startVideo()
        if (::root.isInitialized) render()
    }

    private fun applyVideoBg() {
        val v = videoView ?: return
        val vis = if (videoActive()) View.VISIBLE else View.GONE
        if (v.visibility != vis) v.visibility = vis
        if (vis == View.GONE) releasePlayer()
    }

    /** Keep the video exactly behind the keyboard (the window can be taller because of effects). */
    private fun syncVideo() {
        val v = videoView ?: return
        if (!::root.isInitialized || root.width == 0 || root.height == 0) return
        val lp = v.layoutParams as? FrameLayout.LayoutParams ?: return
        val g = Gravity.TOP or Gravity.START
        if (lp.width != root.width || lp.height != root.height ||
            lp.leftMargin != root.left || lp.topMargin != root.top || lp.gravity != g
        ) {
            lp.gravity = g
            lp.width = root.width
            lp.height = root.height
            lp.leftMargin = root.left
            lp.topMargin = root.top
            v.layoutParams = lp
        }
    }

    private fun startVideo() {
        val name = videoName ?: return
        val sf = videoSurface ?: return
        releasePlayer()
        try {
            val mp = android.media.MediaPlayer()
            if (AnimatedBackgrounds.isCustom(name)) {
                mp.setDataSource(AnimatedBackgrounds.videoFile(this, name).path)   // video the user added
            } else {
                assets.openFd("videos/$name.mp4").use { afd ->
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
            }
            mp.setSurface(sf)
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.setOnVideoSizeChangedListener { _, w, h ->
                videoW = w
                videoH = h
                fitVideo()
            }
            mp.setOnPreparedListener { p -> if (windowVisible) p.start() }
            mp.setOnErrorListener { _, _, _ ->
                releasePlayer()
                true
            }
            mp.prepareAsync()
            player = mp
        } catch (e: Exception) {
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        try {
            player?.release()
        } catch (e: Exception) {
        }
        player = null
    }

    /** Fill the keyboard with the video (centre-crop), like a wallpaper. */
    private fun fitVideo() {
        val v = videoView ?: return
        val vw = v.width.toFloat()
        val vh = v.height.toFloat()
        if (vw <= 0f || vh <= 0f || videoW <= 0 || videoH <= 0) return
        val scale = maxOf(vw / videoW, vh / videoH)
        val m = android.graphics.Matrix()
        m.setScale(scale * videoW / vw, scale * videoH / vh, vw / 2f, vh / 2f)
        v.setTransform(m)
    }

    /** Called by PickImageActivity after a new photo was saved. */
    fun reloadBackground() {
        loadBackground()
        if (::root.isInitialized) render()
    }

    fun removeBackground() {
        videoName = null
        prefs.edit().putBoolean("bgon", false).remove("bgname").remove("vbg").apply()
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
        if (videoActive()) {
            // the video is drawn behind; only a light tint of the theme colour here so keys stay readable
            root.setBackgroundColor((th.bg and 0x00FFFFFF) or (0x44 shl 24))
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
        if (((bgOn && bgBitmap != null) || videoActive()) && Color.alpha(c) == 255) (c and 0x00FFFFFF) or (0xCC shl 24) else c
}
