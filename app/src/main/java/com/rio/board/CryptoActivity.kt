package com.rio.board

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.PersistableBundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Lock / unlock screen for secret messages.
 *  mode "enc": message (from the chat box) + password  ->  🔒RIO:... text that goes back into the chat
 *  mode "dec": copied locked message + password        ->  readable text
 * The window is marked secure (no screenshots, hidden in recent apps).
 */
class CryptoActivity : Activity() {

    private val BG = 0xFF14141A.toInt()
    private val CHIP = 0xFF2A2A35.toInt()
    private val ACCENT = 0xFF7C4DFF.toInt()
    private val RED = 0xFFFF5252.toInt()
    private val GREEN = 0xFF69F0AE.toInt()

    private var enc = true
    private var whole = false
    private var busy = false
    private lateinit var msgField: EditText
    private lateinit var pwField: EditText
    private var pw2Field: EditText? = null
    private lateinit var resultBox: LinearLayout
    private val d by lazy { resources.displayMetrics.density }

    private fun dp(v: Int) = (v * d).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        enc = intent.getStringExtra("mode") != "dec"
        whole = intent.getBooleanExtra("whole", false)
        val initial = intent.getStringExtra("text") ?: ""

        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isVerticalScrollBarEnabled = false
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(28))
        }
        scroll.addView(col)

        // ---- title
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@CryptoActivity).apply {
                text = "←"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setPadding(dp(4), dp(4), dp(14), dp(4))
                setOnClickListener { finish() }
            })
            addView(TextView(this@CryptoActivity).apply {
                text = if (enc) "🔒 Lock message" else "🔓 Unlock message"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = Typeface.DEFAULT_BOLD
            })
        })
        col.addView(hint(
            if (enc) "Your message is locked with the password. Only someone who knows the password can read it."
            else "Paste the locked message, type the password the sender told you, and tap Unlock."
        ))

        // ---- message box
        col.addView(label(if (enc) "Message" else "Locked message"))
        msgField = EditText(this).apply {
            setText(initial)
            setHintTextColor(0xFF77778A.toInt())
            hint = if (enc) "Type your secret message…" else "🔒RIO:…"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                (if (enc) InputType.TYPE_TEXT_FLAG_CAP_SENTENCES else InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
            minLines = 3
            maxLines = 8
            gravity = Gravity.TOP
            background = rounded(CHIP, 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        col.addView(msgField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (!enc) {
            col.addView(smallButton("Paste from clipboard") {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val t = try {
                    cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
                } catch (e: Exception) {
                    null
                }
                if (t.isNullOrEmpty()) toast("Clipboard is empty") else msgField.setText(t)
            })
        }

        // ---- password(s)
        col.addView(label("Password"))
        pwField = passwordField("Password")
        col.addView(pwField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        if (enc) {
            col.addView(label("Repeat password"))
            val f2 = passwordField("Repeat password")
            pw2Field = f2
            col.addView(f2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        }
        var shown = false
        col.addView(smallButton("Show / hide password") {
            shown = !shown
            val v = if (shown) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
            for (f in listOfNotNull(pwField, pw2Field)) {
                val sel = f.selectionEnd
                f.inputType = InputType.TYPE_CLASS_TEXT or v
                f.setSelection(sel.coerceIn(0, f.text.length))
            }
        })
        if (enc) col.addView(hint("⚠ Do not forget the password. There is no way to get a locked message back without it."))

        // ---- action
        col.addView(TextView(this).apply {
            text = if (enc) "Lock 🔒" else "Unlock 🔓"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(ACCENT, 14)
            isClickable = true
            setOnClickListener { if (enc) doLock() else doUnlock() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { setMargins(0, dp(18), 0, dp(8)) })

        resultBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(resultBox)

        setContentView(scroll)
    }

    // ------------------------------------------------------------------ actions
    private fun doLock() {
        if (busy) return
        val msg = msgField.text.toString()
        val p1 = pwField.text.toString()
        val p2 = pw2Field?.text?.toString() ?: p1
        when {
            msg.isBlank() -> { toast("Type a message first"); return }
            p1.length < 4 -> { toast("Password must be at least 4 characters"); return }
            p1 != p2 -> { toast("The two passwords are not the same"); return }
        }
        busy = true
        resultBox.removeAllViews()
        resultBox.addView(hint("Locking…"))
        Thread {
            val token = try {
                RioCrypto.encrypt(msg, p1)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                busy = false
                resultBox.removeAllViews()
                if (token == null) showMessage("Could not lock the message", RED) else showLocked(token)
            }
        }.start()
    }

    private fun doUnlock() {
        if (busy) return
        val text = msgField.text.toString()
        val pw = pwField.text.toString()
        if (RioCrypto.findToken(text) == null) {
            toast("No locked message found. Paste it above.")
            return
        }
        if (pw.isEmpty()) {
            toast("Type the password")
            return
        }
        busy = true
        resultBox.removeAllViews()
        resultBox.addView(hint("Unlocking…"))
        Thread {
            val r = RioCrypto.decrypt(text, pw)
            runOnUiThread {
                busy = false
                resultBox.removeAllViews()
                when (r) {
                    is RioCrypto.Result.Ok -> showOpened(r.text)
                    RioCrypto.Result.WrongPassword -> showMessage("Wrong password (or the message was changed).", RED)
                    RioCrypto.Result.Invalid -> showMessage("This is not a valid locked message.", RED)
                }
            }
        }.start()
    }

    private fun showLocked(token: String) {
        resultBox.addView(card(GREEN, "Locked ✓") {
            addView(TextView(this@CryptoActivity).apply {
                text = token
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.MONOSPACE
                maxLines = 5
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                setTextIsSelectable(true)
            })
        })
        resultBox.addView(bigButton("Insert into chat", ACCENT) {
            val svc = RioBoardService.instance
            if (svc != null) {
                svc.cryptoInsert(token, whole)
                finish()
            } else {
                copy(token, false)
                toast("Copied. Paste it in the chat.")
                finish()
            }
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(bigButton("Copy", CHIP) {
            copy(token, false)
            toast("Copied")
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, dp(8), dp(4), 0) })
        row.addView(bigButton("Share…", CHIP) {
            try {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, token)
                }
                startActivity(Intent.createChooser(send, "Share locked message"))
            } catch (e: Exception) {
                toast("Could not open the share sheet")
            }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(4), dp(8), 0, 0) })
        resultBox.addView(row)
    }

    private fun showOpened(text: String) {
        resultBox.addView(card(GREEN, "Unlocked ✓") {
            addView(TextView(this@CryptoActivity).apply {
                this.text = text
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextIsSelectable(true)
            })
        })
        resultBox.addView(bigButton("Copy", CHIP) {
            copy(text, true)
            toast("Copied")
        })
    }

    private fun showMessage(msg: String, color: Int) {
        resultBox.addView(card(color, msg) {})
    }

    // ------------------------------------------------------------------ helpers
    /** Sensitive copies are flagged so the keyboard's clipboard history does not store them. */
    private fun copy(text: String, sensitive: Boolean) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("RIO", text)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        cm.setPrimaryClip(clip)
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(color)
    }

    private fun label(s: String) = TextView(this).apply {
        text = s
        setTextColor(0xFFB8B8C8.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        setPadding(dp(2), dp(14), 0, dp(5))
    }

    private fun hint(s: String) = TextView(this).apply {
        text = s
        setTextColor(0xFF9A9AB0.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        setPadding(dp(2), dp(8), dp(2), dp(2))
    }

    private fun passwordField(h: String) = EditText(this).apply {
        hint = h
        setHintTextColor(0xFF77778A.toInt())
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        isSingleLine = true
        background = rounded(CHIP, 12)
        setPadding(dp(12), 0, dp(12), 0)
    }

    private fun smallButton(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        setTextColor(0xFFB39DFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(dp(4), dp(10), dp(4), dp(2))
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun bigButton(text: String, color: Int, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(color, 14)
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply {
            setMargins(0, dp(8), 0, 0)
        }
    }

    private fun card(accent: Int, title: String, body: LinearLayout.() -> Unit): View {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(CHIP)
                setStroke(dp(1), accent)
            }
            setPadding(dp(14), dp(12), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        c.addView(TextView(this).apply {
            text = title
            setTextColor(accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(6))
        })
        c.body()
        return c
    }
}
