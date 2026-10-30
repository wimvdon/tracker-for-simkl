package to.wim.watchlist.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import to.wim.watchlist.R
import to.wim.watchlist.WatchlistApp
import to.wim.watchlist.data.api.Parsers
import to.wim.watchlist.data.model.CalendarEntry
import to.wim.watchlist.data.model.Images
import to.wim.watchlist.data.model.MediaType
import to.wim.watchlist.data.model.NewEpisodes
import to.wim.watchlist.data.model.NewReleases
import to.wim.watchlist.data.model.NotifyChannel
import to.wim.watchlist.ui.MainActivity
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Notifies about newly aired episodes of shows the user is watching and released movies on their
 * "Plan to watch" list, each type in its own channel ([NotifyChannel]). It reads SIMKL's calendar
 * data files (static files on data.simkl.in's CDN, which don't count against the API) and the
 * library as it was last synced; it never syncs or calls the API in the background. What to
 * announce is decided by [NewEpisodes] and [NewReleases].
 */
class NewEpisodesWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as WatchlistApp
        val settings = app.settings.current()
        if (settings.accessToken == null || !settings.notifications) return Result.success()
        app.library.load()
        val library = app.library.state.value.items
        // No library copy yet (never synced): nothing to compare with, and nothing to mark as seen.
        if (library.isEmpty()) return Result.success()
        val calendar =
            try {
                airedAndUpcoming(app)
            } catch (e: Exception) {
                return Result.retry()
            }
        // Movies are extra: a failed release file doesn't hold up the episodes.
        val releases = if (MediaType.Movie in settings.types) runCatching { movieReleases(app) }.getOrDefault(emptyList()) else emptyList()
        val now = Instant.now()
        // Episodes and movies share the stored sets; their keys never clash.
        val notified = app.settings.notifiedEpisodes()
        val remembered = app.settings.rememberedEpisodes()
        val episodes = NewEpisodes.plan(calendar, remembered, library, notified, now, settings.types)
        val movies = NewReleases.plan(releases, remembered, library, notified, now)
        val showInfo = app.library.state.value.showInfo
        episodes.alerts.forEach { notify(applicationContext, it, showInfo[it.item.key]?.fanart) }
        movies.alerts.forEach { notifyRelease(applicationContext, it) }
        app.settings.setNotifiedEpisodes(episodes.notified + movies.notified)
        app.settings.setRememberedEpisodes(episodes.remembered + movies.remembered)
        val next = listOfNotNull(NewEpisodes.nextAirDate(episodes.remembered, now), NewReleases.nextReleaseDate(movies.remembered, now)).minOrNull()
        scheduleAtAirTime(applicationContext, next, now)
        return Result.success()
    }

    companion object {
        private const val WORK = "new_episodes"
        private const val AIR_TIME_WORK = "new_episodes_air_time"

        /** SIMKL's air times are when an episode starts; give the calendar files a moment to catch up. */
        private val AIR_TIME_MARGIN: Duration = Duration.ofMinutes(15)

        /** TV and anime episodes from SIMKL's calendar files. */
        private suspend fun airedAndUpcoming(app: WatchlistApp): List<CalendarEntry> =
            listOf(MediaType.Tv, MediaType.Anime).flatMap { type ->
                Parsers.calendar(app.api.calendar(type), type).filter { it.episode != null }
            }

        /** Movie releases from SIMKL's calendar file. */
        private suspend fun movieReleases(app: WatchlistApp): List<CalendarEntry> = Parsers.calendar(app.api.calendar(MediaType.Movie), MediaType.Movie)

        /**
         * Creates (or renames, in the phone's language) one channel per type. Android keeps what
         * the user changed in a channel; only its name and description are updated.
         */
        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            NotifyChannel.entries.forEach { c ->
                val (name, description) =
                    when (c) {
                        NotifyChannel.Tv -> R.string.data_notif_channel_tv to R.string.data_notif_channel_tv_description
                        NotifyChannel.Anime -> R.string.data_notif_channel_anime to R.string.data_notif_channel_anime_description
                        NotifyChannel.Movies -> R.string.data_notif_channel_movies to R.string.data_notif_channel_movies_description
                    }
                val channel = NotificationChannel(c.id, context.getString(name), NotificationManager.IMPORTANCE_DEFAULT)
                channel.description = context.getString(description)
                manager.createNotificationChannel(channel)
            }
        }

        /** Starts the periodic check; episodes that already aired are not announced. */
        suspend fun enable(context: Context) {
            val app = context.applicationContext as WatchlistApp
            val now = Instant.now()
            runCatching {
                val movies = runCatching { NewReleases.released(movieReleases(app), now) }.getOrDefault(emptySet())
                app.settings.setNotifiedEpisodes(NewEpisodes.aired(airedAndUpcoming(app), now) + movies)
            }
            // UPDATE replaces the old library-syncing schedule of earlier versions.
            schedule(context, ExistingPeriodicWorkPolicy.UPDATE)
        }

        /**
         * Called at every app start while notifications are on. Android drops scheduled work when
         * an app is force-stopped (some phones do that to save battery); starting WorkManager
         * brings it back, and KEEP leaves an existing schedule as it is.
         */
        suspend fun restore(context: Context) {
            val settings = (context.applicationContext as WatchlistApp).settings.current()
            if (settings.accessToken != null && settings.notifications) schedule(context, ExistingPeriodicWorkPolicy.KEEP)
        }

        private fun schedule(
            context: Context,
            policy: ExistingPeriodicWorkPolicy,
        ) {
            val request =
                PeriodicWorkRequestBuilder<NewEpisodesWorker>(4, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, policy, request)
        }

        /**
         * One extra check just after the next episode of a watched show airs, so its notification
         * doesn't wait up to four hours for the regular check. Android may still delay it a little
         * (Doze); exact alarms would need an extra permission.
         */
        private fun scheduleAtAirTime(
            context: Context,
            next: Instant?,
            now: Instant,
        ) {
            val manager = WorkManager.getInstance(context)
            if (next == null) {
                manager.cancelUniqueWork(AIR_TIME_WORK)
                return
            }
            val request =
                OneTimeWorkRequestBuilder<NewEpisodesWorker>()
                    .setInitialDelay(Duration.between(now, next).plus(AIR_TIME_MARGIN))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            manager.enqueueUniqueWork(AIR_TIME_WORK, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
            WorkManager.getInstance(context).cancelUniqueWork(AIR_TIME_WORK)
        }

        /** Downloads a picture for a notification (they need software bitmaps); null when it fails. */
        private suspend fun bitmap(
            context: Context,
            url: String?,
        ): Bitmap? {
            url ?: return null
            val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
            val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull()
            return (result as? SuccessResult)?.image?.toBitmap()
        }

        @SuppressLint("MissingPermission")
        private suspend fun notify(
            context: Context,
            alert: NewEpisodes.Alert,
            fanart: String?,
        ) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val item = alert.item
            val intent =
                PendingIntent.getActivity(
                    context,
                    item.ids.simkl.toInt(),
                    MainActivity.detailIntent(context, item.type, item.ids.simkl),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val latest = alert.latest.episode?.label
            val count = alert.episodes.size
            val text =
                when {
                    latest == null -> context.getString(R.string.data_notif_new_episode)
                    count == 1 -> context.getString(R.string.data_notif_episode_aired, latest)
                    else -> context.resources.getQuantityString(R.plurals.data_notif_episodes_aired, count, count, latest)
                }
            val art = Images.notification(item.poster, alert.episodes.firstNotNullOfOrNull { it.poster }, fanart)
            val poster = bitmap(context, art.poster)
            val backdrop = bitmap(context, art.backdrop)
            val builder =
                NotificationCompat.Builder(context, NotifyChannel.of(item.type).id)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(item.title)
                    .setContentText(text)
                    .setLargeIcon(poster)
                    .setContentIntent(intent)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                    .setWhen(alert.latest.date.toEpochMilli())
                    .setShowWhen(true)
            // Expanded: the show's backdrop as a large picture (the poster stays as the thumbnail
            // while collapsed). Without a backdrop the poster alone is used.
            if (backdrop != null) {
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(backdrop)
                        .setSummaryText(text)
                        .showBigPictureWhenCollapsed(false),
                )
            }
            // One notification per show: a newer episode replaces it, and its text counts all of them.
            NotificationManagerCompat.from(context).notify(item.key.hashCode(), builder.build())
        }

        /** "Out now" for a released movie from the "Plan to watch" list, in the movie channel. */
        @SuppressLint("MissingPermission")
        private suspend fun notifyRelease(
            context: Context,
            alert: NewReleases.Alert,
        ) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val item = alert.item
            val intent =
                PendingIntent.getActivity(
                    context,
                    item.ids.simkl.toInt(),
                    MainActivity.detailIntent(context, item.type, item.ids.simkl),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val poster = bitmap(context, Images.poster(item.poster ?: alert.release.poster, large = true))
            val builder =
                NotificationCompat.Builder(context, NotifyChannel.Movies.id)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(item.title)
                    .setContentText(context.getString(R.string.data_notif_movie_out))
                    .setLargeIcon(poster)
                    .setContentIntent(intent)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                    .setWhen(alert.release.date.toEpochMilli())
                    .setShowWhen(true)
            NotificationManagerCompat.from(context).notify(item.key.hashCode(), builder.build())
        }
    }
}
