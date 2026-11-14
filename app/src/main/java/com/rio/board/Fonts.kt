package com.rio.board

/** Stylish unicode text (bold, script, bubble, squared, strikethrough ...). */
object Fonts {

    /** How one style turns a plain letter/digit into its look. */
    private class Style(
        val name: String,
        private val up: Int = -1,                 // code point of "A"
        private val low: Int = -1,                // code point of "a"
        private val dig: Int = -1,                // code point of "0"
        private val holes: Map<Char, Int> = emptyMap(),   // letters that live elsewhere in Unicode
        private val upT: String? = null,          // or: 26-letter tables
        private val lowT: String? = null,
        private val digT: String? = null,         // 10-digit table
        private val mark: String? = null          // or: keep the letter and add a combining mark
    ) {
        fun apply(c: Char): String {
            val isUp = c in 'A'..'Z'
            val isLow = c in 'a'..'z'
            val isDig = c in '0'..'9'
            if (!isUp && !isLow && !isDig) return c.toString()
            holes[c]?.let { return cp(it) }
            if (mark != null) return if (isDig) c.toString() else c.toString() + mark
            return when {
                isUp -> when {
                    upT != null -> upT[c - 'A'].toString()
                    up >= 0 -> cp(up + (c - 'A'))
                    else -> c.toString()
                }
                isLow -> when {
                    lowT != null -> lowT[c - 'a'].toString()
                    low >= 0 -> cp(low + (c - 'a'))
                    else -> c.toString()
                }
                else -> when {
                    digT != null -> digT[c - '0'].toString()
                    dig >= 0 -> cp(dig + (c - '0'))
                    else -> c.toString()
                }
            }
        }
    }

    private fun cp(c: Int): String = String(Character.toChars(c))

    private const val SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ"
    private const val SUP_UP = "ᴬᴮᶜᴰᴱᶠᴳᴴᴵᴶᴷᴸᴹᴺᴼᴾꟴᴿˢᵀᵁⱽᵂˣʸᶻ"
    private const val SUP_LOW = "ᵃᵇᶜᵈᵉᶠᵍʰⁱʲᵏˡᵐⁿᵒᵖᑫʳˢᵗᵘᵛʷˣʸᶻ"
    private const val SUB_LOW = "ₐbcdₑfgₕᵢⱼₖₗₘₙₒₚqᵣₛₜᵤᵥwₓyz"
    private const val COOL = "ᗩᗷᑕᗪᗴᖴᘜᕼIᒍKᒪᗰᑎOᑭᑫᖇᔕTᑌᐯᗯ᙭Yᘔ"
    private const val ETHNIC_UP = "ΛßƇÐЄFƓĦĪĴҠŁMПӨƤQЯƧƬЦƲЩЖЧZ"
    private const val ETHNIC_LOW = "αႦƈԃҽϝɠԋιʝƙʅɱɳσρϙɾʂƚυʋɯxყȥ"

    private val styles: List<Style> = listOf(
        Style("Normal"),
        // ---- original 7 (indexes kept) ----
        Style("Bold", 0x1D5D4, 0x1D5EE, 0x1D7EC),
        Style("Italic", 0x1D608, 0x1D622),
        Style("Script", 0x1D4D0, 0x1D4EA),
        Style("Gothic", 0x1D56C, 0x1D586),
        Style("Mono", 0x1D670, 0x1D68A, 0x1D7F6),
        Style("Bubble", 0x24B6, 0x24D0, digT = "⓪①②③④⑤⑥⑦⑧⑨"),
        Style("Small caps", upT = SMALL_CAPS, lowT = SMALL_CAPS),
        // ---- new ----
        Style("Sans", 0x1D5A0, 0x1D5BA, 0x1D7E2),
        Style("Sans bold italic", 0x1D63C, 0x1D656, 0x1D7EC),
        Style("Serif bold", 0x1D400, 0x1D41A, 0x1D7CE),
        Style("Serif italic", 0x1D434, 0x1D44E, holes = mapOf('h' to 0x210E)),
        Style("Serif bold italic", 0x1D468, 0x1D482),
        Style(
            "Script light", 0x1D49C, 0x1D4B6, holes = mapOf(
                'B' to 0x212C, 'E' to 0x2130, 'F' to 0x2131, 'H' to 0x210B, 'I' to 0x2110, 'L' to 0x2112,
                'M' to 0x2133, 'R' to 0x211B, 'e' to 0x212F, 'g' to 0x210A, 'o' to 0x2134
            )
        ),
        Style(
            "Fraktur", 0x1D504, 0x1D51E, holes = mapOf(
                'C' to 0x212D, 'H' to 0x210C, 'I' to 0x2111, 'R' to 0x211C, 'Z' to 0x2128
            )
        ),
        Style(
            "Double-struck", 0x1D538, 0x1D552, 0x1D7D8, holes = mapOf(
                'C' to 0x2102, 'H' to 0x210D, 'N' to 0x2115, 'P' to 0x2119, 'Q' to 0x211A,
                'R' to 0x211D, 'Z' to 0x2124
            )
        ),
        Style("Black bubble", 0x1F150, 0x1F150, digT = "⓿➊➋➌➍➎➏➐➑➒"),
        Style("Squared", 0x1F130, 0x1F130),
        Style("Black squared", 0x1F170, 0x1F170),
        Style("Parenthesized", 0x1F110, 0x249C, digT = "0⑴⑵⑶⑷⑸⑹⑺⑻⑼"),
        Style("Fullwidth", 0xFF21, 0xFF41, 0xFF10),
        Style("Superscript", upT = SUP_UP, lowT = SUP_LOW, digT = "⁰¹²³⁴⁵⁶⁷⁸⁹"),
        Style("Subscript", lowT = SUB_LOW, digT = "₀₁₂₃₄₅₆₇₈₉"),
        Style("Cool", upT = COOL, lowT = COOL),
        Style("Ethnic", upT = ETHNIC_UP, lowT = ETHNIC_LOW),
        Style("Strikethrough", mark = "\u0336"),
        Style("Underline", mark = "\u0332"),
        Style("Double underline", mark = "\u0333"),
        Style("Overline", mark = "\u0305"),
        Style("Slash through", mark = "\u0338")
    )

    val names: List<String> = styles.map { it.name }

    fun convert(c: Char, style: Int): String {
        if (style <= 0 || style >= styles.size) return c.toString()
        return styles[style].apply(c)
    }

    fun sample(style: Int): String {
        val sb = StringBuilder()
        for (ch in "Hello 123") sb.append(convert(ch, style))
        return sb.toString()
    }
}
