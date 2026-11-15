package com.rio.board

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads config.json (themes, news, update notice) that you publish from the admin web page.
 * Only one thing leaves the phone: a plain GET of CONFIG_URL. Nothing the user types is ever sent.
 */
object RemoteConfig {

    /** Put YOUR link here. GitHub Pages: https://USER.github.io/REPO/config.json  (docs/config.json in the repo). */
    const val CONFIG_URL = "https://darkthila5-arch.github.io/RIO-X-KEYBOARD/config.json"

    /** c = bg, key, special, accent, active, pressed, text, hint, snow */
    class RTheme(val id: String, val name: String, val c: List<String>)
    class News(val id: String, val title: String, val body: String, val date: String, val link: String, val pinned: Boolean)
    class Update(val code: Int, val name: String, val message: String, val url: String)
    /** A wallpaper you added in the admin. file / thumb are paths below the config link, like bg/sunset.jpg */
    class RBg(val id: String, val name: String, val file: String, val thumb: String)

    class Data(
        val themes: List<RTheme>,
        val news: List<News>,
        val update: Update?,
        val hiddenThemes: Set<String> = emptySet(),      // built-in theme names the admin removed
        val hiddenBg: Set<String> = emptySet(),          // built-in photo files the admin removed
        val hiddenVideos: Set<String> = emptySet(),      // built-in animated ids the admin removed
        val backgrounds: List<RBg> = emptyList()
    )

    val empty = Data(emptyList(), emptyList(), null)

    /** Latest config in use (also read by the background strips). */
    @Volatile var current: Data = empty

    private val FILE_OK = Regex("^bg/[A-Za-z0-9._-]{1,80}$")

    private val COLOR = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
    private val KEYS = listOf("bg", "key", "special", "accent", "active", "pressed", "text", "hint", "snow")
    private const val GAP_MS = 3L * 60 * 60 * 1000     // look for changes at most every 3 hours
    private const val RETRY_MS = 10L * 60 * 1000       // after a failed try, again in 10 minutes
    private const val MAX_BYTES = 262144
    @Volatile private var busy = false

    private fun httpsOnly(s: String): String = if (s.startsWith("https://", ignoreCase = true)) s.take(300) else ""

    fun parse(text: String): Data {
        if (text.isBlank()) return empty
        val o = JSONObject(text)

        val themes = ArrayList<RTheme>()
        val ta = o.optJSONArray("themes") ?: JSONArray()
        for (i in 0 until minOf(ta.length(), 30)) {
            val t = ta.optJSONObject(i) ?: continue
            if (!t.optBoolean("enabled", true)) continue
            val name = t.optString("name", "").trim().take(18)
            if (name.isEmpty() || name.equals("Auto", true) || name.equals("Material You", true)) continue
            val cols = KEYS.map { t.optString(it, "") }
            if (cols.any { !COLOR.matches(it) }) continue
            val id = t.optString("id", "").trim().take(40).ifEmpty { name }.replace("|", "_")
            themes.add(RTheme(id, name, cols))
        }

        val news = ArrayList<News>()
        val na = o.optJSONArray("news") ?: JSONArray()
        for (i in 0 until minOf(na.length(), 50)) {
            val n = na.optJSONObject(i) ?: continue
            if (!n.optBoolean("enabled", true)) continue
            val title = n.optString("title", "").trim().take(80)
            if (title.isEmpty()) continue
            val date = n.optString("date", "").take(20)
            val id = n.optString("id", "").trim().take(40).ifEmpty { title + date }.replace("|", "_")
            news.add(News(id, title, n.optString("body", "").take(2000), date, httpsOnly(n.optString("link", "")), n.optBoolean("pinned", false)))
        }
        news.sortByDescending { it.pinned }          // pinned first, keeps the order of the file

        val u = o.optJSONObject("update")
        val update = if (u != null && u.optInt("versionCode", 0) > 0)
            Update(u.optInt("versionCode"), u.optString("versionName", "").take(20), u.optString("message", "").take(300), httpsOnly(u.optString("url", "")))
        else null

        fun strSet(a: JSONArray?): Set<String> {
            val r = HashSet<String>()
            if (a != null) for (i in 0 until minOf(a.length(), 100)) r.add(a.optString(i, ""))
            return r
        }
        val h = o.optJSONObject("hidden")

        val bgs = ArrayList<RBg>()
        val ba = o.optJSONArray("backgrounds") ?: JSONArray()
        for (i in 0 until minOf(ba.length(), 60)) {
            val b = ba.optJSONObject(i) ?: continue
            if (!b.optBoolean("enabled", true)) continue
            val file = b.optString("file", "")
            if (!FILE_OK.matches(file) || file.contains("..")) continue
            val thumb = b.optString("thumb", "").let { if (FILE_OK.matches(it) && !it.contains("..")) it else file }
            val id = b.optString("id", "").trim().take(40).ifEmpty { file }.replace("|", "_")
            bgs.add(RBg(id, b.optString("name", id).take(30), file, thumb))
        }

        return Data(
            themes, news, update,
            strSet(h?.optJSONArray("themes")), strSet(h?.optJSONArray("backgrounds")), strSet(h?.optJSONArray("videos")),
            bgs
        )
    }

