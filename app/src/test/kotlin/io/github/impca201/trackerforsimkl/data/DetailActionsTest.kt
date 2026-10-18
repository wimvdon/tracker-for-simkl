package io.github.impca201.trackerforsimkl.data

import io.github.impca201.trackerforsimkl.data.model.DetailActions
import io.github.impca201.trackerforsimkl.data.model.EpisodeRef
import io.github.impca201.trackerforsimkl.data.model.ListStatus
import io.github.impca201.trackerforsimkl.data.model.MediaType
import io.github.impca201.trackerforsimkl.data.model.Playback
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
}
