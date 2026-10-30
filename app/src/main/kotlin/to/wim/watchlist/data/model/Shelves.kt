package to.wim.watchlist.data.model

import kotlinx.serialization.Serializable
import java.time.Instant

/** The tabs of the TV Shows and Anime screens. */
enum class Shelf {
    UpNext,
    PlanToWatch,

    /** Caught-up shows (watching, nothing to watch now) and completed ones together. */
    Watched,
    OnHold,
    Dropped,
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
    /** When it was worked out (epoch ms); episodes air over time, so it goes stale. */
    val computedAt: Long = 0,
)

/** The tabs of the Movies screen. */
enum class MovieShelf(
    val status: ListStatus,
) {
    PlanToWatch(ListStatus.PlanToWatch),
    Completed(ListStatus.Completed),
    Dropped(ListStatus.Dropped),
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
     * The season the episode list opens on (keys of [seasons]: season numbers, 0 for specials):
     * the paused episode's season, else the season of the latest watched episode, else SIMKL's
     * next episode's, else the first one.
     */
    fun startSeason(
        item: LibraryItem?,
        playback: EpisodeRef?,
        seasons: Collection<Int>,
    ): Int? {
        val candidates =
            listOfNotNull(
                playback?.let { it.season ?: 1 },
                item?.let { latestWatched(it) }?.let { it.season ?: 1 },
                item?.nextToWatch?.ref?.let { it.season ?: 1 },
            )
        return candidates.firstOrNull { it in seasons } ?: seasons.firstOrNull()
    }

    /**
     * True when SIMKL's next episode lies before the latest watched one (the user skipped
     * episodes or seasons).
     */
    fun hasGap(item: LibraryItem): Boolean {
        if (item.status != ListStatus.Watching || !item.type.isEpisodic) return false
        val next = item.nextToWatch?.ref ?: return false
        val latest = latestWatched(item) ?: return false
        return order(next) < order(latest)
    }

    /**
     * Whether the "continue from the latest episode" setting needs this show's episode list:
     * a show being watched that SIMKL's data would put in Up next (its counts include skipped
     * episodes, so they can't tell), or whose next episode lies before the latest watched one.
     */
    fun needsLatestLookup(item: LibraryItem): Boolean {
        if (item.status != ListStatus.Watching || !item.type.isEpisodic || latestWatched(item) == null) return false
        return hasGap(item) || hasAvailableEpisode(item)
    }

    /**
     * Shows whose "next after latest" must be worked out before a sync's result is shown: their
     * watched count changed since [before] (watched in another app) and they need a lookup.
     * Without it they would fall back to SIMKL's counts and briefly jump into Up next.
     */
    fun changedLatestLookups(
        before: List<LibraryItem>,
        after: List<LibraryItem>,
        known: Map<String, LatestNext>,
    ): List<LibraryItem> {
        val old = before.associateBy { it.key }
        return after.filter { item ->
            val previous = old[item.key] ?: return@filter false
            previous.watchedEpisodes != item.watchedEpisodes &&
                needsLatestLookup(item) &&
                validLatest(item, known[item.key]) == null
        }
    }

    /** A lookup result that still matches the item (computed for its current watched count). */
    fun validLatest(
        item: LibraryItem,
        latest: LatestNext?,
    ): LatestNext? = latest?.takeIf { it.watchedCount == item.watchedEpisodes }

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
        return LatestNext(
            item.watchedEpisodes,
            next?.let { NextEpisode(EpisodeRef(it.season, it.episode), it.title, it.date) },
            System.currentTimeMillis(),
        )
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
        if (!fromLatest || item.status != ListStatus.Watching) return item.nextToWatch
        val valid = validLatest(item, latest) ?: return item.nextToWatch
        return valid.next
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
        if (fromLatest && item.status == ListStatus.Watching) {
            // Only aired episodes after the latest watched one count (see latestNextFrom).
            validLatest(item, latest)?.let { return it.next != null }
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
