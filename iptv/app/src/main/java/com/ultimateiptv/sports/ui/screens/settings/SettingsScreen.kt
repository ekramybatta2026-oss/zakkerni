package com.ultimateiptv.sports.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimateiptv.sports.BuildConfig
import com.ultimateiptv.sports.R
import com.ultimateiptv.sports.core.network.ErrorMapper
import com.ultimateiptv.sports.data.db.SubscriptionDao
import com.ultimateiptv.sports.data.settings.AppSettings
import com.ultimateiptv.sports.data.settings.BufferProfile
import com.ultimateiptv.sports.data.settings.PlayerChoice
import com.ultimateiptv.sports.data.settings.SettingsRepository
import com.ultimateiptv.sports.data.settings.SportsProviderId
import com.ultimateiptv.sports.data.sports.MatchRepository
import com.ultimateiptv.sports.notifications.ReminderScheduler
import com.ultimateiptv.sports.ui.components.ScreenHeader
import com.ultimateiptv.sports.ui.components.SectionHeader
import com.ultimateiptv.sports.ui.components.tvFocusBorder
import com.ultimateiptv.sports.ui.navigation.AppActions
import com.ultimateiptv.sports.ui.navigation.Routes
import com.ultimateiptv.sports.ui.screens.matches.providerLabel
import com.ultimateiptv.sports.ui.theme.LiveRed
import com.ultimateiptv.sports.ui.theme.Pitch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val matches: MatchRepository,
    private val scheduler: ReminderScheduler,
    subscriptionDao: SubscriptionDao,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val subscriptionCount = subscriptionDao.observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val keyTest = MutableStateFlow<Result<Unit>?>(null)
    val testingKey = MutableStateFlow(false)

    fun update(f: (AppSettings) -> AppSettings) = viewModelScope.launch { repo.update(f) }

    fun setProvider(p: SportsProviderId) = viewModelScope.launch { repo.update { it.copy(sportsProvider = p) }; keyTest.value = null }

    fun saveKey(p: SportsProviderId, key: String) = viewModelScope.launch {
        repo.setSportsApiKey(p, key)
        testingKey.value = true
        // Verify with a real request for today's fixtures.
        keyTest.value = matches.refreshDay(LocalDate.now(), force = true)
        testingKey.value = false
    }

    fun removeKey(p: SportsProviderId) = viewModelScope.launch { repo.setSportsApiKey(p, null); keyTest.value = null }

    fun setKickoff(enabled: Boolean) = viewModelScope.launch {
        repo.update { it.copy(notifyKickoff = enabled) }
        scheduler.rescheduleAll()
    }
}

