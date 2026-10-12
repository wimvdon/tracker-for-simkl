package io.github.impca201.simklr.data

import io.github.impca201.simklr.data.api.ApiException
import io.github.impca201.simklr.data.api.Parsers
import io.github.impca201.simklr.data.api.SimklApi
import io.github.impca201.simklr.data.api.asObject
import io.github.impca201.simklr.data.api.long
import io.github.impca201.simklr.data.api.obj
import io.github.impca201.simklr.data.model.Episode
import io.github.impca201.simklr.data.model.MediaDetails
import io.github.impca201.simklr.data.model.MediaSummary
import io.github.impca201.simklr.data.model.MediaType
import io.github.impca201.simklr.data.model.UserProfile
import io.github.impca201.simklr.data.model.UserStats
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/** A row on the Discover screen. Each row lists the endpoints to try, in order. */
data class DiscoverSection(
    val id: String,
    val title: String,
    val type: MediaType,
    val sources: List<Pair<String, Map<String, String>>>,
)

/** Public catalogue data (no user state): details, episodes, search, discovery lists. */
class CatalogRepository(
    private val api: SimklApi,
) {
    private val details = ConcurrentHashMap<String, MediaDetails>()
    private val episodes = ConcurrentHashMap<String, List<Episode>>()
    private val lists = ConcurrentHashMap<String, List<MediaSummary>>()

    suspend fun details(
        type: MediaType,
        simklId: Long,
        refresh: Boolean = false,
    ): MediaDetails {
        val key = "$type:$simklId"
        if (!refresh) details[key]?.let { return it }
        val obj = api.details(type, simklId) ?: throw ApiException(404, "Not found")
        return (Parsers.details(obj, type) ?: throw ApiException(404, "Not found")).also { details[key] = it }
    }

    suspend fun episodes(
        type: MediaType,
        simklId: Long,
        refresh: Boolean = false,
    ): List<Episode> {
        if (!type.isEpisodic) return emptyList()
        val key = "$type:$simklId"
        if (!refresh) episodes[key]?.let { return it }
        return Parsers.episodes(api.episodes(type, simklId))
            .sortedWith(compareBy({ if (it.special) 1 else 0 }, { it.season ?: 1 }, { it.episode }))
            .also { episodes[key] = it }
    }

    suspend fun search(
        query: String,
        types: List<MediaType>,
    ): Map<MediaType, List<MediaSummary>> =
        coroutineScope {
            types.map { type ->
                async { type to runCatching { Parsers.summaries(api.search(type, query), type) }.getOrDefault(emptyList()) }
            }.awaitAll().toMap()
        }

    /** First source of the section that returns anything; empty when none does. */
    suspend fun section(
        section: DiscoverSection,
        refresh: Boolean = false,
    ): List<MediaSummary> {
        if (!refresh) lists[section.id]?.let { return it }
        for ((path, params) in section.sources) {
            val items = runCatching { Parsers.summaries(api.list(path, params), section.type) }.getOrDefault(emptyList())
            if (items.isNotEmpty()) return items.also { lists[section.id] = it }
        }
        return emptyList()
    }

    /** A random title from SIMKL's "random" search, or null. */
    suspend fun random(type: MediaType): Long? {
        val result = api.list("/search/random/", mapOf("service" to "simkl", "type" to type.searchPath, "limit" to "1"))
        val obj = result.asObject() ?: (result as? kotlinx.serialization.json.JsonArray)?.firstOrNull().asObject()
        return obj?.long("simkl_id") ?: obj?.long("simkl") ?: obj?.obj("ids")?.long("simkl")
    }

    companion object {
        private val extended = mapOf("extended" to "overview,metadata,tmdb,genres")

        fun sections(type: MediaType): List<DiscoverSection> {
            val p = type.catalogPath
            val dataPath = if (type == MediaType.Movie) "movies" else p
            val list =
                mutableListOf(
                    DiscoverSection(
                        "$p-trending-today",
                        "Trending today",
                        type,
                        listOf("/$p/trending/today" to extended, "https://data.simkl.in/discover/trending/$dataPath/today_100.json" to emptyMap()),
                    ),
                    DiscoverSection(
                        "$p-trending-week",
                        "Trending this week",
                        type,
                        listOf("/$p/trending/week" to extended, "https://data.simkl.in/discover/trending/$dataPath/week_100.json" to emptyMap()),
                    ),
                )
            if (type.isEpisodic) {
                list += DiscoverSection("$p-airing", "Airing today", type, listOf("/$p/airing" to mapOf("date" to "today", "sort" to "rank")))
                list += DiscoverSection("$p-premieres-new", "New premieres", type, listOf("/$p/premieres/new" to mapOf("type" to "all")))
                list += DiscoverSection("$p-premieres-soon", "Premiering soon", type, listOf("/$p/premieres/soon" to mapOf("type" to "all")))
            }
            list +=
                DiscoverSection(
                    "$p-trending-month",
                    "Popular this month",
                    type,
                    listOf("/$p/trending/month" to extended, "https://data.simkl.in/discover/trending/$dataPath/month_100.json" to emptyMap()),
                )
            list +=
                DiscoverSection(
                    "$p-best",
                    "Best of all time",
                    type,
                    listOf("/$p/best/all" to mapOf("type" to if (type == MediaType.Tv) "series" else "all")),
                )
            list += DiscoverSection("$p-voted", "Most voted", type, listOf("/$p/best/voted" to mapOf("type" to "all")))
            return list
        }
    }
}

class AccountRepository(
    private val api: SimklApi,
) {
    private var profile: UserProfile? = null

    suspend fun profile(refresh: Boolean = false): UserProfile {
        if (!refresh) profile?.let { return it }
        return (Parsers.user(api.settings()) ?: throw ApiException(0, "Could not read your SIMKL profile")).also { profile = it }
    }

    suspend fun stats(): UserStats? = Parsers.stats(api.stats(profile().id))

    fun clear() {
        profile = null
    }
}
