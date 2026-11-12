package com.rio.board

import android.content.SharedPreferences
import org.json.JSONArray

/**
 * RIO STYLES: a decoration that is put in front of every word you type.
 * e.g. style 1 + "hello world"  ->  《💗⃝⃘̬͞🍓⃘ٜٜٜ̬͠🎧⃞─⬣hello 《💗⃝⃘̬͞🍓⃘ٜٜٜ̬͠🎧⃞─⬣world
 */
object RioStyles {
    val builtIn: List<String> = listOf(
        "《💗⃝⃘̬͞🍓⃘ٜٜٜ̬͠🎧⃞─⬣",
        "《💗⃝⃘̬͞🧸⃘ٜٜٜ̬͠🍿⃞─⬣"
    )

    private const val KEY_CUSTOM = "rio_styles"
    private const val KEY_SELECTED = "rio_style_sel"
    const val MAX_LEN = 60

    fun custom(prefs: SharedPreferences): MutableList<String> {
        val out = ArrayList<String>()
        try {
            val arr = JSONArray(prefs.getString(KEY_CUSTOM, "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val s = arr.getString(i)
                if (s.isNotEmpty()) out.add(s)
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun saveCustom(prefs: SharedPreferences, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY_CUSTOM, arr.toString()).apply()
    }

    /** built-in styles first, then the ones the user added. */
    fun all(prefs: SharedPreferences): List<String> = builtIn + custom(prefs)

    /** -1 = off */
    fun selected(prefs: SharedPreferences): Int = prefs.getInt(KEY_SELECTED, -1)

    fun select(prefs: SharedPreferences, index: Int) {
        prefs.edit().putInt(KEY_SELECTED, index).apply()
    }

    fun name(index: Int): String = "RIO STYLE ${index + 1}"
}
