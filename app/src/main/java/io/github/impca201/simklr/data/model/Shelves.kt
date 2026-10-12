package io.github.impca201.simklr.data.model

import kotlinx.serialization.Serializable
import java.time.Instant

/** The tabs of the TV Shows and Anime screens. */
enum class Shelf(
    val label: String,
) {
    UpNext("Up next"),

    /** Caught-up shows (watching, nothing to watch now) and completed ones together. */
    Watched("Watched"),
    PlanToWatch("Plan to watch"),
    OnHold("On hold"),
    Dropped("Dropped"),
}

/**
 * The episode after the most recently watched one, worked out from the show's episode list
 * (for the "continue from the latest episode" setting). [watchedCount] is the item's watched
 * count it was computed for, so it is recomputed when that changes.
 */
@Serializable
data class LatestNext(
    val watchedCount: Int,
    val next: NextEpisode?,
)

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
    /** Position of an episode in watching order (specials, season 0, sort first and are ignored). */
    fun order(ref: EpisodeRef): Long = (ref.season ?: 1).toLong() * 100_000 + ref.episode

    /** The most recently aired-order episode the user watched, ignoring specials. */
    fun latestWatched(item: LibraryItem): EpisodeRef? =
        item.watched
            .filter { it.number > 0 }
            .flatMap { season -> season.episodes.map { EpisodeRef(season.number, it) } }
            .maxByOrNull { order(it) }

    /**
     * True when SIMKL's next episode lies before the latest watched one (the user skipped
     * episodes or seasons), so the "continue from the latest episode" setting needs the
     * episode list to find what comes after the latest one.
     */
    fun hasGap(item: LibraryItem): Boolean {
        if (item.status != ListStatus.Watching || !item.type.isEpisodic) return false
        val next = item.nextToWatch?.ref ?: return false
        val latest = latestWatched(item) ?: return false
        return order(next) < order(latest)
    }

    /** The first aired, regular episode after the latest watched one, from the episode list. */
    fun latestNextFrom(
        item: LibraryItem,
        episodes: List<Episode>,
    ): LatestNext {
        val latest = latestWatched(item)?.let { order(it) } ?: 0
        val next =
            episodes
                .filter { !it.special && it.aired }
                .sortedBy { order(EpisodeRef(it.season, it.episode)) }
                .firstOrNull { order(EpisodeRef(it.season, it.episode)) > latest }
        return LatestNext(item.watchedEpisodes, next?.let { NextEpisode(EpisodeRef(it.season, it.episode), it.title, it.date) })
    }

    /**
     * The next episode to watch: SIMKL's (the first unwatched one), or with [fromLatest] the one
     * after the latest watched episode once [latest] is known for a show with a gap.
     */
    fun nextEpisode(
        item: LibraryItem,
        fromLatest: Boolean,
        latest: LatestNext?,
    ): NextEpisode? {
        if (!fromLatest || !hasGap(item) || latest == null || latest.watchedCount != item.watchedEpisodes) return item.nextToWatch
        return latest.next
    }

    /**
     * True when a show the user is watching has an episode they can watch right now: an aired,
     * unwatched episode. Uses the next episode's air date when SIMKL sends one, else the counts.
     */
    fun hasAvailableEpisode(
        item: LibraryItem,
        now: Instant = Instant.now(),
        fromLatest: Boolean = false,
        latest: LatestNext? = null,
    ): Boolean {
        if (fromLatest && hasGap(item) && latest != null && latest.watchedCount == item.watchedEpisodes) {
            // Only aired episodes are considered in latestNextFrom.
            return latest.next != null
        }
        val date = Dates.parse(item.nextToWatch?.date)
        if (date != null) return !date.isAfter(now)
        if (item.totalEpisodes > 0) return item.airedUnwatched > 0
        return item.nextToWatch != null
    }

    fun shelfOf(
        item: LibraryItem,
        now: Instant = Instant.now(),
        fromLatest: Boolean = false,
        latest: LatestNext? = null,
    ): Shelf =
        when (item.status) {
            ListStatus.Watching -> if (hasAvailableEpisode(item, now, fromLatest, latest)) Shelf.UpNext else Shelf.Watched
            ListStatus.PlanToWatch -> Shelf.PlanToWatch
            ListStatus.OnHold -> Shelf.OnHold
            ListStatus.Completed -> Shelf.Watched
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
