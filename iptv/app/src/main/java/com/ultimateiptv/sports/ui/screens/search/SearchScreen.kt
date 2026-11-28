package com.ultimateiptv.sports.ui.screens.search

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimateiptv.sports.R
import com.ultimateiptv.sports.data.db.ChannelListItem
import com.ultimateiptv.sports.data.db.MatchEntity
import com.ultimateiptv.sports.data.db.ProgrammeWithChannel
import com.ultimateiptv.sports.data.iptv.ChannelContext
import com.ultimateiptv.sports.data.iptv.ChannelRepository
import com.ultimateiptv.sports.data.iptv.EpgRepository
import com.ultimateiptv.sports.data.sports.MatchRepository
import com.ultimateiptv.sports.ui.components.ChannelLogo
import com.ultimateiptv.sports.ui.components.ChannelRow
import com.ultimateiptv.sports.ui.components.FocusCard
import com.ultimateiptv.sports.ui.components.ScreenHeader
import com.ultimateiptv.sports.ui.components.SectionHeader
import com.ultimateiptv.sports.ui.components.formatTime
import com.ultimateiptv.sports.ui.navigation.AppActions
import com.ultimateiptv.sports.ui.navigation.Routes
import com.ultimateiptv.sports.ui.screens.matches.MatchCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchResults(
    val channels: List<ChannelListItem> = emptyList(),
    val programmes: List<ProgrammeWithChannel> = emptyList(),
    val matches: List<MatchEntity> = emptyList(),
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val channels: ChannelRepository,
    private val epg: EpgRepository,
    private val matches: MatchRepository,
) : ViewModel() {
    val query = MutableStateFlow("")
    val results = MutableStateFlow(SearchResults())

    init {
        viewModelScope.launch {
            query.debounce(300).distinctUntilChanged().collect { q ->
                results.value = if (q.trim().length < 2) SearchResults()
                else SearchResults(channels.search(q, 60), epg.search(q, 30), matches.search(q))
            }
        }
    }

    fun toggleFavorite(uid: String) = viewModelScope.launch {
        channels.toggleFavorite(uid)
        results.value = results.value.copy(channels = channels.search(query.value, 60))
    }
}

@Composable
fun SearchScreen(actions: AppActions, vm: SearchViewModel = hiltViewModel()) {
    val q by vm.query.collectAsState()
    val r by vm.results.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.nav_search))
        OutlinedTextField(
            value = q, onValueChange = { vm.query.value = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) } },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (r.channels.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.channels) + " (${r.channels.size})") }
                items(r.channels, key = { "c" + it.uid }) { ch ->
                    ChannelRow(ch, null, null, onClick = { actions.play(ch.uid, ChannelContext.Group(ch.subscriptionId, ch.groupTitle, false)) }, onLongClick = { actions.open(Routes.schedule(ch.uid)) }, onFavorite = { vm.toggleFavorite(ch.uid) })
                }
            }
            if (r.programmes.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.programmes)) }
                items(r.programmes, key = { "p" + it.id }) { p ->
                    FocusCard(onClick = { p.channelUid?.let { actions.open(Routes.schedule(it)) } }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ChannelLogo(p.channelLogo, p.channelName ?: "", size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text("${formatTime(p.startMillis)} – ${formatTime(p.stopMillis)} · ${p.channelName.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (r.matches.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.nav_matches)) }
                items(r.matches, key = { "m" + it.id }) { m ->
                    MatchCard(m, false, onClick = { actions.open(Routes.match(m.id)) }, onReminder = null, showCompetition = true, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }
            if (q.trim().length >= 2 && r.channels.isEmpty() && r.programmes.isEmpty() && r.matches.isEmpty()) {
                item { Text(stringResource(R.string.no_results), modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
