package com.rio.board

import android.content.SharedPreferences

/**
 * The tools the user can put on the keyboard's top bar, as shown in the app (Typing tab -> Keyboard toolbar).
 * The ids must match the ids in RioBoardService.allTools. The chosen ids are saved in prefs key "pinned".
 */
object ToolCatalog {
    class Item(val id: String, val glyph: String, val label: String)

    val groups: List<Pair<String, List<Item>>> = listOf(
        "Input" to listOf(
            Item("paste", "\uD83D\uDCCB", "Paste"), Item("clip", "\uD83D\uDD58", "Clipboard"),
            Item("emoji", "\uD83D\uDE0A", "Emoji"), Item("sticker", "\uD83C\uDFAD", "Stickers & GIFs"),
            Item("lang", "\uD83C\uDF10", "Language"), Item("singlish", "A\u2192\u0D85", "Singlish"),
            Item("voice", "\uD83C\uDFA4", "Voice typing"), Item("numrow", "123", "Number row"),
            Item("swipe", "\u301C", "Swipe typing"), Item("layout", "\u0DC3\u0DD2/\u0BA4", "Layout"),
            Item("onehand", "\u261D", "One-handed")
        ),
        "Text & tools" to listOf(
            Item("fonts", "Aa", "Fonts"), Item("snips", "\u26A1", "Shortcuts"),
            Item("case", "a\u21C4A", "Change case"), Item("readmore", "\u22EF", "Read more"),
            Item("date", "\uD83D\uDCC5", "Date & time"), Item("calc", "\uD83E\uDDEE", "Calculator"),
            Item("lock", "\uD83D\uDD12", "Secret message"), Item("wcount", "#W", "Word count"),
            Item("gsearch", "G", "Google search"), Item("gtrans", "\u6587A", "Translate"),
            Item("gvoice", "G\uD83C\uDFA4", "Google voice"),
            Item("ytsearch", "\u25B6", "YouTube"),
            Item("qrmake", "QR", "QR maker"), Item("qrread", "QR\uD83D\uDCF7", "QR reader"),
            Item("voicefx", "\uD83C\uDFA4\u2728", "Voice changer"),
            Item("mock", "aBc", "sPoNgE"), Item("flip", "\u0250q", "Upside down"),
            Item("spaced", "A B", "S P A C E D"), Item("clap", "\uD83D\uDC4F", "Clap text"),
            Item("blank", "[ ]", "Blank message"), Item("divider", "\u2501\u2726\u2501", "Fancy art")
        ),
        "Edit" to listOf(
            Item("selall", "\u25A3", "Select all"), Item("copy", "\u2398", "Copy"),
            Item("cut", "\u2702", "Cut"), Item("undo", "\u21B6", "Undo"),
            Item("redo", "\u21B7", "Redo"), Item("clear", "\uD83D\uDDD1", "Clear all")
        ),
        "Cursor" to listOf(
            Item("home", "\u21E4", "Home"), Item("left", "\u2190", "Left"),
            Item("up", "\u2191", "Up"), Item("down", "\u2193", "Down"),
            Item("right", "\u2192", "Right"), Item("end", "\u21E5", "End")
        ),
        "Look & extras" to listOf(
            Item("keystyle", "\u2328", "Key style"), Item("bg", "\uD83D\uDDBC", "Background"),
            Item("effects", "\u2744", "Effects"), Item("theme", "\uD83C\uDFA8", "Theme"),
            Item("boom", "\uD83D\uDCA3", "Boom"), Item("status", "\uD83D\uDCF1", "Statuses"),
            Item("news", "\uD83D\uDCF0", "News"), Item("settings", "\u2699", "Settings")
        )
    )

    /** These can not be switched off (same list the keyboard keeps always on). */
    val locked = setOf("settings", "emoji", "lang", "singlish", "news")

    /** Tools the user switched OFF in the app (hidden everywhere in the keyboard). */
    fun off(prefs: SharedPreferences): MutableSet<String> =
        (prefs.getString("tools_off", "") ?: "").split(",").filter { it.isNotEmpty() && it !in locked }.toMutableSet()

    fun saveOff(prefs: SharedPreferences, ids: Set<String>) {
        prefs.edit().putString("tools_off", ids.joinToString(",")).apply()
    }

    val defaults = listOf("singlish", "emoji", "sticker", "clip", "fonts", "voice")

    private val valid: Set<String> by lazy { groups.flatMap { g -> g.second.map { it.id } }.toSet() }

    /** Tools currently chosen for the bar, in order. */
    fun load(prefs: SharedPreferences): MutableList<String> {
        val raw = prefs.getString("pinned", null)?.split(",")?.filter { it in valid }?.distinct()?.toMutableList()
        return if (raw.isNullOrEmpty()) defaults.toMutableList() else raw
    }

    fun save(prefs: SharedPreferences, ids: List<String>) {
        prefs.edit().putString("pinned", ids.joinToString(",")).apply()
    }

    /** true = after your chosen tools, the rest of the tools follow at the end of the bar (swipe to reach them). */
    fun showAll(prefs: SharedPreferences) = prefs.getBoolean("bar_all", true)
}
