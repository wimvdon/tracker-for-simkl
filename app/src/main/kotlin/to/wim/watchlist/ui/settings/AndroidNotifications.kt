package to.wim.watchlist.ui.settings

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import to.wim.watchlist.data.model.NotifyChannel

/**
 * What Android itself allows: notifications for the app as a whole (the permission on Android 13+,
 * or the app switch in Android's settings) and each channel. Read again whenever Settings comes
 * back into view, so the app's switches match Android's after a trip to its settings.
 */
data class AndroidNotifications(
    val appEnabled: Boolean,
    val channels: Map<NotifyChannel, Boolean>,
) {
    fun channelOn(channel: NotifyChannel): Boolean = appEnabled && channels[channel] != false

    companion object {
        fun read(context: Context): AndroidNotifications {
            val manager = context.getSystemService(NotificationManager::class.java)
            return AndroidNotifications(
                appEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                // A channel that doesn't exist yet counts as on: it is created on.
                channels = NotifyChannel.entries.associateWith { manager.getNotificationChannel(it.id)?.importance != NotificationManager.IMPORTANCE_NONE },
            )
        }

        /** Android's notification page for this app (all channels). */
        fun openAppSettings(context: Context) =
            open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))

        /** Android's page for one channel: on/off, sound, vibration, pop-up. */
        fun openChannelSettings(
            context: Context,
            channel: NotifyChannel,
        ) = open(
            context,
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channel.id),
        )

        private fun open(
            context: Context,
            intent: Intent,
        ) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                // Some phones lack a page; the app's own details page always exists.
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
