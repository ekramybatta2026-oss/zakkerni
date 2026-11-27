package com.ultimateiptv.sports.core.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Base64

/** Xtream Codes JSON is loosely typed (numbers as strings, nulls, "0"/"1" booleans). */
data class XtreamAccount(
    val username: String,
    val status: String,
    val authenticated: Boolean,
    val expiresAtMillis: Long?,
    val isTrial: Boolean,
    val activeConnections: Int?,
    val maxConnections: Int?,
    val allowedOutputFormats: List<String>,
    val serverUrl: String?,
    val serverTimezone: String?,
    val message: String?,
)

data class XtreamCategory(val id: String, val name: String)

data class XtreamLiveStream(
    val streamId: Long,
    val num: Int?,
    val name: String,
    val icon: String?,
    val epgChannelId: String?,
    val categoryId: String?,
    val tvArchive: Boolean,
    val tvArchiveDuration: Int?,
    val directSource: String?,
)

data class XtreamShortEpg(val title: String, val description: String?, val startMillis: Long, val stopMillis: Long)

object XtreamParsers {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseAccount(root: JsonObject): XtreamAccount {
        val user = root["user_info"] as? JsonObject
            ?: throw IllegalArgumentException("Invalid Xtream response: missing user_info")
        val server = root["server_info"] as? JsonObject
        val formats = (user["allowed_output_formats"] as? JsonArray)?.mapNotNull { it.str() }.orEmpty()
        val serverUrl = server?.let {
            val host = it.str("url") ?: return@let null
            val https = it.str("server_protocol") == "https"
            val port = if (https) it.str("https_port") else it.str("port")
            (if (https) "https://" else "http://") + host + (port?.takeIf { p -> p.isNotBlank() }?.let { p -> ":$p" } ?: "")
        }
        return XtreamAccount(
            username = user.str("username").orEmpty(),
            status = user.str("status").orEmpty(),
            authenticated = user.bool("auth"),
            expiresAtMillis = user.long("exp_date")?.takeIf { it > 0 }?.times(1000),
            isTrial = user.bool("is_trial"),
            activeConnections = user.long("active_cons")?.toInt(),
            maxConnections = user.long("max_connections")?.toInt(),
            allowedOutputFormats = formats,
            serverUrl = serverUrl,
            serverTimezone = server?.str("timezone"),
            message = user.str("message"),
        )
    }

    fun parseCategory(o: JsonObject): XtreamCategory? {
        val id = o.str("category_id") ?: return null
        return XtreamCategory(id, o.str("category_name").orEmpty().ifBlank { id })
    }

    fun parseLiveStream(o: JsonObject): XtreamLiveStream? {
        val id = o.long("stream_id") ?: return null
        return XtreamLiveStream(
            streamId = id,
            num = o.long("num")?.toInt(),
            name = o.str("name").orEmpty().trim().ifEmpty { "Channel $id" },
            icon = o.str("stream_icon")?.takeIf { it.isNotBlank() },
            epgChannelId = o.str("epg_channel_id")?.takeIf { it.isNotBlank() },
            categoryId = o.str("category_id") ?: (o["category_ids"] as? JsonArray)?.firstOrNull()?.str(),
            tvArchive = o.bool("tv_archive"),
            tvArchiveDuration = o.long("tv_archive_duration")?.toInt(),
            directSource = o.str("direct_source")?.takeIf { it.startsWith("http") },
        )
    }

    /** get_short_epg returns base64 encoded title/description. */
    fun parseShortEpg(root: JsonObject): List<XtreamShortEpg> {
        val list = root["epg_listings"] as? JsonArray ?: return emptyList()
        return list.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val start = o.long("start_timestamp")?.times(1000) ?: return@mapNotNull null
            val stop = o.long("stop_timestamp")?.times(1000) ?: return@mapNotNull null
            XtreamShortEpg(decodeB64(o.str("title")).orEmpty(), decodeB64(o.str("description")), start, stop)
        }
    }

    fun buildLiveUrl(server: String, username: String, password: String, streamId: Long, ext: String): String =
        "${server.trimEnd('/')}/live/${enc(username)}/${enc(password)}/$streamId.$ext"

    fun normalizeServer(input: String): String {
        var s = input.trim().trimEnd('/')
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
        // Users often paste the full player_api.php or get.php URL.
        listOf("/player_api.php", "/get.php", "/panel_api.php", "/xmltv.php").forEach { suffix ->
            val i = s.indexOf(suffix, ignoreCase = true)
            if (i > 0) s = s.substring(0, i)
        }
        return s.trimEnd('/')
    }

    private fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8").replace("+", "%20")

    private fun decodeB64(v: String?): String? {
        if (v.isNullOrEmpty()) return null
        return try { String(Base64.getDecoder().decode(v.trim()), Charsets.UTF_8) } catch (_: Exception) { v }
    }

    // --- loose JSON helpers ---
    private fun JsonElement.str(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun JsonObject.str(key: String): String? = this[key]?.str()
    private fun JsonObject.long(key: String): Long? = str(key)?.trim()?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }
    private fun JsonObject.bool(key: String): Boolean = when (str(key)?.trim()?.lowercase()) {
        "1", "true", "yes" -> true
        else -> false
    }
}
