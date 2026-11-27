package com.ultimateiptv.sports.ui.screens.tournaments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimateiptv.sports.R
import com.ultimateiptv.sports.data.db.ChannelListItem
import com.ultimateiptv.sports.data.db.MatchEntity
import com.ultimateiptv.sports.data.iptv.ChannelContext
import com.ultimateiptv.sports.data.iptv.ChannelRepository
import com.ultimateiptv.sports.data.sports.Competition
import com.ultimateiptv.sports.data.sports.Competitions
import com.ultimateiptv.sports.data.sports.MatchRepository
import com.ultimateiptv.sports.notifications.ReminderScheduler
import com.ultimateiptv.sports.ui.components.ChannelLogo
import com.ultimateiptv.sports.ui.components.FocusCard
import com.ultimateiptv.sports.ui.components.ScreenHeader
import com.ultimateiptv.sports.ui.components.SectionHeader
import com.ultimateiptv.sports.ui.components.tvFocusBorder
import com.ultimateiptv.sports.ui.navigation.AppActions
import com.ultimateiptv.sports.ui.navigation.Routes
import com.ultimateiptv.sports.ui.screens.matches.MatchCard
import com.ultimateiptv.sports.ui.screens.matches.isArabic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Composable
fun TournamentsScreen(actions: AppActions, wide: Boolean) {
    val ar = isArabic()
    val featured = Competitions.all.filter { it.featured }
    val more = Competitions.all.filterNot { it.featured }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.nav_tournaments))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (wide) 200.dp else 160.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(featured, key = { it.key }) { c -> CompetitionTile(c, ar) { actions.open(Routes.tournament(c.key)) } }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.more_competitions)) }
            items(more, key = { it.key }) { c -> CompetitionTile(c, ar) { actions.open(Routes.tournament(c.key)) } }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.tournaments_other_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun CompetitionTile(c: Competition, ar: Boolean, onClick: () -> Unit) {
    FocusCard(onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ChannelLogo(c.logoUrl, c.nameEn, size = 64.dp)
            Spacer(Modifier.height(10.dp))
            Text(c.name(ar), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.height(40.dp))
            Text(c.region(ar), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TournamentViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val matches: MatchRepository,
    private val channels: ChannelRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    val key: String = checkNotNull(savedState["key"])
    val competition: Competition? = Competitions.get(key)
    val loading = MutableStateFlow(false)

    private val from = LocalDate.now().minusDays(3).let { matches.dayBounds(it).first }
    private val to = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(8)

    val fixtures: StateFlow<List<MatchEntity>> = matches.observeCompetition(key, from, to)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mapped: StateFlow<List<ChannelListItem>> = matches.competitionChannels(key)
        .map { channels.items(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reminders: StateFlow<Set<String>> = matches.reminders().map { r -> r.map { it.matchId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val searchQuery = MutableStateFlow("")
    val searchResults: StateFlow<List<ChannelListItem>> = searchQuery.flatMapLatest { q ->
        kotlinx.coroutines.flow.flow { emit(if (q.length < 2) emptyList() else channels.search(q, 40)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { refresh(false) }

    fun refresh(force: Boolean) = viewModelScope.launch {
        loading.value = true
        for (d in 0L..2L) matches.refreshDay(LocalDate.now().plusDays(d), force)
        loading.value = false
    }

    fun addChannel(uid: String) = viewModelScope.launch { matches.addCompetitionChannel(key, uid) }
    fun removeChannel(uid: String) = viewModelScope.launch { matches.removeCompetitionChannel(key, uid) }
    fun toggleReminder(m: MatchEntity) = viewModelScope.launch { scheduler.toggle(m) }
}

@Composable
fun TournamentScreen(actions: AppActions, vm: TournamentViewModel = hiltViewModel()) {
    val fixtures by vm.fixtures.collectAsState()
    val mapped by vm.mapped.collectAsState()
    val reminders by vm.reminders.collectAsState()
    val loading by vm.loading.collectAsState()
    var picker by remember { mutableStateOf(false) }
    val title = vm.competition?.name(isArabic()) ?: fixtures.firstOrNull()?.competitionName ?: vm.key

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack = { actions.back() }, subtitle = vm.competition?.region(isArabic())) {
            IconButton(onClick = { vm.refresh(true) }, modifier = Modifier.tvFocusBorder()) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
            item {
                SectionHeader(stringResource(R.string.broadcast_channels), action = {
                    TextButton(onClick = { picker = true }, modifier = Modifier.tvFocusBorder()) {
                        Icon(Icons.Filled.Add, null); Text(stringResource(R.string.add))
                    }
                })
            }
            if (mapped.isEmpty()) item {
                Text(stringResource(R.string.broadcast_channels_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
            items(mapped, key = { "m" + it.uid }) { ch ->
                FocusCard(onClick = { actions.play(ch.uid, ChannelContext.Group(0, null, true)) }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChannelLogo(ch.logo, ch.name, size = 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(ch.name, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.removeChannel(ch.uid) }, modifier = Modifier.tvFocusBorder()) { Icon(Icons.Filled.Close, stringResource(R.string.remove)) }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.fixtures)) }
            if (fixtures.isEmpty()) item {
                Text(stringResource(R.string.tournament_no_fixtures), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            items(fixtures, key = { it.id }) { m ->
                MatchCard(m, hasReminder = m.id in reminders, onClick = { actions.open(Routes.match(m.id)) }, onReminder = { vm.toggleReminder(m) }, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }

    if (picker) ChannelPickerDialog(vm, onDismiss = { picker = false })
}

@Composable
private fun ChannelPickerDialog(vm: TournamentViewModel, onDismiss: () -> Unit) {
    val q by vm.searchQuery.collectAsState()
    val results by vm.searchResults.collectAsState()
    LaunchedEffect(Unit) { vm.searchQuery.value = "" }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        title = { Text(stringResource(R.string.add_broadcast_channel)) },
        text = {
            Column {
                OutlinedTextField(
                    value = q, onValueChange = { vm.searchQuery.value = it }, singleLine = true,
                    label = { Text(stringResource(R.string.search_channels)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.height(320.dp)) {
                    items(results, key = { it.uid }) { ch ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            ChannelLogo(ch.logo, ch.name, size = 32.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(ch.name, modifier = Modifier.weight(1f), maxLines = 1)
                            OutlinedButton(onClick = { vm.addChannel(ch.uid) }, modifier = Modifier.tvFocusBorder()) { Text(stringResource(R.string.add)) }
                        }
                    }
                }
            }
        },
    )
}
