package com.rio.board

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var listBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("rio_board", MODE_PRIVATE)
        val d = resources.displayMetrics.density
        val p = (20 * d).toInt()

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(p, p * 2, p, p)
        layout.setBackgroundColor(Color.parseColor("#17181C"))

        val title = TextView(this)
        title.text = "RIO BOARD"
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
        title.typeface = Typeface.DEFAULT_BOLD
        layout.addView(title)

        val sub = TextView(this)
        sub.text = "සිංහල + English + Singlish Keyboard"
        sub.setTextColor(Color.parseColor("#9AA3C0"))
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        sub.setPadding(0, 0, 0, p)
        layout.addView(sub)

        layout.addView(button("1. Enable RIO BOARD") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        layout.addView(button("2. Select RIO BOARD") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        val test = EditText(this)
        test.hint = "Type here to test..."
        test.setTextColor(Color.WHITE)
        test.setHintTextColor(Color.GRAY)
        layout.addView(test)

        layout.addView(section("Keyboard background"))
        layout.addView(button("Choose photo") {
            startActivity(Intent(this, PickImageActivity::class.java))
        })
        layout.addView(button("Remove photo") {
            prefs.edit().putBoolean("bgon", false).apply()
            RioBoardService.instance?.reloadBackground()
            Toast.makeText(this, "Background removed", Toast.LENGTH_SHORT).show()
        })

        layout.addView(section("Text shortcuts"))
        val hint = TextView(this)
        hint.text = "Type the shortcut then press Space and it expands. Tap an item to delete it."
        hint.setTextColor(Color.parseColor("#9AA3C0"))
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        layout.addView(hint)

        val etKey = EditText(this)
        etKey.hint = "Shortcut (e.g. gm)"
        etKey.setTextColor(Color.WHITE)
        etKey.setHintTextColor(Color.GRAY)
        etKey.setSingleLine(true)
        layout.addView(etKey)

        val etVal = EditText(this)
        etVal.hint = "Expands to (e.g. Good morning)"
        etVal.setTextColor(Color.WHITE)
        etVal.setHintTextColor(Color.GRAY)
        layout.addView(etVal)

        layout.addView(button("Add shortcut") {
            val k = etKey.text.toString().trim().lowercase()
            val v = etVal.text.toString().trim()
            if (k.isNotEmpty() && v.isNotEmpty() && !k.contains(" ")) {
                val o = Snips.load(prefs)
                o.put(k, v)
                Snips.save(prefs, o)
                etKey.setText("")
                etVal.setText("")
                refreshList()
            } else {
                Toast.makeText(this, "Shortcut must be one word and not empty", Toast.LENGTH_SHORT).show()
            }
        })

        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        layout.addView(listBox)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#17181C"))
        scroll.addView(layout)
        setContentView(scroll)
        refreshList()
    }

    private fun section(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextColor(Color.WHITE)
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        t.typeface = Typeface.DEFAULT_BOLD
        t.setPadding(0, (28 * resources.displayMetrics.density).toInt(), 0, (6 * resources.displayMetrics.density).toInt())
        return t
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setOnClickListener { onClick() }
        return b
    }

    private fun refreshList() {
        listBox.removeAllViews()
        val o = Snips.load(prefs)
        val keys = o.keys().asSequence().toList().sorted()
        for (k in keys) {
            val row = TextView(this)
            row.text = k + "  →  " + o.getString(k) + "    ✕"
            row.setTextColor(Color.WHITE)
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            val pad = (12 * resources.displayMetrics.density).toInt()
            row.setPadding(0, pad, 0, pad)
            row.setOnClickListener {
                o.remove(k)
                Snips.save(prefs, o)
                refreshList()
            }
            listBox.addView(row)
        }
    }
}
