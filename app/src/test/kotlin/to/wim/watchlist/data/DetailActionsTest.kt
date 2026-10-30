package to.wim.watchlist.data

import to.wim.watchlist.data.model.DetailActions
import to.wim.watchlist.data.model.EpisodeRef
import to.wim.watchlist.data.model.ListStatus
import to.wim.watchlist.data.model.MediaType
import to.wim.watchlist.data.model.Playback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailActionsTest {
    private val moviePlayback = Playback(id = 1, type = MediaType.Movie, simklId = 10, title = "Film", progress = 40.0)
    private val next = EpisodeRef(1, 3)

    private fun showPlayback(episode: EpisodeRef?) = Playback(id = 2, type = MediaType.Tv, simklId = 20, title = "Show", progress = 25.0, episode = episode)

    @Test
    fun movieWatchedButtonShowsWithoutProgress() {
        assertTrue(DetailActions.showMovieWatched(null, null))
        assertTrue(DetailActions.showMovieWatched(ListStatus.PlanToWatch, null))
    }

    @Test
    fun movieWatchedButtonHidesWhenCompletedOrInProgress() {
        assertFalse(DetailActions.showMovieWatched(ListStatus.Completed, null))
        assertFalse(DetailActions.showMovieWatched(null, moviePlayback))
        assertFalse(DetailActions.showMovieWatched(ListStatus.Watching, moviePlayback))
    }

    @Test
    fun nextEpisodeButtonShowsWithoutProgress() {
        assertTrue(DetailActions.showNextEpisodeWatched(next, null))
    }

    @Test
    fun nextEpisodeButtonHidesWhenTheBannerIsOnThatEpisode() {
        assertFalse(DetailActions.showNextEpisodeWatched(next, showPlayback(EpisodeRef(1, 3))))
    }

    @Test
    fun nextEpisodeButtonStaysForAnotherEpisodeOrUnknownEpisode() {
        assertTrue(DetailActions.showNextEpisodeWatched(next, showPlayback(EpisodeRef(1, 2))))
        assertTrue(DetailActions.showNextEpisodeWatched(next, showPlayback(null)))
    }

    @Test
    fun checkInNameIsTheTitleForAMovie() {
        assertEquals("Film", DetailActions.checkInName("Film", null))
    }

    @Test
    fun checkInNameAddsTheEpisodeForAShow() {
        assertEquals("Show S01E03", DetailActions.checkInName("Show", EpisodeRef(1, 3)))
        assertEquals("Anime E12", DetailActions.checkInName("Anime", EpisodeRef(null, 12)))
    }
}
