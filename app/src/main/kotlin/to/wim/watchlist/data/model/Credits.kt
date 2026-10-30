package to.wim.watchlist.data.model

/** An actor in a title's cast, from TMDB. */
data class CastMember(
    /** TMDB person ID. */
    val personId: Long,
    val name: String,
    /** The role, or roles joined with " / " for a show. */
    val character: String?,
    val photo: String?,
    /** Episodes the actor appears in (shows only). */
    val episodes: Int?,
)

/** The crew jobs worth showing on a details page, in the order they are listed. */
enum class CrewJob {
    Director,
    Writer,
    Story,
    Novel,
    Music,
    Camera,
    ;

    companion object {
        /** TMDB's (English) job name to a job the app shows, or null for the many others. */
        fun of(job: String?): CrewJob? =
            when (job) {
                "Director" -> Director
                "Screenplay", "Writer", "Teleplay" -> Writer
                "Story" -> Story
                "Novel", "Characters" -> Novel
                "Original Music Composer", "Music" -> Music
                "Director of Photography" -> Camera
                else -> null
            }
    }
}

/** A person in a title's crew, with every shown job they had on it. */
data class CrewMember(
    val personId: Long,
    val name: String,
    val jobs: List<CrewJob>,
    val photo: String?,
)

data class Credits(
    val cast: List<CastMember>,
    val crew: List<CrewMember>,
) {
    val isEmpty: Boolean get() = cast.isEmpty() && crew.isEmpty()
}

/** Which TMDB title holds a title's credits: a movie or a TV show, by TMDB ID. */
data class CreditsSource(
    val tmdbId: String,
    val movie: Boolean,
) {
    companion object {
        /**
         * SIMKL's TMDB ID points at a TMDB movie for movies and anime movies, and at a TMDB TV
         * show otherwise. Null when SIMKL has no TMDB ID (common for anime).
         */
        fun of(details: MediaDetails): CreditsSource? {
            val id = details.ids.tmdb?.takeIf { it.all(Char::isDigit) } ?: return null
            val movie = details.type == MediaType.Movie || (details.type == MediaType.Anime && details.animeType.equals("movie", ignoreCase = true))
            return CreditsSource(id, movie)
        }
    }
}

/** A person's page, from TMDB: who they are and what they worked on. */
data class Person(
    val id: Long,
    val name: String,
    val photo: String?,
    val biography: String?,
    val birthday: java.time.LocalDate?,
    val deathday: java.time.LocalDate?,
    val placeOfBirth: String?,
    /** Everything they played in or worked on (shown jobs only), newest first. */
    val credits: List<PersonCredit>,
) {
    /** Their best-known titles: most TMDB votes first. */
    fun knownFor(count: Int = 10): List<PersonCredit> = credits.sortedByDescending { it.votes }.take(count)

    /** Age today, or at death; null without a birthday. */
    fun age(today: java.time.LocalDate): Int? = birthday?.let { java.time.Period.between(it, deathday ?: today).years }
}

/** One title in a person's filmography, with their roles and jobs on it. */
data class PersonCredit(
    val tmdbId: String,
    val movie: Boolean,
    val title: String,
    /** "2008-01-20"; null when unknown or not released. */
    val date: String?,
    val poster: String?,
    val characters: List<String>,
    val jobs: List<CrewJob>,
    val votes: Int,
    val episodes: Int?,
) {
    val year: Int? get() = date?.take(4)?.toIntOrNull()

    /**
     * The library title this credit is, matched by TMDB ID and kind, which saves a SIMKL lookup.
     * TMDB movie and TV IDs overlap, so a TMDB movie only matches a movie and a TMDB show a TV
     * show or anime series (an anime movie is looked up on SIMKL instead).
     */
    fun match(items: List<LibraryItem>): LibraryItem? =
        items.firstOrNull { item -> item.ids.tmdb == tmdbId && (item.type == MediaType.Movie) == movie }

    /** The title's page on themoviedb.org, when SIMKL doesn't have it. */
    val tmdbUrl: String get() = "https://www.themoviedb.org/${if (movie) "movie" else "tv"}/$tmdbId"
}

/** A person found by name (TMDB search), with a few of their best-known titles. */
data class PersonResult(
    val id: Long,
    val name: String,
    val photo: String?,
    val knownFor: List<String>,
)
