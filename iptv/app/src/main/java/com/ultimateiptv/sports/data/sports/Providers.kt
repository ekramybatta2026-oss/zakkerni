package com.ultimateiptv.sports.data.sports

import com.ultimateiptv.sports.core.network.AuthException
import com.ultimateiptv.sports.core.network.HttpStatusException
import com.ultimateiptv.sports.data.db.MatchEntity
import com.ultimateiptv.sports.data.db.MatchStatus
import com.ultimateiptv.sports.data.settings.SportsProviderId
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class ApiQuotaException(message: String) : IOException(message)

interface SportsProvider {
    val id: SportsProviderId
    /** Matches whose kickoff falls on [date] in [zone]. */
    suspend fun fixtures(date: LocalDate, zone: ZoneId, apiKey: String?): List<MatchEntity>
}

private suspend fun <T> mapHttp(block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    when (e.code()) {
        401, 403 -> throw AuthException("Invalid API key")
        429 -> throw ApiQuotaException("Request limit reached")
        else -> throw HttpStatusException(e.code())
    }
}

class ApiFootballProvider(private val service: ApiFootballService) : SportsProvider {
    override val id = SportsProviderId.API_FOOTBALL

    override suspend fun fixtures(date: LocalDate, zone: ZoneId, apiKey: String?): List<MatchEntity> {
        val key = apiKey ?: throw AuthException("API key missing")
        val response = mapHttp { service.fixturesByDate(key, date.toString(), zone.id) }
        checkErrors(response.errors)
        val now = System.currentTimeMillis()
        return response.response.map { it.toEntity(now) }
    }

    suspend fun verify(apiKey: String) {
        val r = mapHttp { service.status(apiKey) }
        checkErrors(r.errors)
    }

    /** API-Football returns HTTP 200 with an "errors" object for key / quota problems. */
    private fun checkErrors(errors: JsonElement?) {
        val obj = errors as? JsonObject ?: return
        if (obj.isEmpty()) return
        val text = obj.values.joinToString { (it as? JsonPrimitive)?.content ?: it.toString() }
        when {
            obj.containsKey("token") || text.contains("key", true) -> throw AuthException(text)
            obj.containsKey("requests") || obj.containsKey("rateLimit") -> throw ApiQuotaException(text)
            else -> throw IOException(text)
        }
    }

    private fun AfFixture.toEntity(now: Long): MatchEntity {
        val comp = Competitions.forApiFootball(league.id)
        return MatchEntity(
            id = "af:${fixture.id}",
            provider = id.name,
            competitionKey = comp?.key ?: "af-${league.id}",
            competitionName = league.name,
            competitionLogo = league.logo,
            country = league.country,
            countryFlag = league.flag,
            round = league.round,
            homeName = teams.home.name,
            homeLogo = teams.home.logo,
            awayName = teams.away.name,
            awayLogo = teams.away.logo,
            homeScore = goals.home,
            awayScore = goals.away,
            status = mapStatus(fixture.status.short),
            minute = fixture.status.elapsed,
            kickoff = fixture.timestamp * 1000,
            venue = listOfNotNull(fixture.venue?.name, fixture.venue?.city).joinToString(", ").ifBlank { null },
            fetchedAt = now,
        )
    }

    companion object {
        fun mapStatus(s: String?): MatchStatus = when (s) {
            "TBD", "NS" -> MatchStatus.SCHEDULED
            "1H", "2H", "ET", "P", "LIVE", "BT", "SUSP", "INT" -> MatchStatus.LIVE
            "HT" -> MatchStatus.HALFTIME
            "FT", "AET", "PEN" -> MatchStatus.FINISHED
            "PST" -> MatchStatus.POSTPONED
            "CANC", "ABD", "AWD", "WO" -> MatchStatus.CANCELLED
            else -> MatchStatus.UNKNOWN
        }
    }
}

class FootballDataProvider(private val service: FootballDataService) : SportsProvider {
    override val id = SportsProviderId.FOOTBALL_DATA

    override suspend fun fixtures(date: LocalDate, zone: ZoneId, apiKey: String?): List<MatchEntity> {
        val key = apiKey ?: throw AuthException("API key missing")
        // The API filters by UTC date; ask for a 3-day window and keep the local day.
        val response = mapHttp { service.matches(key, date.minusDays(1).toString(), date.plusDays(1).toString()) }
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        return response.matches.map { it.toEntity(now) }.filter { it.kickoff in start until end }
    }

    private fun FdMatch.toEntity(now: Long): MatchEntity {
        val comp = Competitions.forFootballData(competition.code)
        return MatchEntity(
            id = "fd:$id",
            provider = this@FootballDataProvider.id.name,
            competitionKey = comp?.key ?: "fd-${competition.code ?: competition.id}",
            competitionName = competition.name,
            competitionLogo = competition.emblem,
            country = area?.name,
            countryFlag = area?.flag,
            round = matchday?.let { "Matchday $it" } ?: stage,
            homeName = homeTeam.shortName ?: homeTeam.name ?: "TBD",
            homeLogo = homeTeam.crest,
            awayName = awayTeam.shortName ?: awayTeam.name ?: "TBD",
            awayLogo = awayTeam.crest,
            homeScore = score.fullTime.home,
            awayScore = score.fullTime.away,
            status = mapStatus(status),
            minute = (minute as? JsonPrimitive)?.intOrNull ?: (minute as? JsonPrimitive)?.content?.takeWhile { it.isDigit() }?.toIntOrNull(),
            kickoff = Instant.parse(utcDate).toEpochMilli(),
            venue = venue,
            fetchedAt = now,
        )
    }

