package io.github.impca201.trackerforsimkl.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.impca201.trackerforsimkl.R
import io.github.impca201.trackerforsimkl.data.NavTab
import io.github.impca201.trackerforsimkl.data.ThemeMode
import io.github.impca201.trackerforsimkl.data.model.ListStatus
import io.github.impca201.trackerforsimkl.data.model.MediaType
import io.github.impca201.trackerforsimkl.data.model.MovieShelf
import io.github.impca201.trackerforsimkl.data.model.Shelf

/** Localised names of the data layer's enums (the data layer itself has no Android resources). */
val NavTab.labelRes: Int
    @StringRes get() =
        when (this) {
            NavTab.Tv -> R.string.tab_tv
            NavTab.Anime -> R.string.tab_anime
            NavTab.Movies -> R.string.tab_movies
            NavTab.Discover -> R.string.tab_discover
            NavTab.Calendar -> R.string.tab_calendar
        }

@Composable
fun NavTab.label(): String = stringResource(labelRes)

@Composable
fun Shelf.label(): String =
    stringResource(
        when (this) {
            Shelf.UpNext -> R.string.shelf_up_next
            Shelf.PlanToWatch -> R.string.status_plan_to_watch
            Shelf.Watched -> R.string.status_watched
            Shelf.OnHold -> R.string.status_on_hold
            Shelf.Dropped -> R.string.status_dropped
        },
    )

@Composable
fun MovieShelf.label(): String =
    stringResource(
        when (this) {
            MovieShelf.PlanToWatch -> R.string.status_plan_to_watch
            MovieShelf.Completed -> R.string.status_watched
            MovieShelf.Dropped -> R.string.status_dropped
        },
    )

@Composable
fun MediaType.label(): String =
    stringResource(
        when (this) {
            MediaType.Tv -> R.string.tab_tv
            MediaType.Anime -> R.string.tab_anime
            MediaType.Movie -> R.string.tab_movies
        },
    )

@Composable
fun ListStatus.label(type: MediaType): String =
    stringResource(
        when (this) {
            ListStatus.Watching -> R.string.status_watching
            ListStatus.PlanToWatch -> R.string.status_plan_to_watch
            ListStatus.OnHold -> R.string.status_on_hold
            ListStatus.Completed -> if (type == MediaType.Movie) R.string.status_watched else R.string.status_completed
            ListStatus.Dropped -> R.string.status_dropped
        },
    )

@Composable
fun ThemeMode.label(): String =
    stringResource(
        when (this) {
            ThemeMode.System -> R.string.theme_system
            ThemeMode.Light -> R.string.theme_light
            ThemeMode.Dark -> R.string.theme_dark
        },
    )
