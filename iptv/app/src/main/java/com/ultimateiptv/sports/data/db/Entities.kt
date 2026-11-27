package com.ultimateiptv.sports.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SubscriptionType { XTREAM, M3U_URL, M3U_FILE, DEMO }

enum class SyncStatus { NEVER, SYNCING, OK, ERROR }

/**
 * An IPTV subscription. Credentials (URL, username, password, EPG URL) are kept only in
 * [secretBlob], encrypted with an Android Keystore AES-GCM key. [displayHost] is a safe label.
 */
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: SubscriptionType,
    val displayHost: String,
    val secretBlob: String,
    val userAgent: String? = null,
    /** Xtream output: "m3u8" or "ts". */
    val outputFormat: String = "m3u8",
    val autoRefreshHours: Int = 24,
    val enabled: Boolean = true,
    val status: SyncStatus = SyncStatus.NEVER,
    val lastError: String? = null,
    val lastRefreshAt: Long? = null,
    val lastEpgRefreshAt: Long? = null,
    val channelCount: Int = 0,
    val programmeCount: Int = 0,
    val expiresAt: Long? = null,
    val maxConnections: Int? = null,
    val accountStatus: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "channels",
    indices = [
        Index("subscriptionId", "groupTitle"),
        Index("normalizedName"),
        Index("isSports"),
        Index("epgId"),
    ],
)
data class ChannelEntity(
    /** "<subscriptionId>:<stable key>" so favourites survive playlist refreshes. */
    @PrimaryKey val uid: String,
    val subscriptionId: Long,
    val name: String,
    val normalizedName: String,
    val logo: String?,
    val groupTitle: String,
    val tvgId: String?,
    /** EPG channel id resolved from tvg-id or from the XMLTV display names. */
    val epgId: String?,
    val number: Int?,
    val isSports: Boolean,
    /** Full stream URL for M3U sources; null for Xtream (built at play time from encrypted credentials). */
    val streamUrl: String?,
    val xtreamStreamId: Long?,
    val userAgent: String?,
    val referrer: String?,
    val headersJson: String?,
    val drmScheme: String?,
    val drmLicenseUrl: String?,
    val catchupDays: Int?,
    val sortIndex: Int,
    val generation: Long,
)

@Entity(tableName = "favorite_channels")
data class FavoriteChannelEntity(
    @PrimaryKey val uid: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "history", indices = [Index("lastWatchedAt")])
data class HistoryEntity(
    @PrimaryKey val uid: String,
    val lastWatchedAt: Long,
    val watchCount: Int = 1,
)

@Entity(
    tableName = "epg_programmes",
    indices = [Index("subscriptionId", "epgChannelId", "startMillis"), Index("stopMillis")],
)
data class EpgProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subscriptionId: Long,
    val epgChannelId: String,
    val startMillis: Long,
    val stopMillis: Long,
    val title: String,
    val description: String?,
    val category: String?,
)

enum class MatchStatus { SCHEDULED, LIVE, HALFTIME, FINISHED, POSTPONED, CANCELLED, UNKNOWN }

@Entity(tableName = "matches", indices = [Index("kickoff"), Index("competitionKey")])
data class MatchEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val competitionKey: String,
    val competitionName: String,
    val competitionLogo: String?,
    val country: String?,
    val countryFlag: String?,
    val round: String?,
    val homeName: String,
    val homeLogo: String?,
    val awayName: String,
    val awayLogo: String?,
    val homeScore: Int?,
    val awayScore: Int?,
    val status: MatchStatus,
    val minute: Int?,
    val kickoff: Long,
    val venue: String?,
    val fetchedAt: Long,
)

@Entity(tableName = "favorite_teams")
data class FavoriteTeamEntity(
    @PrimaryKey val key: String,
    val name: String,
    val logo: String?,
)

@Entity(tableName = "match_reminders")
data class MatchReminderEntity(
    @PrimaryKey val matchId: String,
    val kickoff: Long,
    val title: String,
    val competition: String,
    val minutesBefore: Int,
    val automatic: Boolean = false,
)

/** User-defined broadcaster mapping: which of the user's channels carry a competition. */
@Entity(tableName = "competition_channels", primaryKeys = ["competitionKey", "channelUid"])
data class CompetitionChannelEntity(
    val competitionKey: String,
    val channelUid: String,
)

/** Lightweight projection used by lists. */
data class ChannelListItem(
    val uid: String,
    val subscriptionId: Long,
    val name: String,
    val logo: String?,
    val groupTitle: String,
    val epgId: String?,
    val number: Int?,
    val isSports: Boolean,
    @ColumnInfo(name = "isFavorite") val isFavorite: Boolean,
)

data class GroupCount(val groupTitle: String, val count: Int)

data class ProgrammeWithChannel(
    val id: Long,
    val subscriptionId: Long,
    val epgChannelId: String,
    val startMillis: Long,
    val stopMillis: Long,
    val title: String,
    val description: String?,
    val channelUid: String?,
    val channelName: String?,
    val channelLogo: String?,
)
