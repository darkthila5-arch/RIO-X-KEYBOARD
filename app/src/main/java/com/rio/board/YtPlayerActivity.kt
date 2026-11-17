package com.rio.board

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Floating YouTube player. Opened from the YouTube panel of the keyboard ("Float"): the video turns into a small
 * Picture-in-Picture window that stays on top of other apps (drag it, resize it, tap it for play/pause or to open it big).
 * The song keeps playing when the keyboard closes or you go to another app.
 */
class YtPlayerActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var bar: LinearLayout
    private var id = ""
    private var fullPage = false           // false: clean player (embed), true: the normal YouTube watch page
    private var playing = true
    private val ACTION = "com.rio.board.YT_TOGGLE"
    private var receiverOn = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            toggle()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false        // the song starts by itself
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setBackgroundColor(Color.BLACK)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView?, url: String?) {
                    if (fullPage && inPip()) fillVideo()
                }
            }
        }
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(0xB0000000.toInt()) }
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        bar.addView(barButton("\u25A3  Float") { enterPip() })
        val modeBtn = barButton("Full page") { }
        modeBtn.setOnClickListener {
            fullPage = !fullPage
            modeBtn.text = if (fullPage) "Player" else "Full page"
            loadPlayer()
        }
        bar.addView(modeBtn)
        bar.addView(barButton("Open in YouTube") {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id")))
            } catch (e: Exception) {
                toast("Could not open YouTube")
            }
        })
        bar.addView(barButton("\u2715") { finish() })
        root.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        setContentView(root)

        val f = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(receiver, f)
        receiverOn = true

        startWith(intent)
    }

    private fun barButton(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(8), dp(10), dp(8))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        setOnClickListener { onClick() }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun startWith(i: Intent?) {
        val v = i?.getStringExtra("id") ?: ""
        if (!Regex("[A-Za-z0-9_-]{6,20}").matches(v)) {
            toast("No video to play")
            finish()
            return
        }
        id = v
        playing = true
        loadPlayer()
        web.postDelayed({ enterPip() }, 700)        // float right away, the page loads inside the small window
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        setIntent(i)
        startWith(i)
    }

    private fun loadPlayer() {
        if (id.isEmpty()) return
        if (fullPage) {
            web.loadUrl("https://m.youtube.com/watch?v=$id")
        } else {
            // the iframe lives on a youtube.com base address, so YouTube sees a normal embedded player
            val html = "<html><body style=\"margin:0;background:#000\"><iframe id=\"p\" width=\"100%\" height=\"100%\" " +
                "style=\"position:fixed;top:0;left:0;border:0\" " +
                "src=\"https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&enablejsapi=1&rel=0&origin=https://www.youtube.com\" " +
                "allow=\"autoplay; encrypted-media; picture-in-picture\" allowfullscreen></iframe></body></html>"
            web.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
        }
    }

    private fun inPip(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode

    private fun toggle() {
        playing = !playing
        if (!fullPage) {
            val fn = if (playing) "playVideo" else "pauseVideo"
            web.evaluateJavascript(
                "document.getElementById('p').contentWindow.postMessage(JSON.stringify({event:'command',func:'$fn',args:''}),'*')", null
            )
        } else {
            val act = if (playing) "play()" else "pause()"
            web.evaluateJavascript("document.querySelectorAll('video').forEach(function(v){v.$act})", null)
        }
        if (inPip()) setPipActions()
    }

    /** Full-page mode inside the small window: let the video fill it. */
    private fun fillVideo() {
        web.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(!v)return;document.body.style.overflow='hidden';" +
                "v.style.cssText='position:fixed!important;top:0;left:0;width:100vw!important;height:100vh!important;" +
                "object-fit:contain;z-index:2147483647;background:#000';})()", null
        )
    }

    private fun pipParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val icon = Icon.createWithResource(this, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        val pi = PendingIntent.getBroadcast(
            this, 1, Intent(ACTION).setPackage(packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val label = if (playing) "Pause" else "Play"
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(listOf(RemoteAction(icon, label, label, pi)))
            .build()
    }

    private fun setPipActions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            pipParams()?.let { setPictureInPictureParams(it) }
        } catch (e: Exception) {
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !packageManager.hasSystemFeature("android.software.picture_in_picture")) {
            toast("This phone cannot float videos, so it plays here")
            return
        }
        try {
            pipParams()?.let { enterPictureInPictureMode(it) }
        } catch (e: Exception) {
            toast("Could not float the video")
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!inPip() && id.isNotEmpty()) enterPip()          // Home button: keep it playing in the floating window
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        bar.visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        if (isInPictureInPictureMode && fullPage) fillVideo()
    }

    override fun onDestroy() {
        if (receiverOn) {
            try { unregisterReceiver(receiver) } catch (e: Exception) { }
            receiverOn = false
        }
        try {
            web.loadUrl("about:blank")
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        } catch (e: Exception) {
        }
        super.onDestroy()
    }
}
