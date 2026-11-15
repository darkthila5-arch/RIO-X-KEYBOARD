package com.rio.board

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * WhatsApp status gallery.
 *  - grid of all statuses (All / Photos / Videos), saved ones are marked
 *  - tap one = full size preview (swipe left/right for the next one) with Save, Share and Delete
 * Opened from the keyboard's status panel (extra "open" = uri of the tapped status) or from the More tab.
 */
class StatusActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private val ui = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(3)
    private val thumbs = object : LruCache<String, Bitmap>(80) {}

    private val all = ArrayList<StatusSaver.Item>()
    private val shown = ArrayList<StatusSaver.Item>()
    private var filter = 0                    // 0 all, 1 photos, 2 videos
    private var loaded = false
    private var changed = false               // something was saved/deleted -> keyboard list refreshes

    private lateinit var page: FrameLayout    // holds grid or viewer
    private lateinit var countText: TextView
    private lateinit var emptyText: TextView
    private lateinit var grid: GridView
    private lateinit var gridPage: LinearLayout
    private val tabViews = ArrayList<TextView>()
    private lateinit var adapter: GridAdapter

    // viewer
    private var viewer: FrameLayout? = null
    private var index = 0
    private var openedDirect = false
    private var videoView: VideoView? = null
    private var viewerLoadId = 0

    private val BG = Color.parseColor("#0F1014")
    private val CARD = Color.parseColor("#1A1C23")
    private val FIELD = Color.parseColor("#262932")
    private val SUB = Color.parseColor("#9AA3C0")
    private val PINK = Color.parseColor("#FF4D8D")
    private val PURPLE = Color.parseColor("#B84DFF")
    private val RED = Color.parseColor("#D64545")

    private var density = 1f
    private fun dp(v: Int) = (v * density).toInt()

    private var wantOpen: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("rio_board", MODE_PRIVATE)
        density = resources.displayMetrics.density
        window.statusBarColor = BG
        window.navigationBarColor = BG
        wantOpen = intent?.getStringExtra("open")
        openedDirect = wantOpen != null

        page = FrameLayout(this).apply { setBackgroundColor(BG) }
        buildGridPage()
        page.addView(gridPage, FrameLayout.LayoutParams(-1, -1))
        setContentView(page)

        if (StatusSaver.treeUri(prefs) == null) {
            showEmpty("No folder allowed yet.\nOpen the More tab in the RIO BOARD app and allow the WhatsApp statuses folder.")
        } else {
            load()
        }
    }

    // ------------------------------------------------------------------ grid

    private fun buildGridPage() {
        gridPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(12), dp(6))
        }
        val names = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        names.addView(label("Statuses", 26f, Color.WHITE, true))
        countText = label("", 13f, SUB, false)
        names.addView(countText)
        head.addView(names)
        head.addView(pill("↻", FIELD) { load() })
        gridPage.addView(head)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(4), dp(12), dp(8))
        }
        listOf("All", "Photos", "Videos").forEachIndexed { i, t ->
            val tv = label(t, 14f, SUB, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(8), dp(18), dp(8))
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), 0, dp(4), 0) }
                setOnClickListener {
                    filter = i
                    applyFilter()
                }
            }
            tabViews.add(tv)
            tabs.addView(tv)
        }
        gridPage.addView(tabs)

        adapter = GridAdapter()
        grid = GridView(this).apply {
            numColumns = 3
            horizontalSpacing = dp(4)
            verticalSpacing = dp(4)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            setPadding(dp(8), 0, dp(8), dp(12))
            clipToPadding = false
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            this.adapter = this@StatusActivity.adapter
            setOnItemClickListener { _, _, pos, _ -> openViewer(pos) }
        }
        emptyText = label("", 15f, SUB, false).apply {
            gravity = Gravity.CENTER
            setPadding(dp(30), dp(60), dp(30), dp(30))
            visibility = View.GONE
        }
        val body = FrameLayout(this)
        body.addView(grid, FrameLayout.LayoutParams(-1, -1))
        body.addView(emptyText, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        gridPage.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        paintTabs()
    }

    private fun paintTabs() {
        tabViews.forEachIndexed { i, t ->
            val on = i == filter
            t.setTextColor(if (on) Color.WHITE else SUB)
            t.background = if (on) gradient(PINK, PURPLE, 20) else round(FIELD, 20)
        }
    }

    private fun showEmpty(msg: String) {
        emptyText.text = msg
        emptyText.visibility = View.VISIBLE
        countText.text = ""
    }

    private fun load() {
        val tree = StatusSaver.treeUri(prefs) ?: return
        countText.text = "Loading..."
        Thread {
            var list: List<StatusSaver.Item> = emptyList()
            var failed = false
            try {
                list = StatusSaver.list(this, tree)
            } catch (e: Exception) {
                failed = true
            }
            ui.post {
                if (isFinishing) return@post
                loaded = true
                all.clear()
                all.addAll(list)
                applyFilter()
                if (failed) showEmpty("Could not read the folder.\nAllow it again in the More tab.")
                wantOpen?.let { w ->
                    wantOpen = null
                    val i = shown.indexOfFirst { it.uri.toString() == w }
                    if (i >= 0) openViewer(i) else if (openedDirect) openedDirect = false
                }
            }
        }.start()
    }

    private fun applyFilter() {
        shown.clear()
        all.filterTo(shown) {
            when (filter) {
                1 -> !it.isVideo
                2 -> it.isVideo
                else -> true
            }
        }
        paintTabs()
        adapter.notifyDataSetChanged()
        countText.text = if (all.isEmpty()) "" else "${shown.size} of ${all.size}"
        if (shown.isEmpty() && loaded) {
            showEmpty(
                if (all.isEmpty()) "No statuses yet.\nWatch a status in WhatsApp, then press ↻."
                else "Nothing in this tab."
            )
        } else {
            emptyText.visibility = View.GONE
        }
    }

    private inner class GridAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val cell: Cell = (convertView as? Cell) ?: Cell()
            val item = shown[position]
            val key = item.uri.toString()
            cell.bind(key, item)
            return cell
        }
    }

    private inner class Cell : FrameLayout(this@StatusActivity) {
        private val img = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        private val play = label("▶", 13f, Color.WHITE, true).apply {
            setShadowLayer(5f, 0f, 0f, Color.BLACK)
            setPadding(dp(8), 0, 0, dp(5))
        }
        private val saved = label("✓", 12f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = round(Color.parseColor("#2E9E63"), 20)
        }
        private var tag2: String? = null

        init {
            background = round(CARD, 12)
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
                }
            }
            addView(img, FrameLayout.LayoutParams(-1, -1))
            addView(play, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START))
            addView(saved, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(6), dp(6), 0) })
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            // square cells
            super.onMeasure(widthMeasureSpec, widthMeasureSpec)
        }

        fun bind(key: String, item: StatusSaver.Item) {
            tag2 = key
            play.visibility = if (item.isVideo) View.VISIBLE else View.GONE
            saved.visibility = if (StatusSaver.savedKeys.contains(key)) View.VISIBLE else View.GONE
            val b = thumbs.get(key)
            if (b != null) {
                img.setImageBitmap(b)
            } else {
                img.setImageBitmap(null)
                pool.execute {
                    val t = StatusSaver.thumb(this@StatusActivity, item)
                    if (t != null) {
                        thumbs.put(key, t)
                        ui.post { if (tag2 == key) img.setImageBitmap(t) }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ viewer (full size preview)

    private fun openViewer(pos: Int) {
        if (pos !in shown.indices) return
        index = pos
        val v = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        viewer = v
        page.addView(v, FrameLayout.LayoutParams(-1, -1))
        showCurrent()
    }

    private fun closeViewer() {
        stopVideo()
        viewer?.let { page.removeView(it) }
        viewer = null
        if (openedDirect) {
            finish()
            return
        }
        adapter.notifyDataSetChanged()
    }

    private fun stopVideo() {
        try {
            videoView?.stopPlayback()
        } catch (e: Exception) {
        }
        videoView = null
    }

    private fun showCurrent() {
        val v = viewer ?: return
        stopVideo()
        v.removeAllViews()
        if (shown.isEmpty()) {
            closeViewer()
            return
        }
        index = index.coerceIn(0, shown.size - 1)
        val item = shown[index]
        val key = item.uri.toString()
        val myLoad = ++viewerLoadId

        // ----- media -----
        if (item.isVideo) {
            val vv = VideoView(this)
            videoView = vv
            v.addView(vv, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
            vv.setOnPreparedListener { mp ->
                mp.isLooping = true
                vv.start()
            }
            vv.setOnErrorListener { _, _, _ ->
                Toast.makeText(this, "Could not play this video", Toast.LENGTH_SHORT).show()
                true
            }
            vv.setVideoURI(item.uri)
        } else {
            val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            v.addView(iv, FrameLayout.LayoutParams(-1, -1))
            val maxPx = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
            thumbs.get(key)?.let { iv.setImageBitmap(it) }      // quick preview first
            pool.execute {
                val b = StatusSaver.fullBitmap(this, item, maxPx)
                if (b != null) ui.post { if (myLoad == viewerLoadId && viewer != null) iv.setImageBitmap(b) }
            }
        }

        // swipe left/right = next/previous, tap on video = pause/play
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                videoView?.let { if (it.isPlaying) it.pause() else it.start() }
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                val dy = e2.y - (e1?.y ?: e2.y)
                if (abs(dx) > dp(80) && abs(dx) > abs(dy)) {
                    go(if (dx < 0) 1 else -1)
                    return true
                }
                return false
            }
        })
        val touch = View(this).apply { setOnTouchListener { _, ev -> gd.onTouchEvent(ev); true } }
        v.addView(touch, FrameLayout.LayoutParams(-1, -1))

        // ----- top bar -----
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(30), dp(14), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#CC000000"), Color.TRANSPARENT)
            )
        }
        top.addView(pill("←", Color.parseColor("#44FFFFFF")) { onBackPressed() })
        top.addView(label("  ${index + 1} / ${shown.size}", 15f, Color.WHITE, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        top.addView(label(if (item.isVideo) "VIDEO" else "PHOTO", 12f, SUB, true))
        v.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // ----- bottom bar: Save / Share / Delete -----
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(30), dp(14), dp(22))
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(Color.parseColor("#E6000000"), Color.TRANSPARENT)
            )
        }
        val isSaved = StatusSaver.savedKeys.contains(key)
        bottom.addView(action(if (isSaved) "✓  Saved" else "⬇  Save", true) { saveCurrent() })
        bottom.addView(action("➤  Share", false) { shareCurrent() })
        bottom.addView(action("🗑  Delete", false, RED) { askDelete() })
        v.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    private fun go(step: Int) {
        val n = index + step
        if (n !in shown.indices) {
            Toast.makeText(this, if (step > 0) "That's the last one" else "That's the first one", Toast.LENGTH_SHORT).show()
            return
        }
        index = n
        showCurrent()
    }

    private fun saveCurrent() {
        val item = shown.getOrNull(index) ?: return
        Thread {
            val ok = StatusSaver.saveToGallery(this, item)
            ui.post {
                if (ok) changed = true
                Toast.makeText(
                    this,
                    if (ok) "Saved to gallery (RIO Status folder)" else "Could not save (storage permission?)",
                    Toast.LENGTH_SHORT
                ).show()
                if (viewer != null && shown.getOrNull(index) === item) showCurrent()
            }
        }.start()
    }

    private fun shareCurrent() {
        val item = shown.getOrNull(index) ?: return
        try {
            val mime = when {
                item.mime.startsWith("image/") || item.mime.startsWith("video/") -> item.mime
                item.isVideo -> "video/mp4"
                else -> "image/jpeg"
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, item.uri)
                clipData = android.content.ClipData.newRawUri("", item.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share status"))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not share this status", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askDelete() {
        val item = shown.getOrNull(index) ?: return
        val tree = StatusSaver.treeUri(prefs)
        if (tree != null && !StatusSaver.canDelete(this, tree)) {
            AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Allow deleting")
                .setMessage("Deleting needs one more permission. Open the RIO BOARD app > More and allow the status folder again (tap \"Use this folder\" and then Allow).")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Delete this status?")
            .setMessage("It is removed from WhatsApp's status folder. A copy you already saved to the gallery stays.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> doDelete(item) }
            .show()
    }

    private fun doDelete(item: StatusSaver.Item) {
        Thread {
            val ok = StatusSaver.delete(this, item)
            ui.post {
                if (!ok) {
                    Toast.makeText(this, "Could not delete (allow the folder again in the app)", Toast.LENGTH_LONG).show()
                    return@post
                }
                changed = true
                val key = item.uri.toString()
                all.removeAll { it.uri.toString() == key }
                shown.removeAll { it.uri.toString() == key }
                StatusSaver.savedKeys.remove(key)
                thumbs.remove(key)
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                adapter.notifyDataSetChanged()
                countText.text = if (all.isEmpty()) "" else "${shown.size} of ${all.size}"
                if (shown.isEmpty()) {
                    closeViewer()
                    if (!isFinishing) showEmpty("No statuses left.")
                } else {
                    if (index >= shown.size) index = shown.size - 1
                    showCurrent()
                }
            }
        }.start()
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onBackPressed() {
        if (viewer != null) closeViewer() else super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        try {
            videoView?.pause()
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        stopVideo()
        pool.shutdownNow()
        if (changed) RioBoardService.instance?.statusChangedFromApp()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ small UI helpers

    private fun label(s: String, sp: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun round(color: Int, r: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
    }

    private fun gradient(a: Int, b: Int, r: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(a, b)).apply {
            cornerRadius = dp(r).toFloat()
        }

    private fun pill(t: String, color: Int, onClick: () -> Unit): TextView = label(t, 20f, Color.WHITE, true).apply {
        gravity = Gravity.CENTER
        background = round(color, 22)
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        setOnClickListener { onClick() }
    }

    private fun action(t: String, primary: Boolean, color: Int = FIELD, onClick: () -> Unit): TextView =
        label(t, 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = if (primary) gradient(PINK, PURPLE, 16) else round(if (color == FIELD) Color.parseColor("#33FFFFFF") else color, 16)
            layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply { setMargins(dp(4), 0, dp(4), 0) }
            setOnClickListener { onClick() }
        }
}
