package io.github.impca201.trackerforsimkl.ui.library

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import io.github.impca201.trackerforsimkl.R
import io.github.impca201.trackerforsimkl.ui.theme.AppShapes
import io.github.impca201.trackerforsimkl.ui.theme.Motion
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.impca201.trackerforsimkl.ui.components.Format
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.impca201.trackerforsimkl.data.LibraryState
import io.github.impca201.trackerforsimkl.data.model.AiringState
import io.github.impca201.trackerforsimkl.data.model.LibraryItem
import io.github.impca201.trackerforsimkl.data.model.Shelves
import io.github.impca201.trackerforsimkl.ui.components.AiringBadge
import io.github.impca201.trackerforsimkl.ui.components.Poster
import io.github.impca201.trackerforsimkl.ui.components.PosterTile
import io.github.impca201.trackerforsimkl.ui.main.FloatingBarSpace

enum class SortOrder(
    @StringRes val label: Int,
) {
    Recent(R.string.lib_sort_recent),
    Added(R.string.lib_sort_added),
    Title(R.string.lib_sort_title),
    Rating(R.string.lib_sort_rating),
    Release(R.string.lib_sort_release),
    ;

    fun sort(items: List<LibraryItem>): List<LibraryItem> =
        when (this) {
            Recent -> items.sortedByDescending { it.lastWatchedAt ?: it.addedAt ?: "" }
            Added -> items.sortedByDescending { it.addedAt ?: "" }
            Title -> items.sortedBy { it.title.lowercase() }
            Rating -> items.sortedByDescending { it.userRating ?: 0 }
            // The library only carries the release year; titles keep a stable order within a year.
            Release -> items.sortedWith(compareByDescending<LibraryItem> { it.year ?: 0 }.thenBy { it.title.lowercase() })
        }
}

/** The anime type filter of a tab: SIMKL anime_type ("tv", "movie", "ova", ...), null for all. */
data class LibraryFilter(
    val animeType: String? = null,
) {
    val active: Boolean get() = animeType != null

    fun apply(items: List<LibraryItem>): List<LibraryItem> =
        if (animeType == null) items else items.filter { it.animeType.equals(animeType, ignoreCase = true) }

    /** Compact form for rememberSaveable. */
    fun encode(): String = animeType.orEmpty()

    companion object {
        /** SIMKL anime_type and its label. */
        val animeTypes =
            listOf(
                "tv" to R.string.lib_anime_type_tv,
                "movie" to R.string.lib_anime_type_movie,
                "ova" to R.string.lib_anime_type_ova,
                "ona" to R.string.lib_anime_type_ona,
                "special" to R.string.lib_anime_type_special,
            )

        fun decode(value: String): LibraryFilter = LibraryFilter(animeType = value.ifEmpty { null })
    }
}

/**
 * Sort and filter of each tab of a library screen, kept separately per tab for the session.
 * Indexes are tab positions.
 */
class TabOptions(
    private val sorts: androidx.compose.runtime.MutableState<List<String>>,
    private val filters: androidx.compose.runtime.MutableState<List<String>>,
) {
    /** Changes whenever any tab's sort or filter changes (a remember key). */
    val version: Any get() = sorts.value to filters.value

    fun sort(tab: Int): SortOrder = SortOrder.entries.firstOrNull { it.name == sorts.value.getOrNull(tab) } ?: SortOrder.Recent

    fun filter(tab: Int): LibraryFilter = LibraryFilter.decode(filters.value.getOrNull(tab).orEmpty())

    fun setSort(
        tab: Int,
        value: SortOrder,
    ) {
        sorts.value = sorts.value.toMutableList().also { it[tab] = value.name }
    }

    fun setFilter(
        tab: Int,
        value: LibraryFilter,
    ) {
        filters.value = filters.value.toMutableList().also { it[tab] = value.encode() }
    }
}

