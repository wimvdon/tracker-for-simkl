package io.github.impca201.trackerforsimkl.data

import android.content.Context
import androidx.annotation.StringRes
import io.github.impca201.trackerforsimkl.R
import io.github.impca201.trackerforsimkl.data.api.ApiException
import io.github.impca201.trackerforsimkl.data.api.Parsers
import io.github.impca201.trackerforsimkl.data.api.SimklApi
import io.github.impca201.trackerforsimkl.data.api.TmdbApi
import io.github.impca201.trackerforsimkl.data.api.TmdbKey
import io.github.impca201.trackerforsimkl.data.api.TmdbParsers
import io.github.impca201.trackerforsimkl.data.model.AiringState
import io.github.impca201.trackerforsimkl.data.model.Credits
import io.github.impca201.trackerforsimkl.data.model.CreditsSource
import io.github.impca201.trackerforsimkl.data.model.Episode
import io.github.impca201.trackerforsimkl.data.model.MediaDetails
import io.github.impca201.trackerforsimkl.data.model.MediaSummary
import io.github.impca201.trackerforsimkl.data.model.MediaType
import io.github.impca201.trackerforsimkl.data.model.Person
import io.github.impca201.trackerforsimkl.data.model.PersonCredit
import io.github.impca201.trackerforsimkl.data.model.PersonResult
import io.github.impca201.trackerforsimkl.data.api.tmdbLanguage
import io.github.impca201.trackerforsimkl.data.model.UserProfile
import io.github.impca201.trackerforsimkl.data.model.UserStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** A row on the Discover screen. Each row lists the endpoints to try, in order. */
data class DiscoverSection(
    val id: String,
    @StringRes val title: Int,
    val type: MediaType,
    val sources: List<Pair<String, Map<String, String>>>,
)

/** Public catalogue data (no user state): details, episodes, search, discovery lists. */
class CatalogRepository(
    private val api: SimklApi,
    private val tmdb: TmdbApi,
    private val episodeStore: EpisodeStore,
) {
    private val details = ConcurrentHashMap<String, MediaDetails>()
    private val credits = ConcurrentHashMap<CreditsSource, Credits>()
    private val people = ConcurrentHashMap<Long, Person>()
    private val simklTitles = ConcurrentHashMap<String, MediaSummary>()
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

    /**
     * Cast and crew from TMDB with the user's own [key]; null when the title has no TMDB ID.
     * Throws when TMDB fails, like the SIMKL calls.
     */
    suspend fun credits(
        details: MediaDetails,
        key: TmdbKey,
    ): Credits? {
        val source = CreditsSource.of(details) ?: return null
        credits[source]?.let { return it }
        return TmdbParsers.credits(tmdb.credits(key, source)).also { credits[source] = it }
    }

    /**
     * A person's page from TMDB, in the phone's language; the biography falls back to English,
     * which TMDB often has when the translation is missing.
     */
    suspend fun person(
        personId: Long,
        key: TmdbKey,
    ): Person {
        people[personId]?.let { return it }
        val language = tmdbLanguage(Locale.getDefault())
        val person = TmdbParsers.person(tmdb.person(key, personId, language)) ?: throw ApiException(404, "Not found")
        val withBio =
            if (person.biography == null && !language.startsWith("en")) {
                person.copy(biography = runCatching { TmdbParsers.biography(tmdb.person(key, personId, withCredits = false)) }.getOrNull())
            } else {
                person
            }
        return withBio.also { people[personId] = it }
    }

    /** People by name from TMDB, for Search › People. */
    suspend fun searchPeople(
        query: String,
        key: TmdbKey,
    ): List<PersonResult> = TmdbParsers.people(tmdb.searchPeople(key, query, tmdbLanguage(Locale.getDefault())))

    /** The SIMKL title of a TMDB credit (GET /search/id), or null when SIMKL doesn't have it. */
    suspend fun simklTitle(credit: PersonCredit): MediaSummary? {
        val key = "${credit.movie}:${credit.tmdbId}"
        simklTitles[key]?.let { return it }
        return Parsers.lookup(api.searchByTmdb(credit.tmdbId, credit.movie), credit.movie)?.also { simklTitles[key] = it }
    }

    /** The episodes of a show the user opened (refreshed when they were not in the last hour), sorted. */
    suspend fun episodes(
        type: MediaType,
        simklId: Long,
        refresh: Boolean = false,
    ): List<Episode> {
        if (!type.isEpisodic) return emptyList()
        return episodeStore.episodes(type, simklId, AiringState.Unknown, opened = true, refresh = refresh)
            .sortedWith(compareBy({ if (it.special) 1 else 0 }, { it.season ?: 1 }, { it.episode }))
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
                @StringRes title: Int,
            ) = DiscoverSection("$p-trending-$period", title, type, listOf("$DATA/trending/$files/${period}_100.json" to emptyMap()))

            val bestType = if (type == MediaType.Tv) "series" else "all"
            fun best(
                filter: String,
                @StringRes title: Int,
            ) = DiscoverSection("$p-best-$filter", title, type, listOf("/$p/best/$filter" to mapOf("type" to bestType)))

            return buildList {
                add(trending("today", R.string.discover_trending_today))
                add(trending("week", R.string.discover_trending_week))
                if (type.isEpisodic) {
                    add(DiscoverSection("$p-airing", R.string.discover_airing_today, type, listOf("/$p/airing" to mapOf("date" to "today", "sort" to "popularity"))))
                    add(DiscoverSection("$p-premieres-new", R.string.discover_new_premieres, type, listOf("/$p/premieres/new" to page + ("type" to "all"))))
                    add(DiscoverSection("$p-premieres-soon", R.string.discover_premiering_soon, type, listOf("/$p/premieres/soon" to page + ("type" to "all"))))
                } else {
                    add(DiscoverSection("$p-dvd", R.string.discover_dvd, type, listOf("$DATA/dvd/releases_100.json" to emptyMap())))
                }
                add(trending("month", R.string.discover_trending_month))
                if (type.isEpisodic) {
                    add(best("year", R.string.discover_best_year))
                    add(best("month", R.string.discover_best_month))
                    add(best("watched", R.string.discover_most_watched))
                    add(best("all", R.string.discover_best_all))
                    add(best("voted", R.string.discover_most_voted))
                }
            }
        }
    }
}

/**
 * The user's SIMKL profile (POST /users/settings), kept on the device and only fetched again after
 * the sync saw settings.all move in /sync/activities ([settingsChanged]). Not backed up.
 */
class AccountRepository(
    context: Context,
    private val api: SimklApi,
) {
    private val file = File(context.filesDir, "profile.json")
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var profile: UserProfile? = null

    suspend fun profile(): UserProfile {
        profile?.let { return it }
        val kept = withContext(Dispatchers.IO) { runCatching { json.decodeFromString(UserProfile.serializer(), file.readText()) }.getOrNull() }
        if (kept != null) return kept.also { profile = it }
        val fresh = Parsers.user(api.settings()) ?: throw ApiException(0, "Could not read your SIMKL profile")
        profile = fresh
        withContext(Dispatchers.IO) { runCatching { writeAtomically(file, json.encodeToString(UserProfile.serializer(), fresh)) } }
        return fresh
    }

    suspend fun stats(): UserStats? = Parsers.stats(api.stats(profile().id))

    /** The profile changed on SIMKL: fetch it again the next time it is needed. */
    suspend fun settingsChanged() = clear()

    suspend fun clear() {
        profile = null
        withContext(Dispatchers.IO) { file.delete() }
    }
}
