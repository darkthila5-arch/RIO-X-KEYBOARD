package com.rio.board

import android.content.SharedPreferences

/**
 * Extra typing languages for RIO BOARD (Hindi, Telugu, Malayalam, Kannada, Bengali, Arabic)
 * plus the helpers the language button uses.
 *
 * To add one more language: copy any block in [extra] and change the letters.
 *  - layers = the pages of keys (consonants / more consonants / vowels / signs ...), max 3 rows, max 10 keys per row
 *  - signs  = vowel signs shown in the long-press popup of a consonant (only on layers with popups = true)
 *  - voice  = locale used by voice typing
 */
object Languages {

    class Layer(val label: String, val rows: List<List<String>>, val popups: Boolean = false)

    class Lang(
        val id: String,
        val native: String,
        val english: String,
        val key: String,          // short label on the English keyboard's language key
        val voice: String,
        val layers: List<Layer>,
        val signs: List<String> = emptyList()
    )

    private fun r(s: String): List<String> = s.trim().split(" ").filter { it.isNotEmpty() }

    // dotted circle + sign, used on layer-switch keys
    private fun dot(sign: String) = "\u25CC$sign"

    val extra: List<Lang> = listOf(

        // ---------------- Hindi (Devanagari) ----------------
        Lang(
            id = "hi", native = "हिन्दी", english = "Hindi", key = "हि", voice = "hi-IN",
            signs = r("\u093E \u093F \u0940 \u0941 \u0942 \u0947 \u0948 \u094B"),
            layers = listOf(
                Layer(
                    "क", listOf(
                        r("क ख ग घ ङ च छ ज झ ञ"),
                        r("ट ठ ड ढ ण त थ द ध न"),
                        r("प फ ब भ म य र ल व श")
                    ), popups = true
                ),
                Layer(
                    "ष", listOf(
                        r("ष स ह क्ष त्र ज्ञ श्र ळ"),
                        // letters with a dot below (nukta): ड़ ढ़ क़ ख़ ग़ ज़ फ़
                        r("\u0921\u093C \u0922\u093C \u0915\u093C \u0916\u093C \u0917\u093C \u091C\u093C \u092B\u093C")
                    ), popups = true
                ),
                Layer(
                    "अ", listOf(
                        r("अ आ इ ई उ ऊ ऋ ए ऐ ओ"),
                        r("औ अं अः ऑ ऍ")
                    )
                ),
                Layer(
                    dot("\u093E"), listOf(
                        r("\u093E \u093F \u0940 \u0941 \u0942 \u0943 \u0947 \u0948 \u094B \u094C"),
                        r("\u0902 \u0901 \u0903 \u094D \u093C \u0949 \u0945 \u0964")
                    )
                )
            )
        ),

        // ---------------- Telugu ----------------
        Lang(
            id = "te", native = "తెలుగు", english = "Telugu", key = "తె", voice = "te-IN",
            signs = r("\u0C3E \u0C3F \u0C40 \u0C41 \u0C42 \u0C46 \u0C47 \u0C48"),
            layers = listOf(
                Layer(
                    "క", listOf(
                        r("క ఖ గ ఘ ఙ చ ఛ జ ఝ ఞ"),
                        r("ట ఠ డ ఢ ణ త థ ద ధ న"),
                        r("ప ఫ బ భ మ య ర ల వ శ")
                    ), popups = true
                ),
                Layer(
                    "ష", listOf(
                        r("ష స హ ళ క్ష ఱ")
                    ), popups = true
                ),
                Layer(
                    "అ", listOf(
                        r("అ ఆ ఇ ఈ ఉ ఊ ఋ ౠ ఎ ఏ"),
                        r("ఐ ఒ ఓ ఔ అం అః")
                    )
                ),
                Layer(
                    dot("\u0C3E"), listOf(
                        r("\u0C3E \u0C3F \u0C40 \u0C41 \u0C42 \u0C43 \u0C44 \u0C46 \u0C47 \u0C48"),
                        r("\u0C4A \u0C4B \u0C4C \u0C4D \u0C02 \u0C03 \u0C01")
                    )
                )
            )
        ),

        // ---------------- Malayalam ----------------
        Lang(
            id = "ml", native = "മലയാളം", english = "Malayalam", key = "മ", voice = "ml-IN",
            signs = r("\u0D3E \u0D3F \u0D40 \u0D41 \u0D42 \u0D46 \u0D47 \u0D48"),
            layers = listOf(
                Layer(
                    "ക", listOf(
                        r("ക ഖ ഗ ഘ ങ ച ഛ ജ ഝ ഞ"),
                        r("ട ഠ ഡ ഢ ണ ത ഥ ദ ധ ന"),
                        r("പ ഫ ബ ഭ മ യ ര ല വ ശ")
                    ), popups = true
                ),
                Layer(
                    "ഷ", listOf(
                        r("ഷ സ ഹ ള ഴ റ ക്ഷ"),
                        // chillu letters (ൺ ൻ ർ ൽ ൾ ൿ)
                        r("\u0D7A \u0D7B \u0D7C \u0D7D \u0D7E \u0D7F")
                    ), popups = true
                ),
                Layer(
                    "അ", listOf(
                        r("അ ആ ഇ ഈ ഉ ഊ ഋ എ ഏ ഐ"),
                        r("ഒ ഓ ഔ അം അഃ")
                    )
                ),
                Layer(
                    dot("\u0D3E"), listOf(
                        r("\u0D3E \u0D3F \u0D40 \u0D41 \u0D42 \u0D43 \u0D46 \u0D47 \u0D48 \u0D4A"),
                        r("\u0D4B \u0D4C \u0D4D \u0D02 \u0D03 \u0D57")
                    )
                )
            )
        ),

        // ---------------- Kannada ----------------
        Lang(
            id = "kn", native = "ಕನ್ನಡ", english = "Kannada", key = "ಕ", voice = "kn-IN",
            signs = r("\u0CBE \u0CBF \u0CC0 \u0CC1 \u0CC2 \u0CC6 \u0CC7 \u0CC8"),
            layers = listOf(
                Layer(
                    "ಕ", listOf(
                        r("ಕ ಖ ಗ ಘ ಙ ಚ ಛ ಜ ಝ ಞ"),
                        r("ಟ ಠ ಡ ಢ ಣ ತ ಥ ದ ಧ ನ"),
                        r("ಪ ಫ ಬ ಭ ಮ ಯ ರ ಲ ವ ಶ")
                    ), popups = true
                ),
                Layer(
                    "ಷ", listOf(
                        r("ಷ ಸ ಹ ಳ ಱ ೞ ಕ್ಷ ಜ್ಞ")
                    ), popups = true
                ),
                Layer(
                    "ಅ", listOf(
                        r("ಅ ಆ ಇ ಈ ಉ ಊ ಋ ೠ ಎ ಏ"),
                        r("ಐ ಒ ಓ ಔ ಅಂ ಅಃ")
                    )
                ),
                Layer(
                    dot("\u0CBE"), listOf(
                        r("\u0CBE \u0CBF \u0CC0 \u0CC1 \u0CC2 \u0CC3 \u0CC6 \u0CC7 \u0CC8 \u0CCA"),
                        r("\u0CCB \u0CCC \u0CCD \u0C82 \u0C83")
                    )
                )
            )
        ),

        // ---------------- Bengali ----------------
        Lang(
            id = "bn", native = "বাংলা", english = "Bengali", key = "বা", voice = "bn-IN",
            signs = r("\u09BE \u09BF \u09C0 \u09C1 \u09C2 \u09C7 \u09C8 \u09CB"),
            layers = listOf(
                Layer(
                    "ক", listOf(
                        r("ক খ গ ঘ ঙ চ ছ জ ঝ ঞ"),
                        r("ট ঠ ড ঢ ণ ত থ দ ধ ন"),
                        r("প ফ ব ভ ম য র ল শ ষ")
                    ), popups = true
                ),
                Layer(
                    "স", listOf(
                        r("স হ ক্ষ জ্ঞ ৎ"),
                        // ড় ঢ় য় (dot below)
                        r("\u09A1\u09BC \u09A2\u09BC \u09AF\u09BC")
                    ), popups = true
                ),
                Layer(
                    "অ", listOf(
                        r("অ আ ই ঈ উ ঊ ঋ এ ঐ ও"),
                        r("ঔ অং অঃ")
                    )
                ),
                Layer(
                    dot("\u09BE"), listOf(
                        r("\u09BE \u09BF \u09C0 \u09C1 \u09C2 \u09C3 \u09C7 \u09C8 \u09CB \u09CC"),
                        r("\u09CD \u0982 \u0983 \u0981 \u09BC \u0964")
                    )
                )
            )
        ),

        // ---------------- Arabic (+ Persian / Urdu letters on page 3) ----------------
        Lang(
            id = "ar", native = "العربية", english = "Arabic", key = "ع", voice = "ar-SA",
            layers = listOf(
                Layer(
                    "ا", listOf(
                        r("ض ص ث ق ف غ ع ه خ ح"),
                        r("ش س ي ب ل ا ت ن م ك"),
                        r("ئ ء ؤ ر ى ة و ز ظ ط")
                    )
                ),
                Layer(
                    "ج", listOf(
                        r("ج د ذ أ إ آ \u060C \u061B \u061F"),
                        // diacritics: fathatan dammatan kasratan fatha damma kasra shadda sukun
                        r("\u064B \u064C \u064D \u064E \u064F \u0650 \u0651 \u0652")
                    )
                ),
                Layer(
                    "پ", listOf(
                        r("پ چ ژ گ ک ی ں ھ ہ ے"),
                        r("ٹ ڈ ڑ ڤ \u06D4")
                    )
                )
            )
        )
    )