@Composable
fun rememberTabOptions(defaultSorts: List<SortOrder>): TabOptions {
    val sorts = rememberSaveable { mutableStateOf(defaultSorts.map { it.name }) }
    val filters = rememberSaveable { mutableStateOf(defaultSorts.map { "" }) }
    return remember(sorts, filters) { TabOptions(sorts, filters) }
}

@Composable
fun bottomPadding(): Dp = FloatingBarSpace + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** The sort button of a library tab's title bar: a menu of sort orders, the current one ticked. */
@Composable
fun SortButton(
    sort: SortOrder,
    onSort: (SortOrder) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.lib_sort)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortOrder.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    onClick = {
                        open = false
                        onSort(option)
                    },
                    trailingIcon = { if (option == sort) Icon(Icons.Rounded.Check, null) },
                )
            }
        }
    }
}

/**
 * Anime only: the type filter button of the title bar, a menu of anime types (the current one
 * ticked). A dot on the button shows that a type is chosen.
 */
@Composable
fun AnimeTypeButton(
    filter: LibraryFilter,
    onFilter: (LibraryFilter) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val types = listOf<Pair<String?, Int>>(null to R.string.lib_filter_all) + LibraryFilter.animeTypes
    Box {
        IconButton(onClick = { open = true }) {
            BadgedBox(badge = { if (filter.active) Badge() }) {
                Icon(Icons.Rounded.FilterList, contentDescription = stringResource(R.string.lib_filter_type))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            types.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        open = false
                        onFilter(filter.copy(animeType = key))
                    },
                    trailingIcon = { if (key == filter.animeType) Icon(Icons.Rounded.Check, null) },
                )
            }
        }
    }
}

@Composable
fun TabLabel(
    label: String,
    count: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, maxLines = 1)
        if (count > 0) {
            Badge(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) { Text(count.toString()) }
        }
    }
}

fun progressOf(item: LibraryItem): Float? =
    if (item.type.isEpisodic && item.totalEpisodes > 0) (item.watchedEpisodes.toFloat() / item.totalEpisodes).coerceIn(0f, 1f) else null

/** Poster grid of library items, with airing badges, ratings and the next air date. */
@Composable
fun LibraryGrid(
    items: List<LibraryItem>,
    state: LibraryState,
    onOpen: (LibraryItem) -> Unit,
    showProgress: Boolean,
    showAiring: Boolean,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = bottomPadding()),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(items, key = { it.key }) { item ->
            val playback = state.playbackFor(item.type, item.ids.simkl)
            val nextAir = if (item.type.isEpisodic) state.nextAirDate(item) else null
            PosterTile(
                title = item.title,
                poster = item.poster,
                subtitle =
                    nextAir?.let { stringResource(R.string.lib_next_air, Format.relative(it)) }
                        ?: listOfNotNull(
                            item.year?.toString(),
                            item.userRating?.let { "★ $it" },
                            if (item.type.isEpisodic && item.totalEpisodes > 0) "${item.watchedEpisodes}/${item.totalEpisodes}" else null,
                        ).joinToString(" · ").ifEmpty { null },
                // A title being watched shows how far the movie or episode got.
                progress = playback?.let { (it.progress / 100).toFloat() } ?: if (showProgress) progressOf(item) else null,
                rating = state.showInfo[item.key]?.rating?.takeIf { it > 0 },
                airing =
                    if (showAiring) Shelves.airingOf(item, state.showInfo[item.key], state.nextAirDate(item)) else AiringState.Unknown,
                onClick = { onOpen(item) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    count: Int? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (count != null) Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Wide card used by the "Up next" and "Waiting" lists. */
@Composable
fun ShowRow(
    item: LibraryItem,
    headline: String?,
    supporting: String?,
    airing: AiringState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        shape = AppShapes.Item,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Poster(item.poster, item.title, Modifier.width(68.dp), shape = RoundedCornerShape(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    AiringBadge(airing)
                }
                if (headline != null) {
                    Text(
                        headline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                progressOf(item)?.let { p ->
                    val animated by animateFloatAsState(p, Motion.progressSpec(), label = "showProgress")
                    LinearProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    )
                }
            }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { trailing() }
        }
    }
}
