package io.github.impca201.simklr.ui.settings

import io.github.impca201.simklr.ui.components.label
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.impca201.simklr.BuildConfig
import io.github.impca201.simklr.R
import io.github.impca201.simklr.data.AppSettings
import io.github.impca201.simklr.data.NavConfig
import io.github.impca201.simklr.data.ThemeMode
import io.github.impca201.simklr.data.api.SimklApi
import io.github.impca201.simklr.ui.LocalNavigator
import io.github.impca201.simklr.ui.components.openUrl
import io.github.impca201.simklr.ui.components.rememberApp
import io.github.impca201.simklr.ui.library.SectionHeader
import io.github.impca201.simklr.ui.login.AuthGuideDialog
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
    var showGuide by remember { mutableStateOf(false) }

    fun enableNotifications() {
        scope.launch {
            app.settings.setNotifications(true)
            NewEpisodesWorker.enable(context)
        }
    }
    var notificationsAllowed by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationsAllowed = granted
            if (granted) enableNotifications()
        }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screens_settings)) },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.screens_back)) } },
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
            item { SectionHeader(stringResource(R.string.screens_settings_appearance)) }
            item {
                Group {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Rounded.Palette, null)
                            Text(stringResource(R.string.screens_settings_theme), style = MaterialTheme.typography.titleMedium)
                        }
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = settings.theme == mode,
                                    onClick = { scope.launch { app.settings.setTheme(mode) } },
                                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                ) { Text(mode.label()) }
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SwitchRow(stringResource(R.string.screens_settings_dynamic_color), stringResource(R.string.screens_settings_dynamic_color_sub), settings.dynamicColor) {
                            scope.launch { app.settings.setDynamicColor(it) }
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.screens_settings_bottom_bar)) }
            item {
                Group {
                    Text(
                        stringResource(R.string.screens_settings_bottom_bar_hint),
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
                            headlineContent = { Text(tab.label()) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        enabled = index > 0,
                                        onClick = {
                                            val list = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                                            scope.launch { app.settings.setNav(NavConfig(list, settings.nav.enabled)) }
                                        },
                                    ) { Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.screens_settings_move_up)) }
                                    IconButton(
                                        enabled = index < order.lastIndex,
                                        onClick = {
                                            val list = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                                            scope.launch { app.settings.setNav(NavConfig(list, settings.nav.enabled)) }
                                        },
                                    ) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.screens_settings_move_down)) }
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

            item { SectionHeader(stringResource(R.string.screens_settings_library)) }
            item {
                Group {
                    SwitchRow(stringResource(R.string.screens_settings_hide_specials), stringResource(R.string.screens_settings_hide_specials_sub), settings.hideSpecials, Icons.Rounded.VisibilityOff) {
                        scope.launch { app.settings.setHideSpecials(it) }
                    }
                    SwitchRow(
                        stringResource(R.string.screens_settings_continue_latest),
                        stringResource(R.string.screens_settings_continue_latest_sub),
                        settings.continueFromLatest,
                        Icons.Rounded.SkipNext,
                    ) { on ->
                        scope.launch {
                            app.settings.setContinueFromLatest(on)
                            if (on) app.library.refreshLatestNext()
                        }
                    }
                    SwitchRow(
                        stringResource(R.string.screens_settings_notifications),
                        stringResource(R.string.screens_settings_notifications_sub),
                        // Shown off when the permission was denied or revoked, so turning it on asks again.
                        settings.notifications && notificationsAllowed,
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
                        headlineContent = { Text(stringResource(R.string.screens_settings_client_id)) },
                        supportingContent = { Text(settings.clientId.take(6) + "••••••" + settings.clientId.takeLast(4)) },
                        trailingContent = { TextButton(onClick = { editClientId = true }) { Text(stringResource(R.string.screens_settings_change)) } },
                    )
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.HelpOutline, null) },
                        headlineContent = { Text(stringResource(R.string.guide_title)) },
                        supportingContent = { Text(stringResource(R.string.screens_settings_guide_sub)) },
                        modifier = Modifier.clickable { showGuide = true },
                    )
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                        headlineContent = { Text(stringResource(R.string.screens_settings_manage_apps)) },
                        trailingContent = { TextButton(onClick = { context.openUrl("https://simkl.com/settings/developer/") }) { Text(stringResource(R.string.screens_settings_open)) } },
                    )
                }
            }

            item {
                TextButton(onClick = { context.openUrl("https://simkl.com/") }) { Text(stringResource(R.string.screens_powered_by)) }
                Text(
                    stringResource(R.string.screens_settings_about, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (showGuide) AuthGuideDialog { showGuide = false }

    if (editClientId) {
        var value by remember { mutableStateOf(settings.clientId) }
        AlertDialog(
            onDismissRequest = { editClientId = false },
            title = { Text(stringResource(R.string.screens_settings_client_id_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.screens_settings_client_id_dialog_body), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value, { value = it.trim() }, singleLine = true, label = { Text(stringResource(R.string.screens_client_id)) })
                    TextButton(onClick = { context.openUrl(SimklApi.DEVELOPER_URL) }) { Text(stringResource(R.string.create_simkl_app)) }
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
                ) { Text(stringResource(R.string.screens_settings_save)) }
            },
            dismissButton = { TextButton(onClick = { editClientId = false }) { Text(stringResource(R.string.screens_cancel)) } },
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
