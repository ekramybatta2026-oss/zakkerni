package com.ultimateiptv.sports.core.util

import java.text.Normalizer

object TextUtils {
    private val QUALITY_TOKENS = Regex("""\b(fhd|uhd|hd|sd|4k|8k|hevc|h265|h264|1080p?|720p?|480p?|50fps|60fps|backup|vip|raw)\b""", RegexOption.IGNORE_CASE)
    private val PREFIX = Regex("""^\s*([a-z]{2,3}|[\p{InArabic}]{2,6})\s*[:|]\s*""", RegexOption.IGNORE_CASE)
    private val BRACKETS = Regex("""[\[(][^\])]*[\])]""")
    private val NON_ALNUM = Regex("""[^\p{L}\p{Nd}]+""")
    private val DIACRITICS = Regex("""\p{Mn}+""")

    /** Lower-cased, accent-free, quality/country-prefix stripped key used for search and matching. */
    fun normalize(input: String): String {
        var s = input
        s = PREFIX.replace(s, "")
        s = BRACKETS.replace(s, " ")
        s = QUALITY_TOKENS.replace(s, " ")
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
        s = DIACRITICS.replace(s, "")
        s = s.lowercase()
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه').replace('ى', 'ي')
        s = NON_ALNUM.replace(s, " ").trim()
        return s
    }

    /** Distinct significant words of a team name, used for EPG broadcast detection. */
    fun teamKeywords(team: String): List<String> {
        val stop = setOf("fc", "cf", "sc", "ac", "afc", "club", "de", "the", "united", "city", "real", "sporting", "and", "nadi", "نادي")
        return normalize(team).split(' ').filter { it.length >= 3 && it !in stop }.distinct()
    }

    fun containsAny(haystackNormalized: String, keywords: List<String>): Boolean =
        keywords.any { k -> Regex("""(^|\s)${Regex.escape(k)}(\s|$)""").containsMatchIn(haystackNormalized) }
}

/** Heuristic sports channel detection from the group title and channel name. */
object SportsClassifier {
    private val KEYWORDS = listOf(
        "sport", "sports", "spor", "deporte", "deportes", "sportiva", "bein", "ssc", "alkass", "al kass", "ad sport",
        "abu dhabi sport", "dubai sport", "sharjah sport", "ksa sport", "on time sport", "ontime", "on sport",
        "espn", "sky sport", "eurosport", "dazn", "tnt sport", "bt sport", "arena sport", "supersport", "setanta",
        "premier league", "laliga", "la liga", "serie a", "bundesliga", "ligue 1", "champions", "football", "soccer",
        "fox sport", "nba", "nfl", "nhl", "mlb", "f1", "formula", "motogp", "ufc", "wwe", "tennis", "golf", "cricket",
        "match", "rugby", "boxing", "رياض", "كوره", "كرة", "الكاس", "دوري", "بي ان", "ملاعب",
    ).map { TextUtils.normalize(it) }

    fun isSports(name: String, group: String?): Boolean {
        val text = TextUtils.normalize("${group.orEmpty()} $name")
        if (text.isEmpty()) return false
        return KEYWORDS.any { k -> text.contains(k) }
    }
}
