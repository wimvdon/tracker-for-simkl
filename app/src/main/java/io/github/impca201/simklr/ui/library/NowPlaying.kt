package io.github.impca201.simklr.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.impca201.simklr.R
import io.github.impca201.simklr.data.LibraryState
import io.github.impca201.simklr.data.model.MediaType
import io.github.impca201.simklr.data.model.Playback
import io.github.impca201.simklr.ui.LocalNavigator
import io.github.impca201.simklr.ui.components.ConfirmWatchedDialog
import io.github.impca201.simklr.ui.components.Poster
import io.github.impca201.simklr.ui.components.rememberApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Banner with the titles of one type that are being watched (SIMKL playback sessions), at the top
 * of the TV Shows, Anime and Movies tabs. Marking one watched asks first, as it drops its progress.
 */
@Composable
fun NowPlayingBanner(
    type: MediaType,
    state: LibraryState,
    onError: (String) -> Unit,
) {
    val sessions = remember(state.playback, type) { state.playback.filter { it.type == type } }
    if (sessions.isEmpty()) return
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<Playback?>(null) }
    var busy by remember { mutableStateOf(setOf<Long>()) }
    // One session fills the width; several peek so it is clear the row scrolls.
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = if (sessions.size == 1) screenWidth - 24.dp else screenWidth * 0.82f

    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sessions, key = { it.id }) { session ->
            val item = state.item(session.type, session.simklId)
            Surface(
                onClick = { navigator.openDetail(session.type, session.simklId, session.title, item?.poster) },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.width(cardWidth),
            ) {
                Row(
                    Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Poster(item?.poster, session.title, Modifier.width(44.dp), shape = RoundedCornerShape(8.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(R.string.lib_now_playing),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(session.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                session.episode?.label,
                                session.episodeTitle,
                                stringResource(R.string.lib_percent_watched, session.progress.roundToInt()),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        LinearProgressIndicator(
                            progress = { (session.progress / 100).toFloat() },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                        )
                    }
                    if (session.id in busy) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        FilledTonalIconButton(onClick = { confirm = session }) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.lib_mark_episode_watched, session.episode?.label ?: session.title),
                            )
                        }
                    }
                }
            }
        }
    }

    confirm?.let { session ->
        ConfirmWatchedDialog(
            playback = session,
            onConfirm = {
                busy = busy + session.id
                scope.launch {
                    try {
                        app.library.markPlaybackWatched(session, state.item(session.type, session.simklId))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        onError(context.getString(R.string.lib_mark_failed, session.episode?.label ?: session.title, e.message.orEmpty()))
                    }
                    busy = busy - session.id
                }
            },
            onDismiss = { confirm = null },
        )
    }
}
