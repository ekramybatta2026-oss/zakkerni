package com.ultimateiptv.sports.data.iptv

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.ultimateiptv.sports.core.network.AuthException
import com.ultimateiptv.sports.core.network.HttpFetcher
import com.ultimateiptv.sports.core.parser.M3uEntry
import com.ultimateiptv.sports.core.parser.M3uParser
import com.ultimateiptv.sports.core.parser.XtreamParsers
import com.ultimateiptv.sports.core.security.CredentialCipher
import com.ultimateiptv.sports.core.util.SportsClassifier
import com.ultimateiptv.sports.core.util.TextUtils
import com.ultimateiptv.sports.data.db.AppDatabase
import com.ultimateiptv.sports.data.db.ChannelDao
import com.ultimateiptv.sports.data.db.ChannelEntity
import com.ultimateiptv.sports.data.db.EpgDao
import com.ultimateiptv.sports.data.db.SubscriptionDao
import com.ultimateiptv.sports.data.db.SubscriptionEntity
import com.ultimateiptv.sports.data.db.SubscriptionType
import com.ultimateiptv.sports.data.db.SyncStatus
import com.ultimateiptv.sports.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val subscriptionDao: SubscriptionDao,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val cipher: CredentialCipher,
    private val fetcher: HttpFetcher,
    private val settings: SettingsRepository,
    private val epgRepository: EpgRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val locks = HashMap<Long, Mutex>()
    private val _progress = MutableStateFlow<Map<Long, RefreshProgress>>(emptyMap())
    val progress: StateFlow<Map<Long, RefreshProgress>> = _progress.asStateFlow()

    fun observeAll(): Flow<List<SubscriptionEntity>> = subscriptionDao.observeAll()
    fun observe(id: Long): Flow<SubscriptionEntity?> = subscriptionDao.observe(id)
    fun observeCount(): Flow<Int> = subscriptionDao.observeCount()
    suspend fun get(id: Long) = subscriptionDao.get(id)

    suspend fun secrets(id: Long): SubscriptionSecrets? {
        val sub = subscriptionDao.get(id) ?: return null
        return secretsOf(sub)
    }

    fun secretsOf(sub: SubscriptionEntity): SubscriptionSecrets =
        runCatching { json.decodeFromString(SubscriptionSecrets.serializer(), cipher.decrypt(sub.secretBlob)) }
            .getOrElse { SubscriptionSecrets() }

    private fun encryptSecrets(s: SubscriptionSecrets) = cipher.encrypt(json.encodeToString(SubscriptionSecrets.serializer(), s))

    suspend fun add(input: SubscriptionInput): Long {
        val normalized = normalize(input)
        val entity = SubscriptionEntity(
            name = normalized.name.ifBlank { displayHost(normalized) },
            type = normalized.type,
            displayHost = displayHost(normalized),
            secretBlob = encryptSecrets(
                SubscriptionSecrets(normalized.url, normalized.username, normalized.password, normalized.epgUrl?.takeIf { it.isNotBlank() })
            ),
            userAgent = normalized.userAgent?.takeIf { it.isNotBlank() },
            outputFormat = normalized.outputFormat,
            autoRefreshHours = normalized.autoRefreshHours,
        )
        return subscriptionDao.insert(entity)
    }

    suspend fun update(id: Long, input: SubscriptionInput) {
        val old = subscriptionDao.get(id) ?: return
        val normalized = normalize(input)
        val oldSecrets = secretsOf(old)
        // An empty password field in the editor means "keep the stored one".
        val password = normalized.password.ifEmpty { oldSecrets.password }
        subscriptionDao.update(
            old.copy(
                name = normalized.name.ifBlank { displayHost(normalized) },
                type = normalized.type,
                displayHost = displayHost(normalized),
                secretBlob = encryptSecrets(
                    oldSecrets.copy(url = normalized.url, username = normalized.username, password = password, epgUrl = normalized.epgUrl?.takeIf { it.isNotBlank() })
                ),
                userAgent = normalized.userAgent?.takeIf { it.isNotBlank() },
                outputFormat = normalized.outputFormat,
                autoRefreshHours = normalized.autoRefreshHours,
            )
        )
    }

    suspend fun delete(id: Long) {
        db.withTransaction {
            channelDao.deleteForSubscription(id)
            epgDao.deleteFor(id)
            subscriptionDao.delete(id)
        }
        if (settings.current().lastSubscriptionId == id) settings.update { it.copy(lastSubscriptionId = 0) }
    }

    private fun normalize(input: SubscriptionInput): SubscriptionInput = when (input.type) {
        SubscriptionType.XTREAM -> input.copy(url = XtreamParsers.normalizeServer(input.url), username = input.username.trim(), password = input.password.trim())
        SubscriptionType.M3U_URL -> input.copy(url = input.url.trim())
        else -> input
    }

    private fun displayHost(input: SubscriptionInput): String = when (input.type) {
        SubscriptionType.DEMO -> "Demo"
        SubscriptionType.M3U_FILE -> Uri.parse(input.url).lastPathSegment?.substringAfterLast('/') ?: "file"
        else -> input.url.toHttpUrlOrNull()?.host ?: input.url.take(40)
    }

    /** Validates a subscription without saving it. */
    suspend fun test(input: SubscriptionInput): ConnectionTestResult = withContext(Dispatchers.IO) {
        val n = normalize(input)
        val ua = n.userAgent?.takeIf { it.isNotBlank() } ?: settings.current().defaultUserAgent
        try {
            when (n.type) {
                SubscriptionType.XTREAM -> {
                    val account = fetchAccount(n.url, n.username, n.password, ua)
                    ConnectionTestResult(true, null, account.status, account.expiresAtMillis, account.maxConnections)
                }
                else -> {
                    var count = 0
                    openPlaylist(n.type, n.url, ua) { input -> M3uParser.parse(InputStreamReader(input, Charsets.UTF_8)) { count++ } }
                    ConnectionTestResult(ok = count > 0, channels = count)
                }
            }
        } catch (t: Throwable) {
            ConnectionTestResult(false, error = t)
        }
    }

    private suspend fun fetchAccount(server: String, user: String, pass: String, ua: String) =
        XtreamParsers.parseAccount(json.parseToJsonElement(fetcher.text(xtreamApi(server, user, pass), ua)).jsonObject).also {
            if (!it.authenticated) throw AuthException(it.message ?: "Xtream authentication failed")
        }

    private fun xtreamApi(server: String, user: String, pass: String, action: String? = null): String {
        val u = URLEncoder.encode(user, "UTF-8")
        val p = URLEncoder.encode(pass, "UTF-8")
        return "$server/player_api.php?username=$u&password=$p" + (action?.let { "&action=$it" } ?: "")
    }

    fun xtreamEpgUrl(s: SubscriptionSecrets): String =
        "${s.url}/xmltv.php?username=${URLEncoder.encode(s.username, "UTF-8")}&password=${URLEncoder.encode(s.password, "UTF-8")}"

    private suspend fun <T> openPlaylist(type: SubscriptionType, url: String, ua: String, block: (InputStream) -> T): T = when (type) {
        SubscriptionType.M3U_FILE -> withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(Uri.parse(url)) ?: throw java.io.FileNotFoundException(url)
            stream.buffered().use(block)
        }
        SubscriptionType.DEMO -> withContext(Dispatchers.IO) { context.assets.open(DEMO_ASSET).buffered().use(block) }
        else -> fetcher.stream(url, ua) { input, _ -> block(input) }
    }

    /** Downloads and imports channels (and EPG). Safe to call concurrently; refreshes are serialized per subscription. */
    suspend fun refresh(id: Long, includeEpg: Boolean = true): Result<RefreshSummary> {
        val mutex = synchronized(locks) { locks.getOrPut(id) { Mutex() } }
        return mutex.withLock {
            val sub = subscriptionDao.get(id) ?: return@withLock Result.failure(IllegalStateException("Subscription not found"))
            subscriptionDao.setStatus(id, SyncStatus.SYNCING, null)
            setProgress(id, RefreshProgress.Downloading(0))
            try {
                val secrets = secretsOf(sub)
                val ua = sub.userAgent ?: settings.current().defaultUserAgent
                val generation = System.currentTimeMillis()
                var updated = sub
                val count = when (sub.type) {
                    SubscriptionType.XTREAM -> {
                        val account = fetchAccount(secrets.url, secrets.username, secrets.password, ua)
                        updated = updated.copy(expiresAt = account.expiresAtMillis, maxConnections = account.maxConnections, accountStatus = account.status)
                        importXtream(sub, secrets, ua, generation)
                    }
                    else -> {
                        val (c, epgUrls) = importM3u(sub, secrets, ua, generation)
                        if (epgUrls != secrets.discoveredEpgUrls) {
                            updated = updated.copy(secretBlob = encryptSecrets(secrets.copy(discoveredEpgUrls = epgUrls)))
                        }
                        c
                    }
                }
                channelDao.deleteStale(id, generation)
                updated = updated.copy(status = SyncStatus.OK, lastError = null, lastRefreshAt = System.currentTimeMillis(), channelCount = count)
                subscriptionDao.update(updated)

                var programmes = updated.programmeCount
                var epgError: Throwable? = null
                if (includeEpg) {
                    epgRepository.refresh(updated, secretsOf(updated)) { setProgress(id, RefreshProgress.Epg(it)) }
                        .onSuccess { programmes = it }
                        .onFailure { epgError = it }
                }
                Result.success(RefreshSummary(count, programmes, epgError))
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) {
                    subscriptionDao.setStatus(id, if (sub.lastRefreshAt != null) SyncStatus.OK else SyncStatus.NEVER, null)
                    throw t
                }
                subscriptionDao.setStatus(id, SyncStatus.ERROR, t.message?.take(200) ?: t.javaClass.simpleName)
                Result.failure(t)
            } finally {
                _progress.update { it - id }
            }
        }
    }

    private fun setProgress(id: Long, p: RefreshProgress) = _progress.update { it + (id to p) }

    private suspend fun importM3u(sub: SubscriptionEntity, secrets: SubscriptionSecrets, ua: String, generation: Long): Pair<Int, List<String>> {
        val keys = HashMap<String, Int>()
        val batch = ArrayList<ChannelEntity>(BATCH)
        var index = 0
        val header = openPlaylist(sub.type, secrets.url, ua) { input ->
            M3uParser.parse(InputStreamReader(input, Charsets.UTF_8)) { e ->
                batch += e.toEntity(sub.id, index++, generation, keys)
                if (batch.size >= BATCH) {
                    val copy = ArrayList(batch); batch.clear()
                    runBlocking { channelDao.upsertAll(copy) }
                    setProgress(sub.id, RefreshProgress.Downloading(index))
                }
            }
        }
        if (batch.isNotEmpty()) channelDao.upsertAll(batch)
        return index to header.epgUrls
    }

    private fun M3uEntry.toEntity(subId: Long, index: Int, generation: Long, keys: HashMap<String, Int>): ChannelEntity {
        val group = group ?: UNCATEGORIZED
        val base = stableHash("${TextUtils.normalize(name)}|${group.lowercase()}")
        val n = keys.merge(base, 1, Int::plus) ?: 1
        val key = if (n == 1) base else "$base-$n"
        val headersJson = headers.takeIf { it.isNotEmpty() }?.let { h ->
            json.encodeToString(HEADERS_SERIALIZER, h)
        }
        return ChannelEntity(
            uid = "$subId:m$key",
            subscriptionId = subId,
            name = name,
            normalizedName = TextUtils.normalize(name),
            logo = logo,
            groupTitle = group,
            tvgId = tvgId,
            epgId = tvgId,
            number = attributes["tvg-chno"]?.toIntOrNull(),
            isSports = SportsClassifier.isSports(name, group),
            streamUrl = url,
            xtreamStreamId = null,
            userAgent = userAgent,
            referrer = referrer,
            headersJson = headersJson,
            drmScheme = drmScheme,
            drmLicenseUrl = drmLicenseUrl,
            catchupDays = catchupDays,
            sortIndex = index,
            generation = generation,
        )
    }

    private suspend fun importXtream(sub: SubscriptionEntity, s: SubscriptionSecrets, ua: String, generation: Long): Int {
        val categories = HashMap<String, String>()
        fetcher.stream(xtreamApi(s.url, s.username, s.password, "get_live_categories"), ua) { input, _ ->
            val root = json.parseToJsonElement(input.readBytes().toString(Charsets.UTF_8))
            (root as? JsonArray)?.forEach { el ->
                (el as? JsonObject)?.let(XtreamParsers::parseCategory)?.let { categories[it.id] = it.name }
            }
        }
        var index = 0
        val batch = ArrayList<ChannelEntity>(BATCH)
        fetcher.stream(xtreamApi(s.url, s.username, s.password, "get_live_streams"), ua) { input, _ ->
            // Streaming decode: large panels return 30+ MB of JSON.
            json.decodeToSequence(input, JsonObject.serializer(), DecodeSequenceMode.ARRAY_WRAPPED).forEach { obj ->
                val st = XtreamParsers.parseLiveStream(obj) ?: return@forEach
                val group = st.categoryId?.let { categories[it] } ?: UNCATEGORIZED
                batch += ChannelEntity(
                    uid = "${sub.id}:x${st.streamId}",
                    subscriptionId = sub.id,
                    name = st.name,
                    normalizedName = TextUtils.normalize(st.name),
                    logo = st.icon,
                    groupTitle = group,
                    tvgId = st.epgChannelId,
                    epgId = st.epgChannelId,
                    number = st.num,
                    isSports = SportsClassifier.isSports(st.name, group),
                    streamUrl = null,
                    xtreamStreamId = st.streamId,
                    userAgent = null, referrer = null, headersJson = null, drmScheme = null, drmLicenseUrl = null,
                    catchupDays = if (st.tvArchive) st.tvArchiveDuration else null,
                    sortIndex = index++,
                    generation = generation,
                )
                if (batch.size >= BATCH) {
                    val copy = ArrayList(batch); batch.clear()
                    runBlocking { channelDao.upsertAll(copy) }
                    setProgress(sub.id, RefreshProgress.Downloading(index))
                }
            }
        }
        if (batch.isNotEmpty()) channelDao.upsertAll(batch)
        return index
    }

    /** Adds the bundled, legal demo playlist (public test streams) and imports it. */
    suspend fun addDemo(): Long {
        val existing = subscriptionDao.getAll().firstOrNull { it.type == SubscriptionType.DEMO }
        val id = existing?.id ?: add(SubscriptionInput(name = "Demo", type = SubscriptionType.DEMO, url = DEMO_ASSET, autoRefreshHours = 0))
        refresh(id)
        return id
    }

    companion object {
        const val DEMO_ASSET = "demo/demo_playlist.m3u"
        const val UNCATEGORIZED = "Uncategorized"
        private const val BATCH = 1000
        val HEADERS_SERIALIZER = MapSerializer(String.serializer(), String.serializer())

        /** 64-bit FNV-1a, hex encoded: stable across app versions (unlike String.hashCode semantics on other platforms). */
        fun stableHash(s: String): String {
            var h = -0x340d631b7bdddcdbL
            for (c in s) {
                h = h xor c.code.toLong()
                h *= 0x100000001b3L
            }
            return java.lang.Long.toHexString(h)
        }
    }
}
