package to.wim.watchlist.data.model

import java.time.Instant

/**
 * Decides which movies on the user's "Plan to watch" list the release notifications announce,
 * from SIMKL's movie release calendar. Plain Kotlin so it is unit tested; `NewEpisodesWorker`
 * downloads, stores and posts. Works like [NewEpisodes]: the same seven-day window, upcoming
 * releases remembered between checks, each movie announced once.
 */
object NewReleases {
    /** At most this many movies are announced per check; the others wait for the next check. */
    const val MAX_MOVIES = 5

    data class Alert(
        val item: LibraryItem,
        val release: CalendarEntry,
    )

    data class Plan(
        val alerts: List<Alert>,
        /** Store as the movies already announced (or not worth announcing). */
        val notified: Set<String>,
        /** Store as the remembered releases of planned movies, for the next check. */
        val remembered: Set<String>,
    )

    /** "Movie:123"; never clashes with an episode key ("Tv:123:S01E05"). */
    fun keyOf(entry: CalendarEntry) = "${MediaType.Movie.name}:${entry.simklId}"

    /** A remembered release, e.g. "Movie:123@1792000000" (release date in epoch seconds). */
    fun encode(entry: CalendarEntry) = "${keyOf(entry)}@${entry.date.epochSecond}"

    /** Null for anything that isn't a remembered release (remembered episodes share the store). */
    fun decode(value: String): CalendarEntry? {
        val (key, seconds) = value.split("@").takeIf { it.size == 2 } ?: return null
        val parts = key.split(":").takeIf { it.size == 2 && it[0] == MediaType.Movie.name } ?: return null
        return CalendarEntry(
            type = MediaType.Movie,
            simklId = parts[1].toLongOrNull() ?: return null,
            title = "",
            poster = null,
            date = Instant.ofEpochSecond(seconds.toLongOrNull() ?: return null),
            episode = null,
        )
    }

    /** Releases in the window, to mark as notified when notifications are turned on. */
    fun released(
        calendar: List<CalendarEntry>,
        now: Instant,
    ): Set<String> = calendar.filter { it.type == MediaType.Movie && it.inWindow(now) && !it.date.isAfter(now) }.map(::keyOf).toSet()

    fun plan(
        calendar: List<CalendarEntry>,
        remembered: Set<String>,
        library: List<LibraryItem>,
        notified: Set<String>,
        now: Instant,
    ): Plan {
        val planned = library.filter { it.type == MediaType.Movie && it.status == ListStatus.PlanToWatch }.associateBy { it.ids.simkl }
        // The calendar file wins over a remembered copy (its release date may have moved).
        val entries =
            (calendar.filter { it.type == MediaType.Movie } + remembered.mapNotNull(::decode))
                .filter { it.inWindow(now) }
                .distinctBy(::keyOf)
        val released = entries.filter { !it.date.isAfter(now) }
        val alerts =
            released.mapNotNull { entry -> planned[entry.simklId]?.let { Alert(it, entry) } }
                .filter { keyOf(it.release) !in notified }
                .sortedByDescending { it.release.date }
        val waiting = alerts.drop(MAX_MOVIES).map { keyOf(it.release) }.toSet()
        return Plan(
            alerts = alerts.take(MAX_MOVIES),
            notified = released.map(::keyOf).toSet() - waiting,
            remembered = entries.filter { it.simklId in planned }.map(::encode).toSet(),
        )
    }

    /** The next remembered release, so the check can run right after it. */
    fun nextReleaseDate(
        remembered: Set<String>,
        now: Instant,
    ): Instant? = remembered.mapNotNull(::decode).map { it.date }.filter { it.isAfter(now) }.minOrNull()

    private fun CalendarEntry.inWindow(now: Instant) = date.isAfter(now.minus(NewEpisodes.WINDOW))
}

/**
 * One Android notification channel per media type, so each can be silenced or changed in
 * Android's settings. TV keeps the channel id of earlier versions, so settings people already
 * made for it stay.
 */
enum class NotifyChannel(
    val id: String,
    val type: MediaType,
) {
    Tv("new_episodes", MediaType.Tv),
    Anime("new_episodes_anime", MediaType.Anime),
    Movies("movie_releases", MediaType.Movie),
    ;

    companion object {
        fun of(type: MediaType): NotifyChannel = entries.first { it.type == type }
    }
}
