package io.github.impca201.trackerforsimkl.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.ContextCompat
import io.github.impca201.trackerforsimkl.BuildConfig
import io.github.impca201.trackerforsimkl.R
import io.github.impca201.trackerforsimkl.data.AppSettings
import io.github.impca201.trackerforsimkl.data.NavConfig
import io.github.impca201.trackerforsimkl.data.ThemeMode
import io.github.impca201.trackerforsimkl.ui.LocalNavigator
import io.github.impca201.trackerforsimkl.ui.components.label
import io.github.impca201.trackerforsimkl.ui.components.openUrl
import io.github.impca201.trackerforsimkl.ui.components.rememberApp
import io.github.impca201.trackerforsimkl.ui.main.icon
import io.github.impca201.trackerforsimkl.ui.theme.AppearanceSettings
import io.github.impca201.trackerforsimkl.ui.theme.SettingsBlock
import io.github.impca201.trackerforsimkl.ui.theme.SettingsSection
import io.github.impca201.trackerforsimkl.ui.theme.SettingsFooter
import io.github.impca201.trackerforsimkl.ui.theme.SettingsScaffold
import io.github.impca201.trackerforsimkl.ui.theme.SwitchSetting
import io.github.impca201.trackerforsimkl.work.NewEpisodesWorker
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: AppSettings) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    SettingsScaffold(
        title = stringResource(R.string.screens_settings),
        backLabel = stringResource(R.string.screens_back),
        onBack = { navigator.pop() },
    ) {
        item(key = "library") {
            SettingsSection(title = stringResource(R.string.screens_settings_library)) {
                SwitchSetting(
                    title = stringResource(R.string.screens_settings_hide_specials),
                    summary = stringResource(R.string.screens_settings_hide_specials_sub),
                    checked = settings.hideSpecials,
                    onCheckedChange = { scope.launch { app.settings.setHideSpecials(it) } },
                )
                SwitchSetting(
                    title = stringResource(R.string.screens_settings_continue_latest),
                    summary = stringResource(R.string.screens_settings_continue_latest_sub),
                    checked = settings.continueFromLatest,
                    onCheckedChange = { on ->
                        scope.launch {
                            app.settings.setContinueFromLatest(on)
                            if (on) app.library.refreshLatestNext()
                        }
                    },
                )
                SwitchSetting(
                    title = stringResource(R.string.screens_settings_notifications),
                    summary = stringResource(R.string.screens_settings_notifications_sub),
                    // Shown off when the permission was denied or revoked, so turning it on asks again.
                    checked = settings.notifications && notificationsAllowed,
                    onCheckedChange = { on ->
                        if (on) {
                            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else enableNotifications()
                        } else {
                            scope.launch {
                                app.settings.setNotifications(false)
                                NewEpisodesWorker.cancel(context)
                            }
                        }
                    },
                )
            }
        }

        item(key = "bottom-bar") {
            val order = settings.nav.order
            SettingsSection(title = stringResource(R.string.screens_settings_bottom_bar)) {
                SettingsBlock {
                    Text(
                        stringResource(R.string.screens_settings_bottom_bar_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                order.forEachIndexed { index, tab ->
                    val enabled = tab in settings.nav.enabled
                    ListItem(
                        headlineContent = { Text(tab.label()) },
                        leadingContent = { Icon(tab.icon(enabled), null) },
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

        item(key = "appearance") {
            SettingsSection(title = stringResource(R.string.screens_settings_appearance)) {
                AppearanceSettings(
                    themeLabel = stringResource(R.string.screens_settings_theme),
                    themeOptions = ThemeMode.entries.map { it.label() },
                    selectedTheme = settings.theme.ordinal,
                    onThemeSelect = { index -> scope.launch { app.settings.setTheme(ThemeMode.entries[index]) } },
                    dynamicColorTitle = stringResource(R.string.screens_settings_dynamic_color),
                    dynamicColorSummary = stringResource(R.string.screens_settings_dynamic_color_sub),
                    dynamicColor = settings.dynamicColor,
                    onDynamicColorChange = { scope.launch { app.settings.setDynamicColor(it) } },
                    dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                )
            }
        }

        item(key = "footer") {
            SettingsFooter(stringResource(R.string.screens_settings_about, BuildConfig.VERSION_NAME)) {
                TextButton(onClick = { context.openUrl("https://simkl.com/") }) { Text(stringResource(R.string.screens_powered_by)) }
            }
        }
    }
}
