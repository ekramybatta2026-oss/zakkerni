package com.ultimateiptv.sports.data.iptv

import android.util.Xml
import com.ultimateiptv.sports.core.network.HttpFetcher
import com.ultimateiptv.sports.core.parser.XmltvParser
import com.ultimateiptv.sports.core.util.TextUtils
import com.ultimateiptv.sports.data.db.ChannelDao
import com.ultimateiptv.sports.data.db.EpgDao
import com.ultimateiptv.sports.data.db.EpgProgrammeEntity
import com.ultimateiptv.sports.data.db.ProgrammeWithChannel
import com.ultimateiptv.sports.data.db.SubscriptionDao
import com.ultimateiptv.sports.data.db.SubscriptionEntity
import com.ultimateiptv.sports.data.db.SubscriptionType
import com.ultimateiptv.sports.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgRepository @Inject constructor(
    private val epgDao: EpgDao,
    private val channelDao: ChannelDao,
    private val subscriptionDao: SubscriptionDao,
    private val fetcher: HttpFetcher,
    private val settings: SettingsRepository,
) {
    fun observeNowNext(subscriptionId: Long, epgId: String, now: Long): Flow<List<EpgProgrammeEntity>> =
        epgDao.observeNowNext(subscriptionId, epgId, now)

    suspend fun nowNext(subscriptionId: Long, epgId: String?, now: Long = System.currentTimeMillis()): List<EpgProgrammeEntity> =
        if (epgId == null) emptyList() else epgDao.nowNext(subscriptionId, epgId, now)

    fun observeSchedule(subscriptionId: Long, epgId: String, from: Long, to: Long) = epgDao.observeSchedule(subscriptionId, epgId, from, to)

    suspend fun search(query: String, limit: Int = 50): List<ProgrammeWithChannel> {
        val now = System.currentTimeMillis()
        return epgDao.search(query.trim(), now - TimeUnit.HOURS.toMillis(1), now + TimeUnit.DAYS.toMillis(3), limit)
    }

    suspend fun programmesIn(from: Long, to: Long): List<ProgrammeWithChannel> = epgDao.inWindow(from, to)

    fun epgUrls(sub: SubscriptionEntity, s: SubscriptionSecrets): List<String> {
        s.epgUrl?.takeIf { it.isNotBlank() }?.let { return listOf(it) }
        return when (sub.type) {
            SubscriptionType.XTREAM -> listOf(
                "${s.url}/xmltv.php?username=${URLEncoder.encode(s.username, "UTF-8")}&password=${URLEncoder.encode(s.password, "UTF-8")}"
            )
            else -> s.discoveredEpgUrls
        }
    }

    /** Downloads XMLTV, keeps only the user's channels and a 4-day window. Returns the programme count. */
    suspend fun refresh(sub: SubscriptionEntity, secrets: SubscriptionSecrets, onProgress: (Int) -> Unit = {}): Result<Int> = runCatching {
        if (sub.type == SubscriptionType.DEMO) {
            val programmes = DemoEpgGenerator.generate(sub.id, System.currentTimeMillis())
            epgDao.deleteFor(sub.id)
            programmes.chunked(BATCH).forEach { epgDao.insertAll(it) }
            subscriptionDao.update(sub.copy(programmeCount = programmes.size, lastEpgRefreshAt = System.currentTimeMillis()))
            return@runCatching programmes.size
        }
        val urls = epgUrls(sub, secrets)
        if (urls.isEmpty()) return@runCatching 0
        val ua = sub.userAgent ?: settings.current().defaultUserAgent
        val now = System.currentTimeMillis()
        val windowStart = now - TimeUnit.HOURS.toMillis(6)
        val windowEnd = now + TimeUnit.DAYS.toMillis(4)

        val keep = HashSet(channelDao.epgIds(sub.id))
        // Channels without tvg-id are matched by name against <display-name>.
        val unmatched = HashMap<String, MutableList<String>>()
        channelDao.unmatchedForEpg(sub.id).forEach { unmatched.getOrPut(it.normalizedName) { ArrayList(1) } += it.uid }
        val assignments = ArrayList<Pair<String, String>>()

        var total = 0
        var cleared = false
        var lastError: Throwable? = null
        val parser = XmltvParser { Xml.newPullParser() }
        for (url in urls) {
            try {
                fetcher.stream(url, ua) { input, _ ->
                    val batch = ArrayList<EpgProgrammeEntity>(BATCH)
                    parser.parse(
                        input, windowStart, windowEnd,
                        keepChannel = { it in keep },
                        onChannel = { ch ->
                            ch.displayNames.forEach { dn ->
                                unmatched.remove(TextUtils.normalize(dn))?.forEach { uid -> assignments += uid to ch.id; keep += ch.id }
                            }
                        },
                        onProgramme = { p ->
                            batch += EpgProgrammeEntity(
                                subscriptionId = sub.id, epgChannelId = p.channelId, startMillis = p.startMillis, stopMillis = p.stopMillis,
                                title = p.title, description = p.description ?: p.subTitle, category = p.category,
                            )
                            if (batch.size >= BATCH) {
                                val copy = ArrayList(batch); batch.clear()
                                runBlocking {
                                    if (!cleared) { epgDao.deleteFor(sub.id); cleared = true }
                                    epgDao.insertAll(copy)
                                }
                                total += copy.size
                                onProgress(total)
                            }
                        },
                    )
                    if (batch.isNotEmpty()) runBlocking {
                        if (!cleared) { epgDao.deleteFor(sub.id); cleared = true }
                        epgDao.insertAll(batch)
                    }
                    total += batch.size
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                lastError = t
            }
        }
        assignments.forEach { (uid, epgId) -> channelDao.setEpgId(uid, epgId) }
        if (total == 0 && lastError != null) throw lastError!!
        epgDao.deleteOlderThan(now - TimeUnit.DAYS.toMillis(1))
        subscriptionDao.get(sub.id)?.let { subscriptionDao.update(it.copy(programmeCount = total, lastEpgRefreshAt = System.currentTimeMillis())) }
        total
    }

    private companion object { const val BATCH = 2000 }
}

/** Synthetic, clearly-labelled guide data for the demo playlist so the EPG UI can be tested offline. */
object DemoEpgGenerator {
    private val shows = mapOf(
        "demo.sports1" to listOf("Matchday Live (Demo)", "Pre-match Studio (Demo)", "Goals of the Week (Demo)", "Post-match Analysis (Demo)"),
        "demo.sports2" to listOf("Tennis Classics (Demo)", "Motorsport Weekly (Demo)", "Basketball Night (Demo)"),
        "demo.news" to listOf("World Headlines (Demo)", "Business Today (Demo)", "Tech Review (Demo)"),
        "demo.movies" to listOf("Bip-Bop Test Pattern (Demo)", "Tears of Steel", "Big Buck Bunny"),
        "demo.docs" to listOf("Nature Close-up (Demo)", "Science Hour (Demo)"),
        "demo.live" to listOf("Live Test Stream (Demo)", "Signal Check (Demo)"),
    )

    fun generate(subscriptionId: Long, now: Long): List<EpgProgrammeEntity> {
        val hour = TimeUnit.HOURS.toMillis(1)
        val start = now - now % hour - TimeUnit.HOURS.toMillis(12)
        val end = now + TimeUnit.DAYS.toMillis(3)
        val out = ArrayList<EpgProgrammeEntity>()
        shows.forEach { (channel, titles) ->
            var t = start
            var i = 0
            while (t < end) {
                val len = if (i % 3 == 0) 2 * hour else hour
                val title = titles[i % titles.size]
                out += EpgProgrammeEntity(
                    subscriptionId = subscriptionId, epgChannelId = channel, startMillis = t, stopMillis = t + len,
                    title = title, description = "Sample programme guide entry generated on the device for testing the EPG screens.",
                    category = if (channel.startsWith("demo.sports")) "Sports" else null,
                )
                t += len; i++
            }
        }
        return out
    }
}
