package to.wim.watchlist.data.model

import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * The timestamps of POST /sync/activities the app acts on. Each one moves when that part of the
 * user's data changed; per type ones are joined ("tv|anime|movies") so any type moving counts.
 */
data class ActivityStamps(
    val all: String? = null,
    val removed: String = "",
    val rated: String = "",
    val playback: String = "",
    val settings: String? = null,
)

/**
 * When the app may call SIMKL again, as SIMKL's API team asked: nothing that didn't change, and
 * slow-changing data at most once a day. Plain Kotlin so it is unit tested.
 */
object SyncRules {
    /** Minimum time between automatic syncs (app start, back to the foreground); pull-to-refresh skips it. */
    val AUTO_SYNC_INTERVAL = TimeUnit.MINUTES.toMillis(15)

    /** Community scores and release status (/ratings/all) change slowly: once a day at most. */
    val COMMUNITY_RATINGS_INTERVAL = TimeUnit.DAYS.toMillis(1)

    /** Episode lists of shows still airing are checked once a day for new episodes. */
    val AIRING_EPISODES_INTERVAL = TimeUnit.DAYS.toMillis(1)

    /** Opening a show refreshes its episode list, unless that happened less than this ago. */
    val OPENED_EPISODES_INTERVAL = TimeUnit.HOURS.toMillis(1)

    fun autoSyncDue(
        syncedAt: Long,
        now: Long,
    ): Boolean = now - syncedAt >= AUTO_SYNC_INTERVAL

    fun communityRatingsDue(
        fetchedAt: Long,
        now: Long,
    ): Boolean = now - fetchedAt >= COMMUNITY_RATINGS_INTERVAL

    /**
     * Whether a show's kept episode list (fetched at [fetchedAt], null when there is none) must be
     * downloaded again. A show that came back in a /sync/all-items delta is forgotten instead, so
     * it has no list. Ended shows keep theirs until then or until the user opens them ([opened]).
     */
    fun episodesDue(
        fetchedAt: Long?,
        now: Long,
        airing: AiringState,
        opened: Boolean = false,
    ): Boolean {
        fetchedAt ?: return true
        val age = now - fetchedAt
        return when {
            opened -> age >= OPENED_EPISODES_INTERVAL
            airing != AiringState.Ended -> age >= AIRING_EPISODES_INTERVAL
            else -> false
        }
    }

    /** A kept episode list with the episodes that aired since it was downloaded marked aired. */
    fun airedBy(
        episodes: List<Episode>,
        now: Instant,
    ): List<Episode> =
        episodes.map { e ->
            if (!e.aired && Dates.parse(e.date)?.isBefore(now) == true) e.copy(aired = true) else e
        }
}
