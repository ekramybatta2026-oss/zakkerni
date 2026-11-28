package com.ultimateiptv.sports.data.iptv

import com.ultimateiptv.sports.data.db.SubscriptionType
import kotlinx.serialization.Serializable

/** Everything sensitive about a subscription. Serialized to JSON and encrypted as a whole. */
@Serializable
data class SubscriptionSecrets(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val epgUrl: String? = null,
    val discoveredEpgUrls: List<String> = emptyList(),
)

data class SubscriptionInput(
    val name: String,
    val type: SubscriptionType,
    val url: String,
    val username: String = "",
    val password: String = "",
    val epgUrl: String? = null,
    val userAgent: String? = null,
    val outputFormat: String = "m3u8",
    val autoRefreshHours: Int = 24,
)

sealed interface RefreshProgress {
    data class Downloading(val channels: Int) : RefreshProgress
    data class Epg(val programmes: Int) : RefreshProgress
}

data class RefreshSummary(val channels: Int, val programmes: Int, val epgError: Throwable?)

data class ConnectionTestResult(
    val ok: Boolean,
    val channels: Int? = null,
    val accountStatus: String? = null,
    val expiresAt: Long? = null,
    val maxConnections: Int? = null,
    val error: Throwable? = null,
)
