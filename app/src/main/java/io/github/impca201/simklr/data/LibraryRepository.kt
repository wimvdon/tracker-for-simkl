package io.github.impca201.simklr.data

import android.content.Context
import io.github.impca201.simklr.data.api.ApiException
import io.github.impca201.simklr.data.api.Parsers
import io.github.impca201.simklr.data.api.SimklApi
import io.github.impca201.simklr.data.api.obj
import io.github.impca201.simklr.data.api.str
import io.github.impca201.simklr.data.model.AiringState
import io.github.impca201.simklr.data.model.CalendarEntry
import io.github.impca201.simklr.data.model.Episode
import io.github.impca201.simklr.data.model.EpisodeRef
import io.github.impca201.simklr.data.model.LibraryItem
import io.github.impca201.simklr.data.model.ListStatus
import io.github.impca201.simklr.data.model.MediaDetails
import io.github.impca201.simklr.data.model.MediaIds
import io.github.impca201.simklr.data.model.MediaType
import io.github.impca201.simklr.data.model.NextEpisode
import io.github.impca201.simklr.data.model.ShowInfo
import io.github.impca201.simklr.data.model.WatchedSeason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

@Serializable
private data class LibrarySnapshot(
    val items: List<LibraryItem> = emptyList(),
    /** /sync/activities "all" at the last sync: the date_from of the next one. */
    val activitiesAll: String? = null,
    /** The removed_from_list timestamps at the last sync. */
    val removedStamp: String? = null,
    /** The rated_at timestamps at the last sync. */
    val ratedStamp: String? = null,
    val syncedAt: Long = 0,
)

data class LibraryState(
    val items: List<LibraryItem> = emptyList(),
    /** Show details keyed by [LibraryItem.key]. */
    val showInfo: Map<String, ShowInfo> = emptyMap(),
    /** Upcoming releases from the SIMKL calendars. */
    val calendar: Map<MediaType, List<CalendarEntry>> = emptyMap(),
    val loaded: Boolean = false,
    val syncing: Boolean = false,
    val error: String? = null,
    val syncedAt: Long = 0,
) {
    private val byKey by lazy { items.associateBy { it.key } }

    /** Earliest upcoming release per "Type:simklId" key. */
    val nextAirDates: Map<String, Instant> by lazy {
        val now = Instant.now()
        calendar.values.flatten()
            .filter { it.date.isAfter(now) }
            .groupBy { "${it.type.name}:${it.simklId}" }
            .mapValues { (_, entries) -> entries.minOf { it.date } }
    }

    fun item(
        type: MediaType,
        simklId: Long,
    ): LibraryItem? = byKey["${type.name}:$simklId"]

    fun nextAirDate(item: LibraryItem): Instant? {
        nextAirDates[item.key]?.let { return it }
        val date = io.github.impca201.simklr.data.model.Dates.parse(item.nextToWatch?.date)
        return date?.takeIf { it.isAfter(Instant.now()) }
    }
}

/**
 * The user's SIMKL lists, synced the way SIMKL's "How to use Sync" documentation requires:
 * 1. POST /sync/activities; nothing else when its "all" timestamp did not change.
 * 2. First sync: the full /sync/all-items. Later: only what changed, with date_from.
 * 3. removed_from_list changed: compare against /sync/all-items?extended=simkl_ids_only.
 * 4. rated_at changed: /sync/ratings with date_from.
 * Everything is cached on disk so the app opens instantly and works offline.
 */
