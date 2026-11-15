package com.rio.board

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import java.io.File
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Looping video backgrounds that ship inside the app (assets/videos/NAME.mp4 + NAME.jpg preview).
 * Played muted behind the keys; paused automatically when the keyboard is hidden.
 */
object AnimatedBackgrounds {
    val items: List<Pair<String, String>> = listOf(
        "Aurora" to "aurora",
        "Neon waves" to "neon_waves",
        "Starfield" to "starfield",
        "Lava" to "lava",
        "Synthwave" to "synthwave",
        "Bokeh" to "bokeh",
        "Galaxy" to "galaxy",
        "Plasma" to "plasma"
    )

    // ---------------------------------------------------------------- videos the user added
    // stored in filesDir/vids/u_<time>.mp4 (+ .jpg preview, + .txt name)
    const val MAX_BYTES = 60L * 1024 * 1024

    /** u_ = added by the user on this phone, r_ = added by the admin (downloaded when first used). */
    fun isCustom(id: String) = id.startsWith("u_") || id.startsWith("r_")

    private fun isRemote(id: String) = id.startsWith("r_")

    private fun remoteVideo(id: String): RemoteConfig.RVideo? =
        RemoteConfig.current.videos.firstOrNull { "r_" + it.id == id }

    /** Called (main thread) when a preview picture of an admin video finished downloading. */
    val thumbListeners = HashSet<() -> Unit>()
    private val thumbBusy = HashSet<String>()

    private fun vidDir(ctx: Context): File = File(ctx.filesDir, "vids").apply { mkdirs() }

    fun videoFile(ctx: Context, id: String): File = File(vidDir(ctx), "$id.mp4")

    /** The user's own videos, oldest first: (name, id). */
    fun custom(ctx: Context): List<Pair<String, String>> {
        val files = vidDir(ctx).listFiles { f -> f.name.startsWith("u_") && f.name.endsWith(".mp4") } ?: return emptyList()
        return files.sortedBy { it.name }.map { f ->
            val id = f.name.removeSuffix(".mp4")
            val n = try {
                File(f.parentFile, "$id.txt").readText().trim()
            } catch (e: Exception) {
                ""
            }
            (if (n.isEmpty()) "My video" else n) to id
        }
    }

    /** Built-in (not hidden by the admin) + the user's own videos. */
    fun all(ctx: Context): List<Pair<String, String>> =
        items.filter { it.second !in RemoteConfig.current.hiddenVideos } +
            RemoteConfig.current.videos.map { it.name to ("r_" + it.id) } + custom(ctx)

