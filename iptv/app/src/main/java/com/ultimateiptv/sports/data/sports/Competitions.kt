package com.ultimateiptv.sports.data.sports

/**
 * Catalog of the competitions highlighted in the app. IDs/codes map them to each data provider:
 * - API-Football (api-sports.io) league ids
 * - football-data.org competition codes (free tier covers only part of them)
 * Any other competition returned by the provider is still shown under its own name.
 */
data class Competition(
    val key: String,
    val nameEn: String,
    val nameAr: String,
    val regionEn: String,
    val regionAr: String,
    val apiFootballId: Int?,
    val footballDataCode: String?,
    val featured: Boolean = true,
) {
    val logoUrl: String? get() = apiFootballId?.let { "https://media.api-sports.io/football/leagues/$it.png" }
    fun name(arabic: Boolean) = if (arabic) nameAr else nameEn
    fun region(arabic: Boolean) = if (arabic) regionAr else regionEn
}

object Competitions {
    val all: List<Competition> = listOf(
        Competition("pl", "Premier League", "الدوري الإنجليزي الممتاز", "England", "إنجلترا", 39, "PL"),
        Competition("laliga", "La Liga", "الدوري الإسباني", "Spain", "إسبانيا", 140, "PD"),
        Competition("seriea", "Serie A", "الدوري الإيطالي", "Italy", "إيطاليا", 135, "SA"),
        Competition("bundesliga", "Bundesliga", "الدوري الألماني", "Germany", "ألمانيا", 78, "BL1"),
        Competition("ligue1", "Ligue 1", "الدوري الفرنسي", "France", "فرنسا", 61, "FL1"),
        Competition("ucl", "UEFA Champions League", "دوري أبطال أوروبا", "Europe", "أوروبا", 2, "CL"),
        Competition("uel", "UEFA Europa League", "الدوري الأوروبي", "Europe", "أوروبا", 3, null),
        Competition("cafcl", "CAF Champions League", "دوري أبطال أفريقيا", "Africa", "أفريقيا", 12, null),
        Competition("cafcc", "CAF Confederation Cup", "كأس الكونفدرالية الأفريقية", "Africa", "أفريقيا", 20, null),
        Competition("wc", "FIFA World Cup", "كأس العالم", "World", "العالم", 1, "WC"),
        Competition("afcon", "Africa Cup of Nations", "كأس أمم أفريقيا", "Africa", "أفريقيا", 6, null),
        Competition("asiancup", "AFC Asian Cup", "كأس آسيا", "Asia", "آسيا", 7, null),
        Competition("cwc", "FIFA Club World Cup", "كأس العالم للأندية", "World", "العالم", 15, null),
        Competition("euro", "UEFA European Championship", "كأس الأمم الأوروبية", "Europe", "أوروبا", 4, "EC", featured = false),
        Competition("afccl", "AFC Champions League Elite", "دوري أبطال آسيا للنخبة", "Asia", "آسيا", 17, null, featured = false),
        Competition("spl", "Saudi Pro League", "دوري روشن السعودي", "Saudi Arabia", "السعودية", 307, null, featured = false),
        Competition("egypl", "Egyptian Premier League", "الدوري المصري الممتاز", "Egypt", "مصر", 233, null, featured = false),
        Competition("facup", "FA Cup", "كأس الاتحاد الإنجليزي", "England", "إنجلترا", 45, null, featured = false),
        Competition("copadelrey", "Copa del Rey", "كأس ملك إسبانيا", "Spain", "إسبانيا", 143, null, featured = false),
        Competition("copaamerica", "Copa América", "كوبا أمريكا", "South America", "أمريكا الجنوبية", 9, null, featured = false),
    )

    private val byKey = all.associateBy { it.key }
    private val byApiFootball = all.filter { it.apiFootballId != null }.associateBy { it.apiFootballId!! }
    private val byFootballData = all.filter { it.footballDataCode != null }.associateBy { it.footballDataCode!! }

    fun get(key: String): Competition? = byKey[key]
    fun forApiFootball(id: Int): Competition? = byApiFootball[id]
    fun forFootballData(code: String?): Competition? = code?.let { byFootballData[it] }
}