class LibraryRepository(
    context: Context,
    private val api: SimklApi,
    private val scope: CoroutineScope,
) {
    private val libraryFile = File(context.filesDir, "library.json")
    private val showInfoFile = File(context.filesDir, "show_info.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val showInfoSerializer = MapSerializer(String.serializer(), ShowInfo.serializer())
    private val mutex = Mutex()
    private var snapshot = LibrarySnapshot()
    private var syncJob: Job? = null
    private var calendarFetchedAt = 0L
    private var showInfoFetchedAt = 0L

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    suspend fun load() {
        if (_state.value.loaded) return
        withContext(Dispatchers.IO) {
            snapshot = runCatching { json.decodeFromString(LibrarySnapshot.serializer(), libraryFile.readText()) }.getOrDefault(LibrarySnapshot())
            val info = runCatching { json.decodeFromString(showInfoSerializer, showInfoFile.readText()) }.getOrDefault(emptyMap())
            _state.update { it.copy(items = snapshot.items, showInfo = info, loaded = true, syncedAt = snapshot.syncedAt) }
        }
    }

    /** Syncs with SIMKL; returns an error message or null. */
    suspend fun sync(force: Boolean = false): String? {
        load()
        _state.update { it.copy(syncing = true, error = null) }
        val error =
            try {
                mutex.withLock { syncLocked(force) }
                null
            } catch (e: ApiException) {
                if (e.unauthorized) "Your SIMKL login expired. Log out and in again." else e.message
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
        _state.update { it.copy(syncing = false, error = error) }
        if (error == null) {
            scope.launch { runCatching { refreshCalendar() } }
            scope.launch { runCatching { refreshShowInfo() } }
        }
        return error
    }

    private suspend fun syncLocked(force: Boolean) {
        val activities = api.activities()
        val all = activities?.str("all")
        fun stamp(key: String) = listOf("tv_shows", "anime", "movies").joinToString("|") { activities?.obj(it)?.str(key).orEmpty() }
        val removed = stamp("removed_from_list")
        val rated = stamp("rated_at")
        val since = snapshot.activitiesAll
        if (force || since == null) {
            save(LibrarySnapshot(Parsers.library(api.allItems()), all, removed, rated, System.currentTimeMillis()))
            return
        }
        if (all != null && all == since) {
            save(snapshot.copy(syncedAt = System.currentTimeMillis()))
            return
        }
        var items = snapshot.items
        val changed = Parsers.library(api.allItems(dateFrom = since)).associateBy { it.key }
        if (changed.isNotEmpty()) items = items.map { changed[it.key] ?: it } + changed.values.filter { c -> items.none { it.key == c.key } }
        if (removed != snapshot.removedStamp) {
            val response = api.allItemIds()
            val keys = Parsers.libraryKeys(response)
            // A null response means the lists are empty now.
            if (keys.isNotEmpty() || response is kotlinx.serialization.json.JsonNull) items = items.filter { it.key in keys }
        }
        if (rated != snapshot.ratedStamp) {
            val ratings = Parsers.library(api.ratings(dateFrom = since)).associateBy { it.key }
            items = items.map { item -> ratings[item.key]?.let { item.copy(userRating = it.userRating) } ?: item }
        }
        save(LibrarySnapshot(items, all, removed, rated, System.currentTimeMillis()))
    }

    private suspend fun save(value: LibrarySnapshot) {
        snapshot = value
        _state.update { it.copy(items = value.items, syncedAt = value.syncedAt) }
        withContext(Dispatchers.IO) { runCatching { libraryFile.writeText(json.encodeToString(LibrarySnapshot.serializer(), value)) } }
    }

    /** Syncs a moment after local changes so several quick taps end up in one sync. */
    fun syncSoon() {
        syncJob?.cancel()
        syncJob =
            scope.launch {
                delay(2_000)
                sync()
            }
    }

    suspend fun refreshCalendar(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - calendarFetchedAt < TimeUnit.HOURS.toMillis(6) && _state.value.calendar.isNotEmpty()) return
        val result =
            coroutineScope {
                MediaType.entries.map { type ->
                    async { type to runCatching { Parsers.calendar(api.calendar(type), type) }.getOrDefault(emptyList()) }
                }.awaitAll().toMap()
            }
        if (result.values.any { it.isNotEmpty() }) calendarFetchedAt = System.currentTimeMillis()
        _state.update { it.copy(calendar = result) }
    }

    /**
     * Release status (ended or not) and SIMKL rating of everything in the lists: one
     * /ratings/{type}?user_watchlist=all call per type, at most twice a day.
     */
    private suspend fun refreshShowInfo(force: Boolean = false) {
        val age = System.currentTimeMillis() - showInfoFetchedAt
        val missing = _state.value.items.any { it.key !in _state.value.showInfo }
        // Twice a day, or sooner (but not more than every 10 minutes) when new titles were added.
        if (!force && age < TimeUnit.HOURS.toMillis(if (missing) 0 else 12) + TimeUnit.MINUTES.toMillis(10)) return
        val now = System.currentTimeMillis()
        val fetched =
            coroutineScope {
                MediaType.entries.map { type ->
                    async {
                        runCatching { Parsers.watchlistRatings(api.watchlistRatings(type)) }.getOrNull()?.map { (id, value) ->
                            "${type.name}:$id" to value
                        }
                    }
                }.awaitAll()
            }
        if (fetched.all { it == null }) return
        showInfoFetchedAt = now
        _state.update { state ->
            val info = state.showInfo.toMutableMap()
            fetched.filterNotNull().flatten().forEach { (key, value) ->
                val (airing, rating) = value
                info[key] = ShowInfo(airing, now, rating, info[key]?.fanart)
            }
            state.copy(showInfo = info)
        }
        saveShowInfo()
    }

    private fun MediaDetails.toShowInfo(old: ShowInfo?) =
        ShowInfo(if (airing != AiringState.Unknown) airing else old?.airing ?: AiringState.Unknown, System.currentTimeMillis(), old?.rating, fanart)

    private suspend fun saveShowInfo() {
        val info = _state.value.showInfo
        withContext(Dispatchers.IO) { runCatching { showInfoFile.writeText(json.encodeToString(showInfoSerializer, info)) } }
    }

    /** Called by the details screen, which loads the details anyway. */
    suspend fun rememberDetails(details: MediaDetails) {
        val key = "${details.type.name}:${details.ids.simkl}"
        _state.update { it.copy(showInfo = it.showInfo + (key to details.toShowInfo(it.showInfo[key]))) }
        saveShowInfo()
    }

    // ---- Changes ------------------------------------------------------------------------------

    private fun patch(
        type: MediaType,
        simklId: Long,
        transform: (LibraryItem?) -> LibraryItem?,
    ) {
        val key = "${type.name}:$simklId"
        val old = snapshot.items.firstOrNull { it.key == key }
        val new = transform(old)
        val items =
            when {
                new == null -> snapshot.items.filterNot { it.key == key }
                old == null -> snapshot.items + new
                else -> snapshot.items.map { if (it.key == key) new else it }
            }
        snapshot = snapshot.copy(items = items)
        _state.update { it.copy(items = items) }
    }

    private suspend fun <T> change(block: suspend () -> T): T =
        try {
            block().also { syncSoon() }
        } catch (e: Exception) {
            // Undo the optimistic change by syncing with SIMKL.
            scope.launch { sync() }
            throw e
        }

    suspend fun setStatus(
        type: MediaType,
        ids: MediaIds,
        title: String,
        poster: String?,
        year: Int?,
        status: ListStatus,
    ) = change {
        patch(type, ids.simkl) { old ->
            old?.copy(status = status) ?: LibraryItem(type = type, ids = ids, title = title, year = year, poster = poster, status = status)
        }
        api.addToList(type, ids.simkl, status)
    }

    suspend fun remove(
        type: MediaType,
        simklId: Long,
    ) = change {
        patch(type, simklId) { null }
        api.removeFromList(type, simklId)
    }

    suspend fun rate(
        type: MediaType,
        simklId: Long,
        rating: Int?,
    ) = change {
        patch(type, simklId) { it?.copy(userRating = rating) }
        api.rate(type, simklId, rating)
    }

    suspend fun markMovieWatched(
        ids: MediaIds,
        title: String,
        poster: String?,
        year: Int?,
    ) = change {
        patch(MediaType.Movie, ids.simkl) { old ->
            (old ?: LibraryItem(type = MediaType.Movie, ids = ids, title = title, year = year, poster = poster, status = ListStatus.Completed))
                .copy(status = ListStatus.Completed, lastWatchedAt = Instant.now().toString())
        }
        api.markWatched(MediaType.Movie, ids.simkl)
    }

    /**
     * Marks episodes watched or unwatched. [episodes] is the show's full episode list when the
     * caller has it, so the next episode to watch can be worked out right away.
     */
    suspend fun setEpisodesWatched(
        type: MediaType,
        ids: MediaIds,
        title: String,
        poster: String?,
        refs: List<EpisodeRef>,
        watched: Boolean,
        episodes: List<Episode>? = null,
    ) = change {
        patch(type, ids.simkl) { old ->
            val base = old ?: LibraryItem(type = type, ids = ids, title = title, poster = poster, status = ListStatus.Watching)
            applyEpisodes(base, refs, watched, episodes)
        }
        api.setEpisodesWatched(type, ids.simkl, refs, watched)
    }

    /** Marks every aired episode watched (an ended show moves to Completed). */
    suspend fun markAllWatched(
        type: MediaType,
        ids: MediaIds,
    ) = change { api.markWatched(type, ids.simkl) }

    /** "Watching now" check-in; SIMKL marks it watched when the runtime is over. */
    suspend fun checkIn(
        type: MediaType,
        simklId: Long,
        episode: EpisodeRef?,
    ) = change { api.checkIn(type, simklId, episode) }

    /** The checkmark on an "Up next" card. */
    suspend fun markNextWatched(item: LibraryItem) {
        val next = item.nextToWatch?.ref ?: return
        setEpisodesWatched(item.type, item.ids, item.title, item.poster, listOf(next), true)
    }

    suspend fun clear() {
        mutex.withLock {
            snapshot = LibrarySnapshot()
            _state.value = LibraryState(loaded = true)
            withContext(Dispatchers.IO) {
                libraryFile.delete()
                showInfoFile.delete()
            }
        }
    }

    companion object {
        fun applyEpisodes(
            item: LibraryItem,
            refs: List<EpisodeRef>,
            watched: Boolean,
            episodes: List<Episode>?,
        ): LibraryItem {
            val seasons = item.watched.associate { it.number to it.episodes.toMutableSet() }.toMutableMap()
            refs.forEach { ref ->
                val set = seasons.getOrPut(ref.season ?: 1) { mutableSetOf() }
                if (watched) set += ref.episode else set -= ref.episode
            }
            val newWatched = seasons.filterValues { it.isNotEmpty() }.map { (n, eps) -> WatchedSeason(n, eps.sorted()) }.sortedBy { it.number }
            val count = newWatched.sumOf { it.episodes.size }
            val oldCount = item.watched.sumOf { it.episodes.size }
            val watchedEpisodes = (item.watchedEpisodes + count - oldCount).coerceAtLeast(0)
            val next =
                if (episodes != null) {
                    episodes
                        .filter { !it.special && it.aired }
                        .firstOrNull { e -> newWatched.none { it.number == (e.season ?: 1) && e.episode in it.episodes } }
                        ?.let { NextEpisode(EpisodeRef(it.season, it.episode), it.title, it.date) }
                } else if (watched && refs.any { it == item.nextToWatch?.ref }) {
                    // Without the episode list the next one is unknown until the sync; assume the following number.
                    val last = refs.maxBy { (it.season ?: 0) * 10_000 + it.episode }
                    if (item.airedUnwatched > 1) NextEpisode(EpisodeRef(last.season, last.episode + 1)) else null
                } else {
                    item.nextToWatch
                }
            val lastRef = refs.maxByOrNull { (it.season ?: 0) * 10_000 + it.episode }
            return item.copy(
                watched = newWatched,
                watchedEpisodes = watchedEpisodes,
                nextToWatch = next,
                lastWatched = if (watched && lastRef != null) lastRef.label else item.lastWatched,
                lastWatchedAt = if (watched) Instant.now().toString() else item.lastWatchedAt,
                status = if (watched && item.status == ListStatus.PlanToWatch) ListStatus.Watching else item.status,
            )
        }
    }
}
