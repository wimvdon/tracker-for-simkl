package to.wim.watchlist.data

import to.wim.watchlist.data.model.CalendarEntry
import to.wim.watchlist.data.model.EpisodeRef
import to.wim.watchlist.data.model.LibraryItem
import to.wim.watchlist.data.model.ListStatus
import to.wim.watchlist.data.model.MediaIds
import to.wim.watchlist.data.model.MediaType
import to.wim.watchlist.data.model.NewEpisodes
import to.wim.watchlist.data.model.NewReleases
import to.wim.watchlist.data.model.NotifyChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Movie release notifications ([NewReleases]) and the channel each type uses. */
class NewReleasesTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")

    private fun movie(
        id: Long,
        status: ListStatus = ListStatus.PlanToWatch,
    ) = LibraryItem(type = MediaType.Movie, ids = MediaIds(id), title = "Movie $id", status = status)

    private fun release(
        id: Long,
        hoursAgo: Long,
    ) = CalendarEntry(MediaType.Movie, id, "Movie $id", null, now.minus(Duration.ofHours(hoursAgo)), null)

    @Test
    fun announcesReleasedMoviesOnThePlanToWatchListOnce() {
        val calendar =
            listOf(
                release(1, hoursAgo = 3),
                // Not out yet.
                release(2, hoursAgo = -48),
                // Already watched.
                release(3, hoursAgo = 3),
                // Not in the library.
                release(4, hoursAgo = 3),
                // Out too long ago.
                release(5, hoursAgo = 24 * 8),
            )
        val library = listOf(movie(1), movie(2), movie(3, ListStatus.Completed), movie(5))
        val plan = NewReleases.plan(calendar, emptySet(), library, emptySet(), now)
        assertEquals(listOf(1L), plan.alerts.map { it.item.ids.simkl })
        assertEquals(setOf("Movie:1", "Movie:3", "Movie:4"), plan.notified)
        // The upcoming planned movie is remembered, so a late check still announces it.
        assertEquals(setOf("Movie:1", "Movie:2"), plan.remembered.map { it.substringBefore("@") }.toSet())

        val next = NewReleases.plan(calendar, plan.remembered, library, plan.notified, now.plusSeconds(3600))
        assertTrue(next.alerts.isEmpty())
    }

    @Test
    fun aRememberedReleaseIsAnnouncedWhenTheCheckRunsLate() {
        val library = listOf(movie(2))
        val first = NewReleases.plan(listOf(release(2, hoursAgo = -2)), emptySet(), library, emptySet(), now)
        assertTrue(first.alerts.isEmpty())
        assertEquals(now.plus(Duration.ofHours(2)), NewReleases.nextReleaseDate(first.remembered, now))
        // Three days later the file no longer lists it.
        val late = NewReleases.plan(emptyList(), first.remembered, library, first.notified, now.plus(Duration.ofDays(3)))
        assertEquals(listOf(2L), late.alerts.map { it.item.ids.simkl })
    }

    @Test
    fun atMostFiveMoviesPerCheckTheRestWait() {
        val library = (1L..7L).map { movie(it) }
        val calendar = (1L..7L).map { release(it, hoursAgo = it) }
        val plan = NewReleases.plan(calendar, emptySet(), library, emptySet(), now)
        assertEquals(NewReleases.MAX_MOVIES, plan.alerts.size)
        val next = NewReleases.plan(calendar, plan.remembered, library, plan.notified, now)
        assertEquals(listOf(6L, 7L), next.alerts.map { it.item.ids.simkl })
    }

    @Test
    fun episodesAndMoviesShareTheStoredSetsWithoutMixingUp() {
        val episode = CalendarEntry(MediaType.Tv, 9, "Show", null, now.plus(Duration.ofHours(1)), EpisodeRef(1, 1))
        val shared = setOf(NewEpisodes.encode(episode), NewReleases.encode(release(1, hoursAgo = -1)))
        assertEquals(listOf(1L), shared.mapNotNull(NewReleases::decode).map { it.simklId })
        assertEquals(listOf(9L), shared.mapNotNull(NewEpisodes::decode).map { it.simklId })
        assertNull(NewReleases.decode("Movie:x@1"))
    }

    @Test
    fun releasedMoviesAreMarkedWhenNotificationsAreTurnedOn() {
        val calendar = listOf(release(1, hoursAgo = 3), release(2, hoursAgo = -3))
        assertEquals(setOf("Movie:1"), NewReleases.released(calendar, now))
    }

    @Test
    fun eachTypeHasItsOwnChannelAndTvKeepsTheOldOne() {
        assertEquals("new_episodes", NotifyChannel.of(MediaType.Tv).id)
        assertEquals(NotifyChannel.Anime, NotifyChannel.of(MediaType.Anime))
        assertEquals(NotifyChannel.Movies, NotifyChannel.of(MediaType.Movie))
        assertEquals(3, NotifyChannel.entries.map { it.id }.toSet().size)
    }
}
