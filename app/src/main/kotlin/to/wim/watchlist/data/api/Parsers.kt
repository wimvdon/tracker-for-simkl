package to.wim.watchlist.data.api

import to.wim.watchlist.data.model.ActivityStamps
import to.wim.watchlist.data.model.AirTime
import to.wim.watchlist.data.model.AiringState
import to.wim.watchlist.data.model.CalendarEntry
import to.wim.watchlist.data.model.Dates
import to.wim.watchlist.data.model.Episode
import to.wim.watchlist.data.model.EpisodeRef
import to.wim.watchlist.data.model.LibraryItem
import to.wim.watchlist.data.model.ListStatus
import to.wim.watchlist.data.model.MediaDetails
import to.wim.watchlist.data.model.MediaIds
import to.wim.watchlist.data.model.MediaSummary
import to.wim.watchlist.data.model.MediaType
import to.wim.watchlist.data.model.NextEpisode
import to.wim.watchlist.data.model.Playback
import to.wim.watchlist.data.model.Rating
import to.wim.watchlist.data.model.StatusCount
import to.wim.watchlist.data.model.Trailer
import to.wim.watchlist.data.model.TypeStats
import to.wim.watchlist.data.model.UserProfile
import to.wim.watchlist.data.model.UserStats
import to.wim.watchlist.data.model.WatchedSeason
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Maps SIMKL JSON to the app's models. Pure functions, so they are unit tested. */
object Parsers {
    fun ids(obj: JsonObject?): MediaIds? {
        obj ?: return null
        val simkl = obj.long("simkl") ?: obj.long("simkl_id") ?: obj.long("id") ?: return null
        return MediaIds(
            simkl = simkl,
            slug = obj.str("slug"),
            imdb = obj.str("imdb"),
            tmdb = obj.str("tmdb"),
            tvdb = obj.str("tvdb"),
            mal = obj.str("mal"),
            anilist = obj.str("anilist"),
            anidb = obj.str("anidb"),
            kitsu = obj.str("kitsu"),
            trakt = obj.str("traktslug") ?: obj.str("trakt"),
        )
    }

    private fun yearOf(obj: JsonObject): Int? =
        obj.int("year")
            ?: listOf("release_date", "released", "first_aired", "date").firstNotNullOfOrNull { k ->
                obj.str(k)?.take(4)?.toIntOrNull()
            }

    // ---- /sync/all-items ----------------------------------------------------------------------

    fun library(root: JsonElement?): List<LibraryItem> {
        val obj = root.asObject() ?: return emptyList()
        return buildList {
            obj["shows"].objects().mapNotNullTo(this) { libraryItem(it, MediaType.Tv) }
            obj["anime"].objects().mapNotNullTo(this) { libraryItem(it, MediaType.Anime) }
            obj["movies"].objects().mapNotNullTo(this) { libraryItem(it, MediaType.Movie) }
        }.distinctBy { it.key } // Keys must be unique in the lazy lists.
    }

    fun libraryItem(
        entry: JsonObject,
        type: MediaType,
    ): LibraryItem? {
        val media = entry.obj("show") ?: entry.obj("movie") ?: entry.obj("anime") ?: return null
        val ids = ids(media.obj("ids")) ?: return null
        val status = ListStatus.fromApi(entry.str("status")) ?: return null
        val nextInfo = entry.obj("next_to_watch_info")
        val nextRef =
            nextInfo?.let { info -> info.int("episode")?.let { EpisodeRef(info.int("season"), it) } }
                ?: EpisodeRef.parse(entry.str("next_to_watch"))
        val watched = mutableMapOf<Int, MutableSet<Int>>()
        entry["seasons"].objects().forEach { season ->
            val n = season.int("number") ?: return@forEach
            season["episodes"].objects().forEach { ep -> ep.int("number")?.let { watched.getOrPut(n) { mutableSetOf() } += it } }
        }
        entry["episodes"].objects().forEach { ep ->
            ep.int("number")?.let { watched.getOrPut(ep.int("season") ?: 1) { mutableSetOf() } += it }
        }
        return LibraryItem(
            type = type,
            ids = ids,
            title = media.str("title") ?: "#${ids.simkl}",
            year = yearOf(media),
            poster = media.str("poster"),
            status = status,
            userRating = entry.int("user_rating"),
            addedAt = entry.str("added_to_watchlist_at"),
            lastWatchedAt = entry.str("last_watched_at"),
            lastWatched = entry.str("last_watched"),
            nextToWatch = nextRef?.let { NextEpisode(it, nextInfo?.str("title"), nextInfo?.str("date")) },
            watchedEpisodes = entry.int("watched_episodes_count") ?: watched.values.sumOf { it.size },
            totalEpisodes = entry.int("total_episodes_count") ?: 0,
            notAiredEpisodes = entry.int("not_aired_episodes_count") ?: 0,
            animeType = entry.str("anime_type") ?: media.str("anime_type"),
            watched = watched.map { (n, eps) -> WatchedSeason(n, eps.sorted()) }.sortedBy { it.number },
            memo = entry.obj("memo")?.str("text"),
        )
    }

