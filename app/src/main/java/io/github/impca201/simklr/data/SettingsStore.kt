package io.github.impca201.simklr.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.impca201.simklr.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Destinations of the floating bottom bar. Labels follow SIMKL's own naming. */
enum class NavTab(
    val label: String,
) {
    Tv("TV Shows"),
    Anime("Anime"),
    Movies("Movies"),
    Discover("Discover"),
    Calendar("Calendar"),
    ;

    companion object {
        val defaultOrder = listOf(Tv, Movies, Anime, Discover, Calendar)
        val defaultEnabled = setOf(Tv, Movies, Anime, Discover)
    }
}

enum class ThemeMode { System, Light, Dark }

data class NavConfig(
    /** Every tab, in the user's order. */
    val order: List<NavTab>,
    val enabled: Set<NavTab>,
) {
    val visible: List<NavTab> get() = order.filter { it in enabled }
}

data class AppSettings(
    val clientId: String = "",
    val accessToken: String? = null,
    val theme: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val nav: NavConfig = NavConfig(NavTab.defaultOrder, NavTab.defaultEnabled),
    val notifications: Boolean = false,
    val hideSpecials: Boolean = true,
)

class SettingsStore(
    private val context: Context,
) {
    private object Keys {
        val clientId = stringPreferencesKey("client_id")
        val token = stringPreferencesKey("access_token")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val navOrder = stringPreferencesKey("nav_order")
        val navEnabled = stringSetPreferencesKey("nav_enabled")
        val notifications = booleanPreferencesKey("notifications")
        val hideSpecials = booleanPreferencesKey("hide_specials")
        val notified = stringSetPreferencesKey("notified_episodes")
    }

    val settings: Flow<AppSettings> =
        context.dataStore.data.map { p ->
            val order =
                p[Keys.navOrder]?.split(",")?.mapNotNull { n -> NavTab.entries.firstOrNull { it.name == n } }.orEmpty()
            AppSettings(
                clientId = p[Keys.clientId] ?: BuildConfig.DEFAULT_CLIENT_ID,
                accessToken = p[Keys.token],
                theme = ThemeMode.entries.firstOrNull { it.name == p[Keys.theme] } ?: ThemeMode.System,
                dynamicColor = p[Keys.dynamicColor] ?: true,
                nav =
                    NavConfig(
                        // Tabs added in later versions are appended to a saved order.
                        order = (order + NavTab.defaultOrder).distinct(),
                        enabled =
                            p[Keys.navEnabled]?.mapNotNull { n -> NavTab.entries.firstOrNull { it.name == n } }?.toSet()
                                ?: NavTab.defaultEnabled,
                    ),
                notifications = p[Keys.notifications] ?: false,
                hideSpecials = p[Keys.hideSpecials] ?: true,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun clientId(): String? = current().clientId.takeIf { it.isNotBlank() }

    suspend fun token(): String? = current().accessToken

    suspend fun setClientId(value: String) = context.dataStore.edit { it[Keys.clientId] = value.trim() }

    suspend fun setToken(value: String?) =
        context.dataStore.edit { prefs ->
            if (value == null) {
                prefs.remove(Keys.token)
            } else {
                prefs[Keys.token] = value
            }
        }

    suspend fun setTheme(value: ThemeMode) = context.dataStore.edit { it[Keys.theme] = value.name }

    suspend fun setDynamicColor(value: Boolean) = context.dataStore.edit { it[Keys.dynamicColor] = value }

    suspend fun setNav(value: NavConfig) =
        context.dataStore.edit {
            it[Keys.navOrder] = value.order.joinToString(",") { t -> t.name }
            it[Keys.navEnabled] = value.enabled.map { t -> t.name }.toSet()
        }

    suspend fun setNotifications(value: Boolean) = context.dataStore.edit { it[Keys.notifications] = value }

    suspend fun setHideSpecials(value: Boolean) = context.dataStore.edit { it[Keys.hideSpecials] = value }

    suspend fun notifiedEpisodes(): Set<String> = context.dataStore.data.first()[Keys.notified].orEmpty()

    suspend fun setNotifiedEpisodes(value: Set<String>) = context.dataStore.edit { it[Keys.notified] = value }
}
