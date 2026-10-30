package to.wim.watchlist.data

import android.content.Context
import to.wim.watchlist.data.api.Parsers
import to.wim.watchlist.data.api.SimklApi
import to.wim.watchlist.data.model.AiringState
import to.wim.watchlist.data.model.CalendarEntry
import to.wim.watchlist.data.model.Episode
import to.wim.watchlist.data.model.EpisodeRef
import to.wim.watchlist.data.model.LatestNext
import to.wim.watchlist.data.model.LibraryItem
import to.wim.watchlist.data.model.ListStatus
import to.wim.watchlist.data.model.MediaDetails
import to.wim.watchlist.data.model.MediaIds
import to.wim.watchlist.data.model.MediaType
import to.wim.watchlist.data.model.NextEpisode
import to.wim.watchlist.data.model.Playback
import to.wim.watchlist.data.model.Shelves
import to.wim.watchlist.data.model.ShowInfo
import to.wim.watchlist.data.model.SyncRules
import to.wim.watchlist.data.model.WatchedSeason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
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
    /** /sync/activities "all" at the last sync: the date_from of the next one. */
    val activitiesAll: String? = null,
    /** The removed_from_list timestamps at the last sync. */
    val removedStamp: String? = null,
    /** The rated_at timestamps at the last sync. */
    val ratedStamp: String? = null,
    /** settings.all at the last sync: the profile is fetched again when it moves. */
    val settingsStamp: String? = null,
    val syncedAt: Long = 0,
    /** Paused, partly watched episodes and movies, and the playback timestamps they belong to. */
    val playback: List<Playback> = emptyList(),
    val playbackStamp: String? = null,
    /** Episode after the latest watched one, per item key (continue-from-latest setting). */
    val latestNext: Map<String, LatestNext> = emptyMap(),
    /** When the community ratings (/ratings/all) were last downloaded. */
    val communityRatingsAt: Long = 0,
)

data class LibraryState(
    val items: List<LibraryItem> = emptyList(),
    /** Show details keyed by [LibraryItem.key]. */
    val showInfo: Map<String, ShowInfo> = emptyMap(),
    /** Upcoming releases from the SIMKL calendars. */
    val calendar: Map<MediaType, List<CalendarEntry>> = emptyMap(),
    val playback: List<Playback> = emptyList(),
    val latestNext: Map<String, LatestNext> = emptyMap(),
    val loaded: Boolean = false,
    val syncing: Boolean = false,
    /**
     * The cached lists are being refreshed after the app opened with an outdated cache: Up next
     * waits for it, so episodes watched elsewhere don't show for a moment and then disappear.
     */
    val refreshingStale: Boolean = false,
    val error: String? = null,
    val syncedAt: Long = 0,
) {
    private val byKey by lazy { items.associateBy { it.key } }

    /** The library is empty because it has never been fetched, not because the lists are. */
    val firstSync: Boolean get() = items.isEmpty() && (!loaded || syncing)

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

    /** The paused session of a title (a show has at most one, for one episode). */
    fun playbackFor(
        type: MediaType,
        simklId: Long,
    ): Playback? = playback.firstOrNull { it.type == type && it.simklId == simklId }

    fun nextAirDate(item: LibraryItem): Instant? {
        nextAirDates[item.key]?.let { return it }
        val date = to.wim.watchlist.data.model.Dates.parse(item.nextToWatch?.date)
        return date?.takeIf { it.isAfter(Instant.now()) }
    }
}

/**
 * The user's SIMKL lists, synced the way SIMKL's "How to use Sync" documentation requires:
 * 1. POST /sync/activities; nothing else when its "all" timestamp did not change.
 * 2. First sync: the full /sync/all-items. Later: only what changed, with date_from.
 * 3. removed_from_list changed: compare against /sync/all-items?extended=simkl_ids_only.
 * 4. rated_at changed: /sync/ratings with date_from.
 * 5. playback changed: GET /sync/playback. settings.all changed: [onSettingsChanged].
 * One sync runs at a time: a trigger while one is running waits for it instead of starting another.
 * Everything is cached on disk so the app opens instantly and works offline.
 */
