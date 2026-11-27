package com.ultimateiptv.sports.player

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.annotation.OptIn
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.ultimateiptv.sports.R
import com.ultimateiptv.sports.data.db.ChannelListItem
import com.ultimateiptv.sports.data.db.EpgProgrammeEntity
import com.ultimateiptv.sports.data.iptv.ChannelContext
import com.ultimateiptv.sports.data.iptv.ChannelRepository
import com.ultimateiptv.sports.data.iptv.EpgRepository
import com.ultimateiptv.sports.data.iptv.PlaybackSource
import com.ultimateiptv.sports.data.settings.AppSettings
import com.ultimateiptv.sports.data.settings.AspectMode
import com.ultimateiptv.sports.data.settings.PlayerChoice
import com.ultimateiptv.sports.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.Locale
import javax.inject.Inject
import kotlin.math.min

data class TrackOption(val type: Int, val groupIndex: Int, val trackIndex: Int, val label: String, val selected: Boolean)

data class PlayerUiState(
    val channel: ChannelListItem? = null,
    val title: String = "",
    val position: Int = 0,
    val total: Int = 0,
    val buffering: Boolean = true,
    val playing: Boolean = false,
    val errorRes: Int? = null,
    val errorDetail: String? = null,
    val retryInSeconds: Int? = null,
    val attempt: Int = 0,
    val nowNext: List<EpgProgrammeEntity> = emptyList(),
    val videoTracks: List<TrackOption> = emptyList(),
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
    val textEnabled: Boolean = true,
    val videoAuto: Boolean = true,
    val resolution: String? = null,
    val aspect: AspectMode = AspectMode.FIT,
    val isFavorite: Boolean = false,
    val zapList: List<ChannelListItem> = emptyList(),
)

