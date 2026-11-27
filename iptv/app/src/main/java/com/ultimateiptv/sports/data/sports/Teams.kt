package com.ultimateiptv.sports.data.sports

import com.ultimateiptv.sports.core.util.TextUtils

/**
 * Built-in info for major clubs: crest (API-Football public media CDN) and Arabic name.
 * Used for the demo fixtures and as a fallback when a provider returns no logo,
 * and to show Arabic team names when the app language is Arabic.
 */
/** [apiFootballId] is null when the crest id has not been verified: initials are shown instead of a wrong crest. */
data class TeamInfo(val apiFootballId: Int?, val nameEn: String, val nameAr: String, val aliases: List<String>) {
    val logoUrl: String? get() = apiFootballId?.let { "https://media.api-sports.io/football/teams/$it.png" }
}

object Teams {
    private fun t(id: Int?, en: String, ar: String, vararg aliases: String) = TeamInfo(id, en, ar, aliases.toList())

    // Crest ids were checked visually against the downloaded images (CI job "crests").
    val all: List<TeamInfo> = listOf(
        // England
        t(42, "Arsenal", "آرسنال", "Arsenal FC"),
        t(49, "Chelsea", "تشيلسي", "Chelsea FC"),
        t(40, "Liverpool", "ليفربول", "Liverpool FC"),
        t(50, "Manchester City", "مانشستر سيتي", "Man City", "Manchester City FC"),
        t(33, "Manchester United", "مانشستر يونايتد", "Man United", "Manchester United FC", "Man Utd"),
        t(47, "Tottenham", "توتنهام", "Tottenham Hotspur", "Tottenham Hotspur FC", "Spurs"),
        t(34, "Newcastle", "نيوكاسل", "Newcastle United", "Newcastle United FC"),
        t(66, "Aston Villa", "أستون فيلا", "Aston Villa FC"),
        // Spain
        t(541, "Real Madrid", "ريال مدريد", "Real Madrid CF"),
        t(529, "Barcelona", "برشلونة", "FC Barcelona", "Barça"),
        t(530, "Atletico Madrid", "أتلتيكو مدريد", "Atlético Madrid", "Club Atlético de Madrid", "Atleti"),
        t(536, "Sevilla", "إشبيلية", "Sevilla FC"),
        t(548, "Real Sociedad", "ريال سوسيداد", "Real Sociedad de Fútbol"),
        t(533, "Villarreal", "فياريال", "Villarreal CF"),
        // Italy
        t(505, "Inter", "إنتر ميلان", "Inter Milan", "FC Internazionale Milano", "Internazionale"),
        t(489, "AC Milan", "ميلان", "Milan"),
        t(496, "Juventus", "يوفنتوس", "Juventus FC"),
        t(492, "Napoli", "نابولي", "SSC Napoli"),
        t(497, "Roma", "روما", "AS Roma"),
        t(487, "Lazio", "لاتسيو", "SS Lazio"),
        // Germany
        t(157, "Bayern Munich", "بايرن ميونخ", "Bayern München", "FC Bayern München", "Bayern"),
        t(165, "Borussia Dortmund", "بوروسيا دورتموند", "Dortmund", "BV Borussia 09 Dortmund"),
        t(173, "RB Leipzig", "لايبزيغ", "RasenBallsport Leipzig"),
        t(168, "Bayer Leverkusen", "باير ليفركوزن", "Leverkusen", "Bayer 04 Leverkusen"),
        // France
        t(85, "Paris Saint-Germain", "باريس سان جيرمان", "Paris Saint Germain", "PSG", "Paris Saint-Germain FC"),
        t(81, "Marseille", "مارسيليا", "Olympique de Marseille", "Olympique Marseille"),
        t(80, "Lyon", "ليون", "Olympique Lyonnais"),
        t(91, "Monaco", "موناكو", "AS Monaco", "AS Monaco FC"),
        // Africa
        t(1029, "Al Ahly", "الأهلي", "Al Ahly SC", "Al-Ahly"),
        t(1040, "Zamalek", "الزمالك", "Zamalek SC"),
        t(null, "Pyramids", "بيراميدز", "Pyramids FC"),
        t(1039, "Al Masry", "المصري", "Al-Masry"),
        t(null, "Esperance", "الترجي", "Esperance Tunis", "Espérance de Tunis"),
        t(968, "Wydad", "الوداد", "Wydad AC", "Wydad Casablanca"),
        t(null, "Mamelodi Sundowns", "صن داونز", "Sundowns"),
        // Saudi Arabia
        t(2932, "Al Hilal", "الهلال", "Al-Hilal Saudi FC", "Al-Hilal"),
        t(2939, "Al Nassr", "النصر", "Al-Nassr"),
        t(2938, "Al Ittihad", "الاتحاد", "Al-Ittihad FC", "Al-Ittihad"),
        t(2929, "Al Ahli", "الأهلي السعودي", "Al-Ahli Jeddah", "Al-Ahli Saudi FC"),
        t(null, "Al Shabab", "الشباب", "Al-Shabab"),
        t(2934, "Al Ettifaq", "الاتفاق", "Al-Ettifaq"),
    )

    private val byKey: Map<String, TeamInfo> = buildMap {
        all.forEach { team -> (listOf(team.nameEn) + team.aliases).forEach { put(TextUtils.normalize(it), team) } }
    }

    fun find(name: String): TeamInfo? = byKey[TextUtils.normalize(name)]

    fun logo(name: String): String? = find(name)?.logoUrl

    /** Arabic name when known, otherwise the provider's name. */
    fun displayName(name: String, arabic: Boolean): String = if (arabic) find(name)?.nameAr ?: name else name
}
