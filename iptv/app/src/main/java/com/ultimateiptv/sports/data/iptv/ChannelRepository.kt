package com.ultimateiptv.sports.data.iptv

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.ultimateiptv.sports.core.parser.XtreamParsers
import com.ultimateiptv.sports.data.db.ChannelDao
import com.ultimateiptv.sports.data.db.ChannelEntity
import com.ultimateiptv.sports.data.db.ChannelListItem
import com.ultimateiptv.sports.data.db.FavoriteChannelEntity
import com.ultimateiptv.sports.data.db.GroupCount
import com.ultimateiptv.sports.data.db.SubscriptionDao
import com.ultimateiptv.sports.data.db.SubscriptionType
import com.ultimateiptv.sports.core.util.TextUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Which list the player zaps through with channel up/down. */
sealed interface ChannelContext {
    data class Group(val subscriptionId: Long, val group: String?, val sportsOnly: Boolean) : ChannelContext
    data object Favorites : ChannelContext
    data object History : ChannelContext

    fun encode(): String = when (this) {
        is Group -> "g|$subscriptionId|${if (sportsOnly) 1 else 0}|${group.orEmpty()}"
        Favorites -> "fav"
        History -> "hist"
    }

    companion object {
        fun decode(s: String?): ChannelContext = when {
            s == null -> Group(0, null, false)
            s == "fav" -> Favorites
            s == "hist" -> History
            s.startsWith("g|") -> {
                val parts = s.split('|', limit = 4)
                Group(parts.getOrNull(1)?.toLongOrNull() ?: 0, parts.getOrNull(3)?.takeIf { it.isNotEmpty() }, parts.getOrNull(2) == "1")
            }
            else -> Group(0, null, false)
        }
    }
}

/** Everything the player needs to open a stream. */
data class PlaybackSource(
    val uid: String,
    val subscriptionId: Long,
    val title: String,
    val logo: String?,
    val epgId: String?,
    val urls: List<String>,
    val userAgent: String?,
    val referrer: String?,
    val headers: Map<String, String>,
    val drmScheme: String?,
    val drmLicenseUrl: String?,
)

@Singleton
class ChannelRepository @Inject constructor(
    private val channelDao: ChannelDao,
    private val subscriptionDao: SubscriptionDao,
    private val subscriptions: SubscriptionRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun paged(subscriptionId: Long, group: String?, sportsOnly: Boolean): Flow<PagingData<ChannelListItem>> =
        Pager(PagingConfig(pageSize = 60, prefetchDistance = 60, initialLoadSize = 120, enablePlaceholders = false)) {
            channelDao.page(subscriptionId, group, sportsOnly)
        }.flow

    fun groups(subscriptionId: Long, sportsOnly: Boolean): Flow<List<GroupCount>> = channelDao.observeGroups(subscriptionId, sportsOnly)
    fun favorites(): Flow<List<ChannelListItem>> = channelDao.observeFavorites()
    fun history(limit: Int = 50): Flow<List<ChannelListItem>> = channelDao.observeHistory(limit)
    fun sports(limit: Int = 30): Flow<List<ChannelListItem>> = channelDao.observeSports(limit)
    fun observeItem(uid: String): Flow<ChannelListItem?> = channelDao.observeItem(uid)
    fun observeTotal(): Flow<Int> = channelDao.observeTotal()

    suspend fun get(uid: String): ChannelEntity? = channelDao.get(uid)
    suspend fun items(uids: List<String>): List<ChannelListItem> {
        val map = uids.chunked(900).flatMap { channelDao.items(it) }.associateBy { it.uid }
        return uids.mapNotNull { map[it] }
    }

    suspend fun search(query: String, limit: Int = 100): List<ChannelListItem> {
        val q = TextUtils.normalize(query)
        if (q.isEmpty()) return emptyList()
        return channelDao.search(q, limit)
    }

    suspend fun toggleFavorite(uid: String): Boolean {
        return if (channelDao.isFavorite(uid)) { channelDao.removeFavorite(uid); false }
        else { channelDao.addFavorite(FavoriteChannelEntity(uid)); true }
    }

    suspend fun recordWatched(uid: String) = channelDao.touchHistory(uid, System.currentTimeMillis())
    suspend fun clearHistory() = channelDao.clearHistory()
    suspend fun removeFromHistory(uid: String) = channelDao.removeHistory(uid)

    suspend fun uidsFor(context: ChannelContext): List<String> = when (context) {
        is ChannelContext.Group -> channelDao.uids(context.subscriptionId, context.group, context.sportsOnly)
        ChannelContext.Favorites -> channelDao.favoriteUids()
        ChannelContext.History -> channelDao.historyUids()
    }

    /** Builds the playable URL(s). Xtream URLs are assembled here so passwords never sit in the channel table. */
    suspend fun playback(uid: String): PlaybackSource? {
        val ch = channelDao.get(uid) ?: return null
        val sub = subscriptionDao.get(ch.subscriptionId) ?: return null
        val urls = when {
            ch.xtreamStreamId != null && sub.type == SubscriptionType.XTREAM -> {
                val s = subscriptions.secretsOf(sub)
                val primary = sub.outputFormat.ifBlank { "m3u8" }
                val alternate = if (primary == "m3u8") "ts" else "m3u8"
                listOf(
                    XtreamParsers.buildLiveUrl(s.url, s.username, s.password, ch.xtreamStreamId, primary),
                    XtreamParsers.buildLiveUrl(s.url, s.username, s.password, ch.xtreamStreamId, alternate),
                )
            }
            ch.streamUrl != null -> listOf(ch.streamUrl)
            else -> return null
        }
        val headers: Map<String, String> = ch.headersJson?.let {
            runCatching { json.decodeFromString(SubscriptionRepository.HEADERS_SERIALIZER, it) }.getOrNull()
        }.orEmpty()
        return PlaybackSource(
            uid = ch.uid, subscriptionId = ch.subscriptionId, title = ch.name, logo = ch.logo, epgId = ch.epgId,
            urls = urls, userAgent = ch.userAgent ?: sub.userAgent, referrer = ch.referrer, headers = headers,
            drmScheme = ch.drmScheme, drmLicenseUrl = ch.drmLicenseUrl,
        )
    }
}
