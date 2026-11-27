package com.ultimateiptv.sports.player

import android.app.ActivityManager
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.getSystemService
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.ultimateiptv.sports.data.iptv.PlaybackSource
import com.ultimateiptv.sports.data.settings.AppSettings
import com.ultimateiptv.sports.data.settings.BufferProfile
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.UUID

/** A concrete attempt: one URL with one container hint. The player cycles through these on failures. */
data class StreamAttempt(val url: String, val mimeType: String?)

/** Holds request headers for the stream that is currently playing (read by the data source on every request). */
class HeaderHolder {
    @Volatile var headers: Map<String, String> = emptyMap()
}

@OptIn(UnstableApi::class)
class PlayerFactory(private val context: Context, private val okHttp: OkHttpClient) {

    val headerHolder = HeaderHolder()

    fun create(settings: AppSettings): ExoPlayer {
        val lowRam = context.getSystemService<ActivityManager>()?.isLowRamDevice == true
        val (minBuf, maxBuf, startBuf, rebuf) = when (settings.bufferProfile) {
            BufferProfile.LOW_LATENCY -> listOf(2_000, 12_000, 800, 1_500)
            BufferProfile.BALANCED -> listOf(8_000, 30_000, 1_500, 3_000)
            BufferProfile.STABLE -> listOf(20_000, 60_000, 2_500, 5_000)
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuf, if (lowRam) minOf(maxBuf, 20_000) else maxBuf, startBuf, rebuf)
            .setPrioritizeTimeOverSizeThresholds(!lowRam)
            .build()

        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(settings.decoderFallback)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        val http = OkHttpDataSource.Factory(okHttp)
        val resolving = ResolvingDataSource.Factory(http) { spec ->
            val h = headerHolder.headers
            if (h.isEmpty()) spec else spec.withAdditionalHeaders(h)
        }
        val dataSourceFactory = DefaultDataSource.Factory(context, resolving)

        val drmProvider = DefaultDrmSessionManagerProvider().apply { setDrmHttpDataSourceFactory(http) }
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))
            .setDrmSessionManagerProvider(drmProvider)

        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setPreferredAudioLanguage(Locale.getDefault().language)
                .apply { if (lowRam) setMaxVideoSize(1920, 1080) }
                .build()
        }

        return ExoPlayer.Builder(context, renderers)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    /** Headers for a source: user agent, referrer and any custom provider headers. */
    fun headersFor(source: PlaybackSource, defaultUa: String): Map<String, String> = buildMap {
        putAll(source.headers)
        put("User-Agent", source.userAgent ?: defaultUa)
        source.referrer?.let { put("Referer", it) }
    }

    companion object {
        /** Builds the ordered list of attempts: guessed container first, then explicit HLS/TS hints. */
        fun attempts(source: PlaybackSource): List<StreamAttempt> {
            val out = ArrayList<StreamAttempt>()
            source.urls.forEach { url ->
                val guess = guessMime(url)
                out += StreamAttempt(url, guess)
                if (guess == null) {
                    out += StreamAttempt(url, MimeTypes.APPLICATION_M3U8)
                    out += StreamAttempt(url, MimeTypes.VIDEO_MP2T)
                }
            }
            return out.distinct()
        }

        fun guessMime(url: String): String? {
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            val full = url.lowercase()
            return when {
                path.endsWith(".m3u8") || path.endsWith(".m3u") || full.contains("format=m3u8") || full.contains("/hls/") && !path.contains('.') -> MimeTypes.APPLICATION_M3U8
                path.endsWith(".mpd") || full.contains("format=mpd") -> MimeTypes.APPLICATION_MPD
                path.endsWith(".ism") || path.endsWith("/manifest") -> MimeTypes.APPLICATION_SS
                path.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
                path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".webm") -> null
                else -> null
            }
        }

        fun mediaItem(source: PlaybackSource, attempt: StreamAttempt): MediaItem {
            val builder = MediaItem.Builder()
                .setUri(attempt.url)
                .setMediaId(source.uid)
                .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setMaxPlaybackSpeed(1.04f).build())
            attempt.mimeType?.let { if (it != MimeTypes.VIDEO_MP2T) builder.setMimeType(it) }
            drmUuid(source.drmScheme)?.let { uuid ->
                // DRM only through the provider's official licence server.
                source.drmLicenseUrl?.let { license ->
                    builder.setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(uuid).setLicenseUri(license).setMultiSession(true).build()
                    )
                }
            }
            return builder.build()
        }

        private fun drmUuid(scheme: String?): UUID? = when (scheme) {
            "widevine" -> C.WIDEVINE_UUID
            "playready" -> C.PLAYREADY_UUID
            "clearkey" -> C.CLEARKEY_UUID
            else -> null
        }
    }
}
