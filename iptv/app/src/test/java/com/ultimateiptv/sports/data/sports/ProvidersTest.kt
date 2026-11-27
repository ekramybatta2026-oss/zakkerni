package com.ultimateiptv.sports.data.sports

import com.ultimateiptv.sports.core.network.AuthException
import com.ultimateiptv.sports.data.db.MatchStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.LocalDate
import java.time.ZoneId

class ProvidersTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    private fun retrofit() = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()

    @Test
    fun apiFootballMapping() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"get":"fixtures","parameters":{"date":"2026-11-27"},"errors":[],"results":2,"paging":{"current":1,"total":1},
             "response":[
              {"fixture":{"id":1001,"referee":null,"timezone":"UTC","date":"2026-11-27T20:00:00+00:00","timestamp":1795896000,
                 "venue":{"id":1,"name":"Emirates Stadium","city":"London"},"status":{"long":"Second Half","short":"2H","elapsed":67}},
               "league":{"id":39,"name":"Premier League","country":"England","logo":"https://x/39.png","flag":"https://x/gb.svg","season":2026,"round":"Regular Season - 13"},
               "teams":{"home":{"id":42,"name":"Arsenal","logo":"https://x/42.png","winner":true},"away":{"id":49,"name":"Chelsea","logo":"https://x/49.png","winner":false}},
               "goals":{"home":2,"away":1},"score":{}},
              {"fixture":{"id":1002,"timestamp":1795910400,"venue":{"name":null,"city":null},"status":{"short":"NS","elapsed":null}},
               "league":{"id":12,"name":"CAF Champions League","country":"World","logo":null,"flag":null,"round":"Group Stage - 3"},
               "teams":{"home":{"id":1,"name":"Al Ahly","logo":null},"away":{"id":2,"name":"Esperance","logo":null}},
               "goals":{"home":null,"away":null}}
             ]}
        """.trimIndent()))
        val p = ApiFootballProvider(retrofit().create(ApiFootballService::class.java))
        val list = p.fixtures(LocalDate.of(2026, 11, 27), ZoneId.of("Asia/Riyadh"), "KEY")
        val req = server.takeRequest()
        assertEquals("KEY", req.getHeader("x-apisports-key"))
        assertTrue(req.path!!.contains("timezone=Asia%2FRiyadh"))
        assertEquals(2, list.size)
        assertEquals("pl", list[0].competitionKey)
        assertEquals(MatchStatus.LIVE, list[0].status)
        assertEquals(67, list[0].minute)
        assertEquals(2, list[0].homeScore)
        assertEquals(1795896000000L, list[0].kickoff)
        assertEquals("cafcl", list[1].competitionKey)
        assertEquals(MatchStatus.SCHEDULED, list[1].status)
    }

    @Test(expected = AuthException::class)
    fun apiFootballKeyError() {
        server.enqueue(MockResponse().setBody("""{"errors":{"token":"Error/Missing application key."},"results":0,"response":[]}"""))
        runBlocking { ApiFootballProvider(retrofit().create(ApiFootballService::class.java)).fixtures(LocalDate.now(), ZoneId.of("UTC"), "BAD") }
    }

    @Test(expected = ApiQuotaException::class)
    fun apiFootballQuota() {
        server.enqueue(MockResponse().setBody("""{"errors":{"requests":"You have reached the request limit for the day"},"results":0,"response":[]}"""))
        runBlocking { ApiFootballProvider(retrofit().create(ApiFootballService::class.java)).fixtures(LocalDate.now(), ZoneId.of("UTC"), "K") }
    }

    @Test
    fun footballDataMappingKeepsLocalDay() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"filters":{},"resultSet":{"count":3},"matches":[
              {"id":1,"utcDate":"2026-11-27T17:00:00Z","status":"IN_PLAY","minute":"34","matchday":13,"stage":"REGULAR_SEASON",
               "competition":{"id":2021,"name":"Premier League","code":"PL","emblem":"https://crests/PL.png"},
               "area":{"name":"England","flag":"https://crests/770.svg"},
               "homeTeam":{"id":57,"name":"Arsenal FC","shortName":"Arsenal","crest":"https://crests/57.png"},
               "awayTeam":{"id":61,"name":"Chelsea FC","shortName":"Chelsea","crest":"https://crests/61.png"},
               "score":{"winner":null,"duration":"REGULAR","fullTime":{"home":1,"away":0},"halfTime":{"home":null,"away":null}}},
              {"id":2,"utcDate":"2026-11-27T22:30:00Z","status":"TIMED","competition":{"id":2001,"name":"UEFA Champions League","code":"CL"},
               "homeTeam":{"name":"Inter"},"awayTeam":{"name":"Bayern"},"score":{"fullTime":{"home":null,"away":null}}},
              {"id":3,"utcDate":"2026-11-26T10:00:00Z","status":"FINISHED","competition":{"id":2019,"name":"Serie A","code":"SA"},
               "homeTeam":{"name":"Roma"},"awayTeam":{"name":"Lazio"},"score":{"fullTime":{"home":2,"away":2}}}
            ]}
        """.trimIndent()))
        val p = FootballDataProvider(retrofit().create(FootballDataService::class.java))
        // Riyadh is UTC+3: 22:30Z on the 27th is already the 28th locally.
        val list = p.fixtures(LocalDate.of(2026, 11, 27), ZoneId.of("Asia/Riyadh"), "TOKEN")
        assertEquals("TOKEN", server.takeRequest().getHeader("X-Auth-Token"))
        assertEquals(listOf("fd:1"), list.map { it.id })
        assertEquals(MatchStatus.LIVE, list[0].status)
        assertEquals(34, list[0].minute)
        assertEquals("Arsenal", list[0].homeName)
        assertEquals("pl", list[0].competitionKey)
    }

    @Test
    fun demoIsDeterministic() = runBlocking {
        val d = DemoSportsProvider()
        val a = d.fixtures(LocalDate.of(2026, 11, 27), ZoneId.of("UTC"), null).map { it.id }
        val b = d.fixtures(LocalDate.of(2026, 11, 27), ZoneId.of("UTC"), null).map { it.id }
        assertEquals(a, b)
        assertTrue(a.isNotEmpty())
    }
}
