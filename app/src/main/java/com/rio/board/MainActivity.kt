package com.rio.board

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var snipList: LinearLayout
    private lateinit var styleList: LinearLayout
    private var density = 1f

    private fun dp(v: Int) = (v * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("rio_board", MODE_PRIVATE)
        density = resources.displayMetrics.density
        val p = dp(20)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(p, p * 2, p, p)
        }

        layout.addView(TextView(this).apply {
            text = "RIO BOARD"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            typeface = Typeface.DEFAULT_BOLD
        })
        layout.addView(TextView(this).apply {
            text = "සිංහල + English Keyboard"
            setTextColor(Color.parseColor("#9AA3C0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, 0, 0, p)
        })
        layout.addView(Button(this).apply {
            text = "1. Enable RIO BOARD"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        layout.addView(Button(this).apply {
            text = "2. Select RIO BOARD"
            setOnClickListener {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        })
        layout.addView(EditText(this).apply {
            hint = "Type here to test..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        })

        // ----- Singlish -----
        layout.addView(heading("Singlish typing"))
        layout.addView(
            note(
                "Turn it on with the  A→අ  button in the keyboard toolbar (or long-press the සිං key). " +
                    "Then type English letters and they turn into Sinhala.\n\n" +
                    "mama → මම\n" +
                    "ammaa → අම්මා\n" +
                    "oyaa → ඔයා\n" +
                    "kohomadha → කොහොමද\n" +
                    "sthuthiyi → ස්තුතියි\n" +
                    "shRii → ශ්‍රී\n\n" +
                    "k ක · kh ඛ · g ග · c/ch ච · j ජ · t ට · th ත · d ඩ · dh ද · n න · N ණ · p ප · b බ · m ම · y ය · r ර · l ල · w/v ව · s ස · sh ශ · h හ\n" +
                    "Vowels: a aa ae aae i ii u uu e ee ai o oo au\n" +
                    "Capital T D N L S = ඨ ඪ ණ ළ ෂ · R = ්‍ර · Y = ්‍ය · M = ං · H = ඃ"
            )
        )

        // ----- shortcuts -----
        layout.addView(heading("Shortcuts"))
        layout.addView(note("Type the shortcut and press Space in any app, and it turns into the full text."))
        snipList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(snipList)
        val keyEt = EditText(this).apply {
            hint = "Shortcut (letters only, e.g. gm)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine(true)
        }
        val valEt = EditText(this).apply {
            hint = "Full text (e.g. Good morning)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        layout.addView(keyEt)
        layout.addView(valEt)
        layout.addView(Button(this).apply {
            text = "Add shortcut"
            setOnClickListener {
                val k = keyEt.text.toString().trim().lowercase()
                val v = valEt.text.toString().trim()
                if (k.isEmpty() || k.length > 12 || !k.all { it.isLetter() } || v.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "Shortcut: letters only (max 12), and the full text can't be empty",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val o = Snips.load(prefs)
                    o.put(k, v)
                    Snips.save(prefs, o)
                    keyEt.setText("")
                    valEt.setText("")
                    refreshSnips()
                }
            }
        })

        // ----- RIO styles -----
        layout.addView(heading("RIO STYLES"))
        layout.addView(
            note(
                "A style is put in front of every word you type. Pick one with the Aa button in the keyboard toolbar.\n" +
                    "Write or paste your own decoration below and it becomes the next RIO STYLE."
            )
        )
        styleList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(styleList)
        val styleEt = EditText(this).apply {
            hint = "Your style, e.g. 《💗🍓🎧─⬣"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine(true)
        }
        layout.addView(styleEt)
        layout.addView(Button(this).apply {
            text = "Add RIO STYLE"
            setOnClickListener {
                val v = styleEt.text.toString().trim()
                if (v.isEmpty() || v.length > RioStyles.MAX_LEN) {
                    Toast.makeText(
                        this@MainActivity,
                        "Style can't be empty or longer than ${RioStyles.MAX_LEN} characters",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val list = RioStyles.custom(prefs)
                    if (!list.contains(v) && !RioStyles.builtIn.contains(v)) list.add(v)
                    RioStyles.saveCustom(prefs, list)
                    styleEt.setText("")
                    refreshStyles()
                }
            }
        })

        // ----- background -----
        layout.addView(heading("Keyboard background"))
        layout.addView(note("Pick a photo from your gallery to use behind the keys."))
        layout.addView(Button(this).apply {
            text = "Choose photo"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, PickImageActivity::class.java))
            }
        })
        layout.addView(note("Or pick one of the built-in backgrounds:"))
        layout.addView(DefaultBackgrounds.strip(this))
        layout.addView(Button(this).apply {
            text = "Remove photo"
            setOnClickListener {
                prefs.edit().putBoolean("bgon", false).remove("bgname").apply()
                try {
                    File(filesDir, "kb_bg.jpg").delete()
                } catch (e: Exception) {
                }
                RioBoardService.instance?.reloadBackground()
                Toast.makeText(this@MainActivity, "Background removed", Toast.LENGTH_SHORT).show()
            }
        })

        layout.addView(heading("Fonts & voice"))
        layout.addView(
            note(
                "Aa button = stylish letters (Bold, Script, Bubble ...).\n" +
                    "🎤 button = voice typing (Sinhala when Singlish or the Sinhala layout is on)."
            )
        )

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#17181C"))
            isFillViewport = true
            addView(layout)
        }
        setContentView(scroll)
        refreshSnips()
        refreshStyles()
    }

    private fun heading(s: String) = TextView(this).apply {
        text = s
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(28), 0, dp(6))
    }

    private fun note(s: String) = TextView(this).apply {
        text = s
        setTextColor(Color.parseColor("#9AA3C0"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(0, 0, 0, dp(8))
    }

    private fun refreshSnips() {
        snipList.removeAllViews()
        val o = Snips.load(prefs)
        val keys = o.keys().asSequence().toList().sorted()
        if (keys.isEmpty()) snipList.addView(note("No shortcuts yet."))
        for (k in keys) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this).apply {
                text = "$k  →  ${o.optString(k)}"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(Button(this).apply {
                text = "✕"
                setOnClickListener {
                    val cur = Snips.load(prefs)
                    cur.remove(k)
                    Snips.save(prefs, cur)
                    refreshSnips()
                }
            })
            snipList.addView(row)
        }
    }

    private fun refreshStyles() {
        styleList.removeAllViews()
        val builtInCount = RioStyles.builtIn.size
        val all = RioStyles.all(prefs)
        all.forEachIndexed { i, deco ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this).apply {
                text = "${RioStyles.name(i)}\n${deco}hello ${deco}world"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (i >= builtInCount) {
                row.addView(Button(this).apply {
                    text = "✕"
                    setOnClickListener {
                        val cur = RioStyles.custom(prefs)
                        val idx = i - builtInCount
                        if (idx in cur.indices) cur.removeAt(idx)
                        RioStyles.saveCustom(prefs, cur)
                        // keep the selected style pointing at the right entry
                        val sel = RioStyles.selected(prefs)
                        if (sel == i) RioStyles.select(prefs, -1)
                        else if (sel > i) RioStyles.select(prefs, sel - 1)
                        refreshStyles()
                    }
                })
            }
            styleList.addView(row)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::snipList.isInitialized) refreshSnips()
        if (::styleList.isInitialized) refreshStyles()
    }
}
