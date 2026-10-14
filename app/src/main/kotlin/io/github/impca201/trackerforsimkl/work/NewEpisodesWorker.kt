package io.github.impca201.trackerforsimkl.work

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
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.impca201.trackerforsimkl.R
import io.github.impca201.trackerforsimkl.TrackerApp
import io.github.impca201.trackerforsimkl.data.api.Parsers
import io.github.impca201.trackerforsimkl.data.model.CalendarEntry
import io.github.impca201.trackerforsimkl.data.model.Images
import io.github.impca201.trackerforsimkl.data.model.LibraryItem
import io.github.impca201.trackerforsimkl.data.model.ListStatus
import io.github.impca201.trackerforsimkl.data.model.MediaType
import io.github.impca201.trackerforsimkl.ui.MainActivity
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Notifies about newly aired episodes of shows the user is watching. It reads SIMKL's calendar
 * data files (static files on data.simkl.in's CDN, which don't count against the API) and the
 * library as it was last synced; it never syncs or calls the API in the background.
 */
class NewEpisodesWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as TrackerApp
        val settings = app.settings.current()
        if (settings.accessToken == null || !settings.notifications) return Result.success()
        app.library.load()
        val calendar =
            try {
                airedEpisodes(app)
            } catch (e: Exception) {
                return Result.retry()
            }
        val watching = watchingShows(app)
        val notified = app.settings.notifiedEpisodes()
        val fresh =
            calendar.mapNotNull { entry ->
                val item = watching["${entry.type.name}:${entry.simklId}"] ?: return@mapNotNull null
                val ref = entry.episode ?: return@mapNotNull null
                if (item.isWatched(if (entry.type == MediaType.Anime) null else ref.season, ref.episode)) return@mapNotNull null
                Triple(keyOf(entry), item, entry).takeIf { it.first !in notified }
            }
        val showInfo = app.library.state.value.showInfo
        fresh.take(MAX_NOTIFICATIONS).forEach { (_, item, entry) ->
            notify(applicationContext, item, entry, showInfo[item.key]?.fanart)
        }
        // Remember what aired in the window, so each episode is announced once.
        app.settings.setNotifiedEpisodes(calendar.map(::keyOf).toSet())
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "new_episodes"
        private const val WORK = "new_episodes"
        private const val MAX_NOTIFICATIONS = 5

        /** How far back an air date may be and still be announced (covers phones that were off for a while). */
        private val WINDOW: Duration = Duration.ofDays(2)

        private fun keyOf(entry: CalendarEntry) = "${entry.type.name}:${entry.simklId}:${entry.episode?.label}"

        /** Episodes of TV shows and anime from the calendar files that aired within [WINDOW]. */
        private suspend fun airedEpisodes(app: TrackerApp): List<CalendarEntry> {
            val now = Instant.now()
            val from = now.minus(WINDOW)
            return listOf(MediaType.Tv, MediaType.Anime).flatMap { type ->
                Parsers.calendar(app.api.calendar(type), type).filter { it.episode != null && it.date.isAfter(from) && !it.date.isAfter(now) }
            }
        }

        private fun watchingShows(app: TrackerApp): Map<String, LibraryItem> =
            app.library.state.value.items
                .filter { it.type.isEpisodic && it.status == ListStatus.Watching }
                .associateBy { it.key }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL, context.getString(R.string.data_notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = context.getString(R.string.data_notif_channel_description)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** Starts the periodic check; episodes that already aired are not announced. */
        suspend fun enable(context: Context) {
            val app = context.applicationContext as TrackerApp
            runCatching { app.settings.setNotifiedEpisodes(airedEpisodes(app).map(::keyOf).toSet()) }
            val request =
                PeriodicWorkRequestBuilder<NewEpisodesWorker>(4, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            // UPDATE replaces the old library-syncing schedule of earlier versions.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
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
            item: LibraryItem,
            entry: CalendarEntry,
            fanart: String?,
        ) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val intent =
                PendingIntent.getActivity(
                    context,
                    item.ids.simkl.toInt(),
                    MainActivity.detailIntent(context, item.type, item.ids.simkl),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val text =
                entry.episode?.let { context.getString(R.string.data_notif_episode_aired, it.label) }
                    ?: context.getString(R.string.data_notif_new_episode)
            val art = Images.notification(item.poster, entry.poster, fanart)
            val poster = bitmap(context, art.poster)
            val backdrop = bitmap(context, art.backdrop)
            val builder =
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(item.title)
                    .setContentText(text)
                    .setLargeIcon(poster)
                    .setContentIntent(intent)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
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
            NotificationManagerCompat.from(context).notify(item.key.hashCode(), builder.build())
        }
    }
}
