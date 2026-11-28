package com.ultimateiptv.sports.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.ultimateiptv.sports.data.db.ChannelListItem
import com.ultimateiptv.sports.ui.theme.LiveRed
import com.ultimateiptv.sports.ui.theme.Pitch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

/** Global time-format preference, updated from settings. */
object TimeFormat {
    @Volatile var use24h: Boolean = true
}

private val f24 = DateTimeFormatter.ofPattern("HH:mm")
private val f12 = DateTimeFormatter.ofPattern("h:mm a")

fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(if (TimeFormat.use24h) f24 else f12)

fun isTv(context: Context): Boolean {
    val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

/** Clearly visible focus ring + slight zoom for D-pad navigation on Android TV. */
fun Modifier.tvFocusBorder(shape: Shape = RoundedCornerShape(12.dp), zoom: Boolean = false): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .then(if (zoom && focused) Modifier.scale(1.04f) else Modifier)
        .border(if (focused) 2.5.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
}

@Composable
fun TvIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, large: Boolean = false, tint: Color = Color.White) {
    IconButton(onClick = onClick, modifier = modifier.tvFocusBorder(CircleShape).size(if (large) 64.dp else 52.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(if (large) 40.dp else 28.dp))
    }
}

private val logoPalette = listOf(Color(0xFF2E7D5B), Color(0xFF2F5DA8), Color(0xFF8E3B8F), Color(0xFFB4532A), Color(0xFF3B6E8F), Color(0xFF6B5B95))

@Composable
fun Initials(name: String, size: Dp, shape: Shape = RoundedCornerShape(8.dp)) {
    val initials = name.trim().split(' ', '-', '_').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "TV" }
    Box(
        Modifier.size(size).clip(shape).background(logoPalette[name.hashCode().absoluteValue % logoPalette.size]),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value / 2.8f).sp, maxLines = 1)
    }
}

@Composable
fun ChannelLogo(url: String?, name: String, size: Dp = 44.dp, shape: Shape = RoundedCornerShape(8.dp)) {
    if (url.isNullOrBlank()) {
        Initials(name, size, shape)
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size).clip(shape).background(Color(0x14FFFFFF)),
            error = { Initials(name, size, shape) },
            loading = { Box(Modifier.size(size).background(Color(0x14FFFFFF))) },
        )
    }
}

@Composable
fun LiveBadge(text: String, modifier: Modifier = Modifier) {
    Box(modifier.background(LiveRed, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer, textColor: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Box(Modifier.background(color, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(text, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 18.dp).background(Pitch, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(88.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        actions()
    }
}

/** A focusable surface that works with touch and D-pad (click + long-click). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Surface(
        color = color,
        shape = shape,
        modifier = modifier.tvFocusBorder(shape, zoom = true).clip(shape).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) { content() }
}

@Composable
fun ChannelRow(
    item: ChannelListItem,
    nowTitle: String?,
    progress: Float?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusCard(onClick = onClick, onLongClick = onLongClick, modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            item.number?.let {
                Text("$it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
            }
            ChannelLogo(item.logo, item.name, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (nowTitle != null) {
                    Text(nowTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    progress?.let {
                        Box(Modifier.padding(top = 4.dp).fillMaxWidth(0.6f).height(3.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape)) {
                            Box(Modifier.fillMaxWidth(it.coerceIn(0f, 1f)).height(3.dp).background(Pitch, CircleShape))
                        }
                    }
                } else {
                    Text(item.groupTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onFavorite, modifier = Modifier.tvFocusBorder(CircleShape)) {
                Icon(
                    if (item.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (item.isFavorite) LiveRed else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ChannelTile(item: ChannelListItem, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    FocusCard(onClick = onClick, modifier = modifier.width(150.dp)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ChannelLogo(item.logo, item.name, size = 72.dp)
            Spacer(Modifier.height(8.dp))
            Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.height(40.dp))
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
    }
}

fun LazyListScope.channelRow(title: String, items: List<ChannelListItem>, onClick: (ChannelListItem) -> Unit, action: (@Composable () -> Unit)? = null) {
    if (items.isEmpty()) return
    item(key = "h-$title") { SectionHeader(title, action = action) }
    item(key = "r-$title") {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.uid }) { ChannelTile(it, onClick = { onClick(it) }) }
        }
    }
}

/** Screen title bar without window insets (the root scaffold already handles them). */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.tvFocusBorder(CircleShape)) {
                Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
        actions()
    }
}
