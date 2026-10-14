package io.github.impca201.trackerforsimkl.data.model

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** The three SIMKL catalogues, with the names the API uses for each of them. */
@Serializable
enum class MediaType(
    /** Path segment of the catalogue endpoints: /tv/{id}, /anime/{id}, /movies/{id}. */
    val catalogPath: String,
    /** Path segment of /sync/all-items/{type}. */
    val syncPath: String,
    /** Path segment of /search/{type}. */
    val searchPath: String,
    /** Key of the request body arrays in /sync/... calls (anime goes with the shows). */
    val bodyKey: String,
    /** simkl.com URL segment. */
    val webPath: String,
) {
    Tv("tv", "shows", "tv", "shows", "tv"),
    Anime("anime", "anime", "anime", "shows", "anime"),
    Movie("movies", "movies", "movie", "movies", "movies"),
    ;

    val isEpisodic get() = this != Movie

    companion object {
        fun fromApi(value: String?): MediaType? =
            when (value?.lowercase()) {
                "tv", "show", "shows", "tv_shows", "series" -> Tv
                "anime" -> Anime
                "movie", "movies", "film" -> Movie
                else -> null
            }
    }
}

/** SIMKL list statuses. Movies only use plan to watch, completed and dropped. */
@Serializable
enum class ListStatus(
    val api: String,
) {
    Watching("watching"),
    PlanToWatch("plantowatch"),
    OnHold("hold"),
    Completed("completed"),
    Dropped("dropped"),
    ;

    companion object {
        fun fromApi(value: String?): ListStatus? = entries.firstOrNull { it.api == value?.lowercase() }

        fun forType(type: MediaType): List<ListStatus> =
            if (type == MediaType.Movie) listOf(PlanToWatch, Completed, Dropped) else entries
    }
}

@Serializable
data class MediaIds(
    val simkl: Long,
    val slug: String? = null,
    val imdb: String? = null,
    val tmdb: String? = null,
    val tvdb: String? = null,
    val mal: String? = null,
    val anilist: String? = null,
    val anidb: String? = null,
    val kitsu: String? = null,
    val trakt: String? = null,
)

/** Whether a show will get more episodes. */
@Serializable
enum class AiringState {
    /** Still running: new episodes are airing or the show is coming back. */
    Airing,

    /** Ended or cancelled: no new episodes will come. */
    Ended,

    /** Not known yet (details not loaded). */
    Unknown,
    ;

    companion object {
        fun fromApi(status: String?): AiringState {
            val s = status?.lowercase() ?: return Unknown
            return when {
                // /tv and /anime "status": ended | tba | airing. /ratings "release_status": ended,
                // cancelled | returning series, ongoing, upcoming, planned, rumored, in/post production.
                s.startsWith("end") || "cancel" in s -> Ended
                "air" in s || "return" in s || "tba" in s || "upcoming" in s || "production" in s ||
                    "ongoing" in s || "planned" in s || "rumored" in s -> Airing
                else -> Unknown
            }
        }
    }
}

/** A show's status as SIMKL names it, for showing it in the user's language. */
enum class ShowStatus {
    Airing,
    Ended,
    Cancelled,
    Returning,
    Tba,
    Upcoming,
    InProduction,
    Planned,
    Released,
    ;

    companion object {
        /** Null for a status this app does not know; the screen then shows SIMKL's own text. */
        fun fromApi(status: String?): ShowStatus? {
            val s = status?.trim()?.lowercase() ?: return null
            return when {
                s.startsWith("end") -> Ended
                "cancel" in s -> Cancelled
                "return" in s -> Returning
                "air" in s || "ongoing" in s -> Airing
                s == "tba" -> Tba
                "upcoming" in s -> Upcoming
                "production" in s -> InProduction
                "planned" in s || "rumored" in s -> Planned
                "released" in s -> Released
                else -> null
            }
        }
    }
}

