package com.rio.board

import android.content.SharedPreferences
import org.json.JSONObject

/** Text shortcuts: type "gm" + space -> "Good morning". */
object Snips {
    const val DEFAULT =
        "{\"gm\":\"Good morning\",\"gn\":\"Good night\",\"brb\":\"Be right back\",\"omw\":\"On my way\",\"ty\":\"Thank you\"}"

    fun load(prefs: SharedPreferences): JSONObject {
        return try {
            JSONObject(prefs.getString("snips", DEFAULT) ?: DEFAULT)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    fun save(prefs: SharedPreferences, o: JSONObject) {
        prefs.edit().putString("snips", o.toString()).apply()
    }
}
