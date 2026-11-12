package com.rio.board

/** Stylish unicode text (bold, script, bubble ...). */
object Fonts {
    val names = listOf("Normal", "Bold", "Italic", "Script", "Gothic", "Mono", "Bubble", "Small caps")

    private const val SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ"

    private fun cp(c: Int): String = String(Character.toChars(c))

    fun convert(c: Char, style: Int): String {
        if (style == 0) return c.toString()
        val isUp = c in 'A'..'Z'
        val isLow = c in 'a'..'z'
        val isDig = c in '0'..'9'
        if (!isUp && !isLow && !isDig) return c.toString()
        val u = c - 'A'
        val l = c - 'a'
        val d = c - '0'
        return when (style) {
            1 -> if (isUp) cp(0x1D5D4 + u) else if (isLow) cp(0x1D5EE + l) else cp(0x1D7EC + d)
            2 -> if (isUp) cp(0x1D608 + u) else if (isLow) cp(0x1D622 + l) else c.toString()
            3 -> if (isUp) cp(0x1D4D0 + u) else if (isLow) cp(0x1D4EA + l) else c.toString()
            4 -> if (isUp) cp(0x1D56C + u) else if (isLow) cp(0x1D586 + l) else c.toString()
            5 -> if (isUp) cp(0x1D670 + u) else if (isLow) cp(0x1D68A + l) else cp(0x1D7F6 + d)
            6 -> if (isUp) cp(0x24B6 + u) else if (isLow) cp(0x24D0 + l) else if (d == 0) "\u24EA" else cp(0x2460 + d - 1)
            7 -> if (isUp) SMALL_CAPS[u].toString() else if (isLow) SMALL_CAPS[l].toString() else c.toString()
            else -> c.toString()
        }
    }

    fun sample(style: Int): String {
        val sb = StringBuilder()
        for (ch in "Hello 123") sb.append(convert(ch, style))
        return sb.toString()
    }
}