    /**
     * Copies a video the user picked into the app and makes a preview picture.
     * Runs on its own thread; [done] is called on the main thread with the new id (or null + a reason).
     */
    fun import(ctx: Context, uri: Uri, done: (String?, String) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        Thread {
            var id: String? = null
            var msg = ""
            try {
                var name = "My video"
                var size = -1L
                ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        c.getString(0)?.let { name = it.substringBeforeLast('.').take(24) }
                        size = c.getLong(1)
                    }
                }
                if (size > MAX_BYTES) {
                    msg = "Video is too big (max 60 MB). Pick a shorter clip."
                } else {
                    val nid = "u_" + System.currentTimeMillis()
                    val f = videoFile(ctx, nid)
                    var total = 0L
                    ctx.contentResolver.openInputStream(uri)?.use { ins ->
                        f.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = ins.read(buf)
                                if (n < 0) break
                                total += n
                                if (total > MAX_BYTES) break
                                out.write(buf, 0, n)
                            }
                        }
                    }
                    if (total > MAX_BYTES || total == 0L) {
                        f.delete()
                        msg = if (total == 0L) "Could not read that video." else "Video is too big (max 60 MB). Pick a shorter clip."
                    } else {
                        val r = MediaMetadataRetriever()
                        var frame: Bitmap? = null
                        try {
                            r.setDataSource(f.path)
                            frame = r.getFrameAtTime(1_000_000) ?: r.getFrameAtTime(0)
                        } catch (e: Exception) {
                        } finally {
                            try {
                                r.release()
                            } catch (e: Exception) {
                            }
                        }
                        if (frame == null) {
                            f.delete()
                            msg = "That file is not a video this phone can play."
                        } else {
                            val w = 360
                            val h = (frame.height * (w.toFloat() / frame.width)).toInt().coerceAtLeast(1)
                            val small = Bitmap.createScaledBitmap(frame, w, h, true)
                            File(f.parentFile, "$nid.jpg").outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                            File(f.parentFile, "$nid.txt").writeText(name)
                            id = nid
                        }
                    }
                }
            } catch (e: Exception) {
                msg = "Could not add the video."
            } catch (e: OutOfMemoryError) {
                msg = "Could not add the video (not enough memory)."
            }
            main.post { done(id, msg) }
        }.start()
    }

    /** Deletes one of the user's videos; if it is the active background the background is cleared. */
    fun removeCustom(ctx: Context, id: String) {
        if (!id.startsWith("u_")) return
        val prefs = ctx.getSharedPreferences("rio_board", Context.MODE_PRIVATE)
        val active = prefs.getString("vbg", null) == id
        val dir = vidDir(ctx)
        listOf("mp4", "jpg", "txt").forEach { File(dir, "$id.$it").delete() }
        thumbs.remove(id)
        if (active) {
            val svc = RioBoardService.instance
            if (svc != null) svc.removeBackground() else prefs.edit().remove("vbg").apply()
        }
    }

    /** Makes a video the keyboard background. Admin videos are downloaded first (max 15 MB, once). */
    fun use(ctx: Context, id: String) {
        if (isRemote(id) && !videoFile(ctx, id).exists()) {
            val rv = remoteVideo(id) ?: return
            val main = Handler(Looper.getMainLooper())
            Toast.makeText(ctx, "Downloading ${rv.name}...", Toast.LENGTH_SHORT).show()
            val dest = videoFile(ctx, id)
            Thread {
                val ok = RemoteConfig.fetch(rv.file, dest, 15 * 1024 * 1024)
                main.post {
                    if (ok) activate(ctx, id)
                    else Toast.makeText(ctx, "Could not download (no internet?)", Toast.LENGTH_LONG).show()
                }
            }.start()
            return
        }
        activate(ctx, id)
    }

    private fun activate(ctx: Context, id: String) {
        val svc = RioBoardService.instance
        if (svc != null) {
            svc.setVideoBackground(id)
        } else {
            ctx.getSharedPreferences("rio_board", Context.MODE_PRIVATE).edit()
                .putString("vbg", id).putBoolean("bgon", false).remove("bgname").apply()
            try {
                File(ctx.filesDir, "kb_bg.jpg").delete()
            } catch (e: Exception) {
            }
        }
    }

    private val thumbs = HashMap<String, Bitmap>()

    /** Preview of an admin video: from the cache, otherwise downloaded in the background (then listeners redraw). */
    private fun remoteThumb(ctx: Context, id: String): Bitmap? {
        val rv = remoteVideo(id) ?: return null
        val f = File(File(ctx.cacheDir, "rvid").apply { mkdirs() }, id + "_" + rv.thumb.hashCode() + ".jpg")
        if (f.exists()) return BitmapFactory.decodeFile(f.path)
        val first = synchronized(thumbBusy) { thumbBusy.add(id) }
        if (first) {
            val main = Handler(Looper.getMainLooper())
            Thread {
                val ok = RemoteConfig.fetch(rv.thumb, f, 400 * 1024)
                synchronized(thumbBusy) { thumbBusy.remove(id) }
                if (ok) main.post { thumbListeners.toList().forEach { it() } }
            }.start()
        }
        return null
    }

    fun thumb(ctx: Context, id: String): Bitmap? {
        thumbs[id]?.let { return it }
        return try {
            val b = when {
                isRemote(id) -> remoteThumb(ctx, id)
                isCustom(id) -> BitmapFactory.decodeFile(File(vidDir(ctx), "$id.jpg").path)
                else -> ctx.assets.open("videos/$id.jpg").use { BitmapFactory.decodeStream(it) }
            }
            if (b != null) thumbs[id] = b
            b
        } catch (e: Exception) {
            null
        }
    }

    /** A sideways-scrolling row of previews; tap one to use it as the keyboard background. */
    fun strip(ctx: Context, onPicked: () -> Unit = {}, onRemoved: (() -> Unit)? = null): View {
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }
        all(ctx).forEach { (name, id) ->
            val cell = FrameLayout(ctx).apply {
                setBackgroundColor(Color.parseColor("#2B2D35"))
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
                    }
                }
                layoutParams = LinearLayout.LayoutParams(dp(110), dp(62)).apply { setMargins(dp(4), 0, dp(4), 0) }
                addView(ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    thumb(ctx, id)?.let { setImageBitmap(it) }
                    contentDescription = name
                })
                addView(TextView(ctx).apply {
                    text = "▶"
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END))
                if (id.startsWith("u_") && onRemoved != null) {
                    addView(TextView(ctx).apply {
                        text = "\u2715"
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        textSize = 12f
                        setBackgroundColor(Color.parseColor("#99000000"))
                        setOnClickListener {
                            removeCustom(ctx, id)
                            Toast.makeText(ctx, "Removed", Toast.LENGTH_SHORT).show()
                            onRemoved()
                        }
                    }, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.TOP or Gravity.END))
                }
                setOnClickListener {
                    use(ctx, id)
                    Toast.makeText(ctx, "$name set", Toast.LENGTH_SHORT).show()
                    onPicked()
                }
            }
            row.addView(cell)
        }
        return HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(row)
        }
    }
}
