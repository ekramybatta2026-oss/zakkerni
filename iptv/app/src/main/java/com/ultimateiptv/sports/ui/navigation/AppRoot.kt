package com.ultimateiptv.sports.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ultimateiptv.sports.MainViewModel
import com.ultimateiptv.sports.R
import com.ultimateiptv.sports.data.iptv.ChannelContext
import com.ultimateiptv.sports.player.PlayerActivity
import com.ultimateiptv.sports.ui.components.isTv
import com.ultimateiptv.sports.ui.components.tvFocusBorder
import com.ultimateiptv.sports.ui.screens.browse.ChannelBrowserScreen
import com.ultimateiptv.sports.ui.screens.epg.ScheduleScreen
import com.ultimateiptv.sports.ui.screens.favorites.FavoritesScreen
import com.ultimateiptv.sports.ui.screens.favorites.HistoryScreen
import com.ultimateiptv.sports.ui.screens.home.HomeScreen
import com.ultimateiptv.sports.ui.screens.matches.MatchDetailScreen
import com.ultimateiptv.sports.ui.screens.matches.MatchesScreen
import com.ultimateiptv.sports.ui.screens.more.MoreScreen
import com.ultimateiptv.sports.ui.screens.search.SearchScreen
import com.ultimateiptv.sports.ui.screens.settings.SettingsScreen
import com.ultimateiptv.sports.ui.screens.subscriptions.SubscriptionEditScreen
import com.ultimateiptv.sports.ui.screens.subscriptions.SubscriptionsScreen
import com.ultimateiptv.sports.ui.screens.tournaments.TournamentScreen
import com.ultimateiptv.sports.ui.screens.tournaments.TournamentsScreen

object Routes {
    const val HOME = "home"
    const val LIVE = "live"
    const val SPORTS = "sports"
    const val MATCHES = "matches"
    const val TOURNAMENTS = "tournaments"
    const val TOURNAMENT = "tournament/{key}"
    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val SUBSCRIPTIONS = "subscriptions"
    const val SUB_EDIT = "subscription?id={id}"
    const val SCHEDULE = "schedule/{uid}"
    const val MATCH = "match/{id}"
    const val MORE = "more"

    fun tournament(key: String) = "tournament/${Uri.encode(key)}"
    fun subEdit(id: Long = 0) = "subscription?id=$id"
    fun schedule(uid: String) = "schedule/${Uri.encode(uid)}"
    fun match(id: String) = "match/${Uri.encode(id)}"
}

/** Navigation actions shared by all screens. */
class AppActions(private val nav: NavHostController, private val playFn: (String, ChannelContext) -> Unit) {
    fun play(uid: String, context: ChannelContext) = playFn(uid, context)
    fun open(route: String) = nav.navigate(route) { launchSingleTop = true }
    fun back() = nav.popBackStack()
}

private data class Dest(val route: String, val label: Int, val icon: ImageVector)

private val allDestinations = listOf(
    Dest(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    Dest(Routes.LIVE, R.string.nav_live, Icons.Filled.LiveTv),
    Dest(Routes.SPORTS, R.string.nav_sports, Icons.Filled.SportsSoccer),
    Dest(Routes.MATCHES, R.string.nav_matches, Icons.Filled.Stadium),
    Dest(Routes.TOURNAMENTS, R.string.nav_tournaments, Icons.Filled.EmojiEvents),
    Dest(Routes.FAVORITES, R.string.nav_favorites, Icons.Filled.Favorite),
    Dest(Routes.HISTORY, R.string.nav_history, Icons.Filled.History),
    Dest(Routes.SEARCH, R.string.nav_search, Icons.Filled.Search),
    Dest(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

private val phoneDestinations = listOf(
    Dest(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    Dest(Routes.LIVE, R.string.nav_live, Icons.Filled.LiveTv),
    Dest(Routes.MATCHES, R.string.nav_matches, Icons.Filled.Stadium),
    Dest(Routes.FAVORITES, R.string.nav_favorites, Icons.Filled.Favorite),
    Dest(Routes.MORE, R.string.nav_more, Icons.Filled.MoreHoriz),
)

@Composable
fun AppRoot(mainViewModel: MainViewModel) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val tv = remember { isTv(context) }
    val wide = tv || LocalConfiguration.current.screenWidthDp >= 840
    val actions = remember(nav) {
        AppActions(nav) { uid, ctx -> context.startActivity(PlayerActivity.intent(context, uid, ctx)) }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val pendingMatch by mainViewModel.pendingMatch.collectAsState()
    LaunchedEffect(pendingMatch) {
        pendingMatch?.let { nav.navigate(Routes.match(it)); mainViewModel.consumeMatch() }
    }

    fun go(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    val destinations = if (wide) allDestinations else phoneDestinations
    val moreRoutes = setOf(Routes.MORE, Routes.SPORTS, Routes.TOURNAMENTS, Routes.HISTORY, Routes.SEARCH, Routes.SETTINGS, Routes.SUBSCRIPTIONS)

    Scaffold(
        bottomBar = {
            if (!wide) NavigationBar {
                destinations.forEach { d ->
                    val selected = current == d.route || (d.route == Routes.MORE && current in moreRoutes)
                    NavigationBarItem(
                        selected = selected, onClick = { go(d.route) },
                        icon = { Icon(d.icon, null) },
                        label = { Text(stringResource(d.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (wide) NavigationRail(
                header = { Icon(painterResource(R.drawable.ic_logo_mark), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 12.dp).size(36.dp)) },
            ) {
                destinations.forEach { d ->
                    NavigationRailItem(
                        selected = current == d.route,
                        onClick = { go(d.route) },
                        icon = { Icon(d.icon, null) },
                        label = { Text(stringResource(d.label), maxLines = 1) },
                        modifier = Modifier.tvFocusBorder(),
                    )
                }
            }
            NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.weight(1f)) {
                composable(Routes.HOME) { HomeScreen(actions) }
                composable(Routes.LIVE) { ChannelBrowserScreen(actions, sportsOnly = false, wide = wide) }
                composable(Routes.SPORTS) { ChannelBrowserScreen(actions, sportsOnly = true, wide = wide) }
                composable(Routes.MATCHES) { MatchesScreen(actions) }
                composable(Routes.TOURNAMENTS) { TournamentsScreen(actions, wide) }
                composable(Routes.TOURNAMENT, arguments = listOf(navArgument("key") { type = NavType.StringType })) { TournamentScreen(actions) }
                composable(Routes.FAVORITES) { FavoritesScreen(actions) }
                composable(Routes.HISTORY) { HistoryScreen(actions) }
                composable(Routes.SEARCH) { SearchScreen(actions) }
                composable(Routes.SETTINGS) { SettingsScreen(actions) }
                composable(Routes.SUBSCRIPTIONS) { SubscriptionsScreen(actions) }
                composable(
                    Routes.SUB_EDIT,
                    arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
                ) { SubscriptionEditScreen(actions) }
                composable(Routes.SCHEDULE, arguments = listOf(navArgument("uid") { type = NavType.StringType })) { ScheduleScreen(actions) }
                composable(Routes.MATCH, arguments = listOf(navArgument("id") { type = NavType.StringType })) { MatchDetailScreen(actions) }
                composable(Routes.MORE) { MoreScreen(actions) }
            }
        }
    }
}
