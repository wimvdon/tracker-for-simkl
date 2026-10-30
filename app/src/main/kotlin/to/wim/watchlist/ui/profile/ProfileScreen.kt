package to.wim.watchlist.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import to.wim.watchlist.R
import to.wim.watchlist.data.model.Images
import to.wim.watchlist.data.model.TypeStats
import to.wim.watchlist.data.model.Ratings
import to.wim.watchlist.data.model.MediaType
import to.wim.watchlist.data.model.UserProfile
import to.wim.watchlist.data.model.UserStats
import to.wim.watchlist.ui.LocalNavigator
import to.wim.watchlist.ui.Route
import to.wim.watchlist.ui.components.Format
import to.wim.watchlist.ui.components.openUrl
import to.wim.watchlist.ui.components.rememberApp
import to.wim.watchlist.ui.components.label
import to.wim.watchlist.ui.theme.AppShapes
import kotlinx.coroutines.launch
import to.wim.watchlist.ui.components.CardSkeleton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import to.wim.watchlist.ui.theme.readableWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(types: Set<MediaType>) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library by app.library.state.collectAsState()
    val profile by produceState<UserProfile?>(null) { value = runCatching { app.account.profile() }.getOrNull() }
    val stats by produceState<UserStats?>(null) { value = runCatching { app.account.stats() }.getOrNull() }
    var confirmLogout by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screens_profile_title)) },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.screens_back)) } },
                actions = { IconButton(onClick = { navigator.push(Route.Settings) }) { Icon(Icons.Rounded.Settings, stringResource(R.string.screens_settings)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding(),
                    bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // A readable width on wide windows, centred (DESIGN.md, about 640dp like Settings).
            modifier = Modifier.fillMaxSize().readableWidth(),
        ) {
            item(key = "header") { ProfileHeader(profile) }
            val s = stats
            // Keyed items so the stats fade in and the actions card glides down when they load.
            if (s == null) {
                item(key = "loading") {
                    val loading = stringResource(R.string.loading)
                    Column(
                        Modifier.animateItem().semantics(mergeDescendants = true) { contentDescription = loading },
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CardSkeleton(96.dp)
                        CardSkeleton(120.dp)
                        CardSkeleton(120.dp)
                    }
                }
            } else {
                item(key = "totals") { Box(Modifier.animateItem()) { Totals(s) } }
                // Only the media types turned on in Settings.
                s.types.filter { it.type in types }.forEachIndexed { i, t -> item(key = "type-$i") { Box(Modifier.animateItem()) { TypeCard(t) } } }
            }
            // What you watched and what you thought of it belong together: "My ratings" follows the
            // statistics, apart from the account actions below.
            item(key = "ratings") {
                val rated = remember(library.items, types) { Ratings.count(library.items, types) }
                Surface(shape = AppShapes.Group, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.animateItem()) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.screens_ratings)) },
                        supportingContent = {
                            Text(
                                if (rated > 0) {
                                    pluralStringResource(R.plurals.screens_profile_rated, rated, rated)
                                } else {
                                    stringResource(R.string.screens_ratings_empty)
                                },
                            )
                        },
                        leadingContent = { Icon(Icons.Rounded.Star, null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickableRow { navigator.push(Route.Ratings) },
                    )
                }
            }
            item(key = "actions") {
                Surface(shape = AppShapes.Group, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.animateItem()) {
                    Column {
                        val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.screens_profile_sync_now)) },
                            supportingContent = {
                                Column {
                                Text(
                                    if (library.syncedAt > 0) {
                                        stringResource(R.string.screens_profile_last_synced, Format.ago(java.time.Instant.ofEpochMilli(library.syncedAt).toString()).orEmpty())
                                    } else {
                                        stringResource(R.string.screens_profile_never_synced)
                                    },
                                )
                                library.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                }
                            },
                            leadingContent = { Icon(Icons.Rounded.Sync, null) },
                            colors = colors,
                            trailingContent = {
                                if (library.syncing) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    TextButton(onClick = { scope.launch { app.library.sync() } }) { Text(stringResource(R.string.screens_profile_sync)) }
                                }
                            },
                        )
                        profile?.let { p ->
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.screens_profile_open_page)) },
                                leadingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                                colors = colors,
                                modifier = Modifier.clickableRow { context.openUrl("https://simkl.com/${p.id}/") },
                            )
                        }
                        // Settings is the gear in the top bar; no second entry here.
                    }
                }
            }
            // Log out on its own, apart from the routine actions (DESIGN.md: destructive actions are
            // separated and confirmed).
            item(key = "logout") {
                Surface(shape = AppShapes.Group, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.animateItem()) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.screens_profile_log_out), color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = MaterialTheme.colorScheme.error) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickableRow { confirmLogout = true },
                    )
                }
            }
        }
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.screens_profile_log_out_title)) },
            text = { Text(stringResource(R.string.screens_profile_log_out_body)) },
            confirmButton = { TextButton(onClick = { scope.launch { app.logout() } }) { Text(stringResource(R.string.screens_profile_log_out)) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.screens_cancel)) } },
        )
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit) = clickable(onClick = onClick)

@Composable
private fun ProfileHeader(profile: UserProfile?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            val avatar = Images.avatar(profile?.avatar)
            if (avatar != null) {
                AsyncImage(avatar, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Rounded.Person, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val name =
                when {
                    profile == null -> "…"
                    profile.name.isBlank() -> stringResource(R.string.screens_profile_unnamed)
                    else -> profile.name
                }
            Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val meta =
                listOfNotNull(
                    profile?.accountType?.replaceFirstChar { it.uppercase() },
                    profile?.joinedAt?.let { Format.date(it) }?.let { stringResource(R.string.screens_profile_joined, it) },
                    profile?.location,
                ).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            profile?.bio?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3) }
        }
    }
}

@Composable
private fun Totals(stats: UserStats) {
    Surface(shape = AppShapes.Group, color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Stat(stringResource(R.string.screens_profile_time_watched), Format.duration(stats.totalMinutes))
            stats.lastWeekMinutes?.let { Stat(stringResource(R.string.screens_profile_last_7_days), Format.duration(it)) }
        }
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TypeCard(t: TypeStats) {
    Surface(shape = AppShapes.Group, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.type.label(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    listOfNotNull(
                        Format.duration(t.minutes),
                        t.watchedEpisodes?.let { pluralStringResource(R.plurals.screens_profile_episodes, it, it) },
                        t.leftToWatchMinutes?.takeIf { it > 0 }?.let { stringResource(R.string.screens_profile_time_left, Format.duration(it)) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                t.counts.forEach { c ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(c.count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(c.status.label(t.type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
