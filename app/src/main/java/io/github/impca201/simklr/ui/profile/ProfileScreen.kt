package io.github.impca201.simklr.ui.profile

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.impca201.simklr.data.model.Images
import io.github.impca201.simklr.data.model.TypeStats
import io.github.impca201.simklr.data.model.UserProfile
import io.github.impca201.simklr.data.model.UserStats
import io.github.impca201.simklr.ui.LocalNavigator
import io.github.impca201.simklr.ui.Route
import io.github.impca201.simklr.ui.components.Format
import io.github.impca201.simklr.ui.components.openUrl
import io.github.impca201.simklr.ui.components.rememberApp
import io.github.impca201.simklr.ui.detail.label
import io.github.impca201.simklr.ui.search.label
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
                title = { Text("Profile") },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { navigator.push(Route.Settings) }) { Icon(Icons.Rounded.Settings, "Settings") } },
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
            item { ProfileHeader(profile) }
            val s = stats
            if (s == null) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else {
                item { Totals(s) }
                s.types.forEach { t -> item { TypeCard(t) } }
            }
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column {
                        val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ListItem(
                            headlineContent = { Text("Sync now") },
                            supportingContent = {
                                Text(if (library.syncedAt > 0) "Last synced ${Format.ago(java.time.Instant.ofEpochMilli(library.syncedAt).toString())}" else "Never synced")
                            },
                            leadingContent = { Icon(Icons.Rounded.Sync, null) },
                            colors = colors,
                            trailingContent = {
                                if (library.syncing) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    TextButton(onClick = { scope.launch { app.library.sync(force = true) } }) { Text("Sync") }
                                }
                            },
                        )
                        profile?.let { p ->
                            ListItem(
                                headlineContent = { Text("Open my SIMKL page") },
                                leadingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                                colors = colors,
                                modifier = Modifier.clickableRow { context.openUrl("https://simkl.com/${p.id}/") },
                            )
                        }
                        ListItem(
                            headlineContent = { Text("Settings") },
                            leadingContent = { Icon(Icons.Rounded.Settings, null) },
                            colors = colors,
                            modifier = Modifier.clickableRow { navigator.push(Route.Settings) },
                        )
                        ListItem(
                            headlineContent = { Text("Log out", color = MaterialTheme.colorScheme.error) },
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
            title = { Text("Log out?") },
            text = { Text("Your lists stay on SIMKL. This only removes them from this device.") },
            confirmButton = { TextButton(onClick = { scope.launch { app.logout() } }) { Text("Log out") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
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
                    profile?.joinedAt?.let { Format.date(it) }?.let { "Joined $it" },
                    profile?.location,
                ).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            profile?.bio?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3) }
        }
    }
}

@Composable
private fun Totals(stats: UserStats) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Stat("Time watched", Format.duration(stats.totalMinutes))
            stats.lastWeekMinutes?.let { Stat("Last 7 days", Format.duration(it)) }
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
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.type.label(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    listOfNotNull(Format.duration(t.minutes), t.watchedEpisodes?.let { "$it episodes" }).joinToString(" · "),
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
