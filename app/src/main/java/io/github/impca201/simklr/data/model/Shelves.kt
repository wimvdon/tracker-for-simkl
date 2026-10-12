package io.github.impca201.simklr.data.model

import java.time.Instant

/** The tabs of the TV Shows and Anime screens. */
enum class Shelf(
    val label: String,
) {
    UpNext("Up next"),
    Waiting("Waiting"),
    PlanToWatch("Plan to watch"),
    OnHold("On hold"),
    Completed("Completed"),
    Dropped("Dropped"),
}

/** The tabs of the Movies screen. */
enum class MovieShelf(
    val label: String,
    val status: ListStatus,
) {
    PlanToWatch("Plan to watch", ListStatus.PlanToWatch),
    Completed("Watched", ListStatus.Completed),
    Dropped("Dropped", ListStatus.Dropped),
}

object Shelves {
    /**
     * True when a show the user is watching has an episode they can watch right now: an aired,
     * unwatched episode. Uses the next episode's air date when SIMKL sends one, else the counts.
     */
    fun hasAvailableEpisode(
        item: LibraryItem,
        now: Instant = Instant.now(),
    ): Boolean {
        val date = Dates.parse(item.nextToWatch?.date)
        if (date != null) return !date.isAfter(now)
        if (item.totalEpisodes > 0) return item.airedUnwatched > 0
        return item.nextToWatch != null
    }

    fun shelfOf(
        item: LibraryItem,
        now: Instant = Instant.now(),
    ): Shelf =
        when (item.status) {
            ListStatus.Watching -> if (hasAvailableEpisode(item, now)) Shelf.UpNext else Shelf.Waiting
            ListStatus.PlanToWatch -> Shelf.PlanToWatch
            ListStatus.OnHold -> Shelf.OnHold
            ListStatus.Completed -> Shelf.Completed
            ListStatus.Dropped -> Shelf.Dropped
        }

    /**
     * Whether a show will get new episodes: an announced upcoming episode means yes, otherwise
     * the status from the (cached) show details decides.
     */
    fun airingOf(
        item: LibraryItem,
        info: ShowInfo?,
        nextAirDate: Instant?,
    ): AiringState =
        when {
            nextAirDate != null || item.notAiredEpisodes > 0 -> AiringState.Airing
            info != null -> info.airing
            else -> AiringState.Unknown
        }
}
