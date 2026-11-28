package com.ultimateiptv.sports.data.sports

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/** API-Football v3 (https://www.api-football.com/documentation-v3). Key header: x-apisports-key. */
interface ApiFootballService {
    @GET("fixtures")
    suspend fun fixturesByDate(
        @Header("x-apisports-key") key: String,
        @Query("date") date: String,
        @Query("timezone") timezone: String,
    ): AfResponse

    @GET("fixtures")
    suspend fun fixturesByTeam(
        @Header("x-apisports-key") key: String,
        @Query("team") teamId: Int,
        @Query("next") next: Int,
        @Query("timezone") timezone: String,
    ): AfResponse

    @GET("status")
    suspend fun status(@Header("x-apisports-key") key: String): AfStatusResponse

    companion object { const val BASE_URL = "https://v3.football.api-sports.io/" }
}

@Serializable data class AfResponse(val errors: JsonElement? = null, val results: Int = 0, val response: List<AfFixture> = emptyList())
@Serializable data class AfStatusResponse(val errors: JsonElement? = null, val response: JsonElement? = null)
@Serializable data class AfFixture(val fixture: AfFixtureInfo, val league: AfLeague, val teams: AfTeams, val goals: AfGoals = AfGoals())
@Serializable data class AfFixtureInfo(val id: Long, val timestamp: Long, val status: AfStatus = AfStatus(), val venue: AfVenue? = null)
@Serializable data class AfStatus(val short: String? = null, val elapsed: Int? = null)
@Serializable data class AfVenue(val name: String? = null, val city: String? = null)
@Serializable data class AfLeague(val id: Int, val name: String, val country: String? = null, val logo: String? = null, val flag: String? = null, val round: String? = null)
@Serializable data class AfTeams(val home: AfTeam, val away: AfTeam)
@Serializable data class AfTeam(val id: Int? = null, val name: String, val logo: String? = null)
@Serializable data class AfGoals(val home: Int? = null, val away: Int? = null)

/** football-data.org v4 (https://www.football-data.org/documentation/quickstart). Key header: X-Auth-Token. */
interface FootballDataService {
    @GET("v4/matches")
    suspend fun matches(
        @Header("X-Auth-Token") key: String,
        @Query("dateFrom") dateFrom: String,
        @Query("dateTo") dateTo: String,
    ): FdMatchesResponse

    companion object { const val BASE_URL = "https://api.football-data.org/" }
}

@Serializable data class FdMatchesResponse(val matches: List<FdMatch> = emptyList())
@Serializable
data class FdMatch(
    val id: Long,
    val utcDate: String,
    val status: String,
    val minute: JsonElement? = null,
    val matchday: Int? = null,
    val stage: String? = null,
    val venue: String? = null,
    val competition: FdCompetition,
    val area: FdArea? = null,
    val homeTeam: FdTeam,
    val awayTeam: FdTeam,
    val score: FdScore = FdScore(),
)
@Serializable data class FdCompetition(val id: Int, val name: String, val code: String? = null, val emblem: String? = null)
@Serializable data class FdArea(val name: String? = null, val flag: String? = null)
@Serializable data class FdTeam(val id: Int? = null, val name: String? = null, val shortName: String? = null, val crest: String? = null)
@Serializable data class FdScore(val fullTime: FdGoals = FdGoals(), @SerialName("halfTime") val halfTime: FdGoals = FdGoals())
@Serializable data class FdGoals(val home: Int? = null, val away: Int? = null)