/** When a show airs, as SIMKL gives it ("airs": day, time and the broadcaster's time zone). */
data class AirTime(
    val day: String?,
    val time: String?,
    val timezone: String?,
) {
    /** SIMKL's text as is ("Tuesday 03:30 PM (America/New_York)"), when it can't be converted. */
    val raw: String?
        get() = listOfNotNull(day, time, timezone?.let { "($it)" }).joinToString(" ").ifEmpty { null }

    /**
     * The next broadcast in [zone] (the phone's time zone): the day shifts too when the
     * conversion crosses midnight. Null when the time or time zone can't be read; day is null
     * when SIMKL gives no (readable) day.
     */
    fun inZone(
        zone: ZoneId,
        now: Instant = Instant.now(),
    ): LocalAir? {
        val localTime = parseTime(time) ?: return null
        val source = timezone?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: return null
        val weekday = parseDay(day)
        val today = now.atZone(source).toLocalDate()
        val date = weekday?.let { today.with(TemporalAdjusters.nextOrSame(it)) } ?: today
        val there = ZonedDateTime.of(date, localTime, source).withZoneSameInstant(zone)
        return LocalAir(if (weekday != null) there.dayOfWeek else null, there.toLocalTime())
    }

    companion object {
        private val timeFormats =
            listOf("H:mm", "h:mm a", "hh:mm a", "h:mma", "H:mm:ss", "h a")
                .map { DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH) }

        fun parseTime(value: String?): LocalTime? {
            val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return timeFormats.firstNotNullOfOrNull { f -> runCatching { LocalTime.parse(v, f) }.getOrNull() }
        }

        /** "Tuesday", "tue" or "Tues"; null for anything else ("Monday-Friday", "Weekdays"). */
        fun parseDay(value: String?): DayOfWeek? {
            val v = value?.trim()?.lowercase()?.takeIf { it.length >= 3 && it.all { c -> c.isLetter() } } ?: return null
            return DayOfWeek.entries.firstOrNull { it.name.lowercase().startsWith(v) }
        }
    }
}

/** A broadcast day and time in the phone's time zone. */
data class LocalAir(
    val day: DayOfWeek?,
    val time: LocalTime,
)

@Serializable
data class EpisodeRef(
    val season: Int?,
    val episode: Int,
) {
    val label: String get() = if (season != null) "S%02dE%02d".format(java.util.Locale.ROOT, season, episode) else "E%02d".format(java.util.Locale.ROOT, episode)

    companion object {
        private val pattern = Regex("""(?:S(\d+))?\s*E(\d+)""", RegexOption.IGNORE_CASE)

        /** Parses SIMKL's "S01E05" / "E05" episode labels. */
        fun parse(value: String?): EpisodeRef? {
            val m = pattern.find(value ?: return null) ?: return null
            return EpisodeRef(m.groupValues[1].toIntOrNull(), m.groupValues[2].toIntOrNull() ?: return null)
        }
    }
}

@Serializable
data class NextEpisode(
    val ref: EpisodeRef,
    val title: String? = null,
    /** ISO date or date-time when the episode airs. */
    val date: String? = null,
)

@Serializable
data class WatchedSeason(
    val number: Int,
    val episodes: List<Int>,
)

/** One title in the user's SIMKL lists (from /sync/all-items). */
@Serializable
data class LibraryItem(
    val type: MediaType,
    val ids: MediaIds,
    val title: String,
    val year: Int? = null,
    val poster: String? = null,
    val status: ListStatus,
    val userRating: Int? = null,
    val addedAt: String? = null,
    val lastWatchedAt: String? = null,
    val lastWatched: String? = null,
    val nextToWatch: NextEpisode? = null,
    val watchedEpisodes: Int = 0,
    val totalEpisodes: Int = 0,
    val notAiredEpisodes: Int = 0,
    val animeType: String? = null,
    val watched: List<WatchedSeason> = emptyList(),
    /** The user's memo (/sync/all-items with memos=yes). */
    val memo: String? = null,
) {
    val key: String get() = "${type.name}:${ids.simkl}"

    /** Episodes that have aired but are not watched yet. */
    val airedUnwatched: Int
        get() = (totalEpisodes - notAiredEpisodes - watchedEpisodes).coerceAtLeast(0)

    fun isWatched(
        season: Int?,
        episode: Int,
    ): Boolean = watched.any { it.number == (season ?: 1) && episode in it.episodes }
}

/**
 * What the library needs but /sync/all-items does not include: release status and community
 * rating (from /ratings/{type}?user_watchlist=all), and fanart once the details were opened.
 */
@Serializable
data class ShowInfo(
    val airing: AiringState,
    val fetchedAt: Long,
    val rating: Double? = null,
    val fanart: String? = null,
    /** /ratings "release_status", e.g. "released" or "upcoming" for movies. */
    val releaseStatus: String? = null,
)

/**
 * A paused, partly watched episode or movie (GET /sync/playback). Marking it watched also
 * removes the saved progress.
 */
