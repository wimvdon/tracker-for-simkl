package io.github.impca201.trackerforsimkl.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.impca201.trackerforsimkl.R
import io.github.impca201.trackerforsimkl.ui.theme.AppShapes
import io.github.impca201.trackerforsimkl.ui.theme.SkeletonLine
import io.github.impca201.trackerforsimkl.ui.theme.readableWidth
import io.github.impca201.trackerforsimkl.ui.theme.skeleton

// Placeholders shown while a list loads for the first time. They have the size and shape of the
// real items, so nothing jumps when the content fades in.

/** Announces the placeholders as one "Loading" element to screen readers. */
@Composable
private fun Modifier.loadingSemantics(): Modifier {
    val text = stringResource(R.string.loading)
    return semantics(mergeDescendants = true) { contentDescription = text }
}

/** Stands in for a [PosterTile]: poster, title and subtitle. */
@Composable
fun PosterTileSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).skeleton(PosterShape))
        SkeletonLine(Modifier.fillMaxWidth(0.85f).padding(horizontal = 2.dp))
        SkeletonLine(Modifier.fillMaxWidth(0.6f).padding(horizontal = 2.dp))
        SkeletonLine(Modifier.fillMaxWidth(0.4f).padding(horizontal = 2.dp), height = 10.dp)
    }
}

/** Stands in for a library poster grid. */
@Composable
fun PosterGridSkeleton(bottomPadding: Dp) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = bottomPadding),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize().loadingSemantics(),
    ) {
        items(12) { PosterTileSkeleton() }
    }
}

/** Stands in for a [PosterRow]. */
@Composable
fun PosterRowSkeleton(
    modifier: Modifier = Modifier,
    itemWidth: Dp = 120.dp,
) {
    LazyRow(
        modifier = modifier.loadingSemantics(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = false,
    ) {
        items(6) { PosterTileSkeleton(Modifier.width(itemWidth)) }
    }
}

/** Stands in for a list row with a small poster and two or three lines of text. */
@Composable
fun PosterListRowSkeleton(
    posterWidth: Dp,
    modifier: Modifier = Modifier,
    lines: Int = 3,
) {
    Surface(shape = AppShapes.Item, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.width(posterWidth).aspectRatio(2f / 3f).skeleton(RoundedCornerShape(10.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonLine(Modifier.fillMaxWidth(0.7f), height = 16.dp)
                if (lines > 1) SkeletonLine(Modifier.fillMaxWidth(0.5f))
                if (lines > 2) SkeletonLine(Modifier.fillMaxWidth(0.35f), height = 12.dp)
            }
        }
    }
}

/** A column of [PosterListRowSkeleton]s, for Up next and the calendar. */
@Composable
fun PosterListSkeleton(
    posterWidth: Dp,
    contentPadding: PaddingValues,
    lines: Int = 3,
    spacing: Dp = 8.dp,
) {
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize().readableWidth().loadingSemantics(),
    ) {
        items(8) { PosterListRowSkeleton(posterWidth, lines = lines) }
    }
}

/** Placeholder lines for a block of text, such as an overview. */
@Composable
fun TextSkeleton(
    modifier: Modifier = Modifier,
    lines: Int = 4,
) {
    Column(modifier.loadingSemantics(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(lines) { i ->
            SkeletonLine(Modifier.fillMaxWidth(if (i == lines - 1) 0.6f else 1f))
        }
    }
}

/** Stands in for a card of the given height. */
@Composable
fun CardSkeleton(
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(height).skeleton(AppShapes.Group))
}
