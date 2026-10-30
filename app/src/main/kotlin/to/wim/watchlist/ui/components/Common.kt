package to.wim.watchlist.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import to.wim.watchlist.R
import to.wim.watchlist.WatchlistApp
import to.wim.watchlist.data.model.AirTime
import to.wim.watchlist.data.model.Dates
import to.wim.watchlist.data.model.Images
import to.wim.watchlist.data.model.LocalAir
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun rememberApp(): WatchlistApp = (LocalContext.current.applicationContext as WatchlistApp).also { Format.context = it }

fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, getString(R.string.lib_no_app_for_url, url), Toast.LENGTH_SHORT).show()
    }
}

fun Context.share(
    title: String,
    url: String,
) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** The signed-in user's avatar; opens the profile. */
@Composable
fun AvatarButton(
    avatar: String?,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        if (avatar != null) {
            AsyncImage(
                model = Images.avatar(avatar),
                contentDescription = stringResource(R.string.lib_profile),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Icon(Icons.Rounded.Person, contentDescription = stringResource(R.string.lib_profile))
        }
    }
}

object Format {
    /** Context for the localised strings, set by WatchlistApp.onCreate (and [rememberApp]). */
    @Volatile
    var context: Context? = null

    private val res get() = (context ?: error("No application context")).resources

    private val zone get() = ZoneId.systemDefault()

    // Created per call so they follow the current locale.
    private val dateFormat get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    private val dayFormat get() = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    private val timeFormat get() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())

    fun date(value: String?): String? = Dates.parse(value)?.let { date(it) }

    fun date(instant: Instant): String = dateFormat.format(instant.atZone(zone))

    fun time(instant: Instant): String = timeFormat.format(instant.atZone(zone))

    /** "Today", "Tomorrow", "Yesterday" or "Fri 14 Mar". */
    fun day(date: LocalDate): String {
        val today = LocalDate.now(zone)
        return when (date) {
            today -> res.getString(R.string.lib_day_today)
            today.plusDays(1) -> res.getString(R.string.lib_day_tomorrow)
            today.minusDays(1) -> res.getString(R.string.lib_day_yesterday)
            else -> dayFormat.format(date)
        }
    }

    /** "in 3 days", "tomorrow", "today" or the date when further away. */
    fun relative(instant: Instant): String {
        val date = instant.atZone(zone).toLocalDate()
        val days = Duration.between(LocalDate.now(zone).atStartOfDay(zone).toInstant(), date.atStartOfDay(zone).toInstant()).toDays()
        return when {
            days == 0L -> res.getString(R.string.lib_relative_today, time(instant))
            days == 1L -> res.getString(R.string.lib_relative_tomorrow, time(instant))
            days in 2..13 -> res.getQuantityString(R.plurals.lib_relative_in_days, days.toInt(), days.toInt())
            days < 0 -> date(instant)
            else -> dayFormat.format(date)
        }
    }

    fun ago(value: String?): String? {
        val instant = Dates.parse(value) ?: return null
        val d = Duration.between(instant, Instant.now())
        fun plural(
            id: Int,
            n: Long,
        ) = res.getQuantityString(id, n.toInt(), n.toInt())
        return when {
            d.toMinutes() < 60 -> res.getString(R.string.lib_ago_just_now)
            d.toHours() < 24 -> plural(R.plurals.lib_ago_hours, d.toHours())
            d.toDays() < 30 -> plural(R.plurals.lib_ago_days, d.toDays())
            d.toDays() < 365 -> plural(R.plurals.lib_ago_months, d.toDays() / 30)
            else -> plural(R.plurals.lib_ago_years, d.toDays() / 365)
        }
    }

    fun duration(minutes: Long): String {
        val days = minutes / (60 * 24)
        val hours = (minutes / 60) % 24
        return when {
            days > 0 -> res.getString(R.string.lib_duration_days_hours, days, hours)
            hours > 0 -> res.getString(R.string.lib_duration_hours_minutes, hours, minutes % 60)
            else -> res.getString(R.string.lib_duration_minutes, minutes)
        }
    }

    /** A broadcast in the phone's time zone: "Tuesday 21:30", or just the time without a day. */
    fun airs(air: LocalAir): String {
        val time = timeFormat.format(air.time)
        val day = air.day?.getDisplayName(TextStyle.FULL, Locale.getDefault())?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        return if (day == null) time else res.getString(R.string.detail_airs_day_time, day, time)
    }

    /** SIMKL's air time converted to the phone's time zone, or SIMKL's text when that fails. */
    fun airs(air: AirTime): String? = air.inZone(zone)?.let { airs(it) } ?: air.raw

    fun rating(value: Double): String = if (value >= 10) "10" else "%.1f".format(value)
}
