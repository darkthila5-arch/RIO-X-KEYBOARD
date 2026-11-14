package com.rio.board

/**
 * Word data for:
 *  - Singlish typo fixing   ("kohomada" -> "kohomadha" -> කොහොමද)
 *  - next-word suggestions  (after "ඔයා " -> කොහොමද / මොකද ...)
 *  - swipe typing candidates (Singlish + extra English words)
 *
 * Roman spellings below follow the scheme in Singlish.kt
 * (dh=ද, th=ත, d=ඩ, t=ට, L=ළ, N=ණ, aa=ා, ae=ැ, ee=ේ, M=ං ...).
 */
object SinhalaDict {

    /** Canonical Singlish spellings of common Sinhala words. */
    val roman: List<String> = (
        "mama api oyaa oba eyaa magee oyaagee apee eyaagee mata oyaata apita eyaata " +
        "kohomadha kohedha mokadha monawadha kiiyadha kavudha aeyi " +
        "hari ithin ow naehae naae baehae oona oonee naethnam eth nam ehenam " +
        "karanna yanna enna balanna dhenna ganna kiyanna kanna bonna " +
        "karanawaa yanawaa enawaa balanawaa kanawaa bonawaa dhenawaa kiyanawaa " +
        "thiyenawaa wenawaa hithanawaa innawaa dhannawaa " +
        "giyaa aawaa kiwwaa hitiyaa karalaa kiyalaa " +
        "puLuwan kaemathi hondayi lassanayi lassana mathakayi harima " +
        "dhaen adha heta iiyee hawasa udhee raae dhawasa udhaaesanak raathRiyak " +
        "gedhara yaaluwaa ammaa thaaththaa malli akkaa ayiyaa naMgi aadharee sathutuyi dhukayi kamak " +
        "sthuuthiyi aayuboowan subha samaawenna karuNaakara bohoma godak poddak ikmanata ithaa " +
        "thamayi eka meeka eeka ooka mehe ehe ohoma mehema ekka ekak thawa " +
        "kaaema bath wathura pansala kadee waeda kathaa " +
        "hamuwemu yamu okkoma siMhala laMkaawa jaya"
        ).split(" ").filter { it.isNotBlank() }

    /** Sinhala-script forms of [roman] (used for prefix suggestions). */
    val sinhala: List<String> by lazy { roman.map { Singlish.convert(it) }.distinct() }

    // ---------------------------------------------------------------- typo fixing

    /** Sloppy-typing key: ignores case, long vowels, aspiration marks, doubled letters. */
    fun key(s: String): String {
        var k = s.lowercase()
        k = k.replace('w', 'v')
        k = k.replace("aae", "ae")
        for (p in listOf("aa", "ee", "ii", "oo", "uu")) k = k.replace(p, p.substring(0, 1))
        for (p in listOf("dh", "th", "ph", "bh", "kh", "gh")) k = k.replace(p, p.substring(0, 1))
        val sb = StringBuilder()
        for (c in k) if (sb.isEmpty() || sb[sb.length - 1] != c) sb.append(c)
        return sb.toString()
    }

