package com.ultimateiptv.sports.ui.screens.matches

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimateiptv.sports.R
import com.ultimateiptv.sports.core.network.AuthException
import com.ultimateiptv.sports.core.network.ErrorMapper
import com.ultimateiptv.sports.data.db.MatchEntity
import com.ultimateiptv.sports.data.db.MatchStatus
import com.ultimateiptv.sports.data.settings.SettingsRepository
import com.ultimateiptv.sports.data.settings.SportsProviderId
import com.ultimateiptv.sports.data.sports.ApiQuotaException
import com.ultimateiptv.sports.data.sports.Competitions
import com.ultimateiptv.sports.data.sports.MatchRepository
import com.ultimateiptv.sports.data.sports.isLive
import com.ultimateiptv.sports.notifications.ReminderScheduler
import com.ultimateiptv.sports.ui.components.ChannelLogo
import com.ultimateiptv.sports.ui.components.EmptyState
import com.ultimateiptv.sports.ui.components.ScreenHeader
import com.ultimateiptv.sports.ui.components.tvFocusBorder
import com.ultimateiptv.sports.ui.navigation.AppActions
import com.ultimateiptv.sports.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class StatusFilter { ALL, LIVE, UPCOMING, FINISHED }

data class MatchFilters(
    val date: LocalDate = LocalDate.now(),
    val status: StatusFilter = StatusFilter.ALL,
    val competitions: Set<String> = emptySet(),
    val countries: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
)

data class CompetitionGroup(val key: String, val name: String, val logo: String?, val country: String?, val matches: List<MatchEntity>)

data class MatchesUi(
    val groups: List<CompetitionGroup> = emptyList(),
    val allCompetitions: List<Pair<String, String>> = emptyList(),
    val allCountries: List<String> = emptyList(),
    val reminders: Set<String> = emptySet(),
    val total: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MatchesViewModel @Inject constructor(
    private val repo: MatchRepository,
    private val scheduler: ReminderScheduler,
    settings: SettingsRepository,
) : ViewModel() {
    val filters = MutableStateFlow(MatchFilters())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<Throwable?>(null)
    val provider: StateFlow<SportsProviderId> = settings.settings.map { it.sportsProvider }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SportsProviderId.DEMO)

    private val dayMatches = filters.map { it.date }.flatMapLatest { repo.observeDay(it) }

    val ui: StateFlow<MatchesUi> = combine(dayMatches, filters, repo.reminders(), repo.favoriteTeams()) { list, f, rem, favTeams ->
        val now = System.currentTimeMillis()
        val favKeys = favTeams.map { it.key }.toSet()
        val filtered = list.filter { m ->
            when (f.status) {
                StatusFilter.ALL -> true
                StatusFilter.LIVE -> m.isLive()
                StatusFilter.UPCOMING -> m.status == MatchStatus.SCHEDULED && m.kickoff > now - 60_000
                StatusFilter.FINISHED -> m.status == MatchStatus.FINISHED
            } && (f.competitions.isEmpty() || m.competitionKey in f.competitions) &&
                (f.countries.isEmpty() || m.country in f.countries) &&
                (!f.favoritesOnly || MatchRepository.teamKey(m.homeName) in favKeys || MatchRepository.teamKey(m.awayName) in favKeys)
        }
        val catalogOrder = Competitions.all.withIndex().associate { it.value.key to it.index }
        val groups = filtered.groupBy { it.competitionKey }.map { (key, ms) ->
            CompetitionGroup(key, competitionLabel(ms.first()), ms.first().competitionLogo, ms.first().country, ms.sortedBy { it.kickoff })
        }.sortedWith(compareBy<CompetitionGroup> { catalogOrder[it.key] ?: Int.MAX_VALUE }.thenBy { it.name })
        MatchesUi(
            groups = groups,
            allCompetitions = list.distinctBy { it.competitionKey }.map { it.competitionKey to competitionLabel(it) }
                .sortedWith(compareBy<Pair<String, String>> { catalogOrder[it.first] ?: Int.MAX_VALUE }.thenBy { it.second }),
            allCountries = list.mapNotNull { it.country }.distinct().sorted(),
            reminders = rem.map { it.matchId }.toSet(),
            total = list.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatchesUi())

    private var pollJob: Job? = null

    init {
        viewModelScope.launch { filters.map { it.date }.collect { refresh(force = false) } }
        viewModelScope.launch { provider.collect { refresh(force = false) } }
    }

    /** Polls while the screen is visible; the repository TTL decides whether a network call is made. */
    fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                if (filters.value.date == LocalDate.now()) refresh(force = false, silent = true)
            }
        }
    }

    fun stopPolling() { pollJob?.cancel() }

    fun refresh(force: Boolean, silent: Boolean = false) = viewModelScope.launch {
        if (!silent) loading.value = true
        repo.refreshDay(filters.value.date, force)
            .onSuccess { error.value = null }
            .onFailure { error.value = it }
        loading.value = false
    }

    fun setDate(d: LocalDate) { filters.value = filters.value.copy(date = d) }
    fun setStatus(s: StatusFilter) { filters.value = filters.value.copy(status = s) }
    fun setCompetitions(s: Set<String>) { filters.value = filters.value.copy(competitions = s) }
    fun setCountries(s: Set<String>) { filters.value = filters.value.copy(countries = s) }
    fun setFavoritesOnly(b: Boolean) { filters.value = filters.value.copy(favoritesOnly = b) }
    fun toggleReminder(m: MatchEntity) = viewModelScope.launch { scheduler.toggle(m) }
}

