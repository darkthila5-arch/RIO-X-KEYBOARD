package com.rio.board

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the new APK from the link you set in the admin, checks it is really RIO BOARD
 * (same app, newer version, same signing key) and opens Android's installer.
 * Android always asks the user to press Install; an app can not install silently.
 */
class UpdateActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var bar: ProgressBar
    private lateinit var btn: Button
    private var url = ""
    private var started = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        url = intent.getStringExtra("url") ?: ""
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
        }
        box.addView(TextView(this).apply {
            text = "Update RIO BOARD"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        })
        status = TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(12), 0, dp(8))
        }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        btn = Button(this).apply { visibility = View.GONE }
        box.addView(status)
        box.addView(bar)
        box.addView(btn)
        setContentView(box)
    }

    override fun onResume() {
        super.onResume()
        if (!started) begin()
    }

    private fun begin() {
        if (!url.startsWith("https://")) {
            fail("This update link is not valid.")
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            status.text = "Android needs your OK before RIO BOARD can install an update. " +
                "Tap the button, switch on “Allow from this source”, then come back."
            btn.visibility = View.VISIBLE
            btn.text = "Open settings"
            btn.setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    fail("Open Settings → Apps → RIO BOARD → Install unknown apps, switch it on, then try again.")
                }
            }
            return                                  // onResume runs again when the user comes back
        }
        started = true
        btn.visibility = View.GONE
        bar.visibility = View.VISIBLE
        status.text = "Downloading…"
        Thread {
            var err: String? = null
            var out: File? = null
            try {
                val dir = File(cacheDir, "update")
                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                val f = File(dir, "RIO-BOARD-update.apk")
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 20000
                if (c.responseCode != 200) throw IOException("the server said " + c.responseCode)
                val len = c.contentLengthLong
                var done = 0L
                var last = -1
                c.inputStream.use { ins ->
                    f.outputStream().use { o ->
                        val buf = ByteArray(16384)
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            done += n
                            if (done > 200_000_000L) throw IOException("file is too big")
                            o.write(buf, 0, n)
                            if (len > 0) {
                                val pct = (done * 100 / len).toInt()
                                if (pct != last) {
                                    last = pct
                                    runOnUiThread {
                                        bar.progress = pct
                                        status.text = "Downloading… $pct%"
                                    }
                                }
                            }
                        }
                    }
                }
                out = f
            } catch (e: Exception) {
                err = e.message ?: "unknown error"
            }
            val file = out
            runOnUiThread {
                if (file != null && err == null) verifyAndInstall(file) else fail("Download failed: $err")
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun verifyAndInstall(f: File) {
        val info = try {
            packageManager.getPackageArchiveInfo(f.path, 0)
        } catch (e: Exception) {
            null
        }
        if (info == null || info.packageName != packageName) {
            fail("This file is not a RIO BOARD update.")
            return
        }
        val mine = try {
            packageManager.getPackageInfo(packageName, 0).versionCode
        } catch (e: Exception) {
            0
        }
        if (info.versionCode <= mine) {
            fail("You already have this version or a newer one.")
            return
        }
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val flag = PackageManager.GET_SIGNING_CERTIFICATES
                val a = packageManager.getPackageArchiveInfo(f.path, flag)?.signingInfo?.apkContentsSigners
                val m = packageManager.getPackageInfo(packageName, flag).signingInfo?.apkContentsSigners
                if (a != null && m != null && a.isNotEmpty() && m.isNotEmpty() &&
                    !a[0].toByteArray().contentEquals(m[0].toByteArray())
                ) {
                    fail(
                        "This build is signed with a different key, so Android can not update over the installed app. " +
                            "Uninstall RIO BOARD once and install the new APK fresh (see ADMIN-SETUP)."
                    )
                    return
                }
            } catch (e: Exception) {
                // could not compare: let Android decide
            }
        }
        status.text = "Opening the installer…"
        try {
            val uri = RioFileProvider.uriFor(this, f)
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
        } catch (e: Exception) {
            fail("Could not open the installer.")
        }
    }

    private fun fail(msg: String) {
        started = true
        bar.visibility = View.GONE
        status.text = msg
        btn.visibility = View.VISIBLE
        btn.text = "Close"
        btn.setOnClickListener { finish() }
    }
}