    /** Keys ("Type:simklId") in a /sync/all-items?extended=simkl_ids_only response. */
    fun libraryKeys(root: JsonElement?): Set<String> {
        val obj = root.asObject() ?: return emptySet()
        return buildSet {
            listOf("shows" to MediaType.Tv, "anime" to MediaType.Anime, "movies" to MediaType.Movie).forEach { (key, type) ->
                obj[key].objects().forEach { entry ->
                    val media = entry.obj("show") ?: entry.obj("movie") ?: entry.obj("anime") ?: entry
                    ids(media.obj("ids"))?.let { add("${type.name}:${it.simkl}") }
                }
            }
        }
    }

    /**
     * /ratings/all?user_watchlist=all: release status and SIMKL rating per "Type:simklId" key.
     * Every entry has a "type" (tv, anime or movie); entries without a known one are skipped.
     */
    fun watchlistRatings(root: JsonElement?): Map<String, Pair<String?, Double?>> =
        root.objects().mapNotNull { e ->
            val id = e.long("id") ?: return@mapNotNull null
            val type =
                when (e.str("type")?.lowercase()) {
                    "tv", "show", "shows" -> MediaType.Tv
                    "anime" -> MediaType.Anime
                    "movie", "movies" -> MediaType.Movie
                    else -> return@mapNotNull null
                }
            "${type.name}:$id" to (e.str("release_status") to e.obj("simkl")?.double("rating"))
        }.toMap()

    /** The POST /sync/activities timestamps the sync compares with the previous ones. */
    fun activityStamps(root: JsonObject?): ActivityStamps {
        fun stamp(key: String) = listOf("tv_shows", "anime", "movies").joinToString("|") { root?.obj(it)?.str(key).orEmpty() }
        return ActivityStamps(
            all = root?.str("all"),
            removed = stamp("removed_from_list"),
            rated = stamp("rated_at"),
            playback = stamp("playback"),
            settings = root?.obj("settings")?.str("all"),
        )
    }

    /**
     * GET /sync/playback. Shows come as "show" for TV and anime alike; [typeOf] tells which one a
     * show id is (from the library), defaulting to TV.
     */
    fun playback(
        root: JsonElement?,
        typeOf: (Long) -> MediaType?,
    ): List<Playback> =
        root.objects().mapNotNull { e ->
            val id = e.long("id") ?: return@mapNotNull null
            val movie = e.obj("movie")
            val anime = e.obj("anime")
            val media = movie ?: anime ?: e.obj("show") ?: return@mapNotNull null
            val simklId = ids(media.obj("ids"))?.simkl ?: return@mapNotNull null
            val type =
                when {
                    movie != null -> MediaType.Movie
                    anime != null -> MediaType.Anime
                    else -> typeOf(simklId) ?: MediaType.Tv
                }
            val ep = e.obj("episode")
            Playback(
                id = id,
                type = type,
                simklId = simklId,
                title = media.str("title") ?: "#$simklId",
                progress = (e.double("progress") ?: 0.0).coerceIn(0.0, 100.0),
                pausedAt = e.str("paused_at"),
                // Anime episodes are numbered without seasons in the rest of the app.
                episode = ep?.let { (it.int("episode") ?: it.int("number"))?.let { n -> EpisodeRef(if (type == MediaType.Anime) null else it.int("season"), n) } },
                episodeTitle = ep?.str("title"),
            )
        }

