package com.ultimateiptv.sports.ui.screens.favorites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimateiptv.sports.R
import com.ultimateiptv.sports.data.db.ChannelListItem
import com.ultimateiptv.sports.data.db.FavoriteTeamEntity
import com.ultimateiptv.sports.data.db.MatchReminderEntity
import com.ultimateiptv.sports.data.iptv.ChannelContext
import com.ultimateiptv.sports.data.iptv.ChannelRepository
import com.ultimateiptv.sports.data.sports.MatchRepository
import com.ultimateiptv.sports.notifications.ReminderScheduler
import com.ultimateiptv.sports.ui.components.ChannelLogo
import com.ultimateiptv.sports.ui.components.ChannelRow
import com.ultimateiptv.sports.ui.components.EmptyState
import com.ultimateiptv.sports.ui.components.FocusCard
import com.ultimateiptv.sports.ui.components.ScreenHeader
import com.ultimateiptv.sports.ui.components.formatTime
import com.ultimateiptv.sports.ui.components.tvFocusBorder
import com.ultimateiptv.sports.ui.navigation.AppActions
import com.ultimateiptv.sports.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val channels: ChannelRepository,
    private val matches: MatchRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    val favorites: StateFlow<List<ChannelListItem>> = channels.favorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history: StateFlow<List<ChannelListItem>> = channels.history(200).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val teams: StateFlow<List<FavoriteTeamEntity>> = matches.favoriteTeams().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reminders: StateFlow<List<MatchReminderEntity>> = matches.reminders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleFavorite(uid: String) = viewModelScope.launch { channels.toggleFavorite(uid) }
    fun removeTeam(key: String) = viewModelScope.launch { matches.removeFavoriteTeam(key) }
    fun removeReminder(id: String) = viewModelScope.launch { scheduler.remove(id) }
    fun clearHistory() = viewModelScope.launch { channels.clearHistory() }
    fun removeHistory(uid: String) = viewModelScope.launch { channels.removeFromHistory(uid) }
}

@Composable
fun FavoritesScreen(actions: AppActions, vm: FavoritesViewModel = hiltViewModel()) {
    val favorites by vm.favorites.collectAsState()
    val teams by vm.teams.collectAsState()
    val reminders by vm.reminders.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.nav_favorites))
        TabRow(selectedTabIndex = tab) {
            listOf(R.string.channels, R.string.favorite_teams, R.string.reminders).forEachIndexed { i, res ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(res)) }, modifier = Modifier.tvFocusBorder())
            }
        }
        when (tab) {
            0 -> if (favorites.isEmpty()) EmptyState(Icons.Filled.Favorite, stringResource(R.string.empty_favorites_title), stringResource(R.string.empty_favorites_message))
            else LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(favorites, key = { it.uid }) { ch ->
                    ChannelRow(ch, null, null, onClick = { actions.play(ch.uid, ChannelContext.Favorites) }, onLongClick = { actions.open(Routes.schedule(ch.uid)) }, onFavorite = { vm.toggleFavorite(ch.uid) })
                }
            }
            1 -> if (teams.isEmpty()) EmptyState(Icons.Filled.Star, stringResource(R.string.empty_teams_title), stringResource(R.string.empty_teams_message))
            else LazyColumn(contentPadding = PaddingValues(12.dp)) {
                items(teams, key = { it.key }) { t ->
                    FocusCard(onClick = {}, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            ChannelLogo(t.logo, t.name, size = 36.dp, shape = CircleShape)
                            Spacer(Modifier.width(12.dp))
                            Text(t.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = { vm.removeTeam(t.key) }, modifier = Modifier.tvFocusBorder()) { Icon(Icons.Filled.Close, stringResource(R.string.remove)) }
                        }
                    }
                }
            }
            else -> if (reminders.isEmpty()) EmptyState(Icons.Filled.Notifications, stringResource(R.string.empty_reminders_title), stringResource(R.string.empty_reminders_message))
            else LazyColumn(contentPadding = PaddingValues(12.dp)) {
                val fmt = DateTimeFormatter.ofPattern("EEE d MMM")
                items(reminders, key = { it.matchId }) { r ->
                    FocusCard(onClick = { actions.open(Routes.match(r.matchId)) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${r.competition} · ${Instant.ofEpochMilli(r.kickoff).atZone(ZoneId.systemDefault()).format(fmt)} ${formatTime(r.kickoff)}" +
                                        if (r.automatic) " · " + stringResource(R.string.automatic) else "",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { vm.removeReminder(r.matchId) }, modifier = Modifier.tvFocusBorder()) { Icon(Icons.Filled.Close, stringResource(R.string.remove)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(actions: AppActions, vm: FavoritesViewModel = hiltViewModel()) {
    val history by vm.history.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.nav_history)) {
            if (history.isNotEmpty()) IconButton(onClick = { vm.clearHistory() }, modifier = Modifier.tvFocusBorder()) { Icon(Icons.Filled.DeleteSweep, stringResource(R.string.clear_history)) }
        }
        if (history.isEmpty()) {
            EmptyState(Icons.Filled.History, stringResource(R.string.empty_history_title), stringResource(R.string.empty_history_message))
        } else LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(history, key = { it.uid }) { ch ->
                ChannelRow(ch, null, null, onClick = { actions.play(ch.uid, ChannelContext.History) }, onLongClick = { vm.removeHistory(ch.uid) }, onFavorite = { vm.toggleFavorite(ch.uid) })
            }
        }
    }
}
