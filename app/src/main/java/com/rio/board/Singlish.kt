package com.rio.board

/**
 * Singlish -> Sinhala transliteration.
 *  k=ක kh=ඛ g=ග gh=ඝ ng=ඟ nG=ඞ c/ch=ච Ch=ඡ j=ජ jh=ඣ ny=ඤ nj=ඦ
 *  t=ට T=ඨ th=ත Th=ථ d=ඩ D=ඪ dh=ද Dh=ධ n=න N=ණ nd=ඳ nD=ඬ
 *  p=ප ph=ඵ b=බ bh=භ m=ම mb=ඹ y=ය r=ර l=ල L=ළ w/v=ව s=ස sh=ශ S=ෂ h=හ f=ෆ
 *  vowels: a aa ae aae i ii u uu e ee ai o oo au
 *  R = ්‍ර  Y = ්‍ය  M = ං  H = ඃ
 */
object Singlish {
    private val consonants: Map<String, String> = mapOf(
        "kh" to "ඛ", "gh" to "ඝ", "nG" to "ඞ", "ng" to "ඟ", "ch" to "ච", "Ch" to "ඡ",
        "jh" to "ඣ", "ny" to "ඤ", "nj" to "ඦ", "th" to "ත", "Th" to "ථ", "dh" to "ද",
        "Dh" to "ධ", "nd" to "ඳ", "nD" to "ඬ", "ph" to "ඵ", "bh" to "භ", "mb" to "ඹ",
        "sh" to "ශ",
        "k" to "ක", "g" to "ග", "c" to "ච", "j" to "ජ", "t" to "ට", "T" to "ඨ",
        "d" to "ඩ", "D" to "ඪ", "n" to "න", "N" to "ණ", "p" to "ප", "b" to "බ",
        "m" to "ම", "y" to "ය", "r" to "ර", "l" to "ල", "L" to "ළ", "w" to "ව",
        "v" to "ව", "s" to "ස", "S" to "ෂ", "h" to "හ", "f" to "ෆ"
    )

    private val independent: Map<String, String> = mapOf(
        "aae" to "ඈ", "aa" to "ආ", "ae" to "ඇ", "au" to "ඖ", "ai" to "ඓ",
        "ii" to "ඊ", "uu" to "ඌ", "ee" to "ඒ", "oo" to "ඕ",
        "a" to "අ", "i" to "ඉ", "u" to "උ", "e" to "එ", "o" to "ඔ"
    )

    private val pili: Map<String, String> = mapOf(
        "aae" to "\u0DD1", "aa" to "\u0DCF", "ae" to "\u0DD0", "au" to "\u0DDE", "ai" to "\u0DDB",
        "ii" to "\u0DD3", "uu" to "\u0DD6", "ee" to "\u0DDA", "oo" to "\u0DDD",
        "a" to "", "i" to "\u0DD2", "u" to "\u0DD4", "e" to "\u0DD9", "o" to "\u0DDC"
    )

    private val vowelKeys = listOf("aae", "aa", "ae", "au", "ai", "ii", "uu", "ee", "oo", "a", "i", "u", "e", "o")
    private val consKeys = consonants.keys.sortedByDescending { it.length }

    private const val HAL = "\u0DCA"
    private const val RAKAR = "\u0DCA\u200D\u0DBB"
    private const val YANSAYA = "\u0DCA\u200D\u0DBA"
    private const val KEEP_UPPER = "TDNLSRYMHCG"

    private fun normalize(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            if (c.isUpperCase() && KEEP_UPPER.indexOf(c) < 0) sb.append(c.lowercaseChar()) else sb.append(c)
        }
        return sb.toString()
    }

    fun convert(raw: String): String {
        val s = normalize(raw)
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ck = consKeys.firstOrNull { s.startsWith(it, i) }
            if (ck != null) {
                out.append(consonants[ck])
                i += ck.length
                var cluster = false
                while (i < s.length && (s[i] == 'R' || s[i] == 'Y')) {
                    out.append(if (s[i] == 'R') RAKAR else YANSAYA)
                    cluster = true
                    i++
                }
                val vk = vowelKeys.firstOrNull { s.startsWith(it, i) }
                if (vk != null) {
                    out.append(pili[vk])
                    i += vk.length
                } else if (!cluster) {
                    out.append(HAL)
                }
                continue
            }
            val vk = vowelKeys.firstOrNull { s.startsWith(it, i) }
            if (vk != null) {
                out.append(independent[vk])
                i += vk.length
                continue
            }
            when (s[i]) {
                'M' -> out.append("\u0D82")
                'H' -> out.append("\u0D83")
                else -> out.append(s[i])
            }
            i++
        }
        return out.toString()
    }
}
