package to.wim.watchlist.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import to.wim.watchlist.R
import to.wim.watchlist.ui.theme.AppShapes

/**
 * Where cast and crew would be on a details page without a TMDB token: what a token adds, a
 * button that opens the token dialog, and "Not now" / "Don't show again" (see `TmdbBanner`).
 */
@Composable
fun TmdbTokenBanner(
    onAdd: () -> Unit,
    onNotNow: () -> Unit,
    onNever: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onAdd,
        shape = AppShapes.Group,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top, modifier = Modifier.padding(end = 8.dp)) {
                Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.size(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.detail_tmdb_banner_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.detail_tmdb_banner_text), style = MaterialTheme.typography.bodyMedium)
                }
            }
            // Wraps onto two lines when the words are long (German, large fonts).
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onNever) { Text(stringResource(R.string.detail_tmdb_banner_never)) }
                TextButton(onClick = onNotNow) { Text(stringResource(R.string.detail_tmdb_banner_not_now)) }
                FilledTonalButton(onClick = onAdd) { Text(stringResource(R.string.detail_tmdb_banner_add)) }
            }
        }
    }
}
