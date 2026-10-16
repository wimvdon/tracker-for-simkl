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
import io.github.impca201.trackerforsimkl.data.model.MediaType
import io.github.impca201.trackerforsimkl.data.model.NewEpisodes
import io.github.impca201.trackerforsimkl.ui.MainActivity
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Notifies about newly aired episodes of shows the user is watching. It reads SIMKL's calendar
 * data files (static files on data.simkl.in's CDN, which don't count against the API) and the
 * library as it was last synced; it never syncs or calls the API in the background. Which
 * episodes to announce is decided by [NewEpisodes].
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
        val library = app.library.state.value.items
        // No library copy yet (never synced): nothing to compare with, and nothing to mark as seen.
        if (library.isEmpty()) return Result.success()
        val calendar =
            try {
                airedAndUpcoming(app)
            } catch (e: Exception) {
                return Result.retry()
            }
        val now = Instant.now()
        val plan = NewEpisodes.plan(calendar, app.settings.rememberedEpisodes(), library, app.settings.notifiedEpisodes(), now, settings.types)
        val showInfo = app.library.state.value.showInfo
        plan.alerts.forEach { notify(applicationContext, it, showInfo[it.item.key]?.fanart) }
        app.settings.setNotifiedEpisodes(plan.notified)
        app.settings.setRememberedEpisodes(plan.remembered)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "new_episodes"
        private const val WORK = "new_episodes"

        /** TV and anime episodes from SIMKL's calendar files. */
        private suspend fun airedAndUpcoming(app: TrackerApp): List<CalendarEntry> =
            listOf(MediaType.Tv, MediaType.Anime).flatMap { type ->
                Parsers.calendar(app.api.calendar(type), type).filter { it.episode != null }
            }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL, context.getString(R.string.data_notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = context.getString(R.string.data_notif_channel_description)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** Starts the periodic check; episodes that already aired are not announced. */
        suspend fun enable(context: Context) {
            val app = context.applicationContext as TrackerApp
            runCatching { app.settings.setNotifiedEpisodes(NewEpisodes.aired(airedAndUpcoming(app), Instant.now())) }
            // UPDATE replaces the old library-syncing schedule of earlier versions.
            schedule(context, ExistingPeriodicWorkPolicy.UPDATE)
        }

        /**
         * Called at every app start while notifications are on. Android drops scheduled work when
         * an app is force-stopped (some phones do that to save battery); starting WorkManager
         * brings it back, and KEEP leaves an existing schedule as it is.
         */
        suspend fun restore(context: Context) {
            val settings = (context.applicationContext as TrackerApp).settings.current()
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
                NotificationCompat.Builder(context, CHANNEL)
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
    }
}