    /** {"id": 179868, "url": "..."} from /search/random. */
    fun randomId(root: JsonElement?): Long? {
        val obj = root.asObject() ?: root.asArray()?.firstOrNull()?.let { it.asObject() ?: it.asArray()?.firstOrNull().asObject() }
        return obj?.long("id") ?: obj?.long("simkl_id") ?: obj?.obj("ids")?.long("simkl")
    }

    /** SIMKL overviews may contain HTML line breaks. */
    private fun plainText(value: String?): String? =
        value?.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")?.replace(Regex("<[^>]+>"), "")?.trim()?.ifEmpty { null }

    // ---- Catalogue ----------------------------------------------------------------------------

    fun summary(
        obj: JsonObject,
        type: MediaType,
    ): MediaSummary? {
        val ids = ids(obj.obj("ids")) ?: ids(obj) ?: return null
        val title = obj.str("title") ?: obj.str("en_title") ?: return null
        val rating =
            obj.obj("ratings")?.let { r -> r.obj("simkl")?.double("rating") ?: r.obj("imdb")?.double("rating") }
                ?: obj.double("rating")
        val subtitle =
            listOfNotNull(
                yearOf(obj)?.toString(),
                obj.str("anime_type")?.uppercase(),
                obj.str("network"),
                obj.int("total_episodes")?.takeIf { it > 0 }?.let { "$it eps" },
            ).joinToString(" · ").ifEmpty { null }
        return MediaSummary(
            type = type,
            simklId = ids.simkl,
            title = title,
            year = yearOf(obj),
            poster = obj.str("poster") ?: obj.str("img"),
            rating = rating,
            subtitle = subtitle,
            overview = plainText(obj.str("overview")),
        )
    }

    /**
     * The first match of GET /search/id: `[{"type": "movie"|"show"|"anime", "title", "poster",
     * "year", "ids": {"simkl", ...}}]`. Without a usable type, [movie] decides Movie or TV.
     */
    fun lookup(
        root: JsonElement?,
        movie: Boolean,
    ): MediaSummary? {
        val obj = (root.asArray()?.firstOrNull() ?: root).asObject() ?: return null
        val type = MediaType.fromApi(obj.str("type")) ?: if (movie) MediaType.Movie else MediaType.Tv
        return summary(obj, type)
    }

    fun summaries(
        root: JsonElement?,
        type: MediaType,
    ): List<MediaSummary> {
        // Lists are either a bare array or wrapped in an object ({"items": [...]}).
        val items = root.asArray() ?: root.asObject()?.let { o -> o.arr("items") ?: o.arr("results") ?: o.arr(type.syncPath) }
        return items.objects().mapNotNull { summary(it, type) }.distinctBy { it.simklId }
    }

    fun details(
        obj: JsonObject,
        type: MediaType,
    ): MediaDetails? {
        val ids = ids(obj.obj("ids")) ?: return null
        val ratings =
            obj.obj("ratings")?.mapNotNull { (source, value) ->
                val r = value.asObject() ?: return@mapNotNull null
                val v = r.double("rating") ?: return@mapNotNull null
                Rating(source.uppercase(), v, r.int("votes"))
            }.orEmpty()
        val airs =
            obj.obj("airs")?.let { a ->
                AirTime(a.str("day"), a.str("time"), a.str("timezone")).takeIf { it.raw != null }
            }
        val recommendations =
            obj["users_recommendations"].objects().mapNotNull { rec ->
                val recType = MediaType.fromApi(rec.str("type")) ?: type
                summary(rec, recType)
            }.distinctBy { "${it.type}:${it.simklId}" }
        val altTitles =
            obj.arr("alt_titles")?.mapNotNull { t ->
                (t as? JsonObject)?.str("name") ?: (t as? JsonPrimitive)?.content
            }.orEmpty()
        val title = obj.str("title") ?: obj.str("en_title") ?: "#${ids.simkl}"
        val status = obj.str("status")
        return MediaDetails(
            type = type,
            ids = ids,
            title = title,
            originalTitle = obj.str("en_title")?.takeIf { it != title },
            year = yearOf(obj),
            poster = obj.str("poster"),
            fanart = obj.str("fanart"),
            overview = plainText(obj.str("overview")),
            genres = obj.strings("genres"),
            status = status,
            airing = AiringState.fromApi(status),
            network = obj.str("network"),
            country = obj.str("country")?.uppercase(),
            certification = obj.str("certification"),
            runtime = obj.int("runtime"),
            firstAired = obj.str("first_aired") ?: obj.str("released") ?: obj.str("release_date"),
            airs = airs,
            totalEpisodes = obj.int("total_episodes"),
            animeType = obj.str("anime_type"),
            director = obj.str("director"),
            budget = obj.long("budget")?.takeIf { it > 0 },
            revenue = obj.long("revenue")?.takeIf { it > 0 },
            ratings = ratings,
            rank = obj.int("rank"),
            trailers = obj["trailers"].objects().mapNotNull { t -> t.str("youtube")?.let { Trailer(t.str("name"), it) } },
            recommendations = recommendations,
            alternativeTitles = altTitles.filter { it != title }.distinct(),
        )
    }

