package to.wim.watchlist.data

import android.content.Context
import to.wim.watchlist.data.api.Parsers
import to.wim.watchlist.data.api.SimklApi
import to.wim.watchlist.data.model.AiringState
import to.wim.watchlist.data.model.Episode
import to.wim.watchlist.data.model.MediaType
import to.wim.watchlist.data.model.SyncRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
private data class KeptEpisodes(
    val fetchedAt: Long,
    val episodes: List<Episode>,
)

/**
 * Episode lists (GET /tv/episodes/{id}, /anime/episodes/{id}) kept on the device: they are the
 * same for every user, so they are only downloaded again when [SyncRules.episodesDue] says so or
 * after [forget] (the show came back in a /sync/all-items delta). Not backed up.
 */
class EpisodeStore(
    context: Context,
    private val api: SimklApi,
) {
    private val file = File(context.filesDir, "episodes.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), KeptEpisodes.serializer())
    private val mutex = Mutex()
    private var kept: MutableMap<String, KeptEpisodes>? = null

    private suspend fun keptLocked(): MutableMap<String, KeptEpisodes> =
        kept ?: withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyMap()).toMutableMap()
        }.also { kept = it }

    /**
     * The episode list of a show, unsorted. [opened] is true when the user opened the show, which
     * refreshes it; [refresh] always downloads it. When the download fails the kept list is used.
     */
    suspend fun episodes(
        type: MediaType,
        simklId: Long,
        airing: AiringState,
        opened: Boolean = false,
        refresh: Boolean = false,
    ): List<Episode> {
        val key = "${type.name}:$simklId"
        val now = System.currentTimeMillis()
        val entry = mutex.withLock { keptLocked()[key] }
        if (entry != null && !refresh && !SyncRules.episodesDue(entry.fetchedAt, now, airing, opened)) {
            return SyncRules.airedBy(entry.episodes, Instant.ofEpochMilli(now))
        }
        val fresh =
            try {
                Parsers.episodes(api.episodes(type, simklId))
            } catch (e: Exception) {
                if (e is CancellationException || entry == null) throw e
                return SyncRules.airedBy(entry.episodes, Instant.ofEpochMilli(now))
            }
        mutex.withLock {
            val map = keptLocked()
            map[key] = KeptEpisodes(now, fresh)
            // Shows not looked at for a month are dropped, so the file doesn't keep growing.
            map.values.removeAll { now - it.fetchedAt > TimeUnit.DAYS.toMillis(30) }
            saveLocked(map)
        }
        return fresh
    }

    /** Drops the kept lists of these "Type:simklId" keys, so they are downloaded the next time they are needed. */
    suspend fun forget(keys: Collection<String>) {
        if (keys.isEmpty()) return
        mutex.withLock {
            val map = keptLocked()
            if (map.keys.removeAll(keys.toSet())) saveLocked(map)
        }
    }

    suspend fun clear() {
        mutex.withLock {
            kept = mutableMapOf()
            withContext(Dispatchers.IO) { file.delete() }
        }
    }

    private suspend fun saveLocked(map: Map<String, KeptEpisodes>) {
        val text = json.encodeToString(serializer, map)
        withContext(Dispatchers.IO) { runCatching { writeAtomically(file, text) } }
    }
}
