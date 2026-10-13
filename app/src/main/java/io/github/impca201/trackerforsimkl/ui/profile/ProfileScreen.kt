package io.github.impca201.trackerforsimkl.ui.profile

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
import io.github.impca201.trackerforsimkl.R
import io.github.impca201.trackerforsimkl.data.model.Images
import io.github.impca201.trackerforsimkl.data.model.TypeStats
import io.github.impca201.trackerforsimkl.data.model.UserProfile
import io.github.impca201.trackerforsimkl.data.model.UserStats
import io.github.impca201.trackerforsimkl.ui.LocalNavigator
import io.github.impca201.trackerforsimkl.ui.Route
import io.github.impca201.trackerforsimkl.ui.components.Format
import io.github.impca201.trackerforsimkl.ui.components.openUrl
import io.github.impca201.trackerforsimkl.ui.components.rememberApp
import io.github.impca201.trackerforsimkl.ui.components.label
import io.github.impca201.trackerforsimkl.ui.theme.AppShapes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library by app.library.state.collectAsState()
    val profile by produceState<UserProfile?>(null) { value = runCatching { app.account.profile(refresh = true) }.getOrNull() }
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
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { ProfileHeader(profile) }
            val s = stats
            // Keyed items so the stats fade in and the actions card glides down when they load.
            if (s == null) {
                item(key = "loading") {
                    Box(Modifier.animateItem().fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else {
                item(key = "totals") { Box(Modifier.animateItem()) { Totals(s) } }
                s.types.forEachIndexed { i, t -> item(key = "type-$i") { Box(Modifier.animateItem()) { TypeCard(t) } } }
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
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.screens_settings)) },
                            leadingContent = { Icon(Icons.Rounded.Settings, null) },
                            colors = colors,
                            modifier = Modifier.clickableRow { navigator.push(Route.Settings) },
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.screens_profile_log_out), color = MaterialTheme.colorScheme.error) },
                            leadingContent = { Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = MaterialTheme.colorScheme.error) },
                            colors = colors,
                            modifier = Modifier.clickableRow { confirmLogout = true },
                        )
                    }
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
            Text(profile?.name ?: "…", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
