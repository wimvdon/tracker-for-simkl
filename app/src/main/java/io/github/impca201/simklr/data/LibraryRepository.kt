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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
    val activitiesAll: String? = null,
    val removedStamp: String? = null,
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
 * The user's SIMKL lists. Follows SIMKL's recommended sync: check /sync/activities and only
 * download /sync/all-items when something changed (incrementally with date_from when possible).
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
        val removed =
            listOf("tv_shows", "anime", "movies").joinToString("|") { activities?.obj(it)?.str("removed_from_list").orEmpty() }
        val haveCache = snapshot.items.isNotEmpty() || snapshot.activitiesAll != null
        if (!force && haveCache && all != null && all == snapshot.activitiesAll) {
            save(snapshot.copy(syncedAt = System.currentTimeMillis()))
            return
        }
        val incremental = !force && haveCache && snapshot.activitiesAll != null && removed == snapshot.removedStamp
        val items =
            if (incremental) {
                val changed = Parsers.library(api.allItems(dateFrom = snapshot.activitiesAll))
                val changedKeys = changed.associateBy { it.key }
                snapshot.items.map { changedKeys[it.key] ?: it } + changed.filter { c -> snapshot.items.none { it.key == c.key } }
            } else {
                Parsers.library(api.allItems())
            }
        save(LibrarySnapshot(items, all, removed, System.currentTimeMillis()))
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
     * Loads the details of shows in the library that are needed to tell ended shows from
     * running ones. Ended shows are re-checked monthly, running ones every few days.
     */
    private suspend fun refreshShowInfo() {
        val now = System.currentTimeMillis()
        val current = _state.value.showInfo
        val wanted =
            _state.value.items
                .filter { it.type.isEpisodic }
                .filter { item ->
                    val info = current[item.key] ?: return@filter true
                    val maxAge = if (info.airing == AiringState.Ended) TimeUnit.DAYS.toMillis(30) else TimeUnit.DAYS.toMillis(3)
                    now - info.fetchedAt > maxAge
                }.sortedBy { it.status.ordinal } // Watching first.
        if (wanted.isEmpty()) return
        val semaphore = Semaphore(4)
        coroutineScope {
            wanted.chunked(20).forEach { chunk ->
                val fetched =
                    chunk.map { item ->
                        async {
                            semaphore.withPermit {
                                runCatching { api.details(item.type, item.ids.simkl) }.getOrNull()?.let { obj ->
                                    Parsers.details(obj, item.type)?.let { item.key to it.toShowInfo() }
                                }
                            }
                        }
                    }.awaitAll().filterNotNull()
                _state.update { it.copy(showInfo = it.showInfo + fetched) }
                saveShowInfo()
            }
        }
    }

    private fun MediaDetails.toShowInfo() = ShowInfo(airing, System.currentTimeMillis(), network, fanart)

    private suspend fun saveShowInfo() {
        val info = _state.value.showInfo
        withContext(Dispatchers.IO) { runCatching { showInfoFile.writeText(json.encodeToString(showInfoSerializer, info)) } }
    }

    /** Called by the details screen, which loads the details anyway. */
    suspend fun rememberDetails(details: MediaDetails) {
        if (!details.type.isEpisodic) return
        _state.update { it.copy(showInfo = it.showInfo + ("${details.type.name}:${details.ids.simkl}" to details.toShowInfo())) }
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
            // Undo the optimistic change by reloading from SIMKL.
            scope.launch { sync(force = true) }
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