    /** Everything that can be switched on/off in the language button (English is always there). */
    class Entry(val id: String, val native: String, val english: String)

    private val allEntries: List<Entry> =
        listOf(Entry("si", "සිංහල", "Sinhala"), Entry("ta", "தமிழ்", "Tamil")) +
            extra.map { Entry(it.id, it.native, it.english) }

    /** Language ids the admin removed from the app (set by RemoteConfig). */
    @Volatile var hidden: Set<String> = emptySet()

    /** Languages the user can switch on/off (the ones the admin removed are not listed). */
    val entries: List<Entry>
        get() = allEntries.filter { it.id !in hidden }

    // layoutKind in RioBoardService: 0 Sinhala grid, 1 Wijesekara, 2 Tamil, 3 Easy Sinhala, 4+ = extra[kind - 4]

    fun idForKind(kind: Int): String = when {
        kind == 2 -> "ta"
        kind >= 4 -> extra.getOrNull(kind - 4)?.id ?: "si"
        else -> "si"
    }

    fun kindFor(id: String, sinhalaKind: Int): Int = when (id) {
        "si" -> if (sinhalaKind == 1 || sinhalaKind == 3) sinhalaKind else 0
        "ta" -> 2
        else -> extra.indexOfFirst { it.id == id }.let { if (it < 0) 0 else it + 4 }
    }

