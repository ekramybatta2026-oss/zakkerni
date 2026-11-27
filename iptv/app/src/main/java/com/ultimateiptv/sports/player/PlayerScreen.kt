package com.ultimateiptv.sports.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ultimateiptv.sports.R
import com.ultimateiptv.sports.data.settings.AspectMode
import com.ultimateiptv.sports.ui.components.ChannelLogo
import com.ultimateiptv.sports.ui.components.TvIconButton
import com.ultimateiptv.sports.ui.components.formatTime
import com.ultimateiptv.sports.ui.components.tvFocusBorder
import com.ultimateiptv.sports.ui.theme.LiveRed
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel, inPip: Boolean, onBack: () -> Unit, onExternal: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var overlay by remember { mutableStateOf(true) }
    var listOpen by remember { mutableStateOf(false) }
    var tracksOpen by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val primaryFocus = remember { FocusRequester() }
    var gesture by remember { mutableStateOf<Pair<Boolean, Float>?>(null) }

    fun poke() { interaction++ }

    // Auto-hide the overlay 5 s after the last interaction.
    LaunchedEffect(overlay, interaction, listOpen, tracksOpen) {
        if (overlay && !listOpen && !tracksOpen) {
            delay(5_000)
            overlay = false
        }
    }
    LaunchedEffect(overlay, listOpen, tracksOpen) {
        runCatching { if (!overlay && !listOpen && !tracksOpen) rootFocus.requestFocus() else if (overlay && !listOpen) primaryFocus.requestFocus() }
    }
    // Show the info bar each time the channel changes.
    LaunchedEffect(state.position) { if (state.position > 0) { overlay = true; poke() } }

    BackHandler(enabled = overlay || listOpen) {
        when {
            listOpen -> listOpen = false
            else -> overlay = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                poke()
                if (listOpen || tracksOpen) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.ChannelUp -> { viewModel.next(); true }
                    Key.ChannelDown -> { viewModel.previous(); true }
                    Key.DirectionUp -> if (!overlay) { viewModel.next(); true } else false
                    Key.DirectionDown -> if (!overlay) { viewModel.previous(); true } else false
                    Key.DirectionLeft -> if (!overlay) { listOpen = true; true } else false
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionRight, Key.Info ->
                        if (!overlay) { overlay = true; true } else false
                    Key.Menu -> { tracksOpen = true; true }
                    Key.MediaPlayPause -> { if (viewModel.player.isPlaying) viewModel.player.pause() else viewModel.player.play(); true }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { overlay = !overlay; poke() }, onDoubleTap = { viewModel.cycleAspect() })
            }
            .pointerInput(Unit) {
                var leftSide = false
                detectVerticalDragGestures(
                    onDragStart = { offset -> leftSide = offset.x < size.width / 2 },
                    onDragEnd = { gesture = null },
                    onDragCancel = { gesture = null },
                ) { change, drag ->
                    change.consume()
                    val delta = -drag / size.height
                    gesture = if (leftSide) true to adjustBrightness(context, delta) else false to adjustVolume(context, delta)
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = true
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    player = viewModel.player
                }
            },
            update = { view ->
                view.resizeMode = when (state.aspect) {
                    AspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Test marker (invisible): lets instrumentation tests know playback is running.
        Box(Modifier.size(1.dp).testTag(if (state.playing && !state.buffering && state.errorRes == null) "player_ready" else "player_loading"))

        if (state.buffering && state.errorRes == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = MaterialTheme.colorScheme.primary)
        }

        state.errorRes?.let { res ->
            if (!inPip) ErrorPanel(
                message = stringResource(res), detail = state.errorDetail, retryIn = state.retryInSeconds,
                onRetry = viewModel::retryNow, modifier = Modifier.align(Alignment.Center),
            )
        }

        gesture?.let { (brightness, value) ->
            Row(
                Modifier.align(Alignment.Center).background(Color(0xAA000000), RoundedCornerShape(12.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (brightness) Icons.Filled.WbSunny else Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White)
                Spacer(Modifier.width(12.dp))
                LinearProgressIndicator(progress = { value }, modifier = Modifier.width(140.dp))
            }
        }

        if (!inPip) {
            AnimatedVisibility(overlay, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                Overlay(
                    state = state,
                    primaryFocus = primaryFocus,
                    onBack = onBack,
                    onPlayPause = { if (viewModel.player.isPlaying) viewModel.player.pause() else viewModel.player.play(); poke() },
                    onNext = { viewModel.next(); poke() },
                    onPrevious = { viewModel.previous(); poke() },
                    onList = { listOpen = true },
                    onTracks = { tracksOpen = true },
                    onAspect = { viewModel.cycleAspect(); poke() },
                    onFavorite = { viewModel.toggleFavorite(); poke() },
                    onExternal = onExternal,
                    playing = state.playing,
                )
            }
            AnimatedVisibility(
                listOpen,
                enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it },
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                ChannelPanel(state, onSelect = { uid -> viewModel.playAt(uid); listOpen = false })
            }
        }
    }

    if (tracksOpen) TracksDialog(state, viewModel, onDismiss = { tracksOpen = false })
}

