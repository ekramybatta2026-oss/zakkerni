package com.ultimateiptv.sports.ui.screens.more

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ultimateiptv.sports.R
import com.ultimateiptv.sports.ui.components.FocusCard
import com.ultimateiptv.sports.ui.components.ScreenHeader
import com.ultimateiptv.sports.ui.navigation.AppActions
import com.ultimateiptv.sports.ui.navigation.Routes

@Composable
fun MoreScreen(actions: AppActions) {
    val entries = listOf(
        Triple(Routes.SPORTS, R.string.nav_sports, Icons.Filled.SportsSoccer),
        Triple(Routes.TOURNAMENTS, R.string.nav_tournaments, Icons.Filled.EmojiEvents),
        Triple(Routes.HISTORY, R.string.nav_history, Icons.Filled.History),
        Triple(Routes.SEARCH, R.string.nav_search, Icons.Filled.Search),
        Triple(Routes.SUBSCRIPTIONS, R.string.subscriptions, Icons.AutoMirrored.Filled.PlaylistPlay),
        Triple(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
    )
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.nav_more))
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            items(entries) { (route, label, icon) ->
                FocusCard(onClick = { actions.open(route) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
