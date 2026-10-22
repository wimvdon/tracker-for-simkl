package io.github.impca201.trackerforsimkl.ui.settings

import android.Manifest
import androidx.annotation.RequiresApi
import io.github.impca201.trackerforsimkl.data.model.NotifyChannel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import android.content.Context
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import io.github.impca201.trackerforsimkl.data.api.TmdbKey
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import io.github.impca201.trackerforsimkl.BuildConfig
import io.github.impca201.trackerforsimkl.R
import io.github.impca201.trackerforsimkl.data.AppSettings
import io.github.impca201.trackerforsimkl.data.NavConfig
import io.github.impca201.trackerforsimkl.data.ThemeMode
import io.github.impca201.trackerforsimkl.data.model.MediaType
import io.github.impca201.trackerforsimkl.data.toggled
import io.github.impca201.trackerforsimkl.ui.LocalNavigator
import io.github.impca201.trackerforsimkl.ui.components.label
import io.github.impca201.trackerforsimkl.ui.components.openUrl
import io.github.impca201.trackerforsimkl.ui.components.rememberApp
import io.github.impca201.trackerforsimkl.ui.main.icon
import io.github.impca201.trackerforsimkl.ui.theme.AppearanceSettings
import io.github.impca201.trackerforsimkl.ui.theme.DragHandle
import io.github.impca201.trackerforsimkl.ui.theme.ReorderableColumn
import io.github.impca201.trackerforsimkl.ui.theme.SettingsBlock
import io.github.impca201.trackerforsimkl.ui.theme.SettingsSection
import io.github.impca201.trackerforsimkl.ui.theme.SettingsFooter
import io.github.impca201.trackerforsimkl.ui.theme.SettingItem
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
    // Android's own notification settings, read again each time Settings comes back into view.
    var android by remember { mutableStateOf(AndroidNotifications.read(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { android = AndroidNotifications.read(context) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            android = AndroidNotifications.read(context)
            if (granted) {
                enableNotifications()
            } else if (Build.VERSION.SDK_INT >= 33 && !permissionAskable(context)) {
                // Android no longer shows the question: the switch is in its settings now.
                AndroidNotifications.openAppSettings(context)
            }
        }

    SettingsScaffold(
        title = stringResource(R.string.screens_settings),
        backLabel = stringResource(R.string.screens_back),
        onBack = { navigator.pop() },
    ) {
        item(key = "types") {
            SettingsSection(title = stringResource(R.string.screens_settings_types)) {
                SettingsBlock {
                    Text(
                        stringResource(R.string.screens_settings_types_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MediaType.entries.forEach { type ->
                    val on = type in settings.types
                    SwitchSetting(
                        title = type.label(),
                        summary = null,
                        checked = on,
                        // At least one type stays on.
                        enabled = !on || settings.types.size > 1,
                        onCheckedChange = { scope.launch { app.settings.setTypes(settings.types.toggled(type, it)) } },
                    )
                }
            }
        }

        item(key = "library") {
            SettingsSection(title = stringResource(R.string.screens_settings_library)) {
                SwitchSetting(
                    title = stringResource(R.string.screens_settings_hide_specials),
                    summary = null,
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
            }
        }

        item(key = "notifications") {
            val on = settings.notifications && android.appEnabled
            SettingsSection(title = stringResource(R.string.screens_settings_notifications_section)) {
                SwitchSetting(
                    title = stringResource(R.string.screens_settings_notifications),
                    summary = stringResource(R.string.screens_settings_notifications_sub),
                    // Off whenever Android blocks the app's notifications, so turning it on asks again.
                    checked = on,
                    onCheckedChange = { turnOn ->
                        when {
                            !turnOn ->
                                scope.launch {
                                    app.settings.setNotifications(false)
                                    NewEpisodesWorker.cancel(context)
                                }
                            Build.VERSION.SDK_INT >= 33 && !permissionGranted(context) -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            // Allowed, but switched off in Android's settings: only Android can turn it back on.
                            !android.appEnabled -> {
                                enableNotifications()
                                AndroidNotifications.openAppSettings(context)
                            }
                            else -> enableNotifications()
                        }
                    },
                )
                if (on) {
                    // One row per type that is on in Content, showing Android's own switch for its channel.
                    NotifyChannel.entries.filter { it.type in settings.types }.forEach { channel ->
                        SettingItem(
                            title = stringResource(channel.titleRes()),
                            summary =
                                stringResource(
                                    if (android.channelOn(channel)) R.string.screens_settings_channel_on else R.string.screens_settings_channel_off,
                                ),
                            onClick = { AndroidNotifications.openChannelSettings(context, channel) },
                            trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                        )
                    }
                }
                SettingItem(
                    title = stringResource(R.string.screens_settings_android_notifications),
                    summary = stringResource(R.string.screens_settings_android_notifications_sub),
                    onClick = { AndroidNotifications.openAppSettings(context) },
                    trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                )
            }
        }

        item(key = "tmdb") {
            var editing by rememberSaveable { mutableStateOf(false) }
            SettingsSection(title = stringResource(R.string.screens_settings_tmdb)) {
                SettingItem(
                    title = stringResource(R.string.screens_settings_tmdb_key),
                    summary =
                        stringResource(
                            when {
                                TmdbKey.parse(settings.tmdbKey) != null -> R.string.screens_settings_tmdb_key_on
                                // The API Key that version 1.6.0 accepted: it has to be replaced.
                                settings.tmdbKey != null -> R.string.screens_settings_tmdb_key_old
                                else -> R.string.screens_settings_tmdb_key_off
                            },
                        ),
                    onClick = { editing = true },
                )
            }
            if (editing) TmdbKeyDialog(current = settings.tmdbKey, onDismiss = { editing = false })
        }

        item(key = "bottom-bar") {
            // Tabs of media types that are off aren't listed; they come back when the type does.
            val shown = settings.nav.available(settings.types)
            val shownOn = shown.count { it in settings.nav.enabled }
            SettingsSection(title = stringResource(R.string.screens_settings_bottom_bar)) {
                ReorderableColumn(
                    items = shown,
                    key = { it },
                    onMove = { from, to -> scope.launch { app.settings.setNav(settings.nav.moved(shown[from], to, shown)) } },
                ) { tab ->
                    val enabled = tab in settings.nav.enabled
                    ListItem(
                        headlineContent = { Text(tab.label()) },
                        leadingContent = { Icon(tab.icon(enabled), null) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = enabled,
                                    // At least one destination stays on.
                                    enabled = !enabled || shownOn > 1,
                                    onCheckedChange = { on ->
                                        val set = if (on) settings.nav.enabled + tab else settings.nav.enabled - tab
                                        scope.launch { app.settings.setNav(NavConfig(settings.nav.order, set)) }
                                    },
                                )
                                DragHandle(handleModifier)
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

        item(key = "credits") {
            // Where the data comes from. TMDB's attribution (its approved logo, unchanged, and its
            // notice) is required in a credits section, whether or not a token is set.
            SettingsSection(title = stringResource(R.string.screens_settings_credits)) {
                CreditItem(
                    logo = painterResource(R.drawable.simkl_logo),
                    logoDescription = stringResource(R.string.screens_simkl_logo),
                    title = stringResource(R.string.screens_credit_simkl_title),
                    text = stringResource(R.string.screens_credit_simkl_text),
                    onClick = { context.openUrl("https://simkl.com/") },
                )
                CreditItem(
                    logo = painterResource(R.drawable.tmdb_logo_square),
                    logoDescription = stringResource(R.string.screens_tmdb_logo),
                    title = stringResource(R.string.screens_credit_tmdb_title),
                    text = stringResource(R.string.screens_credit_tmdb_text) + "\n" + stringResource(R.string.screens_tmdb_notice),
                    onClick = { context.openUrl("https://www.themoviedb.org/") },
                )
            }
        }

        item(key = "footer") {
            SettingsFooter(stringResource(R.string.screens_settings_version, BuildConfig.VERSION_NAME))
        }
    }
}

/** A source of the app's data in Settings › Credits: its logo (unchanged, never tinted), name and what it provides. */
@Composable
private fun CreditItem(
    logo: Painter,
    logoDescription: String,
    title: String,
    text: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(text) },
        leadingContent = {
            Image(logo, logoDescription, contentScale = ContentScale.Fit, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
        },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

private fun NotifyChannel.titleRes(): Int =
    when (this) {
        NotifyChannel.Tv -> R.string.screens_settings_channel_tv
        NotifyChannel.Anime -> R.string.screens_settings_channel_anime
        NotifyChannel.Movies -> R.string.screens_settings_channel_movies
    }

@RequiresApi(33)
private fun permissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** False once the user said no twice: Android then refuses silently, so the app opens its settings. */
@RequiresApi(33)
private fun permissionAskable(context: Context): Boolean =
    (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ?: true