    private val sinhalaNames = listOf("Sinhala", "Sinhala · Wijesekara", "Tamil", "Sinhala · Easy")

    /** Name shown in the pop-up message when the language changes. */
    fun title(kind: Int): String = when {
        kind in 0..3 -> sinhalaNames[kind]
        else -> extra.getOrNull(kind - 4)?.let { "${it.native} · ${it.english}" } ?: "Sinhala"
    }

    fun nativeName(kind: Int): String = when {
        kind == 2 -> "தமிழ்"
        kind >= 4 -> extra.getOrNull(kind - 4)?.native ?: "සිංහල"
        else -> "සිංහල"
    }

    /** Label of the key on the English keyboard that jumps to the last used language. */
    fun keyLabel(kind: Int): String = when {
        kind == 2 -> "த"
        kind >= 4 -> extra.getOrNull(kind - 4)?.key ?: "සිං"
        else -> "සිං"
    }

    fun voiceLocale(kind: Int): String = when {
        kind == 2 -> "ta-IN"
        kind >= 4 -> extra.getOrNull(kind - 4)?.voice ?: "si-LK"
        else -> "si-LK"
    }

    // ----- which languages the language button visits -----
    private fun off(prefs: SharedPreferences): Set<String> =
        prefs.getString("langs_off", "")!!.split(",").filter { it.isNotEmpty() }.toSet()

    fun isOn(prefs: SharedPreferences, id: String) = id !in off(prefs) && id !in hidden

    fun toggle(prefs: SharedPreferences, id: String) {
        val o = off(prefs).toMutableSet()
        if (!o.remove(id)) o.add(id)
        prefs.edit().putString("langs_off", o.joinToString(",")).apply()
    }

    /** English first, then every switched-on language. */
    fun cycle(prefs: SharedPreferences): List<String> {
        val on = entries.filter { isOn(prefs, it.id) }.map { it.id }
        return listOf("en") + (if (on.isEmpty()) listOf("si") else on)
    }

    /** Layouts the long-press on ABC walks through (Sinhala has 3, the others 1 each). */
    fun layoutStops(prefs: SharedPreferences): List<Int> {
        val stops = ArrayList<Int>()
        if (isOn(prefs, "si")) stops.addAll(listOf(0, 1, 3))
        if (isOn(prefs, "ta")) stops.add(2)
        extra.forEachIndexed { i, l -> if (isOn(prefs, l.id)) stops.add(i + 4) }
        return if (stops.isEmpty()) listOf(0) else stops
    }

    /** Signs/marks get a dotted circle so they can be seen on their own key. */
    fun label(s: String): String {
        if (s.isEmpty()) return s
        val t = Character.getType(s.codePointAt(0))
        val mark = t == Character.NON_SPACING_MARK.toInt() ||
            t == Character.COMBINING_SPACING_MARK.toInt() ||
            t == Character.ENCLOSING_MARK.toInt()
        return if (mark) "\u25CC$s" else s
    }
}
