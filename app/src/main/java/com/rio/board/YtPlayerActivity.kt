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
import android.webkit.JavascriptInterface
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
 *
 * It first tries the clean YouTube player. Many songs do not allow that, so when the player reports an error or does
 * not start in a few seconds it switches to the normal YouTube watch page by itself (and makes the video fill the window).
 */
class YtPlayerActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var bar: LinearLayout
    private lateinit var status: TextView
    private lateinit var modeBtn: TextView
    private var id = ""
    private var fullPage = false           // false: clean player (embed), true: the normal YouTube watch page
    private var playing = true
    private var playerStarted = false
    private var userPaused = false        // true only when YOU pressed pause in the floating window
    private var resumes = 0               // how many times we restarted a song the page paused by itself
    private var pipRequested = false      // float as soon as the video is really playing
    private val ACTION = "com.rio.board.YT_TOGGLE"
    private var receiverOn = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            toggle()
        }
    }

    /** Messages from the page: player state (1 = playing, 2 = paused) and errors (the video can not be embedded). */
    private inner class Bridge {
        @JavascriptInterface
        fun state(s: Int) {
            runOnUiThread {
                if (s == 1) {
                    playerStarted = true
                    playing = true
                    status.text = "Playing"
                    if (pipRequested) {                     // float only once the video really runs
                        pipRequested = false
                        web.postDelayed({ enterPip() }, 300)
                    }
                    if (inPip()) setPipActions()
                } else if (s == 2) {
                    playing = false
                    if (inPip()) setPipActions()
                    if (!userPaused && !fullPage && resumes < 60) {
                        // the page paused the song by itself (window change): start it again
                        resumes++
                        status.text = "Paused by the page, starting again ($resumes)"
                        web.postDelayed({ embedCmd("playVideo") }, 400)
                    }
                } else if (s == 3) {
                    status.text = "Buffering..."
                }
            }
        }

        @JavascriptInterface
        fun error(code: Int) {
            runOnUiThread {
                if (!fullPage) switchToFull("This video can not play in the small player, using the full page")
            }
        }
    }

    private val watchdog = Runnable {
        if (!fullPage && !playerStarted) switchToFull("The player did not start, using the full page")
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
            addJavascriptInterface(Bridge(), "RioBridge")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView?, url: String?) {
                    if (fullPage) {
                        kickPlay()
                        if (inPip()) fillVideo(true)
                    }
                }
            }
        }
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(0xB0000000.toInt()) }
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        status = TextView(this).apply {
            text = "Loading..."
            setTextColor(0xFFCCCCCC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        buttons.addView(barButton("\u25A3  Float") { enterPip() })
        modeBtn = barButton("Full page") {
            fullPage = !fullPage
            modeBtn.text = if (fullPage) "Player" else "Full page"
            loadPlayer()
        }
        buttons.addView(modeBtn)
        buttons.addView(barButton("Open in YouTube") {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id")))
            } catch (e: Exception) {
                toast("Could not open YouTube")
            }
        })
        buttons.addView(barButton("\u2715") { finish() })
        bar.addView(buttons)
        bar.addView(status)
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
        fullPage = false
        if (::modeBtn.isInitialized) modeBtn.text = "Full page"
        userPaused = false
        resumes = 0
        pipRequested = true
        loadPlayer()
        // float as soon as the video plays; if that never happens float anyway after a few seconds
        web.postDelayed({
            if (pipRequested) {
                pipRequested = false
                enterPip()
            }
        }, 3500)
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        setIntent(i)
        startWith(i)
    }

    private fun switchToFull(msg: String) {
        fullPage = true
        modeBtn.text = "Player"
        status.text = msg
        toast(msg)
        loadPlayer()
    }

    private fun loadPlayer() {
        if (id.isEmpty()) return
        web.removeCallbacks(watchdog)
        playerStarted = false
        if (fullPage) {
            web.loadUrl("https://m.youtube.com/watch?v=$id")
        } else {
            status.text = "Loading the player..."
            // the iframe lives on a youtube.com base address so YouTube sees a normal embedded player;
            // the small script listens to the player and tells us about errors and the playing state
            val html = """
                <html><head><meta name="referrer" content="strict-origin-when-cross-origin"></head>
                <body style="margin:0;background:#000">
                <iframe id="p" width="100%" height="100%" style="position:fixed;top:0;left:0;border:0"
                  src="https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&enablejsapi=1&rel=0&origin=https://www.youtube.com"
                  referrerpolicy="strict-origin-when-cross-origin"
                  allow="autoplay; encrypted-media; picture-in-picture" allowfullscreen></iframe>
                <script>
                var f=document.getElementById('p');
                function listen(){try{f.contentWindow.postMessage(JSON.stringify({event:'listening',id:1,channel:'widget'}),'*');}catch(e){}}
                function cmd(c){try{f.contentWindow.postMessage(JSON.stringify({event:'command',func:c,args:''}),'*');}catch(e){}}
                f.addEventListener('load',function(){listen();setTimeout(listen,500);setTimeout(listen,1500);setTimeout(function(){cmd('playVideo');},1200);});
                window.addEventListener('message',function(e){
                  var d;try{d=(typeof e.data==='string')?JSON.parse(e.data):e.data;}catch(x){return;}
                  if(!d)return;
                  if(d.event==='onError'){RioBridge.error(d.info|0);}
                  else if(d.event==='onStateChange'){RioBridge.state(d.info|0);}
                  else if(d.event==='infoDelivery'&&d.info&&typeof d.info.playerState==='number'){RioBridge.state(d.info.playerState);}
                });
                </script></body></html>
            """.trimIndent()
            web.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
            web.postDelayed(watchdog, 9000)
        }
    }

    private fun inPip(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode

    private fun embedCmd(fn: String) {
        web.evaluateJavascript(
            "try{document.getElementById('p').contentWindow.postMessage(JSON.stringify({event:'command',func:'$fn',args:''}),'*')}catch(e){}", null
        )
    }

    private fun toggle() {
        playing = !playing
        userPaused = !playing
        if (!fullPage) {
            embedCmd(if (playing) "playVideo" else "pauseVideo")
        } else {
            val act = if (playing) "play()" else "pause()"
            web.evaluateJavascript("window.__rioHold=${userPaused};document.querySelectorAll('video').forEach(function(v){v.$act})", null)
        }
        if (inPip()) setPipActions()
    }

    /** Full-page mode: start the video, and start it again whenever the page pauses it by itself. */
    private fun kickPlay() {
        web.evaluateJavascript(
            "(function(){window.__rioHold=false;if(window.__rioTimer)return;window.__rioTimer=setInterval(function(){" +
                "var v=document.querySelector('video');if(v&&v.paused&&!v.ended&&!window.__rioHold){v.play();}},1000);})()", null
        )
        status.text = "Playing (full page)"
        playerStarted = true
        if (pipRequested) web.postDelayed({
            if (pipRequested) {
                pipRequested = false
                enterPip()
            }
        }, 1500)
    }

    /** Full-page mode inside the small window: the video fills the window (a style rule beats the page's own sizes). */
    private fun fillVideo(on: Boolean) {
        val js = if (on) {
            "(function(){var s=document.getElementById('rio-fill');if(!s){s=document.createElement('style');s.id='rio-fill';" +
                "s.textContent='html,body{background:#000!important;overflow:hidden!important}" +
                "video{position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;" +
                "object-fit:contain!important;z-index:2147483647!important;background:#000!important}';" +
                "document.head.appendChild(s);}})()"
        } else {
            "(function(){var s=document.getElementById('rio-fill');if(s)s.remove();})()"
        }
        web.evaluateJavascript(js, null)
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

    override fun onResume() {
        super.onResume()
        web.onResume()
        web.resumeTimers()
    }

    override fun onStop() {
        super.onStop()
        // the floating window is a stopped-looking activity on some phones: keep the page running
        if (inPip() || playing) {
            web.onResume()
            web.resumeTimers()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!inPip() && id.isNotEmpty()) enterPip()          // Home button: keep it playing in the floating window
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        bar.visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        web.onResume()
        web.resumeTimers()
        if (fullPage) fillVideo(isInPictureInPictureMode)
    }

    override fun onDestroy() {
        web.removeCallbacks(watchdog)
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
