package to.wim.watchlist.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import to.wim.watchlist.R
import to.wim.watchlist.data.NavTab
import to.wim.watchlist.data.ThemeMode
import to.wim.watchlist.data.model.ListStatus
import to.wim.watchlist.data.model.MediaType
import to.wim.watchlist.data.model.MovieShelf
import to.wim.watchlist.data.model.Shelf
import to.wim.watchlist.data.model.ShowStatus

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

val ShowStatus.label: Int
    @StringRes get() =
        when (this) {
            ShowStatus.Airing -> R.string.detail_status_airing
            ShowStatus.Ended -> R.string.detail_status_ended
            ShowStatus.Cancelled -> R.string.detail_status_cancelled
            ShowStatus.Returning -> R.string.detail_status_returning
            ShowStatus.Tba -> R.string.detail_status_tba
            ShowStatus.Upcoming -> R.string.detail_status_upcoming
            ShowStatus.InProduction -> R.string.detail_status_in_production
            ShowStatus.Planned -> R.string.detail_status_planned
            ShowStatus.Released -> R.string.detail_status_released
        }
