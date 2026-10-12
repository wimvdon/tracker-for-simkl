package io.github.impca201.simklr.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.impca201.simklr.BuildConfig
import io.github.impca201.simklr.data.AppSettings
import io.github.impca201.simklr.data.NavConfig
import io.github.impca201.simklr.data.ThemeMode
import io.github.impca201.simklr.data.api.SimklApi
import io.github.impca201.simklr.ui.LocalNavigator
import io.github.impca201.simklr.ui.components.openUrl
import io.github.impca201.simklr.ui.components.rememberApp
import io.github.impca201.simklr.ui.library.SectionHeader
import io.github.impca201.simklr.ui.main.icon
import io.github.impca201.simklr.work.NewEpisodesWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: AppSettings) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editClientId by remember { mutableStateOf(false) }

    fun enableNotifications() {
        scope.launch {
            app.settings.setNotifications(true)
            NewEpisodesWorker.enable(context)
        }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) enableNotifications()
        }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { SectionHeader("Appearance") }
            item {
                Group {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Rounded.Palette, null)
                            Text("Theme", style = MaterialTheme.typography.titleMedium)
                        }
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = settings.theme == mode,
                                    onClick = { scope.launch { app.settings.setTheme(mode) } },
                                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                ) { Text(mode.name) }
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SwitchRow("Dynamic color", "Use colors from your wallpaper", settings.dynamicColor) {
                            scope.launch { app.settings.setDynamicColor(it) }
                        }
                    }
                }
            }

            item { SectionHeader("Bottom bar") }
            item {
                Group {
                    Text(
                        "Choose and order the destinations of the bottom bar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    )
                    val order = settings.nav.order
                    order.forEachIndexed { index, tab ->
                        val enabled = tab in settings.nav.enabled
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            leadingContent = { Icon(tab.icon(enabled), null) },
                            headlineContent = { Text(tab.label) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        enabled = index > 0,
                                        onClick = {
                                            val list = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                                            scope.launch { app.settings.setNav(NavConfig(list, settings.nav.enabled)) }
                                        },
                                    ) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                                    IconButton(
                                        enabled = index < order.lastIndex,
                                        onClick = {
                                            val list = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                                            scope.launch { app.settings.setNav(NavConfig(list, settings.nav.enabled)) }
                                        },
                                    ) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                                    Switch(
                                        checked = enabled,
                                        // At least one destination stays on.
                                        enabled = !enabled || settings.nav.enabled.size > 1,
                                        onCheckedChange = { on ->
                                            val set = if (on) settings.nav.enabled + tab else settings.nav.enabled - tab
                                            scope.launch { app.settings.setNav(NavConfig(order, set)) }
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }

            item { SectionHeader("Library") }
            item {
                Group {
                    SwitchRow("Hide specials", "Leave out special episodes in episode lists", settings.hideSpecials, Icons.Rounded.VisibilityOff) {
                        scope.launch { app.settings.setHideSpecials(it) }
                    }
                    SwitchRow(
                        "Continue from the latest episode",
                        "Up next starts after the most recently watched episode. Unwatched episodes or seasons before it are skipped.",
                        settings.continueFromLatest,
                        Icons.Rounded.SkipNext,
                    ) { on ->
                        scope.launch {
                            app.settings.setContinueFromLatest(on)
                            if (on) app.library.refreshLatestNext()
                        }
                    }
                    SwitchRow(
                        "New episode notifications",
                        "Checks SIMKL a few times a day for new episodes of shows you're watching",
                        settings.notifications,
                        Icons.Rounded.Notifications,
                    ) { on ->
                        if (on) {
                            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else enableNotifications()
                        } else {
                            scope.launch {
                                app.settings.setNotifications(false)
                                NewEpisodesWorker.cancel(context)
                            }
                        }
                    }
                }
            }

            item { SectionHeader("SIMKL") }
            item {
                Group {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        leadingContent = { Icon(Icons.Rounded.Key, null) },
                        headlineContent = { Text("API client ID") },
                        supportingContent = { Text(settings.clientId.take(6) + "••••••" + settings.clientId.takeLast(4)) },
                        trailingContent = { TextButton(onClick = { editClientId = true }) { Text("Change") } },
                    )
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                        headlineContent = { Text("Manage your SIMKL apps") },
                        trailingContent = { TextButton(onClick = { context.openUrl("https://simkl.com/settings/developer/") }) { Text("Open") } },
                    )
                }
            }

            item {
                TextButton(onClick = { context.openUrl("https://simkl.com/") }) { Text("Powered by SIMKL · simkl.com") }
                Text(
                    "SIMKLr ${BuildConfig.VERSION_NAME} · Not affiliated with SIMKL. Data and images © SIMKL and their sources.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (editClientId) {
        var value by remember { mutableStateOf(settings.clientId) }
        AlertDialog(
            onDismissRequest = { editClientId = false },
            title = { Text("SIMKL client ID") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Changing it logs you out, because tokens belong to one app.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value, { value = it.trim() }, singleLine = true, label = { Text("Client ID") })
                    TextButton(onClick = { context.openUrl(SimklApi.DEVELOPER_URL) }) { Text("Create a SIMKL app") }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = value.length >= 16,
                    onClick = {
                        editClientId = false
                        scope.launch {
                            if (value != settings.clientId) {
                                app.settings.setClientId(value)
                                app.logout()
                            }
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editClientId = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column { content() }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = if (icon != null) ({ Icon(icon, null) }) else null,
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) ({ Text(subtitle) }) else null,
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}
