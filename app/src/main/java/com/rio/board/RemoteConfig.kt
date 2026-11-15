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
    /** An animated background you added in the admin (mp4 under vid/). */
    class RVideo(val id: String, val name: String, val file: String, val thumb: String)
    /** Extra emoji tab (type "emoji": items are emojis/texts) or sticker tab (type "sticker": items are file paths under packs/). */
    class Pack(val id: String, val name: String, val icon: String, val type: String, val items: List<String>)
    /** Effect defaults pushed from the admin. Applied once per [rev]; -1 = leave the user's choice alone. */
    class Defaults(val rev: Int, val effect: Int, val area: Int, val color: Int)
    class Notice(val id: String, val message: String)

    class Data(
        val themes: List<RTheme>,
        val news: List<News>,
        val update: Update?,
        val hiddenThemes: Set<String> = emptySet(),      // built-in theme names the admin removed
        val hiddenBg: Set<String> = emptySet(),          // built-in photo files the admin removed
        val hiddenVideos: Set<String> = emptySet(),      // built-in animated ids the admin removed
        val backgrounds: List<RBg> = emptyList(),
        val videos: List<RVideo> = emptyList(),
        val packs: List<Pack> = emptyList(),
        val defaults: Defaults? = null,
        val hiddenLangs: Set<String> = emptySet(),       // language ids the admin removed from the app
        val disabled: Set<String> = emptySet(),          // keyboard tools switched off by the admin (kill-switch)
        val minVersion: Int = 0,                         // installed versionCode below this = "update required"
        val notice: Notice? = null                       // maintenance / info message
    )

    val empty = Data(emptyList(), emptyList(), null)

    /** Latest config in use (also read by the background strips). */
    @Volatile var current: Data = empty

    private val FILE_OK = Regex("^bg/[A-Za-z0-9._-]{1,80}$")
    private val VID_OK = Regex("^vid/[A-Za-z0-9._-]{1,80}$")
    private val PACK_OK = Regex("^packs/[A-Za-z0-9_-]{1,40}/[A-Za-z0-9._-]{1,60}$")
    private val ID_OK = Regex("[^A-Za-z0-9_-]")

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

        // ----- video backgrounds -----
        val vids = ArrayList<RVideo>()
        val va = o.optJSONArray("videos") ?: JSONArray()
        for (i in 0 until minOf(va.length(), 30)) {
            val b = va.optJSONObject(i) ?: continue
            if (!b.optBoolean("enabled", true)) continue
            val file = b.optString("file", "")
            if (!VID_OK.matches(file) || file.contains("..")) continue
            val thumb = b.optString("thumb", "")
            if (!VID_OK.matches(thumb) || thumb.contains("..")) continue
            val id = b.optString("id", "").trim().take(40).ifEmpty { file }.replace(ID_OK, "_")
            vids.add(RVideo(id, b.optString("name", id).take(24), file, thumb))
        }

        // ----- emoji / sticker packs -----
        val packs = ArrayList<Pack>()
        val pa = o.optJSONArray("packs") ?: JSONArray()
        for (i in 0 until minOf(pa.length(), 6)) {
            val p = pa.optJSONObject(i) ?: continue
            if (!p.optBoolean("enabled", true)) continue
            val type = p.optString("type", "emoji")
            if (type != "emoji" && type != "sticker") continue
            val items = ArrayList<String>()
            val ia = p.optJSONArray("items") ?: JSONArray()
            for (k in 0 until minOf(ia.length(), if (type == "sticker") 40 else 400)) {
                val it2 = ia.optString(k, "").trim()
                if (it2.isEmpty()) continue
                if (type == "sticker") {
                    if (!PACK_OK.matches(it2) || it2.contains("..")) continue
                    if (!it2.endsWith(".png") && !it2.endsWith(".gif")) continue
                    items.add(it2)
                } else items.add(it2.take(16))
            }
            if (items.isEmpty()) continue
            val id = p.optString("id", "").trim().take(40).ifEmpty { "pack$i" }.replace(ID_OK, "_")
            packs.add(Pack(id, p.optString("name", id).take(24), p.optString("icon", "\u2B50").take(8).ifEmpty { "\u2B50" }, type, items))
        }

        // ----- defaults, languages, kill-switch, notice -----
        val dj = o.optJSONObject("defaults")
        val defaults = if (dj != null) Defaults(
            dj.optInt("rev", 0), dj.optInt("effect", -1).coerceIn(-1, 10),
            dj.optInt("area", -1).coerceIn(-1, 2), dj.optInt("color", -1).coerceIn(-1, 6)
        ) else null
        val lj = o.optJSONObject("languages")
        val fj = o.optJSONObject("flags")
        val nj = fj?.optJSONObject("notice")
        val notice = if (nj != null && nj.optBoolean("on", false) && nj.optString("message", "").isNotBlank())
            Notice(nj.optString("id", "n").take(40), nj.optString("message", "").take(240)) else null

        return Data(
            themes, news, update,
            strSet(h?.optJSONArray("themes")), strSet(h?.optJSONArray("backgrounds")), strSet(h?.optJSONArray("videos")),
            bgs, vids, packs, defaults,
            strSet(lj?.optJSONArray("hidden")), strSet(fj?.optJSONArray("disabled")),
            (fj?.optInt("minVersion", 0) ?: 0).coerceAtLeast(0), notice
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

    /** Makes a config the one in use (also tells Languages which ones the admin removed). */
    private fun adopt(d: Data): Data {
        current = d
        Languages.hidden = d.hiddenLangs
        return d
    }

    fun cached(prefs: SharedPreferences): Data = try {
        adopt(parse(prefs.getString("remote_json", "") ?: ""))
    } catch (e: Exception) {
        empty
    }

    /**
     * Effect defaults from the admin: applied once each time the admin raises "rev".
     * Returns true when something was written (the keyboard then reloads its effect).
     */
    fun applyDefaults(prefs: SharedPreferences, d: Data): Boolean {
        val df = d.defaults ?: return false
        if (df.rev <= 0 || df.rev <= prefs.getInt("defaults_rev", 0)) return false
        val e = prefs.edit().putInt("defaults_rev", df.rev)
        if (df.effect >= 0) e.putInt("effect", df.effect)
        if (df.area >= 0) {
            e.putInt("snow", df.area)
            if (df.area > 0) e.putInt("lastsnow", df.area)
        }
        if (df.color >= 0) e.putInt("snowcol", df.color)
        e.apply()
        return true
    }

    fun baseFor(path: String): String = baseUrl() + path

    /** Blocking: downloads a file that lives next to config.json. */
    fun fetch(path: String, dest: File, maxBytes: Int): Boolean = download(baseFor(path), dest, maxBytes)

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
                val data = adopt(parse(text))                   // throws if it is not valid: then nothing is saved
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