    fun episodes(root: JsonElement?): List<Episode> =
        root.objects().mapNotNull { e ->
            val number = e.int("episode") ?: e.int("number") ?: return@mapNotNull null
            val season = e.int("season")
            val date = e.str("date")
            Episode(
                season = season,
                episode = number,
                title = e.str("title"),
                description = plainText(e.str("description") ?: e.str("overview")),
                image = e.str("img"),
                date = date,
                aired = e.bool("aired") ?: (Dates.parse(date)?.let { it.isBefore(java.time.Instant.now()) } ?: true),
                special = e.str("type") == "special" || season == 0,
            )
        }

    fun calendar(
        root: JsonElement?,
        type: MediaType,
    ): List<CalendarEntry> =
        root.objects().mapNotNull { e ->
            val ids = ids(e.obj("ids")) ?: return@mapNotNull null
            val date = Dates.parse(e.str("date") ?: e.str("release_date")) ?: return@mapNotNull null
            val ep = e.obj("episode")
            CalendarEntry(
                type = type,
                simklId = ids.simkl,
                title = e.str("title") ?: return@mapNotNull null,
                poster = e.str("poster"),
                date = date,
                episode = ep?.int("episode")?.let { EpisodeRef(ep.int("season"), it) },
            )
        }.distinctBy { "${it.simklId}:${it.episode?.label}:${it.date}" }

    // ---- Users --------------------------------------------------------------------------------

    fun user(root: JsonElement?): UserProfile? {
        val obj = root.asObject() ?: return null
        val user = obj.obj("user") ?: return null
        val account = obj.obj("account")
        return UserProfile(
            id = account?.long("id") ?: user.long("id") ?: return null,
            name = user.str("name") ?: "SIMKL user",
            avatar = user.str("avatar"),
            joinedAt = user.str("joined_at"),
            location = user.str("loc") ?: user.str("location"),
            bio = user.str("bio"),
            accountType = account?.str("type"),
            timezone = account?.str("timezone"),
        )
    }

    fun stats(root: JsonElement?): UserStats? {
        val obj = root.asObject() ?: return null
        val types =
            listOf(MediaType.Tv to "tv", MediaType.Anime to "anime", MediaType.Movie to "movies").mapNotNull { (type, key) ->
                val t = obj.obj(key) ?: return@mapNotNull null
                TypeStats(
                    type = type,
                    minutes = t.long("total_mins") ?: 0,
                    watchedEpisodes =
                        ListStatus.entries.mapNotNull { s -> t.obj(s.api)?.int("watched_episodes_count") }
                            .takeIf { it.isNotEmpty() }?.sum(),
                    leftToWatchMinutes =
                        ListStatus.entries.mapNotNull { s -> t.obj(s.api)?.long("left_to_watch_mins") }
                            .takeIf { it.isNotEmpty() }?.sum(),
                    counts =
                        ListStatus.forType(type).mapNotNull { s ->
                            // Stats call the Dropped list "notinteresting".
                            val block = t.obj(s.api) ?: if (s == ListStatus.Dropped) t.obj("notinteresting") else null
                            block?.int("count")?.let { StatusCount(s, it) }
                        },
                )
            }
        return UserStats(
            totalMinutes = obj.long("total_mins") ?: types.sumOf { it.minutes },
            lastWeekMinutes = obj.obj("watched_last_week")?.long("total_mins"),
            types = types,
        )
    }
}
