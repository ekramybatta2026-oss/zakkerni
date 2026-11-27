package com.ultimateiptv.sports.ui.screens.matches

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimateiptv.sports.R
import com.ultimateiptv.sports.data.db.MatchEntity
import com.ultimateiptv.sports.data.db.MatchStatus
import com.ultimateiptv.sports.data.iptv.ChannelContext
import com.ultimateiptv.sports.data.sports.BroadcastCandidate
import com.ultimateiptv.sports.data.sports.MatchRepository
import com.ultimateiptv.sports.data.sports.isLive
import com.ultimateiptv.sports.notifications.ReminderScheduler
import com.ultimateiptv.sports.ui.components.ChannelLogo
import com.ultimateiptv.sports.ui.components.FocusCard
import com.ultimateiptv.sports.ui.components.LiveBadge
import com.ultimateiptv.sports.ui.components.ScreenHeader
import com.ultimateiptv.sports.ui.components.SectionHeader
import com.ultimateiptv.sports.ui.components.Tag
import com.ultimateiptv.sports.ui.components.formatTime
import com.ultimateiptv.sports.ui.components.tvFocusBorder
import com.ultimateiptv.sports.ui.navigation.AppActions
import com.ultimateiptv.sports.ui.navigation.Routes
import com.ultimateiptv.sports.ui.theme.Pitch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class MatchDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: MatchRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    private val id: String = checkNotNull(savedState["id"])
    val match: StateFlow<MatchEntity?> = repo.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val candidates = MutableStateFlow<List<BroadcastCandidate>?>(null)

    val reminder: StateFlow<Boolean> = repo.reminders().combine(match) { r, _ -> r.any { it.matchId == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val favoriteTeams: StateFlow<Set<String>> = repo.favoriteTeams().combine(match) { t, _ -> t.map { it.key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch {
            val m = repo.get(id) ?: run { candidates.value = emptyList(); return@launch }
            candidates.value = repo.broadcastCandidates(m)
        }
    }

    fun toggleReminder() = viewModelScope.launch { match.value?.let { scheduler.toggle(it) } }
    fun toggleTeam(name: String, logo: String?) = viewModelScope.launch { repo.toggleFavoriteTeam(name, logo) }
}

@Composable
fun MatchDetailScreen(actions: AppActions, vm: MatchDetailViewModel = hiltViewModel()) {
    val m by vm.match.collectAsState()
    val candidates by vm.candidates.collectAsState()
    val reminder by vm.reminder.collectAsState()
    val favTeams by vm.favoriteTeams.collectAsState()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(m?.let { competitionLabel(it) } ?: stringResource(R.string.nav_matches), onBack = { actions.back() }, subtitle = m?.round)
        val match = m ?: run {
            Text(stringResource(R.string.match_not_found), modifier = Modifier.padding(24.dp))
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TeamColumn(teamLabel(match.homeName), match.homeLogo, MatchRepository.teamKey(match.homeName) in favTeams, Modifier.weight(1f)) { vm.toggleTeam(match.homeName, match.homeLogo) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp)) {
                        if (match.homeScore != null && match.status != MatchStatus.SCHEDULED) {
                            Text("${match.homeScore} - ${match.awayScore}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = if (match.isLive()) Pitch else MaterialTheme.colorScheme.onSurface)
                        } else {
                            Text(formatTime(match.kickoff), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                        if (match.isLive()) LiveBadge(statusText(match)) else Text(statusText(match), style = MaterialTheme.typography.labelMedium)
                    }
                    TeamColumn(teamLabel(match.awayName), match.awayLogo, MatchRepository.teamKey(match.awayName) in favTeams, Modifier.weight(1f)) { vm.toggleTeam(match.awayName, match.awayLogo) }
                }
            }
            item {
                val date = Instant.ofEpochMilli(match.kickoff).atZone(ZoneId.systemDefault())
                Text(
                    listOfNotNull(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) + " · " + formatTime(match.kickoff), match.venue, match.country).joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
            if (match.status == MatchStatus.SCHEDULED && match.kickoff > System.currentTimeMillis()) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    FilledTonalButton(onClick = { vm.toggleReminder() }, modifier = Modifier.tvFocusBorder()) {
                        Icon(if (reminder) Icons.Filled.Notifications else Icons.Filled.NotificationsNone, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (reminder) R.string.match_reminder_on else R.string.match_remind))
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.match_where_to_watch)) }
            when {
                candidates == null -> item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                candidates!!.isEmpty() -> item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(stringResource(R.string.match_no_channels), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { actions.open(Routes.tournament(match.competitionKey)) }, modifier = Modifier.tvFocusBorder()) {
                            Text(stringResource(R.string.match_map_channels))
                        }
                    }
                }
                else -> items(candidates!!, key = { it.channel.uid }) { c ->
                    FocusCard(onClick = { actions.play(c.channel.uid, ChannelContext.Group(0, null, true)) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            ChannelLogo(c.channel.logo, c.channel.name, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.channel.name, style = MaterialTheme.typography.titleSmall)
                                c.programmeTitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                                Spacer(Modifier.height(4.dp))
                                Tag(
                                    stringResource(
                                        when (c.source) {
                                            BroadcastCandidate.Source.MAPPED -> R.string.source_mapped
                                            BroadcastCandidate.Source.EPG_BOTH_TEAMS -> R.string.source_epg_confirmed
                                            BroadcastCandidate.Source.EPG_ONE_TEAM -> R.string.source_epg_possible
                                        }
                                    )
                                )
                            }
                            Button(onClick = { actions.play(c.channel.uid, ChannelContext.Group(0, null, true)) }, modifier = Modifier.tvFocusBorder()) {
                                Icon(Icons.Filled.PlayArrow, null)
                                Text(stringResource(R.string.watch))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamColumn(name: String, logo: String?, favorite: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ChannelLogo(logo, name, size = 64.dp, shape = CircleShape)
        Spacer(Modifier.height(8.dp))
        Text(name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2)
        androidx.compose.material3.IconButton(onClick = onToggle, modifier = Modifier.tvFocusBorder(CircleShape)) {
            Icon(if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder, stringResource(R.string.favorite_team), tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
