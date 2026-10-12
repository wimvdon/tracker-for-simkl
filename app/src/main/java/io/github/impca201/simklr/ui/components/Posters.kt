package io.github.impca201.simklr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import io.github.impca201.simklr.R
import io.github.impca201.simklr.data.model.AiringState
import io.github.impca201.simklr.data.model.Images
import io.github.impca201.simklr.data.model.MediaSummary

val PosterShape: Shape = RoundedCornerShape(12.dp)

@Composable
fun Poster(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    shape: Shape = PosterShape,
) {
    Box(
        modifier.aspectRatio(2f / 3f).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val url = Images.poster(path, large)
        if (url == null) {
            Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                error = { Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
        }
    }
}

/** Small label drawn on top of a poster. */
@Composable
fun PosterBadge(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = container, contentColor = content) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun AiringBadge(
    state: AiringState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        AiringState.Airing ->
            PosterBadge(
                stringResource(R.string.lib_badge_airing),
                modifier,
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
            )
        AiringState.Ended ->
            PosterBadge(
                stringResource(R.string.lib_badge_ended),
                modifier,
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        AiringState.Unknown -> Unit
    }
}

/** A poster with its title underneath, for grids and rows. */
@Composable
fun PosterTile(
    title: String,
    poster: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float? = null,
    rating: Double? = null,
    airing: AiringState = AiringState.Unknown,
    inLibrary: Boolean = false,
) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Poster(poster, title, Modifier.fillMaxWidth())
            Row(
                Modifier.align(Alignment.TopStart).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AiringBadge(airing)
            }
            if (rating != null) {
                Surface(
                    Modifier.align(Alignment.TopEnd).padding(6.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                ) {
                    Row(Modifier.padding(horizontal = 5.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Star, null, Modifier.size(12.dp), tint = Color(0xFFFFC857))
                        Text(Format.rating(rating), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (inLibrary) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.lib_in_your_lists),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier.align(Alignment.BottomEnd).padding(6.dp).size(22.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50)),
                )
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    trackColor = Color.Black.copy(alpha = 0.4f),
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

/** A titled, horizontally scrolling row of posters. */
@Composable
fun PosterRow(
    items: List<MediaSummary>,
    onClick: (MediaSummary) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 120.dp,
    inLibrary: (MediaSummary) -> Boolean = { false },
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items, key = { "${it.type}:${it.simklId}" }) { item ->
            PosterTile(
                title = item.title,
                poster = item.poster,
                subtitle = item.year?.toString(),
                rating = item.rating,
                inLibrary = inLibrary(item),
                onClick = { onClick(item) },
                modifier = Modifier.width(itemWidth),
            )
        }
    }
}
