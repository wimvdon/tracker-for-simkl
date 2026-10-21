package io.github.impca201.trackerforsimkl

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import io.github.impca201.trackerforsimkl.data.AccountRepository
import io.github.impca201.trackerforsimkl.data.CatalogRepository
import io.github.impca201.trackerforsimkl.data.LibraryRepository
import io.github.impca201.trackerforsimkl.data.SettingsStore
import io.github.impca201.trackerforsimkl.data.api.SimklApi
import io.github.impca201.trackerforsimkl.data.api.TmdbApi
import io.github.impca201.trackerforsimkl.work.NewEpisodesWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class TrackerApp :
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
    val library by lazy { LibraryRepository(this, api, settings, scope) }
    val tmdb by lazy { TmdbApi(http) }
    val catalog by lazy { CatalogRepository(api, tmdb) }
    val account by lazy { AccountRepository(api) }

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
        io.github.impca201.trackerforsimkl.ui.components.Format.context = this
        runCatching { NewEpisodesWorker.createChannel(this) }
        scope.launch { runCatching { NewEpisodesWorker.restore(this@TrackerApp) } }
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
        NewEpisodesWorker.cancel(this)
    }
}
