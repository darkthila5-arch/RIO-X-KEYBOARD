package com.rio.board

/**
 * Turns the letters a finger glided over into word guesses.
 *
 * [path] is the list of keys touched, in order, consecutive repeats removed
 * (gliding t -> h -> e gives "the", usually with a few extra letters in between).
 * A word is a candidate when it starts and ends on the first/last key of the path
 * and all its letters appear in order inside the path.
 */
object SwipeDecoder {

    /**
     * @param words   candidates, most common first (earlier = preferred on ties)
     * @param bonus   extra score for words the user types a lot
     */
    fun rank(
        path: String,
        words: List<String>,
        bonus: (String) -> Int = { 0 },
        max: Int = 3
    ): List<String> {
        if (path.length < 2) return emptyList()
        val first = path.first()
        val last = path.last()
        val scored = ArrayList<Pair<String, Double>>()
        for ((idx, original) in words.withIndex()) {
            val w = original.lowercase()
            if (w.length < 2 || w[0] != first || w[w.length - 1] != last) continue
            if (!w.all { it in 'a'..'z' }) continue
            val c = StringBuilder()
            for (ch in w) if (c.isEmpty() || c[c.length - 1] != ch) c.append(ch)
            if (!isSubsequence(c, path)) continue
            val unexplained = path.length - c.length
            val score = c.length * 4.0 - unexplained - idx * 0.01 + bonus(original)
            scored.add(original to score)
        }
        return scored.sortedByDescending { it.second }.map { it.first }.distinct().take(max)
    }

    private fun isSubsequence(word: CharSequence, path: String): Boolean {
        var i = 0
        for (ch in path) {
            if (i < word.length && word[i] == ch) i++
        }
        return i == word.length
    }
}
