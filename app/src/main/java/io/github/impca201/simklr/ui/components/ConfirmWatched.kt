package io.github.impca201.simklr.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.github.impca201.simklr.data.model.Playback
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
        title = { Text("Mark as watched?") },
        text = {
            Text(
                "You stopped $what at ${playback.progress.roundToInt()}%. " +
                    "Marking it watched removes that saved progress on SIMKL.",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text("Mark watched") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
