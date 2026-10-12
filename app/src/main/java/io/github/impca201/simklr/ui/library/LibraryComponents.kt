package io.github.impca201.simklr.ui.library

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import io.github.impca201.simklr.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.impca201.simklr.data.model.MediaType
import io.github.impca201.simklr.ui.components.Format
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
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.impca201.simklr.data.LibraryState
import io.github.impca201.simklr.data.model.AiringState
import io.github.impca201.simklr.data.model.LibraryItem
import io.github.impca201.simklr.data.model.Shelves
import io.github.impca201.simklr.ui.components.AiringBadge
import io.github.impca201.simklr.ui.components.Poster
import io.github.impca201.simklr.ui.components.PosterTile
import io.github.impca201.simklr.ui.main.FloatingBarSpace

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

/** Ended or still running (TV, anime); released or upcoming (movies). */
enum class ReleaseFilter(
    @StringRes val showLabel: Int,
    @StringRes val movieLabel: Int,
) {
    All(R.string.lib_filter_all, R.string.lib_filter_all),
    Running(R.string.lib_filter_still_airing, R.string.lib_filter_upcoming),
    Finished(R.string.lib_filter_ended, R.string.lib_filter_released),
}

enum class RatingFilter(
    @StringRes val label: Int,
) {
    Any(R.string.lib_filter_any),
    Rated(R.string.lib_filter_rated),
    Unrated(R.string.lib_filter_unrated),
}

data class LibraryFilter(
    val release: ReleaseFilter = ReleaseFilter.All,
    val rating: RatingFilter = RatingFilter.Any,
    /** SIMKL anime_type ("tv", "movie", "ova", ...), anime only. */
    val animeType: String? = null,
    /** Title search within the tab. */
    val query: String = "",
) {
    val active: Boolean get() = this != LibraryFilter()

    fun apply(
        items: List<LibraryItem>,
        state: LibraryState,
    ): List<LibraryItem> =
        items.filter { item ->
            val info = state.showInfo[item.key]
            val releaseOk =
                when (release) {
                    ReleaseFilter.All -> true
                    ReleaseFilter.Running ->
                        if (item.type == MediaType.Movie) {
                            info?.releaseStatus != null && info.releaseStatus != "released"
                        } else {
                            Shelves.airingOf(item, info, state.nextAirDate(item)) == AiringState.Airing
                        }
                    ReleaseFilter.Finished ->
                        if (item.type == MediaType.Movie) {
                            info?.releaseStatus == "released"
                        } else {
                            Shelves.airingOf(item, info, state.nextAirDate(item)) == AiringState.Ended
                        }
                }
            val ratingOk =
                when (rating) {
                    RatingFilter.Any -> true
                    RatingFilter.Rated -> item.userRating != null
                    RatingFilter.Unrated -> item.userRating == null
                }
            releaseOk && ratingOk && (animeType == null || item.animeType.equals(animeType, ignoreCase = true)) &&
                (query.isBlank() || item.title.contains(query.trim(), ignoreCase = true))
        }

    /** Compact form for rememberSaveable: "release|rating|animeType". */
    fun encode(): String = "${release.name}|${rating.name}|${animeType.orEmpty()}|$query"

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

        fun decode(value: String): LibraryFilter {
            // The query is last and may itself contain "|".
            val parts = value.split("|", limit = 4)
            return LibraryFilter(
                release = ReleaseFilter.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: ReleaseFilter.All,
                rating = RatingFilter.entries.firstOrNull { it.name == parts.getOrNull(1) } ?: RatingFilter.Any,
                animeType = parts.getOrNull(2)?.ifEmpty { null },
                query = parts.getOrNull(3).orEmpty(),
            )
        }
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

/**
 * Sort and filters of a library tab as one row of dropdown chips: sort, status (still airing /
 * ended, or released / upcoming for movies), my rating and, for anime, the type. A chip with a
 * filter set shows its value and an ✕ that clears it.
 */
@Composable
fun LibraryControls(
    type: MediaType,
    searchHint: String,
    sort: SortOrder,
    onSort: (SortOrder) -> Unit,
    filter: LibraryFilter,
    onFilter: (LibraryFilter) -> Unit,
) {
    // The search icon at the end of the row swaps the chips for a title search field.
    var searching by remember { mutableStateOf(filter.query.isNotEmpty()) }
    if (searching) {
        TitleSearchField(
            query = filter.query,
            hint = searchHint,
            onQuery = { onFilter(filter.copy(query = it)) },
            onClose = {
                onFilter(filter.copy(query = ""))
                searching = false
            },
        )
        return
    }
    val movie = type == MediaType.Movie
    fun releaseLabel(value: ReleaseFilter) = if (movie) value.movieLabel else value.showLabel
    // Labels resolved here, since the chip's option labels are plain lambdas.
    val sortLabels = SortOrder.entries.associateWith { stringResource(it.label) }
    val releaseLabels = ReleaseFilter.entries.associateWith { stringResource(releaseLabel(it)) }
    val ratingLabels = RatingFilter.entries.associateWith { stringResource(it.label) }
    val animeTypeLabels = LibraryFilter.animeTypes.associate { (key, label) -> key to stringResource(label) }
    val allLabel = stringResource(R.string.lib_filter_all)
    val statusChip = stringResource(if (movie) R.string.lib_chip_release else R.string.lib_chip_status)
    val ratingChip = stringResource(R.string.lib_chip_rating)
    val typeChip = stringResource(R.string.lib_chip_type)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "sort") {
            DropdownChip(
                label = sortLabels.getValue(sort),
                selected = false,
                leading = Icons.AutoMirrored.Rounded.Sort,
                options = SortOrder.entries,
                current = sort,
                optionLabel = { sortLabels.getValue(it) },
                onSelect = onSort,
                onClear = null,
            )
        }
        item(key = "status") {
            DropdownChip(
                label = if (filter.release == ReleaseFilter.All) statusChip else releaseLabels.getValue(filter.release),
                selected = filter.release != ReleaseFilter.All,
                options = ReleaseFilter.entries,
                current = filter.release,
                optionLabel = { releaseLabels.getValue(it) },
                onSelect = { onFilter(filter.copy(release = it)) },
                onClear = { onFilter(filter.copy(release = ReleaseFilter.All)) },
            )
        }
        item(key = "rating") {
            DropdownChip(
                label = if (filter.rating == RatingFilter.Any) ratingChip else ratingLabels.getValue(filter.rating),
                selected = filter.rating != RatingFilter.Any,
                options = RatingFilter.entries,
                current = filter.rating,
                optionLabel = { ratingLabels.getValue(it) },
                onSelect = { onFilter(filter.copy(rating = it)) },
                onClear = { onFilter(filter.copy(rating = RatingFilter.Any)) },
            )
        }
        if (type == MediaType.Anime) {
            item(key = "type") {
                val types = listOf<String?>(null) + LibraryFilter.animeTypes.map { it.first }
                fun typeLabel(key: String?) = key?.let { animeTypeLabels[it] } ?: allLabel
                DropdownChip(
                    label = if (filter.animeType == null) typeChip else typeLabel(filter.animeType),
                    selected = filter.animeType != null,
                    options = types,
                    current = filter.animeType,
                    optionLabel = { typeLabel(it) },
                    onSelect = { onFilter(filter.copy(animeType = it)) },
                    onClear = { onFilter(filter.copy(animeType = null)) },
                )
            }
        }
        item(key = "search") {
            IconButton(onClick = { searching = true }) { Icon(Icons.Rounded.Search, contentDescription = searchHint) }
        }
    }
}

