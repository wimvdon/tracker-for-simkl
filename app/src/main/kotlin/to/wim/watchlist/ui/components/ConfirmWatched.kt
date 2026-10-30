package to.wim.watchlist.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import to.wim.watchlist.R
import to.wim.watchlist.data.model.Playback
import kotlin.math.roundToInt

/** Asks before marking a partly watched episode or movie watched, which removes its saved progress. */
@Composable
fun ConfirmWatchedDialog(
    playback: Playback,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val what = playback.episode?.let { "${playback.title} ${it.label}" } ?: playback.title
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CheckCircle, null) },
        title = { Text(stringResource(R.string.lib_confirm_watched_title)) },
        text = {
            Text(stringResource(R.string.lib_confirm_watched_text, what, playback.progress.roundToInt()))
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(stringResource(R.string.lib_mark_watched)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lib_cancel)) } },
    )
}
