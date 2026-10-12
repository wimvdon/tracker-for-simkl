package io.github.impca201.simklr.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
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
import io.github.impca201.simklr.R
import io.github.impca201.simklr.SimklrApp
import io.github.impca201.simklr.data.LibraryState
import io.github.impca201.simklr.data.model.LibraryItem
import io.github.impca201.simklr.data.model.NextEpisode
import io.github.impca201.simklr.data.model.Shelf
import io.github.impca201.simklr.data.model.Shelves
import io.github.impca201.simklr.ui.MainActivity
import java.util.concurrent.TimeUnit

/** Syncs the library a few times a day and notifies about episodes that became watchable. */
class NewEpisodesWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SimklrApp
        val settings = app.settings.current()
        if (settings.accessToken == null || !settings.notifications) return Result.success()
        if (app.library.sync() != null) return Result.retry()
        app.library.refreshLatestNext()
        val upNext = upNextKeys(app.library.state.value, settings.continueFromLatest)
        val notified = app.settings.notifiedEpisodes()
        val fresh = upNext.filterKeys { it !in notified }.values
        val state = app.library.state.value
        fresh.take(MAX_NOTIFICATIONS).forEach {
            notify(applicationContext, it, Shelves.nextEpisode(it, settings.continueFromLatest, state.latestNext[it.key]))
        }
        app.settings.setNotifiedEpisodes(upNext.keys)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "new_episodes"
        private const val WORK = "new_episodes"
        private const val MAX_NOTIFICATIONS = 5

        /** "Up next" episodes keyed by show and episode, so each episode is announced once. */
        fun upNextKeys(
            state: LibraryState,
            fromLatest: Boolean,
        ): Map<String, LibraryItem> =
            state.items
                .filter { it.type.isEpisodic && Shelves.shelfOf(it, fromLatest = fromLatest, latest = state.latestNext[it.key]) == Shelf.UpNext }
                .associateBy {
                    val next = Shelves.nextEpisode(it, fromLatest, state.latestNext[it.key])
                    "${it.key}:${next?.ref?.label ?: it.watchedEpisodes}"
                }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL, "New episodes", NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "When a new episode of a show you are watching is available"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** Starts the periodic check, remembering what is already up next so only new episodes notify. */
        suspend fun enable(context: Context) {
            val app = context.applicationContext as SimklrApp
            app.settings.setNotifiedEpisodes(upNextKeys(app.library.state.value, app.settings.current().continueFromLatest).keys)
            val request =
                PeriodicWorkRequestBuilder<NewEpisodesWorker>(4, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }

        @SuppressLint("MissingPermission")
        private fun notify(
            context: Context,
            item: LibraryItem,
            next: NextEpisode?,
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
                listOfNotNull(next?.ref?.label, next?.title).joinToString(" · ").ifEmpty { "New episode available" }
            val notification =
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(item.title)
                    .setContentText(text)
                    .setContentIntent(intent)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                    .build()
            NotificationManagerCompat.from(context).notify(item.key.hashCode(), notification)
        }
    }
}