@Serializable
data class Playback(
    val id: Long,
    val type: MediaType,
    val simklId: Long,
    val title: String,
    /** 0 to 100. */
    val progress: Double,
    val pausedAt: String? = null,
    val episode: EpisodeRef? = null,
    val episodeTitle: String? = null,
)

/** A title anywhere in the catalogue: search results, trending lists, recommendations. */
@Serializable
data class MediaSummary(
    val type: MediaType,
    val simklId: Long,
    val title: String,
    val year: Int? = null,
    val poster: String? = null,
    val rating: Double? = null,
    val subtitle: String? = null,
    val overview: String? = null,
)

data class Rating(
    val source: String,
    val value: Double,
    val votes: Int?,
)

data class Trailer(
    val name: String?,
    val youtubeId: String,
)

data class MediaDetails(
    val type: MediaType,
    val ids: MediaIds,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val poster: String?,
    val fanart: String?,
    val overview: String?,
    val genres: List<String>,
    val status: String?,
    val airing: AiringState,
    val network: String?,
    val country: String?,
    val certification: String?,
    val runtime: Int?,
    val firstAired: String?,
    val airs: AirTime?,
    val totalEpisodes: Int?,
    val animeType: String?,
    val director: String?,
    val budget: Long?,
    val revenue: Long?,
    val ratings: List<Rating>,
    val rank: Int?,
    val trailers: List<Trailer>,
    val recommendations: List<MediaSummary>,
    val alternativeTitles: List<String>,
)

data class Episode(
    val season: Int?,
    val episode: Int,
    val title: String?,
    val description: String?,
    val image: String?,
    val date: String?,
    val aired: Boolean,
    val special: Boolean,
)

data class CalendarEntry(
    val type: MediaType,
    val simklId: Long,
    val title: String,
    val poster: String?,
    val date: Instant,
    val episode: EpisodeRef?,
)

data class UserProfile(
    val id: Long,
    val name: String,
    val avatar: String?,
    val joinedAt: String?,
    val location: String?,
    val bio: String?,
    val accountType: String?,
    val timezone: String?,
)

data class StatusCount(
    val status: ListStatus,
    val count: Int,
)

data class TypeStats(
    val type: MediaType,
    val minutes: Long,
    val watchedEpisodes: Int?,
    val leftToWatchMinutes: Long?,
    val counts: List<StatusCount>,
)

data class UserStats(
    val totalMinutes: Long,
    val lastWeekMinutes: Long?,
    val types: List<TypeStats>,
)

/**
 * Image URLs as documented by SIMKL: {domain}{prefix}{image}{suffix}{extension}, loaded through the
 * wsrv.nl proxy SIMKL asks apps to use. Coil caches them on disk (the URLs never change).
 */
object Images {
    private const val CDN = "https://wsrv.nl/?url=https://simkl.in"

    private fun absolute(path: String): String? =
        when {
            path.isBlank() -> null
            path.startsWith("//") -> "https:$path"
            path.startsWith("http") -> path
            else -> null
        }

    /** `_ca` (190x279) for lists and grids, `_m` (340 wide) for the details header. */
    fun poster(
        path: String?,
        large: Boolean = false,
    ): String? {
        path ?: return null
        absolute(path)?.let { return it }
        return "$CDN/posters/${path}_${if (large) "m" else "ca"}.webp"
    }

    /** `_mobile`: 960x540. */
    fun fanart(path: String?): String? {
        path ?: return null
        absolute(path)?.let { return it }
        return "$CDN/fanart/${path}_mobile.webp"
    }

    /** `_w`: 600x338. */
    fun episode(path: String?): String? {
        path ?: return null
        absolute(path)?.let { return it }
        return "$CDN/episodes/${path}_w.webp"
    }

    fun avatar(path: String?): String? = path?.let { absolute(it) ?: "$CDN/avatars/${it}_100.jpg" }
}

object Dates {
    /** Parses SIMKL dates ("2024-05-01T20:00:00Z", "2024-05-01T20:00:00-04:00" or "2024-05-01"). */
    fun parse(value: String?): Instant? {
        val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            OffsetDateTime.parse(v).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                Instant.parse(v)
            } catch (_: DateTimeParseException) {
                try {
                    // A date-time without an offset ("2024-05-01T20:00:00"): SIMKL times are UTC.
                    LocalDateTime.parse(v.replace(' ', 'T')).toInstant(ZoneOffset.UTC)
                } catch (_: DateTimeParseException) {
                    try {
                        LocalDate.parse(v.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant()
                    } catch (_: DateTimeParseException) {
                        null
                    }
                }
            }
        }
    }
}