    /** Folder the config link lives in, with a closing slash. */
    fun baseUrl(): String = CONFIG_URL.substringBeforeLast("/") + "/"

    fun thumbFile(ctx: Context, b: RBg): File {
        val dir = File(ctx.cacheDir, "rbg")
        dir.mkdirs()
        return File(dir, b.id.replace(Regex("[^A-Za-z0-9_-]"), "_") + "_" + b.thumb.hashCode() + ".jpg")
    }

    /** Blocking download (call from a background thread). Only https. */
    fun download(url: String, dest: File, maxBytes: Int): Boolean {
        if (!url.startsWith("https://")) return false
        val tmp = File(dest.path + ".part")
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 15000
            if (c.responseCode != 200) throw IOException("http " + c.responseCode)
            var total = 0
            c.inputStream.use { ins ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw IOException("too big")
                        out.write(buf, 0, n)
                    }
                }
            }
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
        } catch (e: Exception) {
            tmp.delete()
            false
        }
    }

    fun cached(prefs: SharedPreferences): Data = try {
        parse(prefs.getString("remote_json", "") ?: "")
    } catch (e: Exception) {
        empty
    }

    /** Checks the link in the background. onChanged runs on the main thread, only when the file really changed. */
    fun refresh(prefs: SharedPreferences, force: Boolean, onChanged: (Data) -> Unit) {
        if (CONFIG_URL.contains("YOUR_USER")) return          // link not set yet: feature stays off
        val now = System.currentTimeMillis()
        if (busy || (!force && now - prefs.getLong("remote_at", 0L) < GAP_MS)) return
        busy = true
        Thread {
            try {
                val c = URL(CONFIG_URL + "?t=" + (now / 60000)).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.requestMethod = "GET"
                c.setRequestProperty("Cache-Control", "no-cache")
                if (c.responseCode != 200) throw IOException("http " + c.responseCode)
                val buf = ByteArrayOutputStream()
                val tmp = ByteArray(4096)
                var total = 0
                c.inputStream.use { ins ->
                    while (true) {
                        val n = ins.read(tmp)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw IOException("too big")
                        buf.write(tmp, 0, n)
                    }
                }
                val text = buf.toString("UTF-8")
                val data = parse(text)                          // throws if it is not valid: then nothing is saved
                val changed = text != prefs.getString("remote_json", "")
                prefs.edit().putString("remote_json", text).putLong("remote_at", now).apply()
                if (changed) Handler(Looper.getMainLooper()).post { onChanged(data) }
            } catch (e: Exception) {
                prefs.edit().putLong("remote_at", now - GAP_MS + RETRY_MS).apply()
            } finally {
                busy = false
            }
        }.start()
    }

    // ---------- which news the user has already seen ----------
    private fun seen(prefs: SharedPreferences): Set<String> =
        (prefs.getString("news_seen", "") ?: "").split("|").filter { it.isNotEmpty() }.toSet()

    fun unread(prefs: SharedPreferences, d: Data): Int {
        val s = seen(prefs)
        return d.news.count { it.id !in s }
    }

    fun markSeen(prefs: SharedPreferences, d: Data) {
        if (d.news.isEmpty()) return
        prefs.edit().putString("news_seen", d.news.take(200).joinToString("|") { it.id }).apply()
    }

    /** The newest unread post, once only, for the small pop-up when the keyboard opens. */
    fun toastWorthy(prefs: SharedPreferences, d: Data): News? {
        val s = seen(prefs)
        val n = d.news.firstOrNull { it.id !in s } ?: return null
        if (prefs.getString("news_toast", "") == n.id) return null
        prefs.edit().putString("news_toast", n.id).apply()
        return n
    }
}
