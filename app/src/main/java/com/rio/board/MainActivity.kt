package com.rio.board

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val p = (20 * d).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(p, p * 2, p, p)
            setBackgroundColor(Color.parseColor("#17181C"))
        }

        val title = TextView(this).apply {
            text = "RIO BOARD"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val sub = TextView(this).apply {
            text = "සිංහල + English Keyboard"
            setTextColor(Color.parseColor("#9AA3C0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, 0, 0, p)
        }
        val enable = Button(this).apply {
            text = "1. Enable RIO BOARD"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }
        val select = Button(this).apply {
            text = "2. Select RIO BOARD"
            setOnClickListener {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        }
        val test = EditText(this).apply {
            hint = "Type here to test..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        layout.addView(title)
        layout.addView(sub)
        layout.addView(enable)
        layout.addView(select)
        layout.addView(test)
        setContentView(layout)
    }
}
