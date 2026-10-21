package io.github.impca201.trackerforsimkl.data.model

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
