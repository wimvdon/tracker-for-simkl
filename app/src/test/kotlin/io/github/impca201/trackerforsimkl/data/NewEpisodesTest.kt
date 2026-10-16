package io.github.impca201.trackerforsimkl.data

import io.github.impca201.trackerforsimkl.data.model.CalendarEntry
import io.github.impca201.trackerforsimkl.data.model.EpisodeRef
import io.github.impca201.trackerforsimkl.data.model.LibraryItem
import io.github.impca201.trackerforsimkl.data.model.ListStatus
import io.github.impca201.trackerforsimkl.data.model.MediaIds
import io.github.impca201.trackerforsimkl.data.model.MediaType
import io.github.impca201.trackerforsimkl.data.model.NewEpisodes
import io.github.impca201.trackerforsimkl.data.model.WatchedSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class NewEpisodesTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")

    private fun show(
        id: Long,
        status: ListStatus = ListStatus.Watching,
        watched: List<WatchedSeason> = emptyList(),
    ) = LibraryItem(type = MediaType.Tv, ids = MediaIds(id), title = "Show $id", status = status, watched = watched)

    private fun episode(
        id: Long,
        episode: Int,
        hoursAgo: Long,
    ) = CalendarEntry(MediaType.Tv, id, "Show $id", null, now.minus(Duration.ofHours(hoursAgo)), EpisodeRef(1, episode))

    @Test
    fun announcesAiredUnwatchedEpisodesOfWatchedShowsOnce() {
        val calendar =
            listOf(
                episode(1, 5, hoursAgo = 3),
                // Not aired yet.
                episode(1, 6, hoursAgo = -24),
                // Already watched.
                episode(2, 1, hoursAgo = 3),
                // Not a show the user is watching.
                episode(3, 1, hoursAgo = 3),
            )
        val library = listOf(show(1), show(2, watched = listOf(WatchedSeason(1, listOf(1)))), show(3, ListStatus.PlanToWatch))
        val plan = NewEpisodes.plan(calendar, emptySet(), library, emptySet(), now)
        assertEquals(listOf("Tv:1"), plan.alerts.map { it.item.key })
        assertEquals(setOf("Tv:1:S01E05", "Tv:2:S01E01", "Tv:3:S01E01"), plan.notified)

        val next = NewEpisodes.plan(calendar, plan.remembered, library, plan.notified, now.plusSeconds(3600))
        assertTrue(next.alerts.isEmpty())
    }

    @Test
    fun passesOverEpisodesOfTypesTurnedOffWithoutAnnouncingThemLater() {
        val calendar = listOf(episode(1, 5, hoursAgo = 3))
        val library = listOf(show(1))
        val off = NewEpisodes.plan(calendar, emptySet(), library, emptySet(), now, types = setOf(MediaType.Anime, MediaType.Movie))
        assertTrue(off.alerts.isEmpty())
        // Marked as notified, so turning TV shows back on doesn't bring a backlog of notifications.
        assertEquals(setOf("Tv:1:S01E05"), off.notified)
        val backOn = NewEpisodes.plan(calendar, off.remembered, library, off.notified, now.plusSeconds(3600))
        assertTrue(backOn.alerts.isEmpty())
    }

    @Test
    fun remembersUpcomingEpisodesWhenTheCalendarFileNoLongerListsThem() {
        val library = listOf(show(1), show(3, ListStatus.PlanToWatch))
        val first = NewEpisodes.plan(listOf(episode(1, 6, hoursAgo = -24), episode(3, 2, hoursAgo = -24)), emptySet(), library, emptySet(), now)
        assertTrue(first.alerts.isEmpty())
        // Only episodes of shows being watched are remembered.
        assertEquals(setOf("Tv:1:S01E06@${now.plus(Duration.ofDays(1)).epochSecond}"), first.remembered)

        // Three days later (the phone was off), the file has moved on.
        val later = now.plus(Duration.ofDays(3))
        val plan = NewEpisodes.plan(emptyList(), first.remembered, library, first.notified, later)
        assertEquals(listOf("Tv:1"), plan.alerts.map { it.item.key })
        assertEquals("S01E06", plan.alerts.single().latest.episode?.label)
        assertEquals(setOf("Tv:1:S01E06"), plan.notified)

        // Out of the window: forgotten.
        val gone = NewEpisodes.plan(emptyList(), plan.remembered, library, plan.notified, now.plus(Duration.ofDays(9)))
        assertTrue(gone.alerts.isEmpty())
        assertTrue(gone.remembered.isEmpty())
        assertTrue(gone.notified.isEmpty())
    }

    @Test
    fun oneAlertPerShowCountingAllNewEpisodes() {
        val calendar = (1..8).map { episode(1, it, hoursAgo = 2) }
        val plan = NewEpisodes.plan(calendar, emptySet(), listOf(show(1)), emptySet(), now)
        val alert = plan.alerts.single()
        assertEquals(8, alert.episodes.size)
        assertEquals("S01E08", alert.latest.episode?.label)

        // A new episode later: the show is announced again, with the ones still unwatched.
        val more = calendar + episode(1, 9, hoursAgo = 0)
        val again = NewEpisodes.plan(more, plan.remembered, listOf(show(1)), plan.notified, now)
        assertEquals(9, again.alerts.single().episodes.size)
    }

    @Test
    fun showsBeyondTheLimitWaitForTheNextCheck() {
        val calendar = (1L..7L).map { episode(it, 1, hoursAgo = it) }
        val library = (1L..7L).map { show(it) }
        val plan = NewEpisodes.plan(calendar, emptySet(), library, emptySet(), now)
        // Newest first.
        assertEquals((1L..5L).map { "Tv:$it" }, plan.alerts.map { it.item.key })
        assertEquals((1L..5L).map { "Tv:$it:S01E01" }.toSet(), plan.notified)

        val next = NewEpisodes.plan(calendar, plan.remembered, library, plan.notified, now)
        assertEquals(listOf("Tv:6", "Tv:7"), next.alerts.map { it.item.key })
    }

    @Test
    fun calendarFileWinsOverRememberedAirDate() {
        // Remembered for tomorrow, but SIMKL moved it to next week.
        val remembered = setOf("Tv:1:S01E06@${now.plus(Duration.ofDays(1)).epochSecond}")
        val moved = episode(1, 6, hoursAgo = -24 * 7)
        val plan = NewEpisodes.plan(listOf(moved), remembered, listOf(show(1)), emptySet(), now.plus(Duration.ofDays(2)))
        assertTrue(plan.alerts.isEmpty())
        assertEquals(setOf("Tv:1:S01E06@${moved.date.epochSecond}"), plan.remembered)
    }

    @Test
    fun animeEpisodesIgnoreTheSeason() {
        val anime = LibraryItem(MediaType.Anime, MediaIds(9), "Anime", status = ListStatus.Watching, watched = listOf(WatchedSeason(1, listOf(12))))
        val entry = CalendarEntry(MediaType.Anime, 9, "Anime", null, now.minusSeconds(60), EpisodeRef(2, 12))
        assertTrue(NewEpisodes.plan(listOf(entry), emptySet(), listOf(anime), emptySet(), now).alerts.isEmpty())
    }

    @Test
    fun rememberedEpisodesRoundTrip() {
        val entry = episode(42, 3, hoursAgo = -5)
        val decoded = NewEpisodes.decode(NewEpisodes.encode(entry))!!
        assertEquals(NewEpisodes.keyOf(entry), NewEpisodes.keyOf(decoded))
        assertEquals(entry.date, decoded.date)
        assertNull(NewEpisodes.decode("garbage"))
        assertNull(NewEpisodes.decode("Tv:1:S01E01@soon"))
        assertNull(NewEpisodes.decode("Book:1:S01E01@1"))
    }

    @Test
    fun turningOnMarksOnlyRecentlyAiredEpisodes() {
        val calendar = listOf(episode(1, 1, hoursAgo = 3), episode(1, 2, hoursAgo = -3), episode(1, 0, hoursAgo = 24 * 8))
        assertEquals(setOf("Tv:1:S01E01"), NewEpisodes.aired(calendar, now))
    }
}