class LibraryRepository(
    context: Context,
    private val api: SimklApi,
    private val settings: SettingsStore,
    private val episodes: EpisodeStore,
    private val scope: CoroutineScope,
    private val onSettingsChanged: suspend () -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val libraryFile = File(context.filesDir, "library.json")
    private val showInfoFile = File(context.filesDir, "show_info.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val showInfoSerializer = MapSerializer(String.serializer(), ShowInfo.serializer())
    private val mutex = Mutex()
    @Volatile private var snapshot = LibrarySnapshot()
    private var syncJob: Job? = null
    private val runningLock = Any()
    private var running: Deferred<String?>? = null
    private val communityRatingsLock = Mutex()
    private var calendarFetchedAt = 0L

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    suspend fun load() {
        if (_state.value.loaded) return
        // Under the lock so concurrent callers don't read the file twice and overwrite a newer snapshot.
        mutex.withLock { if (!_state.value.loaded) loadLocked() }
    }

    private suspend fun loadLocked() {
        withContext(Dispatchers.IO) {
            snapshot = runCatching { json.decodeFromString(LibrarySnapshot.serializer(), libraryFile.readText()) }.getOrDefault(LibrarySnapshot())
            val info = runCatching { json.decodeFromString(showInfoSerializer, showInfoFile.readText()) }.getOrDefault(emptyMap())
            _state.update {
                it.copy(
                    items = snapshot.items,
                    showInfo = info,
                    playback = snapshot.playback,
                    latestNext = snapshot.latestNext,
                    loaded = true,
                    syncedAt = snapshot.syncedAt,
                )
            }
        }
    }

    /**
     * The automatic sync when the app starts or comes back to the foreground: joins a sync that is
     * already running, and is skipped when the last one is less than [SyncRules.AUTO_SYNC_INTERVAL]
     * old, as SIMKL's API team asks. Pull-to-refresh calls [sync] directly.
     */
    suspend fun syncIfStale(): String? {
        load()
        synchronized(runningLock) { running?.takeIf { it.isActive } }?.let { return it.await() }
        if (!SyncRules.autoSyncDue(snapshot.syncedAt, System.currentTimeMillis())) return null
        _state.update { it.copy(refreshingStale = true) }
        try {
            return sync()
        } finally {
            _state.update { it.copy(refreshingStale = false) }
        }
    }

    /**
     * Syncs with SIMKL; returns an error message or null. A sync that is already running is joined,
     * unless [force] or [fresh] (after a change, which that sync may have missed): then a new one
     * runs right after it.
     */
    suspend fun sync(
        force: Boolean = false,
        fresh: Boolean = false,
    ): String? {
        load()
        val run =
            synchronized(runningLock) {
                running?.takeIf { it.isActive && !force && !fresh }
                    ?: scope.async { runSync(force) }.also { running = it }
            }
        return run.await()
    }

    private suspend fun runSync(force: Boolean): String? {
        _state.update { it.copy(syncing = true, error = null) }
        val error =
            try {
                mutex.withLock { syncLocked(force) }
                null
            } catch (e: CancellationException) {
                // Cancelled (signed out): not an error to show.
                _state.update { it.copy(syncing = false) }
                throw e
            } catch (e: Exception) {
                e.userMessage(appContext.resources)
            }
        _state.update { it.copy(syncing = false, error = error) }
        if (error == null) {
            scope.launch { runCatching { refreshCalendar() } }
            scope.launch { runCatching { refreshCommunityRatings() } }
            scope.launch { runCatching { refreshLatestNext() } }
        }
        return error
    }

    private suspend fun syncLocked(force: Boolean) {
        val stamps = Parsers.activityStamps(api.activities())
        val all = stamps.all
        val removed = stamps.removed
        val rated = stamps.rated
        val since = snapshot.activitiesAll
        if (stamps.settings != snapshot.settingsStamp) runCatching { onSettingsChanged() }
        // Only when SIMKL says the paused sessions changed, or when everything is downloaded again.
        val playbackMoved = force || since == null || stamps.playback != snapshot.playbackStamp
        if (force || since == null) {
            val items = Parsers.library(api.allItems())
            saveWithPlayback(
                snapshot.copy(
                    items = items,
                    activitiesAll = all,
                    removedStamp = removed,
                    ratedStamp = rated,
                    settingsStamp = stamps.settings,
                    syncedAt = System.currentTimeMillis(),
                ),
                stamps.playback,
                playbackMoved,
            )
            return
        }
        if (all != null && all == since) {
            saveWithPlayback(snapshot.copy(settingsStamp = stamps.settings, syncedAt = System.currentTimeMillis()), stamps.playback, playbackMoved)
            return
        }
        var items = snapshot.items
        val changed = Parsers.library(api.allItems(dateFrom = since)).associateBy { it.key }
        if (changed.isNotEmpty()) items = items.map { changed[it.key] ?: it } + changed.values.filter { c -> items.none { it.key == c.key } }
        // Shows in the delta may have new episodes: their kept episode lists are downloaded again when needed.
        episodes.forget(changed.values.filter { it.type.isEpisodic }.map { it.key })
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
        // Shows watched elsewhere get their "next after latest" first, so they don't flash into Up next.
        val latestNext = snapshot.latestNext + lookupLatestNext(Shelves.changedLatestLookups(snapshot.items, items, snapshot.latestNext))
        // Watching an episode also hides its paused session, so refresh both together.
        saveWithPlayback(
            snapshot.copy(
                items = items,
                activitiesAll = all,
                removedStamp = removed,
                ratedStamp = rated,
                settingsStamp = stamps.settings,
                syncedAt = System.currentTimeMillis(),
                latestNext = latestNext,
            ),
            stamps.playback,
            playbackMoved,
        )
    }

    /**
     * Saves [value], when [fetch] with a fresh GET /sync/playback in the same update, so lists
     * ordered by playback don't reshuffle twice. A failed playback request keeps the previous list
     * and timestamp, so the next sync tries again (it is only a convenience).
     */
    private suspend fun saveWithPlayback(
        value: LibrarySnapshot,
        stamp: String,
        fetch: Boolean,
    ) {
        if (!fetch) return save(value)
        val items = value.items
        val list =
            runCatching {
                Parsers.playback(api.playback()) { id ->
                    when {
                        items.any { it.type == MediaType.Anime && it.ids.simkl == id } -> MediaType.Anime
                        items.any { it.type == MediaType.Tv && it.ids.simkl == id } -> MediaType.Tv
                        else -> null
                    }
                }
            }.getOrNull()
        save(if (list != null) value.copy(playback = list, playbackStamp = stamp) else value)
    }

    private suspend fun save(value: LibrarySnapshot) {
        snapshot = value
        _state.update { it.copy(items = value.items, playback = value.playback, latestNext = value.latestNext, syncedAt = value.syncedAt) }
        withContext(Dispatchers.IO) { runCatching { writeAtomically(libraryFile, json.encodeToString(LibrarySnapshot.serializer(), value)) } }
    }

    /** Syncs a moment after local changes so several quick taps end up in one sync. */
    fun syncSoon() {
        syncJob?.cancel()
        syncJob =
            scope.launch {
                delay(2_000)
                sync(fresh = true)
            }
    }

    suspend fun refreshCalendar(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - calendarFetchedAt < TimeUnit.HOURS.toMillis(6) && _state.value.calendar.isNotEmpty()) return
        val result =
            coroutineScope {
                MediaType.entries.map { type ->
                    async { type to runCatching { Parsers.calendar(api.calendar(type), type) }.getOrNull() }
                }.awaitAll()
            }
        if (result.any { it.second?.isNotEmpty() == true }) calendarFetchedAt = System.currentTimeMillis()
        // A failed fetch (offline) keeps that type's previous calendar instead of wiping it.
        _state.update { state -> state.copy(calendar = result.associate { (type, list) -> type to (list ?: state.calendar[type].orEmpty()) }) }
    }

    /**
     * Release status (ended or not) and community rating of everything in the lists: one
     * /ratings/all?user_watchlist=all call, at most once a day (the time is kept on the device).
     * Titles added in between get theirs when their details are opened, or the next day.
     */
    private suspend fun refreshCommunityRatings() {
        // Already running (two syncs finishing close together): nothing to add.
        if (!communityRatingsLock.tryLock()) return
        try {
            val now = System.currentTimeMillis()
            if (!SyncRules.communityRatingsDue(snapshot.communityRatingsAt, now)) return
            val fetched = Parsers.watchlistRatings(api.watchlistRatings())
            applyCommunityRatings(fetched, now)
            mutex.withLock { save(snapshot.copy(communityRatingsAt = now)) }
        } finally {
            communityRatingsLock.unlock()
        }
    }

    private suspend fun applyCommunityRatings(
        fetched: Map<String, Pair<String?, Double?>>,
        now: Long,
    ) {
        _state.update { state ->
            val info = state.showInfo.toMutableMap()
            fetched.forEach { (key, value) ->
                val (status, rating) = value
                val old = info[key]
                // A missing release status keeps what was known (e.g. from the details screen).
                val airing = AiringState.fromApi(status).takeIf { it != AiringState.Unknown } ?: old?.airing ?: AiringState.Unknown
                info[key] = ShowInfo(airing, now, rating ?: old?.rating, old?.fanart, status ?: old?.releaseStatus)
            }
            state.copy(showInfo = info)
        }
        saveShowInfo()
    }

    private fun MediaDetails.toShowInfo(old: ShowInfo?) =
        ShowInfo(
            if (airing != AiringState.Unknown) airing else old?.airing ?: AiringState.Unknown,
            System.currentTimeMillis(),
            old?.rating,
            fanart,
            old?.releaseStatus ?: status,
        )

    /**
     * For the "continue from the latest episode" setting: finds the first aired episode after the
     * latest watched one, from the episode lists of shows that would otherwise be in Up next.
     * A few at a time; results are kept until the watched count changes or they are 6 hours old.
     */
    suspend fun refreshLatestNext() {
        if (!settings.current().continueFromLatest) return
        val now = System.currentTimeMillis()
        val wanted =
            snapshot.items.filter { item ->
                val known = snapshot.latestNext[item.key]
                Shelves.needsLatestLookup(item) &&
                    (known?.watchedCount != item.watchedEpisodes || now - known.computedAt > TimeUnit.HOURS.toMillis(6))
            }
        if (wanted.isEmpty()) return
        val found = lookupLatestNext(wanted)
        mutex.withLock { save(snapshot.copy(latestNext = snapshot.latestNext + found)) }
    }

    /**
     * Episode lists of [items], a few at a time (only with the continue-from-latest setting). The
     * lists kept on the device are used while [SyncRules.episodesDue] allows, so most don't call SIMKL.
     */
    private suspend fun lookupLatestNext(items: List<LibraryItem>): Map<String, LatestNext> {
        if (items.isEmpty() || !settings.current().continueFromLatest) return emptyMap()
        val semaphore = Semaphore(3)
        val showInfo = _state.value.showInfo
        return coroutineScope {
            items.map { item ->
                async {
                    semaphore.withPermit {
                        val airing = showInfo[item.key]?.airing ?: AiringState.Unknown
                        runCatching { episodes.episodes(item.type, item.ids.simkl, airing) }.getOrNull()?.let {
                            item.key to Shelves.latestNextFrom(item, it)
                        }
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    private suspend fun saveShowInfo() {
        val info = _state.value.showInfo
        withContext(Dispatchers.IO) { runCatching { writeAtomically(showInfoFile, json.encodeToString(showInfoSerializer, info)) } }
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
            // Undo the optimistic change by syncing with SIMKL. A failed change leaves the
            // activities unchanged, so a normal sync would keep the optimistic item: force it.
            if (e !is CancellationException) scope.launch { sync(force = true) }
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

    /** "Watching now" check-in; SIMKL marks it watched when the runtime is over. */
    suspend fun checkIn(
        type: MediaType,
        simklId: Long,
        episode: EpisodeRef?,
    ) = change { api.checkIn(type, simklId, episode) }

    /** The checkmark on an "Up next" card. */
    suspend fun markEpisodeWatched(
        item: LibraryItem,
        ref: EpisodeRef,
    ) = setEpisodesWatched(item.type, item.ids, item.title, item.poster, listOf(ref), true)

    /**
     * Marks a paused (partly watched) episode or movie watched and removes its saved progress.
     * SIMKL hides a session once the item is watched; deleting it keeps the playback list clean.
     */
    suspend fun markPlaybackWatched(
        playback: Playback,
        item: LibraryItem?,
    ) {
        val ids = item?.ids ?: MediaIds(playback.simklId)
        if (playback.type == MediaType.Movie) {
            markMovieWatched(ids, playback.title, item?.poster, item?.year)
        } else {
            val ref = playback.episode ?: return
            setEpisodesWatched(playback.type, ids, playback.title, item?.poster, listOf(ref), true)
        }
        runCatching { api.deletePlayback(playback.id) }
        save(snapshot.copy(playback = snapshot.playback.filterNot { it.id == playback.id }))
    }

    suspend fun clear() {
        syncJob?.cancel()
        synchronized(runningLock) { running?.cancel() }
        mutex.withLock {
            calendarFetchedAt = 0L
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
