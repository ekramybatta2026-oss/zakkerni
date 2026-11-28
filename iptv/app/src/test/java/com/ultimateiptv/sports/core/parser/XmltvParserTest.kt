package com.ultimateiptv.sports.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.GZIPOutputStream

class XmltvParserTest {
    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE tv SYSTEM "xmltv.dtd">
        <tv generator-info-name="test">
          <channel id="sport1.ae"><display-name lang="en">Sport 1</display-name><icon src="http://x/i.png"/></channel>
          <programme start="20261127180000 +0300" stop="20261127200000 +0300" channel="sport1.ae">
            <title lang="ar">الدوري: الأهلي ضد الزمالك</title>
            <desc>Live &amp; exclusive</desc>
            <category>Sports</category>
          </programme>
          <programme start="20261127200000 +0300" stop="20261127210000 +0300" channel="other">
            <title>Skip me</title>
          </programme>
          <programme start="20261128000000" channel="sport1.ae"><title><![CDATA[Night Show]]></title></programme>
        </tv>
    """.trimIndent()

    @Test
    fun parsesChannelsAndProgrammes() {
        val channels = mutableListOf<XmltvChannel>()
        val progs = mutableListOf<XmltvProgramme>()
        XmltvParser { KXmlParser() }.parse(
            xml.byteInputStream(), keepChannel = { it == "sport1.ae" },
            onChannel = { channels += it }, onProgramme = { progs += it },
        )
        assertEquals(1, channels.size)
        assertEquals("Sport 1", channels[0].displayNames.first())
        assertEquals(2, progs.size)
        assertEquals("الدوري: الأهلي ضد الزمالك", progs[0].title)
        assertEquals("Live & exclusive", progs[0].description)
        assertEquals(Instant.parse("2026-11-27T15:00:00Z").toEpochMilli(), progs[0].startMillis)
        assertEquals("Night Show", progs[1].title)
        assertEquals(Instant.parse("2026-11-28T01:00:00Z").toEpochMilli(), progs[1].stopMillis)
    }

    @Test
    fun windowFilters() {
        val progs = mutableListOf<XmltvProgramme>()
        val start = Instant.parse("2026-11-27T23:00:00Z").toEpochMilli()
        XmltvParser { KXmlParser() }.parse(xml.byteInputStream(), windowStart = start, onProgramme = { progs += it })
        assertEquals(listOf("Night Show"), progs.map { it.title })
    }

    @Test
    fun gzipIsDetected() {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(xml.toByteArray()) }
        var n = 0
        XmltvParser { KXmlParser() }.parse(bos.toByteArray().inputStream(), onProgramme = { n++ })
        assertEquals(3, n)
    }

    @Test
    fun timeFormats() {
        assertNotNull(XmltvParser.parseTime("202611271800 +0000"))
        assertEquals(Instant.parse("2026-11-27T20:30:00Z").toEpochMilli(), XmltvParser.parseTime("20261127180000 -0230"))
    }
}
