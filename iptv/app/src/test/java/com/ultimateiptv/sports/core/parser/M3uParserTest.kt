package com.ultimateiptv.sports.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    private val sample = """
        #EXTM3U url-tvg="https://example.com/epg.xml.gz, https://example.com/epg2.xml" user-agent="GlobalUA/1.0"
        #EXTINF:-1 tvg-id="sport1.ae" tvg-name="Sport 1, HD" tvg-logo="https://example.com/s1.png" group-title="Sports",Sport 1 HD
        https://example.com/live/sport1.m3u8
        #EXTINF:-1 tvg-id="" group-title="News",News 24
        #EXTVLCOPT:http-user-agent=VLC/3.0
        #EXTVLCOPT:http-referrer=https://ref.example.com/
        http://example.com/news.ts
        #EXTINF:-1,Pipe Headers
        #EXTGRP:Movies
        https://example.com/movie.m3u8|User-Agent=Custom%20UA&Referer=https://r.example&X-Token=abc
        #EXTINF:-1 tvg-id="drm1",DRM Channel
        #KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
        #KODIPROP:inputstream.adaptive.license_key=https://license.example.com/wv
        https://example.com/drm/manifest.mpd
    """.trimIndent()

    @Test
    fun parsesHeaderAndEntries() {
        val (header, entries) = M3uParser.parseAll(sample)
        assertEquals(listOf("https://example.com/epg.xml.gz", "https://example.com/epg2.xml"), header.epgUrls)
        assertEquals(4, entries.size)

        val s1 = entries[0]
        assertEquals("Sport 1 HD", s1.name)
        assertEquals("sport1.ae", s1.tvgId)
        assertEquals("Sport 1, HD", s1.tvgName)
        assertEquals("Sports", s1.group)
        assertEquals("https://example.com/s1.png", s1.logo)
        assertEquals("GlobalUA/1.0", s1.userAgent)

        val news = entries[1]
        assertNull(news.tvgId)
        assertEquals("VLC/3.0", news.userAgent)
        assertEquals("https://ref.example.com/", news.referrer)

        val pipe = entries[2]
        assertEquals("https://example.com/movie.m3u8", pipe.url)
        assertEquals("Movies", pipe.group)
        assertEquals("Custom UA", pipe.userAgent)
        assertEquals("https://r.example", pipe.referrer)
        assertEquals(mapOf("X-Token" to "abc"), pipe.headers)

        val drm = entries[3]
        assertEquals("widevine", drm.drmScheme)
        assertEquals("https://license.example.com/wv", drm.drmLicenseUrl)
    }

    @Test
    fun plainUrlListIsAccepted() {
        val (_, entries) = M3uParser.parseAll("http://a.example/one.m3u8\nhttp://a.example/two.ts\n")
        assertEquals(2, entries.size)
        assertEquals("one.m3u8", entries[0].name)
    }

    @Test(expected = M3uFormatException::class)
    fun rejectsHtml() {
        M3uParser.parseAll("<html>\n<head>\n<title>Login</title>\n</head>\n<body>\nnope\n</body></html>")
    }

    @Test
    fun handlesBomAndCrlf() {
        val (_, entries) = M3uParser.parseAll("\uFEFF#EXTM3U\r\n#EXTINF:-1,A\r\nhttp://x/a\r\n")
        assertEquals(1, entries.size)
        assertEquals("A", entries[0].name)
        assertTrue(entries[0].url == "http://x/a")
    }

    @Test
    fun largePlaylistStreams() {
        val sb = StringBuilder("#EXTM3U\n")
        repeat(50_000) { sb.append("#EXTINF:-1 group-title=\"G${it % 20}\",Ch $it\nhttp://x/$it.ts\n") }
        var count = 0
        M3uParser.parse(sb.toString().reader()) { count++ }
        assertEquals(50_000, count)
    }
}
