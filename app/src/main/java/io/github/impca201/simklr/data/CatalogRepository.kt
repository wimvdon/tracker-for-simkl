package io.github.impca201.simklr.data

import io.github.impca201.simklr.data.api.ApiException
import io.github.impca201.simklr.data.api.Parsers
import io.github.impca201.simklr.data.api.SimklApi
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

    /** A random title from SIMKL's random search (skips what the user watched), or null. */
    suspend fun random(type: MediaType): Long? = Parsers.randomId(api.random(type))

    companion object {
        private const val DATA = "https://data.simkl.in/discover"
        private val page = mapOf("page" to "1", "limit" to "50")

        /**
         * Discover rows: SIMKL's trending files (titles must credit Simkl) and the documented
         * airing / premieres / best endpoints. Movies have trending files and DVD releases only.
         */
        fun sections(type: MediaType): List<DiscoverSection> {
            val p = type.catalogPath
            val files = if (type == MediaType.Movie) "movies" else p
            fun trending(
                period: String,
                title: String,
            ) = DiscoverSection("$p-trending-$period", title, type, listOf("$DATA/trending/$files/${period}_100.json" to emptyMap()))

            val bestType = if (type == MediaType.Tv) "series" else "all"
            fun best(
                filter: String,
                title: String,
            ) = DiscoverSection("$p-best-$filter", title, type, listOf("/$p/best/$filter" to mapOf("type" to bestType)))

            return buildList {
                add(trending("today", "Trending on Simkl today"))
                add(trending("week", "Trending on Simkl this week"))
                if (type.isEpisodic) {
                    add(DiscoverSection("$p-airing", "Airing today", type, listOf("/$p/airing" to mapOf("date" to "today", "sort" to "popularity"))))
                    add(DiscoverSection("$p-premieres-new", "New premieres", type, listOf("/$p/premieres/new" to page + ("type" to "all"))))
                    add(DiscoverSection("$p-premieres-soon", "Premiering soon", type, listOf("/$p/premieres/soon" to page + ("type" to "all"))))
                } else {
                    add(DiscoverSection("$p-dvd", "New on DVD & digital", type, listOf("$DATA/dvd/releases_100.json" to emptyMap())))
                }
                add(trending("month", "Trending on Simkl this month"))
                if (type.isEpisodic) {
                    add(best("year", "Best of the year"))
                    add(best("month", "Best this month"))
                    add(best("watched", "Most watched"))
                    add(best("all", "Best of all time"))
                    add(best("voted", "Most voted"))
                }
            }
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