@Composable
fun MatchesScreen(actions: AppActions, vm: MatchesViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    val f by vm.filters.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val provider by vm.provider.collectAsState()
    var filterDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        vm.startPolling()
        onDispose { vm.stopPolling() }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.nav_matches), subtitle = providerLabel(provider)) {
            IconButton(onClick = { filterDialog = true }, modifier = Modifier.tvFocusBorder()) { Icon(Icons.Filled.FilterList, stringResource(R.string.filters)) }
            IconButton(onClick = { vm.refresh(force = true) }, modifier = Modifier.tvFocusBorder()) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        val today = LocalDate.now()
        val days = (-1L..5L).map { today.plusDays(it) }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days, key = { it.toEpochDay() }) { d ->
                FilterChip(selected = d == f.date, onClick = { vm.setDate(d) }, label = { Text(dayLabel(d, today)) }, modifier = Modifier.tvFocusBorder())
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(StatusFilter.entries) { s ->
                FilterChip(selected = f.status == s, onClick = { vm.setStatus(s) }, label = { Text(stringResource(statusLabel(s))) }, modifier = Modifier.tvFocusBorder())
            }
            item {
                FilterChip(selected = f.favoritesOnly, onClick = { vm.setFavoritesOnly(!f.favoritesOnly) }, label = { Text(stringResource(R.string.favorite_teams)) }, modifier = Modifier.tvFocusBorder())
            }
        }

        if (provider == SportsProviderId.DEMO) InfoBanner(stringResource(R.string.matches_demo_banner), stringResource(R.string.configure)) { actions.open(Routes.SETTINGS) }
        error?.let { e ->
            val msg = when (e) {
                is AuthException -> stringResource(R.string.matches_error_key)
                is ApiQuotaException -> stringResource(R.string.matches_error_quota)
                else -> stringResource(ErrorMapper.messageRes(e))
            }
            InfoBanner(msg, stringResource(R.string.configure)) { actions.open(Routes.SETTINGS) }
        }

        if (ui.groups.isEmpty() && !loading) {
            EmptyState(Icons.Filled.SportsSoccer, stringResource(R.string.matches_empty_title), stringResource(R.string.matches_empty_message))
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
            ui.groups.forEach { g ->
                item(key = "g-${g.key}") {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChannelLogo(g.logo, g.name, size = 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.titleSmall)
                            g.country?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        TextButton(onClick = { actions.open(Routes.tournament(g.key)) }, modifier = Modifier.tvFocusBorder()) { Text(stringResource(R.string.see_all)) }
                    }
                }
                items(g.matches, key = { it.id }) { m ->
                    MatchCard(
                        m, hasReminder = m.id in ui.reminders,
                        onClick = { actions.open(Routes.match(m.id)) },
                        onReminder = { vm.toggleReminder(m) },
                        isDemo = provider == SportsProviderId.DEMO,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }

    if (filterDialog) FilterDialog(ui, f, vm, onDismiss = { filterDialog = false })
}

@Composable
fun providerLabel(p: SportsProviderId): String = stringResource(
    when (p) {
        SportsProviderId.DEMO -> R.string.provider_demo
        SportsProviderId.API_FOOTBALL -> R.string.provider_api_football
        SportsProviderId.FOOTBALL_DATA -> R.string.provider_football_data
    }
)

@Composable
fun InfoBanner(text: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSecondaryContainer)
        action?.let { TextButton(onClick = onAction, modifier = Modifier.tvFocusBorder()) { Text(it) } }
    }
}

@Composable
private fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> stringResource(R.string.today)
    today.minusDays(1) -> stringResource(R.string.yesterday)
    today.plusDays(1) -> stringResource(R.string.tomorrow)
    else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM"))
}

private fun statusLabel(s: StatusFilter) = when (s) {
    StatusFilter.ALL -> R.string.filter_all
    StatusFilter.LIVE -> R.string.live
    StatusFilter.UPCOMING -> R.string.filter_upcoming
    StatusFilter.FINISHED -> R.string.match_finished
}

@Composable
private fun FilterDialog(ui: MatchesUi, f: MatchFilters, vm: MatchesViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = { vm.setCompetitions(emptySet()); vm.setCountries(emptySet()) }) { Text(stringResource(R.string.reset)) } },
        title = { Text(stringResource(R.string.filters)) },
        text = {
            LazyColumn {
                item { Text(stringResource(R.string.nav_tournaments), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                items(ui.allCompetitions, key = { "c" + it.first }) { (key, name) ->
                    CheckRow(name, key in f.competitions) { vm.setCompetitions(if (key in f.competitions) f.competitions - key else f.competitions + key) }
                }
                if (ui.allCountries.isNotEmpty()) {
                    item { Text(stringResource(R.string.country), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp)) }
                    items(ui.allCountries, key = { "k$it" }) { c ->
                        CheckRow(c, c in f.countries) { vm.setCountries(if (c in f.countries) f.countries - c else f.countries + c) }
                    }
                }
            }
        },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().tvFocusBorder(RoundedCornerShape(8.dp)), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(label)
    }
}

