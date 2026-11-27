package com.ultimateiptv.sports.core.parser

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamParsersTest {
    private fun obj(s: String) = XtreamParsers.json.parseToJsonElement(s).jsonObject

    @Test
    fun account() {
        val a = XtreamParsers.parseAccount(obj("""
            {"user_info":{"username":"u","password":"p","auth":1,"status":"Active","exp_date":"1798761600","is_trial":"0",
             "active_cons":"0","max_connections":"2","allowed_output_formats":["m3u8","ts"]},
             "server_info":{"url":"srv.example.com","port":"8080","https_port":"8443","server_protocol":"http","timezone":"Europe/London"}}
        """))
        assertTrue(a.authenticated)
        assertEquals(1798761600000L, a.expiresAtMillis)
        assertEquals(2, a.maxConnections)
        assertEquals(listOf("m3u8", "ts"), a.allowedOutputFormats)
        assertEquals("http://srv.example.com:8080", a.serverUrl)
    }

    @Test
    fun liveStreamLooseTypes() {
        val s = XtreamParsers.parseLiveStream(obj("""{"num":1,"name":" Sport HD ","stream_type":"live","stream_id":"1234",
            "stream_icon":"","epg_channel_id":null,"category_id":"7","tv_archive":0}"""))!!
        assertEquals(1234L, s.streamId)
        assertEquals("Sport HD", s.name)
        assertEquals(null, s.icon)
        assertEquals(null, s.epgChannelId)
        assertEquals("7", s.categoryId)
    }

    @Test
    fun shortEpgBase64() {
        val list = XtreamParsers.parseShortEpg(obj("""{"epg_listings":[{"title":"TWF0Y2g=","description":"","start_timestamp":"100","stop_timestamp":"200"}]}"""))
        assertEquals("Match", list[0].title)
        assertEquals(100_000L, list[0].startMillis)
    }

    @Test
    fun urls() {
        assertEquals("http://h.example:80", XtreamParsers.normalizeServer("h.example:80/player_api.php?username=a"))
        assertEquals("https://h.example", XtreamParsers.normalizeServer("https://h.example/"))
        assertEquals("http://h:1/live/a%20b/p%40/9.m3u8", XtreamParsers.buildLiveUrl("http://h:1/", "a b", "p@", 9, "m3u8"))
    }

    @Suppress("unused") private val unused: JsonObject? = null
}