@Composable
fun SettingsScreen(actions: AppActions, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsState()
    val subCount by vm.subscriptionCount.collectAsState()
    val keyTest by vm.keyTest.collectAsState()
    val testingKey by vm.testingKey.collectAsState()
    val context = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.update { it.copy(notificationsEnabled = granted) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.nav_settings))
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionHeader(stringResource(R.string.settings_general)) }
            item {
                NavRow(stringResource(R.string.subscriptions), stringResource(R.string.channels_subscriptions_count, subCount)) { actions.open(Routes.SUBSCRIPTIONS) }
            }
            item {
                val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                Choice(stringResource(R.string.language), listOf("" to stringResource(R.string.lang_system), "ar" to "العربية", "en" to "English"), current.substringBefore('-')) {
                    AppCompatDelegate.setApplicationLocales(if (it.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(it))
                }
            }
            item { SwitchRow(stringResource(R.string.time_24h), null, s.use24h) { v -> vm.update { it.copy(use24h = v) } } }

            item { SectionHeader(stringResource(R.string.settings_player)) }
            item {
                Choice(stringResource(R.string.buffer_profile), listOf(
                    BufferProfile.LOW_LATENCY.name to stringResource(R.string.buffer_low),
                    BufferProfile.BALANCED.name to stringResource(R.string.buffer_balanced),
                    BufferProfile.STABLE.name to stringResource(R.string.buffer_stable),
                ), s.bufferProfile.name, stringResource(R.string.buffer_hint)) { v -> vm.update { it.copy(bufferProfile = BufferProfile.valueOf(v)) } }
            }
            item {
                Choice(stringResource(R.string.default_player), listOf(
                    PlayerChoice.INTERNAL.name to stringResource(R.string.player_internal),
                    PlayerChoice.EXTERNAL_ASK.name to stringResource(R.string.player_external),
                ), s.playerChoice.name) { v -> vm.update { it.copy(playerChoice = PlayerChoice.valueOf(v)) } }
            }
            item { SwitchRow(stringResource(R.string.auto_reconnect), stringResource(R.string.auto_reconnect_hint), s.autoReconnect) { v -> vm.update { it.copy(autoReconnect = v) } } }
            item { SwitchRow(stringResource(R.string.decoder_fallback), stringResource(R.string.decoder_fallback_hint), s.decoderFallback) { v -> vm.update { it.copy(decoderFallback = v) } } }
            item {
                var ua by remember(s.defaultUserAgent) { mutableStateOf(s.defaultUserAgent) }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    OutlinedTextField(ua, { ua = it }, label = { Text(stringResource(R.string.field_user_agent)) }, singleLine = true, modifier = Modifier.fillMaxWidth().tvFocusBorder())
                    Row {
                        TextButton(onClick = { vm.update { it.copy(defaultUserAgent = ua.trim()) } }, modifier = Modifier.tvFocusBorder()) { Text(stringResource(R.string.save)) }
                        TextButton(onClick = { ua = AppSettings.DEFAULT_USER_AGENT; vm.update { it.copy(defaultUserAgent = AppSettings.DEFAULT_USER_AGENT) } }, modifier = Modifier.tvFocusBorder()) { Text(stringResource(R.string.reset)) }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_epg)) }
            item {
                Choice(stringResource(R.string.epg_refresh), listOf(0, 6, 12, 24).map { it.toString() to if (it == 0) stringResource(R.string.off) else stringResource(R.string.hours, it) },
                    s.epgRefreshHours.toString()) { v -> vm.update { it.copy(epgRefreshHours = v.toInt()) } }
            }

            item { SectionHeader(stringResource(R.string.settings_sports)) }
            item {
                Choice(stringResource(R.string.sports_provider), SportsProviderId.entries.map { it.name to providerLabel(it) }, s.sportsProvider.name) { v -> vm.setProvider(SportsProviderId.valueOf(v)) }
            }
            if (s.sportsProvider != SportsProviderId.DEMO) item {
                var key by remember(s.sportsProvider) { mutableStateOf("") }
                val help = when (s.sportsProvider) {
                    SportsProviderId.API_FOOTBALL -> stringResource(R.string.api_football_help)
                    else -> stringResource(R.string.football_data_help)
                }
                val url = if (s.sportsProvider == SportsProviderId.API_FOOTBALL) "https://dashboard.api-football.com/register" else "https://www.football-data.org/client/register"
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }, modifier = Modifier.tvFocusBorder()) { Text(stringResource(R.string.get_api_key)) }
                    OutlinedTextField(
                        key, { key = it }, singleLine = true,
                        label = { Text(stringResource(if (s.hasSportsApiKey) R.string.api_key_saved else R.string.api_key)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().tvFocusBorder(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { vm.saveKey(s.sportsProvider, key); key = "" }, enabled = key.isNotBlank() && !testingKey, modifier = Modifier.tvFocusBorder()) { Text(stringResource(R.string.save_and_test)) }
                        if (s.hasSportsApiKey) OutlinedButton(onClick = { vm.removeKey(s.sportsProvider) }, modifier = Modifier.tvFocusBorder()) { Text(stringResource(R.string.remove)) }
                        if (testingKey) CircularProgressIndicator(Modifier.width(20.dp))
                    }
                    keyTest?.let { r ->
                        if (r.isSuccess) Text(stringResource(R.string.api_key_ok), color = Pitch)
                        else Text(stringResource(ErrorMapper.messageRes(r.exceptionOrNull()!!)) + " (${r.exceptionOrNull()?.message?.take(120)})", color = LiveRed)
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_notifications)) }
            item {
                SwitchRow(stringResource(R.string.notifications_enable), null, s.notificationsEnabled) { v ->
                    if (v && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else vm.update { it.copy(notificationsEnabled = v) }
                }
            }
            item {
                Choice(stringResource(R.string.reminder_before), listOf(5, 10, 15, 30, 60).map { it.toString() to stringResource(R.string.minutes, it) },
                    s.reminderMinutes.toString()) { v -> vm.update { it.copy(reminderMinutes = v.toInt()) } }
            }
            item { SwitchRow(stringResource(R.string.notify_kickoff), null, s.notifyKickoff) { vm.setKickoff(it) } }
            item { SwitchRow(stringResource(R.string.notify_fav_teams), stringResource(R.string.notify_fav_teams_hint), s.notifyFavoriteTeams) { v -> vm.update { it.copy(notifyFavoriteTeams = v) } } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) item {
                NavRow(stringResource(R.string.exact_alarms), stringResource(R.string.exact_alarms_hint)) {
                    runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_about)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.padding(2.dp))
                    Text(stringResource(R.string.legal_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).tvFocusBorder(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).tvFocusBorder(RoundedCornerShape(10.dp)).clickable { onChange(!checked) }.padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Choice(title: String, options: List<Pair<String, String>>, selected: String, hint: String? = null, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)) }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options, key = { it.first }) { (value, label) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) }, modifier = Modifier.tvFocusBorder())
            }
        }
    }
}
