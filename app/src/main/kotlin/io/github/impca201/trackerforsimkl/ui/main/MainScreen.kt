package io.github.impca201.trackerforsimkl.ui.main

import io.github.impca201.trackerforsimkl.ui.components.label
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.impca201.trackerforsimkl.data.AppSettings
import io.github.impca201.trackerforsimkl.data.NavTab
import io.github.impca201.trackerforsimkl.data.model.MediaType
import io.github.impca201.trackerforsimkl.ui.calendar.CalendarScreen
import io.github.impca201.trackerforsimkl.ui.discover.DiscoverScreen
import io.github.impca201.trackerforsimkl.ui.library.MoviesScreen
import io.github.impca201.trackerforsimkl.ui.library.ShowsScreen

/**
 * True while the docked navigation bar is shown below a destination: the bar then takes care of
 * the system navigation bar, so the destination's content doesn't have to.
 */
val LocalNavigationBarShown = staticCompositionLocalOf { false }

fun NavTab.icon(selected: Boolean): ImageVector =
    when (this) {
        NavTab.Tv -> if (selected) Icons.Rounded.Tv else Icons.Outlined.Tv
        NavTab.Anime -> if (selected) Icons.Rounded.Animation else Icons.Outlined.Animation
        NavTab.Movies -> if (selected) Icons.Rounded.Movie else Icons.Outlined.Movie
        NavTab.Discover -> if (selected) Icons.Rounded.Explore else Icons.Outlined.Explore
        NavTab.Calendar -> if (selected) Icons.Rounded.CalendarMonth else Icons.Outlined.CalendarMonth
    }

/**
 * The destinations of the app with Material 3's navigation bar docked at the bottom edge. With
 * only one destination turned on in Settings there is nothing to switch to, so no bar. Library
 * tabs of media types turned off in Settings are left out.
 */
@Composable
fun MainScreen(settings: AppSettings) {
    val tabs = settings.tabs.ifEmpty { listOf(NavTab.Discover) }
    var selectedName by rememberSaveable { mutableStateOf(tabs.first().name) }
    val selected = tabs.firstOrNull { it.name == selectedName } ?: tabs.first()
    val showBar = tabs.size > 1

    // Each tab keeps its own saved state (shelf, sort, filters, scroll) while another tab is shown.
    val tabStates = rememberSaveableStateHolder()

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (showBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
        ) {
            CompositionLocalProvider(LocalNavigationBarShown provides showBar) {
                tabStates.SaveableStateProvider(selected.name) {
                    when (selected) {
                        NavTab.Tv -> ShowsScreen(MediaType.Tv, settings)
                        NavTab.Anime -> ShowsScreen(MediaType.Anime, settings)
                        NavTab.Movies -> MoviesScreen()
                        NavTab.Discover -> DiscoverScreen(settings.typeList)
                        NavTab.Calendar -> CalendarScreen(settings.typeList)
                    }
                }
            }
        }
        if (showBar) {
            NavigationBar {
                tabs.forEach { tab ->
                    val isSelected = tab == selected
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedName = tab.name },
                        icon = { Icon(tab.icon(isSelected), contentDescription = null) },
                        label = { Text(tab.label(), maxLines = 1) },
                    )
                }
            }
        }
    }
}
