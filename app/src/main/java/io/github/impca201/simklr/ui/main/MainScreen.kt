package io.github.impca201.simklr.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.impca201.simklr.data.AppSettings
import io.github.impca201.simklr.data.NavTab
import io.github.impca201.simklr.data.model.MediaType
import io.github.impca201.simklr.ui.calendar.CalendarScreen
import io.github.impca201.simklr.ui.discover.DiscoverScreen
import io.github.impca201.simklr.ui.library.MoviesScreen
import io.github.impca201.simklr.ui.library.ShowsScreen

/** Space the content leaves free at the bottom for the floating bar. */
val FloatingBarSpace: Dp = 96.dp

fun NavTab.icon(selected: Boolean): ImageVector =
    when (this) {
        NavTab.Tv -> if (selected) Icons.Filled.Tv else Icons.Outlined.Tv
        NavTab.Anime -> if (selected) Icons.Filled.Animation else Icons.Outlined.Animation
        NavTab.Movies -> if (selected) Icons.Filled.Movie else Icons.Outlined.Movie
        NavTab.Discover -> if (selected) Icons.Filled.Explore else Icons.Outlined.Explore
        NavTab.Calendar -> if (selected) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth
    }

@Composable
fun MainScreen(settings: AppSettings) {
    val tabs = settings.nav.visible.ifEmpty { listOf(NavTab.Tv) }
    var selectedName by rememberSaveable { mutableStateOf(tabs.first().name) }
    val selected = tabs.firstOrNull { it.name == selectedName } ?: tabs.first()
    var barVisible by remember { mutableStateOf(true) }
    val hideOnScroll =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y < -6f) barVisible = false
                    if (available.y > 6f) barVisible = true
                    return Offset.Zero
                }
            }
        }

    Box(Modifier.fillMaxSize().nestedScroll(hideOnScroll)) {
        when (selected) {
            NavTab.Tv -> ShowsScreen(MediaType.Tv, settings)
            NavTab.Anime -> ShowsScreen(MediaType.Anime, settings)
            NavTab.Movies -> MoviesScreen()
            NavTab.Discover -> DiscoverScreen()
            NavTab.Calendar -> CalendarScreen()
        }
        AnimatedVisibility(
            visible = barVisible || tabs.size == 1,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (tabs.size > 1) {
                FloatingBar(
                    tabs = tabs,
                    selected = selected,
                    onSelect = {
                        selectedName = it.name
                        barVisible = true
                    },
                )
            }
        }
    }
}

/** A floating, pill-shaped navigation bar; the selected destination expands to show its label. */
@Composable
private fun FloatingBar(
    tabs: List<NavTab>,
    selected: NavTab,
    onSelect: (NavTab) -> Unit,
) {
    Surface(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 16.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier.padding(8.dp).height(52.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selected
                val background by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    label = "tabBackground",
                )
                val content by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tabContent",
                )
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(background)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                        .height(52.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(tab.icon(isSelected), contentDescription = tab.label, tint = content, modifier = Modifier.size(24.dp))
                    AnimatedVisibility(
                        visible = isSelected,
                        enter = expandHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                        exit = shrinkHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
                    ) {
                        Row {
                            Spacer(Modifier.width(8.dp))
                            Text(tab.label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