    companion object {
        fun mapStatus(s: String): MatchStatus = when (s) {
            "SCHEDULED", "TIMED" -> MatchStatus.SCHEDULED
            "IN_PLAY", "LIVE", "SUSPENDED" -> MatchStatus.LIVE
            "PAUSED" -> MatchStatus.HALFTIME
            "FINISHED", "AWARDED" -> MatchStatus.FINISHED
            "POSTPONED" -> MatchStatus.POSTPONED
            "CANCELLED" -> MatchStatus.CANCELLED
            else -> MatchStatus.UNKNOWN
        }
    }
}

/**
 * Offline sample fixtures so that every screen can be tried without an API key.
 * The UI always marks these as DEMO; they are not real results.
 */
class DemoSportsProvider : SportsProvider {
    override val id = SportsProviderId.DEMO

    private val teams = mapOf(
        "pl" to listOf("Arsenal", "Chelsea", "Liverpool", "Manchester City", "Manchester United", "Tottenham", "Newcastle", "Aston Villa"),
        "laliga" to listOf("Real Madrid", "Barcelona", "Atletico Madrid", "Sevilla", "Real Sociedad", "Villarreal"),
        "seriea" to listOf("Inter", "AC Milan", "Juventus", "Napoli", "Roma", "Lazio"),
        "bundesliga" to listOf("Bayern Munich", "Borussia Dortmund", "RB Leipzig", "Bayer Leverkusen"),
        "ligue1" to listOf("Paris Saint-Germain", "Marseille", "Lyon", "Monaco"),
        "ucl" to listOf("Real Madrid", "Manchester City", "Bayern Munich", "Inter", "Arsenal", "Paris Saint-Germain"),
        "cafcl" to listOf("Al Ahly", "Zamalek", "Esperance", "Wydad", "Mamelodi Sundowns", "Pyramids"),
        "spl" to listOf("Al Hilal", "Al Nassr", "Al Ittihad", "Al Ahli", "Al Shabab", "Al Ettifaq"),
        "egypl" to listOf("Al Ahly", "Zamalek", "Pyramids", "Al Masry", "Future"),
    )

    override suspend fun fixtures(date: LocalDate, zone: ZoneId, apiKey: String?): List<MatchEntity> {
        val rnd = Random(date.toEpochDay())
        val now = System.currentTimeMillis()
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val out = ArrayList<MatchEntity>()
        teams.forEach { (key, list) ->
            val comp = Competitions.get(key)!!
            val shuffled = list.shuffled(rnd)
            val games = minOf(shuffled.size / 2, 1 + rnd.nextInt(3))
            for (g in 0 until games) {
                val home = shuffled[g * 2]
                val away = shuffled[g * 2 + 1]
                val hour = listOf(13, 15, 17, 18, 19, 20, 21, 22)[rnd.nextInt(8)]
                val kickoff = dayStart + TimeUnit.HOURS.toMillis(hour.toLong()) + TimeUnit.MINUTES.toMillis(if (rnd.nextBoolean()) 0 else 30)
                val elapsed = TimeUnit.MILLISECONDS.toMinutes(now - kickoff).toInt()
                val status = when {
                    elapsed < 0 -> MatchStatus.SCHEDULED
                    elapsed < 45 -> MatchStatus.LIVE
                    elapsed < 60 -> MatchStatus.HALFTIME
                    elapsed < 110 -> MatchStatus.LIVE
                    else -> MatchStatus.FINISHED
                }
                val minute = when {
                    status == MatchStatus.HALFTIME -> 45
                    status == MatchStatus.LIVE -> if (elapsed < 45) elapsed + 1 else minOf(90, elapsed - 14)
                    else -> null
                }
                val progress = when (status) {
                    MatchStatus.SCHEDULED -> null
                    MatchStatus.FINISHED -> 1.0
                    else -> (minute ?: 0) / 90.0
                }
                val finalHome = rnd.nextInt(4)
                val finalAway = rnd.nextInt(3)
                out += MatchEntity(
                    id = "demo:${date}:$key:$g",
                    provider = id.name,
                    competitionKey = key,
                    competitionName = comp.nameEn,
                    competitionLogo = comp.logoUrl,
                    country = comp.regionEn,
                    countryFlag = null,
                    round = "Demo round ${1 + rnd.nextInt(30)}",
                    homeName = home, homeLogo = null,
                    awayName = away, awayLogo = null,
                    homeScore = progress?.let { (finalHome * it).toInt() },
                    awayScore = progress?.let { (finalAway * it).toInt() },
                    status = status,
                    minute = minute,
                    kickoff = kickoff,
                    venue = null,
                    fetchedAt = now,
                )
            }
        }
        return out.sortedBy { it.kickoff }
    }
}
