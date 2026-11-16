package com.rio.board

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * QR reader opened from the keyboard: scan with the camera (Google's code scanner, no camera permission needed)
 * or read a QR from a picture in the gallery. The result can be put in the chat, copied or opened.
 */
class QrReadActivity : Activity() {

    private lateinit var resultBox: LinearLayout
    private lateinit var resultText: TextView
    private lateinit var openBtn: Button
    private var result = ""

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        box.addView(TextView(this).apply {
            text = "QR reader"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(Button(this).apply {
            text = "\uD83D\uDCF7  Scan with camera"
            isAllCaps = false
            setOnClickListener { scanCamera() }
        })
        box.addView(Button(this).apply {
            text = "\uD83D\uDDBC  Read from a picture"
            isAllCaps = false
            setOnClickListener { pickPicture() }
        })

        resultBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        resultText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextIsSelectable(true)
            setPadding(0, dp(12), 0, dp(8))
        }
        resultBox.addView(resultText)
        resultBox.addView(Button(this).apply {
            text = "Put in chat"
            isAllCaps = false
            setOnClickListener {
                RioBoardService.instance?.cryptoInsert(result, false)   // goes in when the keyboard is back in the chat box
                finish()
            }
        })
        resultBox.addView(Button(this).apply {
            text = "Copy"
            isAllCaps = false
            setOnClickListener {
                try {
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("QR", result))
                    Toast.makeText(this@QrReadActivity, "Copied", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                }
            }
        })
        openBtn = Button(this).apply {
            text = "Open link"
            isAllCaps = false
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result)))
                } catch (e: Exception) {
                    Toast.makeText(this@QrReadActivity, "Could not open it", Toast.LENGTH_SHORT).show()
                }
            }
        }
        resultBox.addView(openBtn)
        box.addView(resultBox)
        setContentView(box)
    }

    private fun showResult(text: String) {
        result = text
        resultText.text = text
        openBtn.visibility = if (text.startsWith("http://") || text.startsWith("https://")) View.VISIBLE else View.GONE
        resultBox.visibility = View.VISIBLE
    }

    private fun scanCamera() {
        try {
            GmsBarcodeScanning.getClient(this).startScan()
                .addOnSuccessListener { code ->
                    val v = code.rawValue
                    if (!v.isNullOrEmpty()) showResult(v) else toast("Nothing found")
                }
                .addOnFailureListener {
                    toast("Camera scanner is not available on this phone. Use \"Read from a picture\".")
                }
        } catch (e: Exception) {
            toast("Camera scanner is not available on this phone. Use \"Read from a picture\".")
        }
    }

    private fun pickPicture() {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = "image/*"
        i.addCategory(Intent.CATEGORY_OPENABLE)
        try {
            startActivityForResult(Intent.createChooser(i, "Choose a picture with a QR code"), 31)
        } catch (e: Exception) {
            toast("No gallery app found")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != 31 || resultCode != RESULT_OK || uri == null) return
        Thread {
            val text = try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                if (bmp != null) QrKit.decode(bmp) else null
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (!text.isNullOrEmpty()) showResult(text) else toast("No QR code found in that picture")
            }
        }.start()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
