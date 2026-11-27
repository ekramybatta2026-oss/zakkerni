package com.ultimateiptv.sports.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimateiptv.sports.R
import com.ultimateiptv.sports.data.db.ChannelListItem
import com.ultimateiptv.sports.data.db.MatchEntity
import com.ultimateiptv.sports.data.db.MatchStatus
import com.ultimateiptv.sports.data.iptv.ChannelContext
import com.ultimateiptv.sports.data.iptv.ChannelRepository
import com.ultimateiptv.sports.data.iptv.SubscriptionRepository
import com.ultimateiptv.sports.data.settings.SettingsRepository
import com.ultimateiptv.sports.data.settings.SportsProviderId
import com.ultimateiptv.sports.data.sports.MatchRepository
import com.ultimateiptv.sports.data.sports.isLive
import com.ultimateiptv.sports.ui.components.SectionHeader
import com.ultimateiptv.sports.ui.components.channelRow
import com.ultimateiptv.sports.ui.components.tvFocusBorder
import com.ultimateiptv.sports.ui.navigation.AppActions
import com.ultimateiptv.sports.ui.navigation.Routes
import com.ultimateiptv.sports.ui.screens.matches.MatchCard
import com.ultimateiptv.sports.ui.theme.Night
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeState(
    val loaded: Boolean = false,
    val subscriptionCount: Int = 0,
    val history: List<ChannelListItem> = emptyList(),
    val favorites: List<ChannelListItem> = emptyList(),
    val sports: List<ChannelListItem> = emptyList(),
    val liveMatches: List<MatchEntity> = emptyList(),
    val upcoming: List<MatchEntity> = emptyList(),
    val demoProvider: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    channels: ChannelRepository,
    private val subscriptions: SubscriptionRepository,
    private val matches: MatchRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val addingDemo = MutableStateFlow(false)

    private val channelsFlow = combine(channels.history(15), channels.favorites(), channels.sports(20)) { h, f, s -> Triple(h, f, s) }

    val state: StateFlow<HomeState> = combine(
        subscriptions.observeCount(), channelsFlow, matches.observeDay(LocalDate.now()), settings.settings,
    ) { count, (h, f, s), today, st ->
        val now = System.currentTimeMillis()
        HomeState(
            loaded = true,
            subscriptionCount = count,
            history = h, favorites = f.take(20), sports = s,
            liveMatches = today.filter { it.isLive() },
            upcoming = today.filter { it.status == MatchStatus.SCHEDULED && it.kickoff > now }.take(8),
            demoProvider = st.sportsProvider == SportsProviderId.DEMO,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    init {
        viewModelScope.launch { matches.refreshDay(LocalDate.now()) }
    }

    fun addDemo() {
        if (addingDemo.value) return
        addingDemo.value = true
        viewModelScope.launch {
            try { subscriptions.addDemo() } finally { addingDemo.value = false }
        }
    }
}

@Composable
fun HomeScreen(actions: AppActions, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val addingDemo by vm.addingDemo.collectAsState()
    val continueTitle = stringResource(R.string.home_continue)
    val favoritesTitle = stringResource(R.string.nav_favorites)
    val sportsTitle = stringResource(R.string.home_sports_channels)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_logo_mark), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { actions.open(Routes.SEARCH) }, modifier = Modifier.tvFocusBorder(CircleShape)) { Icon(Icons.Filled.Search, stringResource(R.string.nav_search)) }
                IconButton(onClick = { actions.open(Routes.SETTINGS) }, modifier = Modifier.tvFocusBorder(CircleShape)) { Icon(Icons.Filled.Settings, stringResource(R.string.nav_settings)) }
            }
        }

        if (state.loaded && state.subscriptionCount == 0) {
            item { WelcomeCard(addingDemo, onAdd = { actions.open(Routes.subEdit()) }, onDemo = vm::addDemo) }
        }

        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { AssistChip(onClick = { actions.open(Routes.SPORTS) }, label = { Text(stringResource(R.string.nav_sports)) }, leadingIcon = { Icon(Icons.Filled.SportsSoccer, null) }, modifier = Modifier.tvFocusBorder()) }
                item { AssistChip(onClick = { actions.open(Routes.TOURNAMENTS) }, label = { Text(stringResource(R.string.nav_tournaments)) }, leadingIcon = { Icon(Icons.Filled.EmojiEvents, null) }, modifier = Modifier.tvFocusBorder()) }
                item { AssistChip(onClick = { actions.open(Routes.HISTORY) }, label = { Text(stringResource(R.string.nav_history)) }, leadingIcon = { Icon(Icons.Filled.History, null) }, modifier = Modifier.tvFocusBorder()) }
                item { AssistChip(onClick = { actions.open(Routes.SUBSCRIPTIONS) }, label = { Text(stringResource(R.string.subscriptions)) }, leadingIcon = { Icon(Icons.Filled.Add, null) }, modifier = Modifier.tvFocusBorder()) }
            }
        }

        if (state.liveMatches.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.home_live_now), action = { TextButton(onClick = { actions.open(Routes.MATCHES) }, modifier = Modifier.tvFocusBorder()) { Text(stringResource(R.string.see_all)) } }) }
            item { MatchesRow(state.liveMatches, state.demoProvider) { actions.open(Routes.match(it.id)) } }
        }

        channelRow(continueTitle, state.history, onClick = { actions.play(it.uid, ChannelContext.History) })
        channelRow(favoritesTitle, state.favorites, onClick = { actions.play(it.uid, ChannelContext.Favorites) },
            action = { TextButton(onClick = { actions.open(Routes.FAVORITES) }, modifier = Modifier.tvFocusBorder()) { Text(stringResource(R.string.see_all)) } })

        if (state.upcoming.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.home_today_matches), action = { TextButton(onClick = { actions.open(Routes.MATCHES) }, modifier = Modifier.tvFocusBorder()) { Text(stringResource(R.string.see_all)) } }) }
            item { MatchesRow(state.upcoming, state.demoProvider) { actions.open(Routes.match(it.id)) } }
        }

        channelRow(sportsTitle, state.sports, onClick = { actions.play(it.uid, ChannelContext.Group(0, null, true)) },
            action = { TextButton(onClick = { actions.open(Routes.SPORTS) }, modifier = Modifier.tvFocusBorder()) { Text(stringResource(R.string.see_all)) } })
    }
}

@Composable
private fun MatchesRow(list: List<MatchEntity>, demo: Boolean, onClick: (MatchEntity) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(list, key = { it.id }) { m ->
            MatchCard(m, hasReminder = false, onClick = { onClick(m) }, onReminder = null, showCompetition = true, isDemo = demo, modifier = Modifier.width(320.dp))
        }
    }
}

@Composable
private fun WelcomeCard(addingDemo: Boolean, onAdd: () -> Unit, onDemo: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(16.dp)
            .background(Brush.linearGradient(listOf(Color(0xFF003B22), Color(0xFF0D2547), Night)), RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        Column {
            Icon(Icons.Filled.PlayCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.welcome_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onAdd, modifier = Modifier.tvFocusBorder()) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add_subscription))
                }
                OutlinedButton(onClick = onDemo, enabled = !addingDemo, modifier = Modifier.tvFocusBorder()) {
                    if (addingDemo) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.try_demo))
                }
            }
        }
    }
}
