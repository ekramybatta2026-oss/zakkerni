package com.ultimateiptv.sports.ui.screens.epg

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimateiptv.sports.R
import com.ultimateiptv.sports.data.db.ChannelListItem
import com.ultimateiptv.sports.data.db.EpgProgrammeEntity
import com.ultimateiptv.sports.data.iptv.ChannelContext
import com.ultimateiptv.sports.data.iptv.ChannelRepository
import com.ultimateiptv.sports.data.iptv.EpgRepository
import com.ultimateiptv.sports.ui.components.ChannelLogo
import com.ultimateiptv.sports.ui.components.EmptyState
import com.ultimateiptv.sports.ui.components.ScreenHeader
import com.ultimateiptv.sports.ui.components.formatTime
import com.ultimateiptv.sports.ui.components.tvFocusBorder
import com.ultimateiptv.sports.ui.navigation.AppActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val channels: ChannelRepository,
    private val epg: EpgRepository,
) : ViewModel() {
    val uid: String = checkNotNull(savedState["uid"])
    val day = MutableStateFlow(LocalDate.now())
    val channel: StateFlow<ChannelListItem?> = channels.observeItem(uid).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val programmes: StateFlow<List<EpgProgrammeEntity>> = combine(channel, day) { c, d -> c to d }.flatMapLatest { (c, d) ->
        val epgId = c?.epgId
        if (c == null || epgId == null) flowOf(emptyList())
        else {
            val zone = ZoneId.systemDefault()
            epg.observeSchedule(c.subscriptionId, epgId, d.atStartOfDay(zone).toInstant().toEpochMilli(), d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleFavorite() = viewModelScope.launch { channels.toggleFavorite(uid) }
}

@Composable
fun ScheduleScreen(actions: AppActions, vm: ScheduleViewModel = hiltViewModel()) {
    val channel by vm.channel.collectAsState()
    val day by vm.day.collectAsState()
    val programmes by vm.programmes.collectAsState()
    val listState = rememberLazyListState()
    val now = System.currentTimeMillis()

    LaunchedEffect(programmes) {
        val i = programmes.indexOfFirst { it.stopMillis > now }
        if (i > 0) listState.scrollToItem(i)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(channel?.name ?: "", onBack = { actions.back() }, subtitle = channel?.groupTitle) {
            IconButton(onClick = { vm.toggleFavorite() }, modifier = Modifier.tvFocusBorder()) {
                Icon(if (channel?.isFavorite == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, stringResource(R.string.favorite))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            channel?.let { ChannelLogo(it.logo, it.name, size = 56.dp) }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.epg_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = { actions.play(vm.uid, ChannelContext.Group(channel?.subscriptionId ?: 0, channel?.groupTitle, false)) }, modifier = Modifier.tvFocusBorder()) {
                Icon(Icons.Filled.PlayArrow, null); Text(stringResource(R.string.watch))
            }
        }
        val today = LocalDate.now()
        LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((-1L..3L).map { today.plusDays(it) }) { d ->
                FilterChip(
                    selected = d == day, onClick = { vm.day.value = d }, modifier = Modifier.tvFocusBorder(),
                    label = {
                        Text(
                            when (d) {
                                today -> stringResource(R.string.today)
                                today.plusDays(1) -> stringResource(R.string.tomorrow)
                                today.minusDays(1) -> stringResource(R.string.yesterday)
                                else -> d.format(DateTimeFormatter.ofPattern("EEE d"))
                            }
                        )
                    },
                )
            }
        }
        if (programmes.isEmpty()) {
            EmptyState(Icons.Filled.Tv, stringResource(R.string.epg_empty_title), stringResource(if (channel?.epgId == null) R.string.epg_no_id else R.string.epg_empty_message))
            return@Column
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
            items(programmes, key = { it.id }) { p ->
                val current = p.startMillis <= now && p.stopMillis > now
                val past = p.stopMillis <= now
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .tvFocusBorder(RoundedCornerShape(12.dp))
                        .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${formatTime(p.startMillis)} – ${formatTime(p.stopMillis)}", style = MaterialTheme.typography.labelLarge,
                            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(p.title, style = MaterialTheme.typography.titleSmall, color = if (past) Color(0x99FFFFFF) else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    }
                    if (current) LinearProgressIndicator(
                        progress = { ((now - p.startMillis).toFloat() / (p.stopMillis - p.startMillis).coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                    p.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (current) 4 else 2) }
                }
            }
        }
    }
}
