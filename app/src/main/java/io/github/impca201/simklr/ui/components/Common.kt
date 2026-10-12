package io.github.impca201.simklr.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.impca201.simklr.SimklrApp
import io.github.impca201.simklr.data.model.Dates
import io.github.impca201.simklr.data.model.Images
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun rememberApp(): SimklrApp = LocalContext.current.applicationContext as SimklrApp

fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "No app can open $url", Toast.LENGTH_SHORT).show()
    }
}

fun Context.share(
    title: String,
    url: String,
) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        action?.invoke()
    }
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
                contentDescription = "Profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Icon(Icons.Rounded.Person, contentDescription = "Profile")
        }
    }
}

object Format {
    private val zone get() = ZoneId.systemDefault()
    private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    fun date(value: String?): String? = Dates.parse(value)?.let { date(it) }

    fun date(instant: Instant): String = dateFormat.format(instant.atZone(zone))

    fun time(instant: Instant): String = timeFormat.format(instant.atZone(zone))

    /** "Today", "Tomorrow", "Yesterday" or "Fri 14 Mar". */
    fun day(date: LocalDate): String {
        val today = LocalDate.now(zone)
        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> dayFormat.format(date)
        }
    }

    /** "in 3 days", "tomorrow", "today" or the date when further away. */
    fun relative(instant: Instant): String {
        val date = instant.atZone(zone).toLocalDate()
        val days = Duration.between(LocalDate.now(zone).atStartOfDay(zone).toInstant(), date.atStartOfDay(zone).toInstant()).toDays()
        return when {
            days == 0L -> "today ${time(instant)}"
            days == 1L -> "tomorrow ${time(instant)}"
            days in 2..13 -> "in $days days"
            days < 0 -> date(instant)
            else -> dayFormat.format(date)
        }
    }

    fun ago(value: String?): String? {
        val instant = Dates.parse(value) ?: return null
        val d = Duration.between(instant, Instant.now())
        return when {
            d.toMinutes() < 60 -> "just now"
            d.toHours() < 24 -> "${d.toHours()} h ago"
            d.toDays() < 30 -> "${d.toDays()} d ago"
            d.toDays() < 365 -> "${d.toDays() / 30} mo ago"
            else -> "${d.toDays() / 365} y ago"
        }
    }

    fun duration(minutes: Long): String {
        val days = minutes / (60 * 24)
        val hours = (minutes / 60) % 24
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            else -> "${minutes}m"
        }
    }

    fun rating(value: Double): String = if (value >= 10) "10" else "%.1f".format(value)
}
