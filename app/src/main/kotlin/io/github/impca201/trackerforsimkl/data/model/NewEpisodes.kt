package io.github.impca201.trackerforsimkl.data.model

import java.time.Duration
import java.time.Instant

/**
 * Decides which newly aired episodes the new-episode notifications announce. Plain Kotlin so it
 * is unit tested; `NewEpisodesWorker` only downloads, stores and posts.
 *
 * Upcoming episodes of watched shows are remembered between checks, so an episode is still
 * announced when the check was delayed (phone off, battery saver) until it no longer appears in
 * SIMKL's calendar file.
 */
object NewEpisodes {
    /** How far back an air date may be and still be announced. */
    val WINDOW: Duration = Duration.ofDays(7)

    /** At most this many shows are announced per check; the others wait for the next check. */
    const val MAX_SHOWS = 5

    /** One notification: a show with its aired, unwatched episodes in the window, oldest first. */
    data class Alert(
        val item: LibraryItem,
        val episodes: List<CalendarEntry>,
    ) {
        val latest: CalendarEntry get() = episodes.last()
    }

    data class Plan(
        val alerts: List<Alert>,
        /** Store as the episodes already announced (or not worth announcing). */
        val notified: Set<String>,
        /** Store as the remembered episodes of watched shows, for the next check. */
        val remembered: Set<String>,
    )

    fun keyOf(entry: CalendarEntry) = "${entry.type.name}:${entry.simklId}:${entry.episode?.label}"

    /** A remembered episode, e.g. "Tv:123:S01E05@1792000000" (air date in epoch seconds). */
    fun encode(entry: CalendarEntry) = "${keyOf(entry)}@${entry.date.epochSecond}"

    fun decode(value: String): CalendarEntry? {
        val (key, seconds) = value.split("@").takeIf { it.size == 2 } ?: return null
        val parts = key.split(":").takeIf { it.size == 3 } ?: return null
        return CalendarEntry(
            type = MediaType.entries.firstOrNull { it.name == parts[0] } ?: return null,
            simklId = parts[1].toLongOrNull() ?: return null,
            title = "",
            poster = null,
            date = Instant.ofEpochSecond(seconds.toLongOrNull() ?: return null),
            episode = EpisodeRef.parse(parts[2]) ?: return null,
        )
    }

    /** Episodes that aired in the window, to mark as notified when notifications are turned on. */
    fun aired(
        calendar: List<CalendarEntry>,
        now: Instant,
    ): Set<String> = calendar.filter { it.episode != null && it.aired(now) }.map(::keyOf).toSet()

    fun plan(
        calendar: List<CalendarEntry>,
        remembered: Set<String>,
        library: List<LibraryItem>,
        notified: Set<String>,
        now: Instant,
    ): Plan {
        val watching = library.filter { it.type.isEpisodic && it.status == ListStatus.Watching }.associateBy { it.key }
        // The calendar file wins over a remembered copy (its air date may have moved).
        val entries =
            (calendar + remembered.mapNotNull(::decode))
                .filter { it.episode != null && it.date.isAfter(now.minus(WINDOW)) }
                .distinctBy(::keyOf)
        val aired = entries.filter { !it.date.isAfter(now) }
        val unwatched =
            aired.mapNotNull { entry ->
                val item = watching["${entry.type.name}:${entry.simklId}"] ?: return@mapNotNull null
                val ref = entry.episode!!
                (item to entry).takeUnless { item.isWatched(if (entry.type == MediaType.Anime) null else ref.season, ref.episode) }
            }
        val alerts =
            unwatched.groupBy { it.first.key }
                .values
                .filter { pairs -> pairs.any { keyOf(it.second) !in notified } }
                .map { pairs -> Alert(pairs.first().first, pairs.map { it.second }.sortedWith(compareBy({ it.date }, { it.episode?.season }, { it.episode?.episode }))) }
                .sortedByDescending { it.latest.date }
        val announced = alerts.take(MAX_SHOWS)
        val waiting = alerts.drop(MAX_SHOWS).flatMap { it.episodes }.map(::keyOf).toSet()
        return Plan(
            alerts = announced,
            notified = aired.map(::keyOf).toSet() - waiting,
            remembered = entries.filter { "${it.type.name}:${it.simklId}" in watching }.map(::encode).toSet(),
        )
    }

    private fun CalendarEntry.aired(now: Instant) = date.isAfter(now.minus(WINDOW)) && !date.isAfter(now)
}