@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    app: Application,
    savedState: SavedStateHandle,
    private val channels: ChannelRepository,
    private val epg: EpgRepository,
    private val settingsRepo: SettingsRepository,
    okHttp: OkHttpClient,
) : AndroidViewModel(app) {

    private val factory = PlayerFactory(app, okHttp)
    private val settings: AppSettings = runBlocking { settingsRepo.current() }
    val player: ExoPlayer = factory.create(settings)

    private val context = ChannelContext.decode(savedState.get<String>(PlayerActivity.EXTRA_CONTEXT))
    private var uids: List<String> = emptyList()
    private var index = 0
    private var source: PlaybackSource? = null
    private var attempts: List<StreamAttempt> = emptyList()
    private var attemptIndex = 0
    private var networkRetries = 0
    private var everReady = false

    private var loadJob: Job? = null
    private var retryJob: Job? = null
    private var stallJob: Job? = null
    private var epgJob: Job? = null

    /** "Open in external player" chosen as the default player in settings. */
    val preferExternal: Boolean = settings.playerChoice == PlayerChoice.EXTERNAL_ASK
    private var externalHandled = false
    private val _externalReady = MutableStateFlow(false)
    val externalReady: StateFlow<Boolean> = _externalReady.asStateFlow()

    private val _state = MutableStateFlow(PlayerUiState(aspect = settings.aspectMode))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> { _state.update { it.copy(buffering = true) }; watchStall() }
                Player.STATE_READY -> {
                    stallJob?.cancel()
                    networkRetries = 0
                    if (!everReady) { everReady = true; source?.let { s -> viewModelScope.launch { channels.recordWatched(s.uid) } } }
                    _state.update { it.copy(buffering = false, errorRes = null, errorDetail = null, retryInSeconds = null) }
                }
                Player.STATE_ENDED -> {
                    // A live stream should not end: the provider closed the connection. Reconnect.
                    if (settings.autoReconnect) scheduleRetry(sameAttempt = true) else _state.update { it.copy(buffering = false) }
                }
                Player.STATE_IDLE -> _state.update { it.copy(buffering = false) }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = _state.update { it.copy(playing = isPlaying) }

        override fun onPlayerError(error: PlaybackException) = handleError(error)

        override fun onTracksChanged(tracks: Tracks) = updateTracks(tracks)

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.height > 0) _state.update { it.copy(resolution = "${videoSize.width}×${videoSize.height}") }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Connection is back: retry right away instead of waiting for the backoff.
            viewModelScope.launch { if (_state.value.errorRes != null || retryJob?.isActive == true) retryNow() }
        }
    }

    init {
        player.addListener(listener)
        player.playWhenReady = true
        runCatching { app.getSystemService<ConnectivityManager>()?.registerDefaultNetworkCallback(networkCallback) }
        val startUid = savedState.get<String>(PlayerActivity.EXTRA_UID)
        viewModelScope.launch {
            uids = channels.uidsFor(context).ifEmpty { listOfNotNull(startUid) }
            index = uids.indexOf(startUid).coerceAtLeast(0)
            if (startUid != null && startUid !in uids) { uids = listOf(startUid) + uids; index = 0 }
            _state.update { it.copy(total = uids.size) }
            load(immediate = true)
            _state.update { it.copy(zapList = channels.items(uids.take(2000))) }
        }
    }

    fun next() = zap(+1)
    fun previous() = zap(-1)

    fun playAt(uid: String) {
        val i = uids.indexOf(uid)
        if (i >= 0) { index = i; load(immediate = true) }
    }

    private fun zap(delta: Int) {
        if (uids.isEmpty()) return
        index = (index + delta + uids.size) % uids.size
        load(immediate = false)
    }

    /** Shows the new channel name instantly; the stream itself starts after a short debounce so fast zapping stays smooth. */
    private fun load(immediate: Boolean) {
        loadJob?.cancel(); retryJob?.cancel(); stallJob?.cancel()
        val uid = uids.getOrNull(index) ?: return
        loadJob = viewModelScope.launch {
            val item = channels.items(listOf(uid)).firstOrNull()
            _state.update {
                it.copy(channel = item, title = item?.name.orEmpty(), position = index + 1, isFavorite = item?.isFavorite == true,
                    errorRes = null, errorDetail = null, retryInSeconds = null, buffering = true, nowNext = emptyList(), resolution = null)
            }
            if (!immediate) delay(ZAP_DEBOUNCE_MS)
            val src = channels.playback(uid)
            if (src == null) {
                _state.update { it.copy(buffering = false, errorRes = R.string.player_error_missing) }
                return@launch
            }
            source = src
            attempts = PlayerFactory.attempts(src)
            attemptIndex = 0
            networkRetries = 0
            everReady = false
            factory.headerHolder.headers = factory.headersFor(src, settings.defaultUserAgent)
            if (preferExternal && !externalHandled) {
                externalHandled = true
                _externalReady.value = true
                return@launch
            }
            startAttempt()
            observeEpg(src)
        }
    }

    private fun startAttempt() {
        val src = source ?: return
        val attempt = attempts.getOrNull(attemptIndex) ?: return
        player.setMediaItem(PlayerFactory.mediaItem(src, attempt))
        player.prepare()
        player.play()
        _state.update { it.copy(attempt = attemptIndex) }
    }

    private fun observeEpg(src: PlaybackSource) {
        epgJob?.cancel()
        val epgId = src.epgId ?: return
        epgJob = viewModelScope.launch {
            while (isActive) {
                val list = epg.nowNext(src.subscriptionId, epgId)
                _state.update { it.copy(nowNext = list) }
                val now = System.currentTimeMillis()
                val wait = list.firstOrNull()?.stopMillis?.minus(now)?.coerceIn(5_000, 60_000) ?: 60_000
                delay(wait)
            }
        }
    }

    private fun handleError(error: PlaybackException) {
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        val http = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        val formatProblem = error.errorCode in PARSING_ERRORS ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && http == 404 ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
        val fatal = error.errorCode in DRM_ERRORS || http == 401 || http == 403

        if (formatProblem && attemptIndex + 1 < attempts.size) {
            attemptIndex++
            startAttempt()
            return
        }
        val res = when {
            error.errorCode in DRM_ERRORS -> R.string.player_error_drm
            http == 401 || http == 403 -> R.string.player_error_forbidden
            http == 404 -> R.string.player_error_not_found
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> R.string.player_error_network
            error.errorCode in PARSING_ERRORS || error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> R.string.player_error_format
            else -> R.string.player_error_generic
        }
        _state.update { it.copy(errorRes = res, errorDetail = error.errorCodeName + (http?.let { c -> " · HTTP $c" } ?: ""), buffering = false) }
        if (settings.autoReconnect && !fatal) scheduleRetry(sameAttempt = !formatProblem)
    }

    private fun scheduleRetry(sameAttempt: Boolean) {
        if (networkRetries >= MAX_RETRIES) return
        retryJob?.cancel()
        val delaySec = min(1 shl networkRetries, 15)
        networkRetries++
        retryJob = viewModelScope.launch {
            for (s in delaySec downTo 1) { _state.update { it.copy(retryInSeconds = s) }; delay(1000) }
            _state.update { it.copy(retryInSeconds = null) }
            if (!sameAttempt) attemptIndex = 0
            startAttempt()
        }
    }

    fun retryNow() {
        retryJob?.cancel()
        networkRetries = 0
        attemptIndex = 0
        _state.update { it.copy(errorRes = null, retryInSeconds = null, buffering = true) }
        if (source == null) load(immediate = true) else startAttempt()
    }

    /** Live streams sometimes freeze without an error: re-prepare if buffering lasts too long. */
    private fun watchStall() {
        stallJob?.cancel()
        stallJob = viewModelScope.launch {
            delay(STALL_TIMEOUT_MS)
            if (player.playbackState == Player.STATE_BUFFERING && settings.autoReconnect) {
                if (player.isCurrentMediaItemLive) player.seekToDefaultPosition()
                player.prepare()
            }
        }
    }

    fun toggleFavorite() {
        val uid = source?.uid ?: uids.getOrNull(index) ?: return
        viewModelScope.launch {
            val fav = channels.toggleFavorite(uid)
            _state.update { it.copy(isFavorite = fav) }
        }
    }

    fun cycleAspect() {
        val next = AspectMode.entries[(_state.value.aspect.ordinal + 1) % AspectMode.entries.size]
        _state.update { it.copy(aspect = next) }
        viewModelScope.launch { settingsRepo.update { s -> s.copy(aspectMode = next) } }
    }

    fun currentSource(): PlaybackSource? = source
    fun currentAttemptUrl(): String? = attempts.getOrNull(attemptIndex)?.url
    fun currentHeaders(): Map<String, String> = factory.headerHolder.headers

    // ---- Tracks ----
    private fun updateTracks(tracks: Tracks) {
        val video = ArrayList<TrackOption>()
        val audio = ArrayList<TrackOption>()
        val text = ArrayList<TrackOption>()
        tracks.groups.forEachIndexed { gi, group ->
            for (ti in 0 until group.length) {
                if (!group.isTrackSupported(ti)) continue
                val f = group.getTrackFormat(ti)
                val opt = TrackOption(group.type, gi, ti, label(group.type, f), group.isTrackSelected(ti))
                when (group.type) {
                    C.TRACK_TYPE_VIDEO -> video += opt
                    C.TRACK_TYPE_AUDIO -> audio += opt
                    C.TRACK_TYPE_TEXT -> text += opt
                }
            }
        }
        val params = player.trackSelectionParameters
        _state.update {
            it.copy(
                videoTracks = video.sortedByDescending { o -> tracks.groups[o.groupIndex].getTrackFormat(o.trackIndex).height },
                audioTracks = audio, textTracks = text,
                textEnabled = C.TRACK_TYPE_TEXT !in params.disabledTrackTypes,
                videoAuto = params.overrides.keys.none { k -> k.type == C.TRACK_TYPE_VIDEO },
            )
        }
    }

    private fun label(type: Int, f: Format): String {
        val lang = f.language?.takeIf { it != "und" }?.let { Locale(it).displayLanguage }
        return when (type) {
            C.TRACK_TYPE_VIDEO -> buildString {
                append(if (f.height > 0) "${f.height}p" else "?")
                if (f.frameRate > 0) append(" · ${f.frameRate.toInt()}fps")
                if (f.bitrate > 0) append(" · %.1f Mbps".format(Locale.US, f.bitrate / 1_000_000f))
            }
            C.TRACK_TYPE_AUDIO -> listOfNotNull(f.label ?: lang, f.channelCount.takeIf { c -> c > 0 }?.let { c -> if (c >= 6) "5.1" else if (c == 2) "Stereo" else "${c}ch" }, f.sampleMimeType?.substringAfter('/')?.uppercase())
                .joinToString(" · ").ifEmpty { "Audio" }
            else -> f.label ?: lang ?: "Subtitle"
        }
    }

    fun selectTrack(option: TrackOption) {
        val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(option.type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .build()
    }

    fun autoVideo() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .apply { if (!enabled) clearOverridesOfType(C.TRACK_TYPE_TEXT) }
            .build()
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().getSystemService<ConnectivityManager>()?.unregisterNetworkCallback(networkCallback) }
        player.removeListener(listener)
        player.release()
    }

    companion object {
        private const val ZAP_DEBOUNCE_MS = 350L
        private const val STALL_TIMEOUT_MS = 15_000L
        private const val MAX_RETRIES = 12
        private val PARSING_ERRORS = setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        )
        private val DRM_ERRORS = setOf(
            PlaybackException.ERROR_CODE_DRM_UNSPECIFIED, PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED, PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED, PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION,
            PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR, PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
            PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED,
        )
    }
}
