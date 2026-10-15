package io.github.impca201.trackerforsimkl.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

// A corrupt file is reset instead of crashing every launch.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = DataFiles.DEVICE,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private val Context.backedUpStore: DataStore<Preferences> by preferencesDataStore(
    name = DataFiles.BACKED_UP_SETTINGS,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    // These settings used to live in the device file; move them over once.
    produceMigrations = { context -> listOf(MoveKeysMigration(context.dataStore, SettingsStore.BACKED_UP_KEYS)) },
)

/**
 * Names of the DataStore files. Only [BACKED_UP_SETTINGS] goes into Android's backup and
 * phone-to-phone transfer (`res/xml/data_extraction_rules.xml`, `res/xml/backup_rules.xml`,
 * checked by `BackupRulesTest`).
 */
object DataFiles {
    /** Appearance, bottom bar and list settings. Backed up. */
    const val BACKED_UP_SETTINGS = "backed_up_settings"

    /** Sign-in tokens and notification state: never leave this phone. Historical name. */
    const val DEVICE = "settings"

    /** Where DataStore keeps a file, relative to the app's files folder (backup rules use this). */
    fun path(name: String) = "datastore/$name.preferences_pb"
}

/** Destinations of the floating bottom bar. Labels follow SIMKL's own naming. */
enum class NavTab {
    Tv,
    Anime,
    Movies,
    Discover,
    Calendar,
    ;

    companion object {
        val defaultOrder = listOf(Tv, Movies, Anime, Discover, Calendar)
        val defaultEnabled = setOf(Tv, Movies, Anime, Discover, Calendar)
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
    val accessToken: String? = null,
    val theme: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val nav: NavConfig = NavConfig(NavTab.defaultOrder, NavTab.defaultEnabled),
    val notifications: Boolean = false,
    val hideSpecials: Boolean = true,
    /** Up next starts after the most recently watched episode, ignoring skipped ones before it. */
    val continueFromLatest: Boolean = false,
)

/**
 * App settings and the SIMKL sign-in. Settings the user chose are in a backed-up file; tokens and
 * notification state stay on this phone (see [DataFiles]).
 */
class SettingsStore(
    private val context: Context,
) {
    internal object Keys {
        val token = stringPreferencesKey("access_token")
        val refreshToken = stringPreferencesKey("refresh_token")
        val tokenExpiresAt = longPreferencesKey("token_expires_at")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val navOrder = stringPreferencesKey("nav_order")
        val navEnabled = stringSetPreferencesKey("nav_enabled")
        val notifications = booleanPreferencesKey("notifications")
        val hideSpecials = booleanPreferencesKey("hide_specials")
        val continueFromLatest = booleanPreferencesKey("continue_from_latest")
        val notified = stringSetPreferencesKey("notified_episodes")
        val authState = stringPreferencesKey("auth_state")
        val authVerifier = stringPreferencesKey("auth_verifier")
    }

    val settings: Flow<AppSettings> =
        combine(context.dataStore.data.ignoringReadErrors(), context.backedUpStore.data.ignoringReadErrors()) { d, p ->
            val order =
                p[Keys.navOrder]?.split(",")?.mapNotNull { n -> NavTab.entries.firstOrNull { it.name == n } }.orEmpty()
            AppSettings(
                accessToken = d[Keys.token],
                theme = ThemeMode.entries.firstOrNull { it.name == p[Keys.theme] } ?: ThemeMode.System,
                dynamicColor = p[Keys.dynamicColor] ?: false,
                nav =
                    NavConfig(
                        // Tabs added in later versions are appended to a saved order.
                        order = (order + NavTab.defaultOrder).distinct(),
                        enabled =
                            p[Keys.navEnabled]?.mapNotNull { n -> NavTab.entries.firstOrNull { it.name == n } }?.toSet()
                                ?: NavTab.defaultEnabled,
                    ),
                notifications = d[Keys.notifications] ?: false,
                hideSpecials = p[Keys.hideSpecials] ?: true,
                continueFromLatest = p[Keys.continueFromLatest] ?: false,
            )
        }

    private fun Flow<Preferences>.ignoringReadErrors() =
        catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    suspend fun current(): AppSettings = settings.first()

    suspend fun token(): String? = current().accessToken

    /** AUTH V2 refresh token and access-token expiry (epoch ms); null for AUTH V1 tokens, which never expire. */
    suspend fun refreshToken(): String? = context.dataStore.data.first()[Keys.refreshToken]

    suspend fun tokenExpiresAt(): Long? = context.dataStore.data.first()[Keys.tokenExpiresAt]

    suspend fun setTokens(
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long?,
    ) = context.dataStore.edit { prefs ->
        prefs[Keys.token] = accessToken
        if (refreshToken != null) prefs[Keys.refreshToken] = refreshToken else prefs -= Keys.refreshToken
        if (expiresAt != null) prefs[Keys.tokenExpiresAt] = expiresAt else prefs -= Keys.tokenExpiresAt
    }

    /**
     * The state and PKCE code verifier of a sign-in waiting for the browser to come back. Kept on
     * disk because Android may stop the app while the user is approving on simkl.com.
     */
    suspend fun setPendingAuth(
        state: String,
        verifier: String,
    ) = context.dataStore.edit {
        it[Keys.authState] = state
        it[Keys.authVerifier] = verifier
    }

    suspend fun pendingAuth(): Pair<String, String>? {
        val prefs = context.dataStore.data.first()
        return (prefs[Keys.authState] ?: return null) to (prefs[Keys.authVerifier] ?: return null)
    }

    suspend fun clearPendingAuth() =
        context.dataStore.edit {
            it -= Keys.authState
            it -= Keys.authVerifier
        }

    suspend fun setToken(value: String?) =
        context.dataStore.edit { prefs ->
            if (value == null) {
                prefs -= Keys.token
                prefs -= Keys.refreshToken
                prefs -= Keys.tokenExpiresAt
            } else {
                // An AUTH V1 token: it never expires and has no refresh token.
                prefs[Keys.token] = value
                prefs -= Keys.refreshToken
                prefs -= Keys.tokenExpiresAt
            }
        }

    suspend fun setTheme(value: ThemeMode) = context.backedUpStore.edit { it[Keys.theme] = value.name }

    suspend fun setDynamicColor(value: Boolean) = context.backedUpStore.edit { it[Keys.dynamicColor] = value }

    suspend fun setNav(value: NavConfig) =
        context.backedUpStore.edit {
            it[Keys.navOrder] = value.order.joinToString(",") { t -> t.name }
            it[Keys.navEnabled] = value.enabled.map { t -> t.name }.toSet()
        }

    suspend fun setNotifications(value: Boolean) = context.dataStore.edit { it[Keys.notifications] = value }

    suspend fun setHideSpecials(value: Boolean) = context.backedUpStore.edit { it[Keys.hideSpecials] = value }

    suspend fun setContinueFromLatest(value: Boolean) = context.backedUpStore.edit { it[Keys.continueFromLatest] = value }

    suspend fun notifiedEpisodes(): Set<String> = context.dataStore.data.first()[Keys.notified].orEmpty()

    suspend fun setNotifiedEpisodes(value: Set<String>) = context.dataStore.edit { it[Keys.notified] = value }

    companion object {
        /**
         * Moved to the backed-up file. Notifications stay: they need the notification permission
         * and a scheduled check on this phone, which a restore doesn't bring along.
         */
        internal val BACKED_UP_KEYS: Set<Preferences.Key<*>> =
            setOf(Keys.theme, Keys.dynamicColor, Keys.navOrder, Keys.navEnabled, Keys.hideSpecials, Keys.continueFromLatest)
    }
}