/** Title search for one tab: filters as you type; the close button clears it and shows the chips again. */
@Composable
private fun TitleSearchField(
    query: String,
    hint: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }
    TextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(hint) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.lib_close_search)) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        shape = RoundedCornerShape(28.dp),
        colors =
            TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).focusRequester(focus),
    )
}


/** A filter chip that opens a menu of [options]; when [selected], its ✕ calls [onClear]. */
@Composable
private fun <T> DropdownChip(
    label: String,
    selected: Boolean,
    options: List<T>,
    current: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    onClear: (() -> Unit)?,
    leading: ImageVector? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label) },
            leadingIcon = if (leading != null) ({ Icon(leading, null, Modifier.size(18.dp)) }) else null,
            trailingIcon = {
                if (selected && onClear != null) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.lib_clear_filter, label),
                        modifier = Modifier.size(18.dp).clip(CircleShape).clickable(onClick = onClear),
                    )
                } else {
                    Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(18.dp))
                }
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                    trailingIcon = { if (option == current) Icon(Icons.Rounded.Check, null) },
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
                // A paused movie shows how far it got.
                progress = playback?.takeIf { item.type == MediaType.Movie }?.let { (it.progress / 100).toFloat() } ?: if (showProgress) progressOf(item) else null,
                rating = state.showInfo[item.key]?.rating?.takeIf { it > 0 },
                airing =
                    if (showAiring) Shelves.airingOf(item, state.showInfo[item.key], state.nextAirDate(item)) else AiringState.Unknown,
                onClick = { onOpen(item) },
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
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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
        shape = RoundedCornerShape(20.dp),
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
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    )
                }
            }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { trailing() }
        }
    }
}
