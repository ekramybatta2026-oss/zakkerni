package com.ultimateiptv.sports.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ultimateiptv.sports.core.security.CredentialCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class BufferProfile { LOW_LATENCY, BALANCED, STABLE }
enum class AspectMode { FIT, FILL, ZOOM }
enum class PlayerChoice { INTERNAL, EXTERNAL_ASK }
enum class SportsProviderId { DEMO, API_FOOTBALL, FOOTBALL_DATA }

data class AppSettings(
    val bufferProfile: BufferProfile = BufferProfile.BALANCED,
    val aspectMode: AspectMode = AspectMode.FIT,
    val playerChoice: PlayerChoice = PlayerChoice.INTERNAL,
    val decoderFallback: Boolean = true,
    val autoReconnect: Boolean = true,
    val defaultUserAgent: String = DEFAULT_USER_AGENT,
    val epgRefreshHours: Int = 12,
    val use24h: Boolean = true,
    val sportsProvider: SportsProviderId = SportsProviderId.DEMO,
    val hasSportsApiKey: Boolean = false,
    val showAllCompetitions: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val reminderMinutes: Int = 15,
    val notifyFavoriteTeams: Boolean = true,
    val notifyKickoff: Boolean = true,
    val lastSubscriptionId: Long = 0,
    val onboardingDone: Boolean = false,
) {
    companion object {
        const val DEFAULT_USER_AGENT = "UltimateIPTV/1.0 (Linux; Android) ExoPlayer/Media3"
    }
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cipher: CredentialCipher,
) {
    private object K {
        val buffer = stringPreferencesKey("buffer_profile")
        val aspect = stringPreferencesKey("aspect")
        val player = stringPreferencesKey("player_choice")
        val decoderFallback = booleanPreferencesKey("decoder_fallback")
        val reconnect = booleanPreferencesKey("auto_reconnect")
        val ua = stringPreferencesKey("default_ua")
        val epgHours = intPreferencesKey("epg_hours")
        val use24h = booleanPreferencesKey("use_24h")
        val provider = stringPreferencesKey("sports_provider")
        val apiKeyApiFootball = stringPreferencesKey("key_api_football")
        val apiKeyFootballData = stringPreferencesKey("key_football_data")
        val showAll = booleanPreferencesKey("show_all_comp")
        val notif = booleanPreferencesKey("notifications")
        val reminderMin = intPreferencesKey("reminder_min")
        val notifFavTeams = booleanPreferencesKey("notify_fav_teams")
        val notifKickoff = booleanPreferencesKey("notify_kickoff")
        val lastSub = stringPreferencesKey("last_sub")
        val onboarding = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { settingsFrom(it) }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val cur = settingsFrom(p)
            val n = transform(cur)
            p[K.buffer] = n.bufferProfile.name
            p[K.aspect] = n.aspectMode.name
            p[K.player] = n.playerChoice.name
            p[K.decoderFallback] = n.decoderFallback
            p[K.reconnect] = n.autoReconnect
            p[K.ua] = n.defaultUserAgent
            p[K.epgHours] = n.epgRefreshHours
            p[K.use24h] = n.use24h
            p[K.provider] = n.sportsProvider.name
            p[K.showAll] = n.showAllCompetitions
            p[K.notif] = n.notificationsEnabled
            p[K.reminderMin] = n.reminderMinutes
            p[K.notifFavTeams] = n.notifyFavoriteTeams
            p[K.notifKickoff] = n.notifyKickoff
            p[K.lastSub] = n.lastSubscriptionId.toString()
            p[K.onboarding] = n.onboardingDone
        }
    }

    private fun settingsFrom(p: Preferences): AppSettings {
        val provider = p[K.provider]?.let { runCatching { SportsProviderId.valueOf(it) }.getOrNull() } ?: SportsProviderId.DEMO
        return AppSettings(
            bufferProfile = p[K.buffer]?.let { runCatching { BufferProfile.valueOf(it) }.getOrNull() } ?: BufferProfile.BALANCED,
            aspectMode = p[K.aspect]?.let { runCatching { AspectMode.valueOf(it) }.getOrNull() } ?: AspectMode.FIT,
            playerChoice = p[K.player]?.let { runCatching { PlayerChoice.valueOf(it) }.getOrNull() } ?: PlayerChoice.INTERNAL,
            decoderFallback = p[K.decoderFallback] ?: true,
            autoReconnect = p[K.reconnect] ?: true,
            defaultUserAgent = p[K.ua]?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_USER_AGENT,
            epgRefreshHours = p[K.epgHours] ?: 12,
            use24h = p[K.use24h] ?: true,
            sportsProvider = provider,
            hasSportsApiKey = when (provider) {
                SportsProviderId.API_FOOTBALL -> p[K.apiKeyApiFootball] != null
                SportsProviderId.FOOTBALL_DATA -> p[K.apiKeyFootballData] != null
                SportsProviderId.DEMO -> true
            },
            showAllCompetitions = p[K.showAll] ?: true,
            notificationsEnabled = p[K.notif] ?: true,
            reminderMinutes = p[K.reminderMin] ?: 15,
            notifyFavoriteTeams = p[K.notifFavTeams] ?: true,
            notifyKickoff = p[K.notifKickoff] ?: true,
            lastSubscriptionId = p[K.lastSub]?.toLongOrNull() ?: 0,
            onboardingDone = p[K.onboarding] ?: false,
        )
    }

    /** API keys are encrypted with the Keystore before being written to DataStore. */
    suspend fun setSportsApiKey(provider: SportsProviderId, key: String?) {
        val prefKey = when (provider) {
            SportsProviderId.API_FOOTBALL -> K.apiKeyApiFootball
            SportsProviderId.FOOTBALL_DATA -> K.apiKeyFootballData
            SportsProviderId.DEMO -> return
        }
        context.dataStore.edit { p ->
            if (key.isNullOrBlank()) p.remove(prefKey) else p[prefKey] = cipher.encrypt(key.trim())
        }
    }

    suspend fun sportsApiKey(provider: SportsProviderId): String? {
        val prefKey = when (provider) {
            SportsProviderId.API_FOOTBALL -> K.apiKeyApiFootball
            SportsProviderId.FOOTBALL_DATA -> K.apiKeyFootballData
            SportsProviderId.DEMO -> return null
        }
        val blob = context.dataStore.data.first()[prefKey] ?: return null
        return runCatching { cipher.decrypt(blob) }.getOrNull()
    }
}
