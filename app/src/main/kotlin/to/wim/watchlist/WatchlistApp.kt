package to.wim.watchlist

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import to.wim.watchlist.data.AccountRepository
import to.wim.watchlist.data.CatalogRepository
import to.wim.watchlist.data.EpisodeStore
import to.wim.watchlist.data.LibraryRepository
import to.wim.watchlist.data.SettingsStore
import to.wim.watchlist.data.SubscriptionRepository
import to.wim.watchlist.data.api.SimklApi
import to.wim.watchlist.data.api.TmdbApi
import to.wim.watchlist.work.NewEpisodesWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class WatchlistApp :
    Application(),
    SingletonImageLoader.Factory,
    Configuration.Provider {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "http"), 50L * 1024 * 1024))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val settings by lazy { SettingsStore(this) }
    val api by lazy { SimklApi(http, settings) }
    val episodes by lazy { EpisodeStore(this, api) }
    val account by lazy { AccountRepository(this, api) }
    val library by lazy { LibraryRepository(this, api, settings, episodes, scope) { account.settingsChanged() } }
    val tmdb by lazy { TmdbApi(http) }
    val subscription by lazy { SubscriptionRepository(this, settings, scope, BuildConfig.REQUIRE_SUBSCRIPTION) }
    val catalog by lazy { CatalogRepository(api, tmdb, episodes) }

    /** "Not now" on the TMDB token card: hidden until the app restarts. */
    val tmdbBannerHiddenForNow = MutableStateFlow(false)

    /** The latest sign-in redirect from simkl.com, until the login screen has handled it. */
    val authRedirect = MutableStateFlow<Uri?>(null)

    // WorkManager starts on first use: at app start only while notifications are on.
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Before the libraries' start-up providers run, so their crashes are reported too.
        CrashReporter.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        to.wim.watchlist.ui.components.Format.context = this
        runCatching { NewEpisodesWorker.createChannels(this) }
        scope.launch { runCatching { NewEpisodesWorker.restore(this@WatchlistApp) } }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .crossfade(true)
            .build()

    suspend fun logout() {
        settings.setToken(null)
        library.clear()
        account.clear()
        episodes.clear()
        NewEpisodesWorker.cancel(this)
        // Which episodes were announced belongs to the account that signed out.
        settings.setNotifiedEpisodes(emptySet())
        settings.setRememberedEpisodes(emptySet())
    }
}
