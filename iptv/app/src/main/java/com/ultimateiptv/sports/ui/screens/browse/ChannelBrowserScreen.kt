package com.ultimateiptv.sports.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.ultimateiptv.sports.R
import com.ultimateiptv.sports.data.db.ChannelListItem
import com.ultimateiptv.sports.data.db.EpgProgrammeEntity
import com.ultimateiptv.sports.data.db.GroupCount
import com.ultimateiptv.sports.data.db.SubscriptionEntity
import com.ultimateiptv.sports.data.iptv.ChannelContext
import com.ultimateiptv.sports.data.iptv.ChannelRepository
import com.ultimateiptv.sports.data.iptv.EpgRepository
import com.ultimateiptv.sports.data.iptv.SubscriptionRepository
import com.ultimateiptv.sports.work.SubscriptionRefreshWorker
import com.ultimateiptv.sports.ui.components.ChannelRow
import com.ultimateiptv.sports.ui.components.EmptyState
import com.ultimateiptv.sports.ui.components.ScreenHeader
import com.ultimateiptv.sports.ui.components.tvFocusBorder
import com.ultimateiptv.sports.ui.navigation.AppActions
import com.ultimateiptv.sports.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowserFilter(val subscriptionId: Long = 0, val group: String? = null, val sportsOnly: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChannelBrowserViewModel @Inject constructor(
    private val channels: ChannelRepository,
    private val epg: EpgRepository,
    private val subscriptionRepo: SubscriptionRepository,
) : ViewModel() {
    val filter = MutableStateFlow(BrowserFilter())

    val subscriptions: StateFlow<List<SubscriptionEntity>> =
        subscriptionRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val total: StateFlow<Int?> = channels.observeTotal().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val progress = subscriptionRepo.progress

    val groups: StateFlow<List<GroupCount>> = filter.flatMapLatest { f -> channels.groups(f.subscriptionId, f.sportsOnly) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val paged: Flow<PagingData<ChannelListItem>> = filter.flatMapLatest { f -> channels.paged(f.subscriptionId, f.group, f.sportsOnly) }
        .cachedIn(viewModelScope)

    /** Now-playing cache for the rows currently on screen. */
    val nowPlaying = mutableStateMapOf<String, EpgProgrammeEntity?>()

    fun init(sportsOnly: Boolean) {
        if (filter.value.sportsOnly != sportsOnly) filter.value = filter.value.copy(sportsOnly = sportsOnly, group = null)
    }

    fun select(subscriptionId: Long) { filter.value = filter.value.copy(subscriptionId = subscriptionId, group = null) }
    fun selectGroup(group: String?) { filter.value = filter.value.copy(group = group) }

    fun loadNow(item: ChannelListItem) {
        if (item.uid in nowPlaying || item.epgId == null) return
        nowPlaying[item.uid] = null
        viewModelScope.launch {
            nowPlaying[item.uid] = epg.nowNext(item.subscriptionId, item.epgId).firstOrNull()?.takeIf { it.startMillis <= System.currentTimeMillis() }
        }
    }

    fun toggleFavorite(uid: String) = viewModelScope.launch { channels.toggleFavorite(uid) }

    fun context(): ChannelContext = filter.value.let { ChannelContext.Group(it.subscriptionId, it.group, it.sportsOnly) }

    fun refreshAll(context: android.content.Context) = viewModelScope.launch {
        subscriptions.value.forEach { SubscriptionRefreshWorker.refreshNow(context, it.id) }
    }

}

@Composable
fun ChannelBrowserScreen(actions: AppActions, sportsOnly: Boolean, wide: Boolean, vm: ChannelBrowserViewModel = hiltViewModel()) {
    LaunchedEffect(sportsOnly) { vm.init(sportsOnly) }
    val filter by vm.filter.collectAsState()
    val subs by vm.subscriptions.collectAsState()
    val groups by vm.groups.collectAsState()
    val total by vm.total.collectAsState()
    val progress by vm.progress.collectAsState()
    val items = vm.paged.collectAsLazyPagingItems()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(if (sportsOnly) R.string.nav_sports else R.string.nav_live),
            subtitle = groups.sumOf { it.count }.takeIf { it > 0 }?.let { stringResource(R.string.channels_count, it) },
        ) {
            IconButton(onClick = { vm.refreshAll(context) }, modifier = Modifier.tvFocusBorder()) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) }
            IconButton(onClick = { actions.open(Routes.SEARCH) }, modifier = Modifier.tvFocusBorder()) { Icon(Icons.Filled.Search, stringResource(R.string.nav_search)) }
        }
        if (progress.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (subs.size > 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = filter.subscriptionId == 0L, onClick = { vm.select(0) }, label = { Text(stringResource(R.string.all_subscriptions)) }, modifier = Modifier.tvFocusBorder()) }
                items(subs, key = { it.id }) { s ->
                    FilterChip(selected = filter.subscriptionId == s.id, onClick = { vm.select(s.id) }, label = { Text(s.name) }, modifier = Modifier.tvFocusBorder())
                }
            }
        }

        if (total == 0) {
            EmptyState(
                Icons.Filled.LiveTv, stringResource(R.string.empty_channels_title), stringResource(R.string.empty_channels_message),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { actions.open(Routes.subEdit()) }, modifier = Modifier.tvFocusBorder()) { Text(stringResource(R.string.add_subscription)) }
                    OutlinedButton(onClick = { actions.open(Routes.SUBSCRIPTIONS) }, modifier = Modifier.tvFocusBorder()) { Text(stringResource(R.string.subscriptions)) }
                }
            }
            return@Column
        }

        val groupList: @Composable (Modifier) -> Unit = { modifier ->
            if (wide) {
                LazyColumn(modifier, contentPadding = PaddingValues(8.dp)) {
                    item { GroupItem(stringResource(R.string.all_groups), null, filter.group == null) { vm.selectGroup(null) } }
                    items(groups, key = { it.groupTitle }) { g -> GroupItem(g.groupTitle, g.count, filter.group == g.groupTitle) { vm.selectGroup(g.groupTitle) } }
                }
            } else {
                LazyRow(modifier, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = filter.group == null, onClick = { vm.selectGroup(null) }, label = { Text(stringResource(R.string.all_groups)) }, modifier = Modifier.tvFocusBorder()) }
                    items(groups, key = { it.groupTitle }) { g ->
                        FilterChip(selected = filter.group == g.groupTitle, onClick = { vm.selectGroup(g.groupTitle) }, label = { Text("${g.groupTitle} (${g.count})", maxLines = 1) }, modifier = Modifier.tvFocusBorder())
                    }
                }
            }
        }

        val channelList: @Composable (Modifier) -> Unit = { modifier ->
            Box(modifier) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(count = items.itemCount, key = items.itemKey { it.uid }) { index ->
                        val item = items[index] ?: return@items
                        LaunchedEffect(item.uid) { vm.loadNow(item) }
                        val now = vm.nowPlaying[item.uid]
                        val t = System.currentTimeMillis()
                        ChannelRow(
                            item = item,
                            nowTitle = now?.title,
                            progress = now?.let { ((t - it.startMillis).toFloat() / (it.stopMillis - it.startMillis).coerceAtLeast(1)) },
                            onClick = { actions.play(item.uid, vm.context()) },
                            onLongClick = { actions.open(Routes.schedule(item.uid)) },
                            onFavorite = { vm.toggleFavorite(item.uid) },
                        )
                    }
                }
                if (items.loadState.refresh is LoadState.Loading && items.itemCount == 0) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                groupList(Modifier.width(280.dp).fillMaxHeight())
                channelList(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            groupList(Modifier.fillMaxWidth())
            channelList(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun GroupItem(title: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .tvFocusBorder(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        count?.let { Text("$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
