package com.ultimateiptv.sports.data.sports

import com.ultimateiptv.sports.core.util.TextUtils
import com.ultimateiptv.sports.data.db.ChannelListItem
import com.ultimateiptv.sports.data.db.CompetitionChannelEntity
import com.ultimateiptv.sports.data.db.FavoriteTeamEntity
import com.ultimateiptv.sports.data.db.MatchDao
import com.ultimateiptv.sports.data.db.MatchEntity
import com.ultimateiptv.sports.data.db.MatchReminderEntity
import com.ultimateiptv.sports.data.db.MatchStatus
import com.ultimateiptv.sports.data.iptv.ChannelRepository
import com.ultimateiptv.sports.data.iptv.EpgRepository
import com.ultimateiptv.sports.data.settings.SettingsRepository
import com.ultimateiptv.sports.data.settings.SportsProviderId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class BroadcastCandidate(val channel: ChannelListItem, val source: Source, val programmeTitle: String?) {
    enum class Source { MAPPED, EPG_BOTH_TEAMS, EPG_ONE_TEAM }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MatchRepository @Inject constructor(
    private val dao: MatchDao,
    private val settings: SettingsRepository,
    private val providers: Map<SportsProviderId, @JvmSuppressWildcards SportsProvider>,
    private val channels: ChannelRepository,
    private val epg: EpgRepository,
) {
    private val mutex = Mutex()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    val providerFlow: Flow<SportsProviderId> = settings.settings.map { it.sportsProvider }.distinctUntilChanged()

    fun dayBounds(date: LocalDate): Pair<Long, Long> =
        date.atStartOfDay(zone).toInstant().toEpochMilli() to date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun observeDay(date: LocalDate): Flow<List<MatchEntity>> = providerFlow.flatMapLatest { p ->
        val (from, to) = dayBounds(date)
        dao.observeRange(p.name, from, to)
    }

    fun observeRange(from: Long, to: Long): Flow<List<MatchEntity>> = providerFlow.flatMapLatest { p -> dao.observeRange(p.name, from, to) }

    fun observeCompetition(key: String, from: Long, to: Long): Flow<List<MatchEntity>> =
        providerFlow.flatMapLatest { p -> dao.observeCompetition(p.name, key, from, to) }

    fun observe(id: String) = dao.observe(id)
    suspend fun get(id: String) = dao.get(id)

    /**
     * Fetches [date] from the configured provider unless the cache is fresh enough.
     * TTL: 1 min while matches are live, 15 min for today, 1 h for future days, 6 h for past days.
     */
    suspend fun refreshDay(date: LocalDate, force: Boolean = false): Result<Unit> = mutex.withLock {
        runCatching {
            val s = settings.current()
            val provider = providers.getValue(s.sportsProvider)
            val (from, to) = dayBounds(date)
            val now = System.currentTimeMillis()
            if (!force) {
                val oldest = dao.oldestFetch(provider.id.name, from, to)
                if (oldest != null && now - oldest < ttl(date, provider.id, from, to, now)) return@runCatching
            }
            val key = settings.sportsApiKey(s.sportsProvider)
            val list = provider.fixtures(date, zone, key)
            dao.replaceRange(provider.id.name, from, to, list.filter { it.kickoff in from until to })
            // Football-data returns neighbour days too: cache them without wiping.
            list.filter { it.kickoff !in from until to }.takeIf { it.isNotEmpty() }?.let { dao.upsertAll(it) }
            dao.deleteOlderThan(now - TimeUnit.DAYS.toMillis(14))
        }
    }

    private suspend fun ttl(date: LocalDate, provider: SportsProviderId, from: Long, to: Long, now: Long): Long {
        val today = LocalDate.now(zone)
        if (provider == SportsProviderId.DEMO) return TimeUnit.MINUTES.toMillis(1)
        return when {
            date.isBefore(today) -> TimeUnit.HOURS.toMillis(6)
            date.isAfter(today) -> TimeUnit.HOURS.toMillis(1)
            else -> {
                dao.observeRange(provider.name, from, to).firstOrNull()?.let { matches ->
                    if (matches.any { it.isLiveOrSoon(now) }) TimeUnit.SECONDS.toMillis(if (provider == SportsProviderId.API_FOOTBALL) 120 else 60)
                    else TimeUnit.MINUTES.toMillis(15)
                } ?: TimeUnit.MINUTES.toMillis(15)
            }
        }
    }

    // Favourite teams & reminders
    fun favoriteTeams() = dao.observeFavoriteTeams()
    suspend fun toggleFavoriteTeam(name: String, logo: String?): Boolean {
        val key = teamKey(name)
        val exists = dao.favoriteTeams().any { it.key == key }
        if (exists) dao.removeFavoriteTeam(key) else dao.addFavoriteTeam(FavoriteTeamEntity(key, name, logo))
        return !exists
    }
    suspend fun removeFavoriteTeam(key: String) = dao.removeFavoriteTeam(key)
    suspend fun favoriteTeamKeys(): Set<String> = dao.favoriteTeams().map { it.key }.toSet()
    fun reminders() = dao.observeReminders()
    suspend fun reminder(id: String) = dao.reminder(id)
    suspend fun saveReminder(r: MatchReminderEntity) = dao.upsertReminder(r)
    suspend fun deleteReminder(id: String) = dao.deleteReminder(id)
    suspend fun futureReminders() = dao.futureReminders(System.currentTimeMillis())
    suspend fun cleanupReminders() = dao.deleteOldReminders(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(3))

    // Broadcaster mapping
    fun competitionChannels(key: String) = dao.observeCompetitionChannels(key)
    suspend fun addCompetitionChannel(key: String, uid: String) = dao.addCompetitionChannel(CompetitionChannelEntity(key, uid))
    suspend fun removeCompetitionChannel(key: String, uid: String) = dao.removeCompetitionChannel(key, uid)

    suspend fun search(q: String): List<MatchEntity> = dao.search(settings.current().sportsProvider.name, q.trim())

    /**
     * Finds channels likely to carry the match, from the user's own sources only:
     * 1) channels the user mapped to the competition, 2) EPG programmes around kickoff whose
     * title mentions the teams. Nothing is guessed from outside data.
     */
    suspend fun broadcastCandidates(match: MatchEntity): List<BroadcastCandidate> {
        val result = LinkedHashMap<String, BroadcastCandidate>()
        channels.items(dao.competitionChannels(match.competitionKey)).forEach {
            result[it.uid] = BroadcastCandidate(it, BroadcastCandidate.Source.MAPPED, null)
        }
        val home = TextUtils.teamKeywords(match.homeName)
        val away = TextUtils.teamKeywords(match.awayName)
        if (home.isNotEmpty() || away.isNotEmpty()) {
            val window = epg.programmesIn(match.kickoff - TimeUnit.MINUTES.toMillis(20), match.kickoff + TimeUnit.MINUTES.toMillis(30))
            val scored = window.mapNotNull { p ->
                val uid = p.channelUid ?: return@mapNotNull null
                val title = TextUtils.normalize(p.title + " " + p.description.orEmpty().take(200))
                val h = TextUtils.containsAny(title, home)
                val a = TextUtils.containsAny(title, away)
                when {
                    h && a -> Triple(uid, BroadcastCandidate.Source.EPG_BOTH_TEAMS, p.title)
                    h || a -> Triple(uid, BroadcastCandidate.Source.EPG_ONE_TEAM, p.title)
                    else -> null
                }
            }.sortedBy { it.second.ordinal }
            val items = channels.items(scored.map { it.first }.distinct()).associateBy { it.uid }
            scored.forEach { (uid, src, title) ->
                val item = items[uid] ?: return@forEach
                if (uid !in result) result[uid] = BroadcastCandidate(item, src, title)
            }
        }
        return result.values.take(20)
    }

    companion object {
        fun teamKey(name: String) = TextUtils.normalize(name)
    }
}

fun MatchEntity.isLive() = status == MatchStatus.LIVE || status == MatchStatus.HALFTIME
fun MatchEntity.isLiveOrSoon(now: Long) =
    isLive() || (status == MatchStatus.SCHEDULED && kickoff - now in -TimeUnit.HOURS.toMillis(3)..TimeUnit.MINUTES.toMillis(30))
