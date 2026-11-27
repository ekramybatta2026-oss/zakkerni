package com.ultimateiptv.sports.core.parser

import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream

data class XmltvChannel(val id: String, val displayNames: List<String>, val icon: String?)

data class XmltvProgramme(
    val channelId: String,
    val startMillis: Long,
    val stopMillis: Long,
    val title: String,
    val subTitle: String?,
    val description: String?,
    val category: String?,
    val icon: String?,
)

/**
 * Streaming XMLTV (EPG) parser on top of XmlPullParser.
 * Android provides the implementation through android.util.Xml; unit tests use kxml2.
 */
class XmltvParser(private val parserFactory: () -> XmlPullParser) {

    /**
     * @param keepChannel return false to skip programmes of channels the user does not have.
     * @param windowStart / windowEnd only programmes overlapping this window are emitted.
     */
    fun parse(
        input: InputStream,
        windowStart: Long = Long.MIN_VALUE,
        windowEnd: Long = Long.MAX_VALUE,
        keepChannel: (String) -> Boolean = { true },
        onChannel: (XmltvChannel) -> Unit = {},
        onProgramme: (XmltvProgramme) -> Unit,
    ) {
        val stream = maybeGunzip(input)
        val parser = parserFactory()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(InputStreamReader(stream, Charsets.UTF_8))

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> readChannel(parser)?.let(onChannel)
                    "programme" -> {
                        val channel = parser.getAttributeValue(null, "channel").orEmpty()
                        val start = parser.getAttributeValue(null, "start")?.let(::parseTime)
                        val stop = parser.getAttributeValue(null, "stop")?.let(::parseTime)
                        if (channel.isEmpty() || start == null || !keepChannel(channel)) {
                            skip(parser)
                        } else {
                            val end = stop ?: (start + DEFAULT_DURATION)
                            if (end < windowStart || start > windowEnd) skip(parser)
                            else readProgramme(parser, channel, start, end)?.let(onProgramme)
                        }
                    }
                }
            }
            event = parser.next()
        }
    }

    private fun readChannel(p: XmlPullParser): XmltvChannel? {
        val id = p.getAttributeValue(null, "id") ?: run { skip(p); return null }
        val names = ArrayList<String>(2)
        var icon: String? = null
        val depth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_TAG && p.depth == depth) break
            if (e == XmlPullParser.END_DOCUMENT) break
            if (e == XmlPullParser.START_TAG) when (p.name) {
                "display-name" -> readText(p).takeIf { it.isNotBlank() }?.let { names += it.trim() }
                "icon" -> { icon = p.getAttributeValue(null, "src"); skip(p) }
                else -> skip(p)
            }
        }
        return XmltvChannel(id, names, icon)
    }

    private fun readProgramme(p: XmlPullParser, channel: String, start: Long, stop: Long): XmltvProgramme? {
        var title: String? = null
        var sub: String? = null
        var desc: String? = null
        var category: String? = null
        var icon: String? = null
        val depth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_TAG && p.depth == depth) break
            if (e == XmlPullParser.END_DOCUMENT) break
            if (e == XmlPullParser.START_TAG) when (p.name) {
                "title" -> { val t = readText(p).trim(); if (title == null && t.isNotEmpty()) title = t }
                "sub-title" -> { val t = readText(p).trim(); if (sub == null && t.isNotEmpty()) sub = t }
                "desc" -> { val t = readText(p).trim(); if (desc == null && t.isNotEmpty()) desc = t }
                "category" -> { val t = readText(p).trim(); if (category == null && t.isNotEmpty()) category = t }
                "icon" -> { icon = p.getAttributeValue(null, "src"); skip(p) }
                else -> skip(p)
            }
        }
        val finalTitle = title ?: return null
        return XmltvProgramme(channel, start, stop, finalTitle, sub, desc, category, icon)
    }

    private fun readText(p: XmlPullParser): String {
        val sb = StringBuilder()
        val depth = p.depth
        while (true) {
            val e = p.next()
            when (e) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> sb.append(p.text)
                XmlPullParser.START_TAG -> skip(p)
                XmlPullParser.END_TAG -> if (p.depth == depth) return sb.toString()
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
    }

    private fun skip(p: XmlPullParser) {
        if (p.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth != 0) {
            when (p.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    companion object {
        private const val DEFAULT_DURATION = 60 * 60 * 1000L
        private val BASE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

        /** Parses XMLTV time such as "20261127183000 +0300" (offset optional, defaults to UTC). */
        fun parseTime(value: String): Long? {
            val v = value.trim()
            if (v.length < 12) return null
            return try {
                val digits = v.takeWhile { it.isDigit() }
                val padded = when {
                    digits.length >= 14 -> digits.substring(0, 14)
                    digits.length == 12 -> digits + "00"
                    else -> return null
                }
                val local = LocalDateTime.parse(padded, BASE)
                val rest = v.substring(digits.length).trim()
                val offset = parseOffset(rest)
                local.toInstant(offset).toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }

        private fun parseOffset(s: String): ZoneOffset {
            if (s.isEmpty()) return ZoneOffset.UTC
            val m = Regex("""^([+-])(\d{2}):?(\d{2})""").find(s) ?: return ZoneOffset.UTC
            val sign = if (m.groupValues[1] == "-") -1 else 1
            return ZoneOffset.ofHoursMinutes(sign * m.groupValues[2].toInt(), sign * m.groupValues[3].toInt())
        }

        /** Transparently decompresses .xml.gz content by checking the gzip magic bytes. */
        fun maybeGunzip(input: InputStream): InputStream {
            val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input, 64 * 1024)
            buffered.mark(2)
            val b1 = buffered.read()
            val b2 = buffered.read()
            buffered.reset()
            return if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(buffered, 64 * 1024), 64 * 1024) else buffered
        }
    }
}
