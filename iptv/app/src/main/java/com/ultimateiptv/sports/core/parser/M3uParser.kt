package com.ultimateiptv.sports.core.parser

import java.io.BufferedReader
import java.io.Reader

/** One entry of an M3U / M3U8 playlist, as written by the provider. */
data class M3uEntry(
    val name: String,
    val url: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val logo: String? = null,
    val group: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val drmScheme: String? = null,
    val drmLicenseUrl: String? = null,
    val catchupDays: Int? = null,
    val attributes: Map<String, String> = emptyMap(),
)

/** Header (#EXTM3U line) information. */
data class M3uHeader(val epgUrls: List<String> = emptyList(), val userAgent: String? = null)

class M3uFormatException(message: String) : Exception(message)

/**
 * Streaming M3U parser. It never holds the whole playlist in memory, so playlists with
 * 100k+ entries can be imported on low-end Android TV boxes.
 *
 * Supported tags: #EXTM3U (url-tvg / x-tvg-url), #EXTINF attributes, #EXTGRP,
 * #EXTVLCOPT (http-user-agent, http-referrer), #KODIPROP (inputstream.adaptive license type/key,
 * stream headers) and the "url|Header=Value&Header2=Value" convention.
 */
object M3uParser {

    private val ATTR_REGEX = Regex("""([A-Za-z0-9_\-]+)\s*=\s*"([^"]*)"""")
    private val STREAM_SCHEMES = listOf("http://", "https://", "rtmp://", "rtmps://", "rtsp://", "udp://", "rtp://", "file://", "content://", "asset://")

    /**
     * Parses [reader] and calls [onEntry] for every channel. Returns the header info.
     * @throws M3uFormatException when the content is clearly not an M3U playlist.
     */
    fun parse(reader: Reader, onEntry: (M3uEntry) -> Unit): M3uHeader {
        val br = if (reader is BufferedReader) reader else BufferedReader(reader, 64 * 1024)
        var header = M3uHeader()
        var sawHeaderOrInf = false
        var nonEmptyLines = 0

        var pendingInf: String? = null
        var pendingGroup: String? = null
        var pendingUa: String? = null
        var pendingRef: String? = null
        var pendingDrmType: String? = null
        var pendingDrmKey: String? = null
        val pendingHeaders = LinkedHashMap<String, String>()

        fun reset() {
            pendingInf = null; pendingGroup = null; pendingUa = null; pendingRef = null
            pendingDrmType = null; pendingDrmKey = null; pendingHeaders.clear()
        }

        while (true) {
            val raw = br.readLine() ?: break
            val line = raw.trim().removePrefix("﻿")
            if (line.isEmpty()) continue
            nonEmptyLines++
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    sawHeaderOrInf = true
                    val attrs = parseAttributes(line)
                    val epg = (attrs["url-tvg"] ?: attrs["x-tvg-url"] ?: attrs["tvg-url"])
                        ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                    header = M3uHeader(epgUrls = epg, userAgent = attrs["user-agent"] ?: attrs["http-user-agent"])
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    sawHeaderOrInf = true
                    reset()
                    pendingInf = line
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> pendingGroup = line.substringAfter(':').trim()
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim()
                    when (key) {
                        "http-user-agent" -> pendingUa = value
                        "http-referrer", "http-referer" -> pendingRef = value
                    }
                }
                line.startsWith("#KODIPROP:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim()
                    when (key) {
                        "inputstream.adaptive.license_type" -> pendingDrmType = value
                        "inputstream.adaptive.license_key" -> pendingDrmKey = value
                        "inputstream.adaptive.stream_headers", "inputstream.adaptive.common_headers" ->
                            pendingHeaders.putAll(parseHeaderString(value))
                    }
                }
                line.startsWith("#") -> Unit // other tags are ignored
                isStreamUrl(line) -> {
                    val inf = pendingInf
                    if (inf != null) {
                        val (url, pipeHeaders) = splitPipeHeaders(line)
                        val nameIdx = nameSeparatorIndex(inf)
                        val attrs = parseAttributes(if (nameIdx >= 0) inf.substring(0, nameIdx) else inf)
                        val name = (if (nameIdx >= 0) inf.substring(nameIdx + 1) else "").trim()
                        val allHeaders = LinkedHashMap<String, String>().apply { putAll(pendingHeaders); putAll(pipeHeaders) }
                        val ua = pendingUa ?: attrs["user-agent"] ?: attrs["http-user-agent"]
                            ?: allHeaders.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
                        val ref = pendingRef ?: attrs["http-referrer"]
                            ?: allHeaders.entries.firstOrNull { it.key.equals("Referer", true) || it.key.equals("Referrer", true) }?.value
                        allHeaders.keys.removeAll { it.equals("User-Agent", true) || it.equals("Referer", true) || it.equals("Referrer", true) }
                        val licenseUrl = pendingDrmKey?.takeIf { it.startsWith("http", ignoreCase = true) }
                        onEntry(
                            M3uEntry(
                                name = name.ifEmpty { attrs["tvg-name"].orEmpty().ifEmpty { url.substringAfterLast('/') } },
                                url = url,
                                tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
                                tvgName = attrs["tvg-name"]?.takeIf { it.isNotBlank() },
                                logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() } ?: attrs["logo"]?.takeIf { it.isNotBlank() },
                                group = (attrs["group-title"] ?: pendingGroup)?.takeIf { it.isNotBlank() },
                                userAgent = ua?.takeIf { it.isNotBlank() } ?: header.userAgent,
                                referrer = ref?.takeIf { it.isNotBlank() },
                                headers = allHeaders,
                                drmScheme = pendingDrmType?.let(::normalizeDrmScheme),
                                drmLicenseUrl = licenseUrl,
                                catchupDays = (attrs["catchup-days"] ?: attrs["tvg-rec"])?.toIntOrNull(),
                                attributes = attrs,
                            )
                        )
                    } else {
                        // Plain list of URLs without #EXTINF: still playable.
                        val (url, pipeHeaders) = splitPipeHeaders(line)
                        onEntry(M3uEntry(name = url.substringAfterLast('/').substringBefore('?').ifEmpty { url }, url = url, headers = pipeHeaders))
                        sawHeaderOrInf = true
                    }
                    reset()
                }
                else -> Unit
            }
            if (!sawHeaderOrInf && nonEmptyLines >= 5) {
                throw M3uFormatException("Not an M3U playlist")
            }
        }
        if (!sawHeaderOrInf) throw M3uFormatException("Not an M3U playlist")
        return header
    }

    /** Collects all entries; convenient for small playlists and tests. */
    fun parseAll(text: String): Pair<M3uHeader, List<M3uEntry>> {
        val list = ArrayList<M3uEntry>()
        val header = parse(text.reader()) { list += it }
        return header to list
    }

    internal fun parseAttributes(line: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        ATTR_REGEX.findAll(line).forEach { m -> map[m.groupValues[1].lowercase()] = m.groupValues[2].trim() }
        return map
    }

    /** Index of the comma separating the attribute section from the display name (quotes aware). */
    internal fun nameSeparatorIndex(inf: String): Int {
        var inQuotes = false
        for (i in inf.indices) {
            val c = inf[i]
            if (c == '"') inQuotes = !inQuotes
            else if (c == ',' && !inQuotes) return i
        }
        return -1
    }

    private fun isStreamUrl(line: String): Boolean = STREAM_SCHEMES.any { line.startsWith(it, ignoreCase = true) }

    internal fun splitPipeHeaders(line: String): Pair<String, Map<String, String>> {
        val idx = line.indexOf('|')
        if (idx < 0) return line to emptyMap()
        return line.substring(0, idx).trim() to parseHeaderString(line.substring(idx + 1))
    }

    private fun parseHeaderString(value: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        value.split('&').forEach { part ->
            val k = part.substringBefore('=').trim()
            val v = part.substringAfter('=', "").trim()
            if (k.isNotEmpty() && v.isNotEmpty()) map[k] = decodePercent(v)
        }
        return map
    }

    private fun decodePercent(v: String): String = try {
        java.net.URLDecoder.decode(v.replace("+", "%2B"), "UTF-8")
    } catch (_: Exception) {
        v
    }

    private fun normalizeDrmScheme(type: String): String? = when (type.lowercase()) {
        "com.widevine.alpha", "widevine" -> "widevine"
        "com.microsoft.playready", "playready" -> "playready"
        "org.w3.clearkey", "clearkey" -> "clearkey"
        else -> null
    }
}