    private val index: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        for (w in roman) m.putIfAbsent(key(w), Singlish.convert(w))
        m
    }
    private val keyList: List<String> by lazy { index.keys.toList() }
    private val romanSet: Set<String> by lazy { roman.toHashSet() }

    /**
     * Returns the corrected Sinhala word for a mistyped Singlish word,
     * or null when it is already fine / nothing close enough is known.
     */
    fun correct(raw: String): String? {
        if (raw.length < 3 || raw in romanSet) return null
        val typed = Singlish.convert(raw)
        val k = key(raw)
        index[k]?.let { return if (it != typed) it else null }
        if (k.length >= 5) {
            var found: String? = null
            for (cand in keyList) {
                if (within1(cand, k)) {
                    if (found != null) return null   // ambiguous -> leave it alone
                    found = cand
                }
            }
            return found?.let { index[it] }?.takeIf { it != typed }
        }
        return null
    }

    /** True when [a] and [b] differ by at most one insertion, deletion, substitution or adjacent swap. */
    private fun within1(a: String, b: String): Boolean {
        if (a == b) return true
        val la = a.length
        val lb = b.length
        if (kotlin.math.abs(la - lb) > 1) return false
        if (la == lb) {
            var first = -1
            var second = -1
            var diffs = 0
            for (i in 0 until la) {
                if (a[i] != b[i]) {
                    diffs++
                    if (diffs == 1) first = i else if (diffs == 2) second = i else return false
                }
            }
            if (diffs == 1) return true
            return diffs == 2 && second == first + 1 && a[first] == b[second] && a[second] == b[first]
        }
        val s = if (la < lb) a else b
        val l = if (la < lb) b else a
        var i = 0
        while (i < s.length && s[i] == l[i]) i++
        return s.substring(i) == l.substring(i + 1)
    }

    // ---------------------------------------------------------------- next word

    private val nextRoman: Map<String, List<String>> = mapOf(
        "oyaa" to listOf("kohomadha", "mokadha", "kohedha"),
        "mama" to listOf("dhaen", "gedhara", "yanawaa"),
        "api" to listOf("yamu", "dhaen", "okkoma"),
        "eyaa" to listOf("kiwwaa", "giyaa", "aawaa"),
        "kohomadha" to listOf("oyaata", "oyaa", "ithin"),
        "mokadha" to listOf("oyaa", "eka", "karannee"),
        "oyaata" to listOf("mokadha", "kohomadha", "oonee"),
        "mata" to listOf("oonee", "puLuwan", "mathakayi"),
        "sthuuthiyi" to listOf("oyaata", "godak", "hari"),
        "aayuboowan" to listOf("oyaata", "oyaa", "api"),
        "subha" to listOf("udhaaesanak", "raathRiyak", "dhawasak"),
        "godak" to listOf("sthuuthiyi", "lassanayi", "hondayi"),
        "bohoma" to listOf("sthuuthiyi", "lassanayi", "hondayi"),
        "karuNaakara" to listOf("samaawenna", "mata", "eka"),
        "naehae" to listOf("mama", "oyaa", "eka"),
        "ow" to listOf("mama", "hari", "eka"),
        "hari" to listOf("mama", "oyaa", "api"),
        "dhaen" to listOf("mama", "oyaa", "api"),
        "adha" to listOf("mama", "oyaa", "api"),
        "heta" to listOf("api", "mama", "oyaa"),
        "yamu" to listOf("api", "dhaen", "heta"),
        "thawa" to listOf("eka", "godak", "poddak"),
        "eka" to listOf("hari", "naae", "thamayi")
    )

    private val nextEnglish: Map<String, List<String>> = mapOf(
        "i" to listOf("am", "have", "will"),
        "i'm" to listOf("not", "going", "so"),
        "you" to listOf("are", "can", "have"),
        "we" to listOf("are", "can", "will"),
        "how" to listOf("are", "to", "is"),
        "what" to listOf("is", "are", "do"),
        "thank" to listOf("you", "u", "god"),
        "thanks" to listOf("for", "a", "so"),
        "good" to listOf("morning", "night", "luck"),
        "happy" to listOf("birthday", "new", "to"),
        "see" to listOf("you", "the", "it"),
        "love" to listOf("you", "it", "this"),
        "let" to listOf("me", "us", "know"),
        "please" to listOf("let", "send", "call"),
        "hello" to listOf("there", "how", "sir"),
        "hi" to listOf("there", "how", "dear"),
        "on" to listOf("the", "my", "a"),
        "in" to listOf("the", "a", "my"),
        "of" to listOf("the", "a", "course"),
        "to" to listOf("be", "the", "do"),
        "are" to listOf("you", "we", "they"),
        "do" to listOf("you", "not", "it"),
        "will" to listOf("be", "you", "not"),
        "can" to listOf("you", "i", "we"),
        "is" to listOf("the", "it", "this"),
        "it" to listOf("is", "was", "will"),
        "the" to listOf("same", "best", "first"),
        "going" to listOf("to", "home", "out"),
        "have" to listOf("a", "to", "you")
    )

    private val nextMap: Map<String, List<String>> by lazy {
        val m = HashMap<String, List<String>>()
        for ((k, v) in nextRoman) m[Singlish.convert(k)] = v.map { Singlish.convert(it) }
        m.putAll(nextEnglish)
        m
    }

    fun next(prev: String): List<String> = nextMap[prev] ?: emptyList()

    // ---------------------------------------------------------------- swipe: English words

    /** More everyday English words so swipe typing has something to match (ordered roughly by use). */
    val englishExtra: List<String> = (
        "okay yeah yes no maybe sure thanks please sorry hello hey bye later soon today tonight tomorrow " +
        "morning evening afternoon weekend monday tuesday wednesday thursday friday saturday sunday " +
        "where when why who whom whose which while until since though although because between without within " +
        "mother father brother sister family friend friends people everyone someone anyone anything everything " +
        "love like hate want need miss wish hope think thought feel felt know knew mean meant remember forget " +
        "come came going went gone being done doing made make making take took taken give gave given " +
        "tell told said asked answer question reply message text call called calling phone number email " +
        "work working worked office meeting project report email boss manager team company business money pay paid " +
        "home house room door window table chair bed kitchen bathroom garden street road city town village country " +
        "school college university class teacher student exam study studying homework lesson book books paper " +
        "food eat ate eating drink water tea coffee rice bread chicken fish fruit lunch dinner breakfast hungry thirsty " +
        "happy sad angry tired sleepy busy free ready late early fast slow easy hard difficult simple important " +
        "beautiful pretty handsome cute nice cool awesome amazing wonderful perfect fine great best better worse worst " +
        "good bad big small little large huge tiny long short tall high low old young new next last first second third " +
        "time minute hour day week month year night morning moment always never often sometimes usually really actually " +
        "maybe probably definitely certainly exactly almost nearly quite rather enough too very much many more most " +
        "something nothing anything everything somewhere nowhere anywhere everywhere somehow anyway however therefore " +
        "again still already yet just only even also either neither both each every other another such same different " +
        "right left up down over under above below before after around through across along behind beside near far " +
        "bring brought buy bought sell sold send sent receive received open opened close closed start started stop stopped " +
        "wait waiting stay stayed leave left return returned arrive arrived reach reached travel traveled drive driven ride " +
        "watch watched look looked looking listen listened hear heard read reading write wrote written draw drew " +
        "play played playing game games music song songs movie movies video photo picture pictures camera " +
        "help helped helping try tried trying use used using find found search searching choose chose pick picked " +
        "happen happened change changed changing move moved moving carry carried hold held keep kept lose lost win won " +
        "birthday party gift present wedding festival holiday vacation trip journey weather rain sunny cold hot warm " +
        "doctor hospital medicine health sick pain better healthy exercise walk walked walking run ran running " +
        "phone mobile laptop computer internet online offline wifi network battery charge charger screen keyboard " +
        "facebook instagram whatsapp youtube google twitter telegram snapchat tiktok " +
        "tomorrow yesterday tonight forever together alone together without within inside outside"
        ).split(" ").filter { it.isNotBlank() }.distinct()
}