@Composable
private fun Overlay(
    state: PlayerUiState,
    primaryFocus: FocusRequester,
    playing: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onList: () -> Unit,
    onTracks: () -> Unit,
    onAspect: () -> Unit,
    onFavorite: () -> Unit,
    onExternal: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Top: channel + current programme
        Column(
            Modifier.fillMaxWidth().align(Alignment.TopStart)
                .background(Brush.verticalGradient(listOf(Color(0xE6000000), Color.Transparent)))
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack)
                Spacer(Modifier.width(8.dp))
                ChannelLogo(state.channel?.logo, state.title, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            if (state.total > 0) "${state.position}/${state.total}" else null,
                            state.channel?.groupTitle, state.resolution,
                        ).joinToString("  •  "),
                        style = MaterialTheme.typography.bodySmall, color = Color(0xCCFFFFFF),
                    )
                }
                Box(Modifier.background(LiveRed, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(stringResource(R.string.live), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            val now = state.nowNext.firstOrNull()
            if (now != null) {
                Spacer(Modifier.height(10.dp))
                val t = System.currentTimeMillis()
                Text("${formatTime(now.startMillis)} – ${formatTime(now.stopMillis)}  ${now.title}", color = Color.White, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(
                    progress = { ((t - now.startMillis).toFloat() / (now.stopMillis - now.startMillis).coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(0.5f).padding(vertical = 6.dp),
                )
                state.nowNext.getOrNull(1)?.let {
                    Text(stringResource(R.string.epg_next) + ": ${formatTime(it.startMillis)}  ${it.title}", color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }
        }
        // Bottom: controls
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvIconButton(Icons.AutoMirrored.Filled.List, stringResource(R.string.player_channels), onList)
            TvIconButton(Icons.Filled.KeyboardArrowDown, stringResource(R.string.player_previous), onPrevious)
            TvIconButton(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                stringResource(if (playing) R.string.player_pause else R.string.player_play),
                onPlayPause, large = true, modifier = Modifier.focusRequester(primaryFocus),
            )
            TvIconButton(Icons.Filled.KeyboardArrowUp, stringResource(R.string.player_next), onNext)
            TvIconButton(Icons.Filled.Settings, stringResource(R.string.player_tracks), onTracks)
            TvIconButton(Icons.Filled.AspectRatio, stringResource(R.string.player_aspect), onAspect)
            TvIconButton(if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, stringResource(R.string.favorite), onFavorite)
            TvIconButton(Icons.Filled.OpenInNew, stringResource(R.string.player_external), onExternal)
        }
    }
}

@Composable
private fun ChannelPanel(state: PlayerUiState, onSelect: (String) -> Unit) {
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val currentIndex = state.zapList.indexOfFirst { it.uid == state.channel?.uid }.coerceAtLeast(0)
    LaunchedEffect(Unit) {
        listState.scrollToItem((currentIndex - 3).coerceAtLeast(0))
        runCatching { focus.requestFocus() }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.width(340.dp).fillMaxHeight().background(Color(0xF0101522)).padding(vertical = 12.dp),
    ) {
        itemsIndexed(state.zapList, key = { _, it -> it.uid }) { i, item ->
            val selected = item.uid == state.channel?.uid
            Row(
                Modifier.fillMaxWidth()
                    .then(if (i == currentIndex) Modifier.focusRequester(focus) else Modifier)
                    .tvFocusBorder(RoundedCornerShape(8.dp))
                    .clickable { onSelect(item.uid) }
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${item.number ?: (i + 1)}", color = Color(0x99FFFFFF), modifier = Modifier.width(44.dp), style = MaterialTheme.typography.bodySmall)
                ChannelLogo(item.logo, item.name, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Text(item.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TracksDialog(state: PlayerUiState, vm: PlayerViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.player_tracks)) },
        text = {
            LazyColumn {
                item { SectionTitle(stringResource(R.string.player_quality)) }
                item { TrackRow(stringResource(R.string.player_auto), state.videoAuto) { vm.autoVideo() } }
                state.videoTracks.forEach { t -> item { TrackRow(t.label, !state.videoAuto && t.selected) { vm.selectTrack(t) } } }
                if (state.audioTracks.size > 1) {
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)); SectionTitle(stringResource(R.string.player_audio)) }
                    state.audioTracks.forEach { t -> item { TrackRow(t.label, t.selected) { vm.selectTrack(t) } } }
                }
                if (state.textTracks.isNotEmpty()) {
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(stringResource(R.string.player_subtitles), Modifier.weight(1f))
                            Switch(checked = state.textEnabled, onCheckedChange = { vm.setSubtitlesEnabled(it) })
                        }
                    }
                    if (state.textEnabled) state.textTracks.forEach { t -> item { TrackRow(t.label, t.selected) { vm.selectTrack(t) } } }
                }
                if (state.videoTracks.isEmpty() && state.audioTracks.isEmpty()) {
                    item { Text(stringResource(R.string.player_no_tracks)) }
                }
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(vertical = 4.dp))

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().tvFocusBorder(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun ErrorPanel(message: String, detail: String?, retryIn: Int?, onRetry: () -> Unit, modifier: Modifier) {
    Column(
        modifier.background(Color(0xCC000000), RoundedCornerShape(16.dp)).padding(24.dp).width(360.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = Color.White, style = MaterialTheme.typography.titleMedium)
        detail?.let { Text(it, color = Color(0x99FFFFFF), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
        retryIn?.let { Text(stringResource(R.string.player_retry_in, it), color = Color(0xCCFFFFFF), modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.retry))
        }
    }
}

private fun adjustBrightness(context: Context, delta: Float): Float {
    val activity = context as? Activity ?: return 0f
    val lp = activity.window.attributes
    val current = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
    val next = (current + delta).coerceIn(0.02f, 1f)
    lp.screenBrightness = next
    activity.window.attributes = lp
    return next
}

private val volumeAccumulator = mutableFloatStateOf(0f)

private fun adjustVolume(context: Context, delta: Float): Float {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    volumeAccumulator.floatValue += delta * max
    val steps = volumeAccumulator.floatValue.toInt()
    if (steps != 0) {
        volumeAccumulator.floatValue -= steps
        val v = (am.getStreamVolume(AudioManager.STREAM_MUSIC) + steps).coerceIn(0, max)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
    }
    return am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max.coerceAtLeast(1)
}

