package io.github.impca201.trackerforsimkl.data.model

/** Which watch buttons the detail screen shows. Kept out of the composable so it can be unit tested. */
object DetailActions {
    /**
     * A movie's own "Watched" button. Hidden for a completed movie, and while the progress banner
     * is showing, because the banner has a Watched button of its own.
     */
    fun showMovieWatched(
        status: ListStatus?,
        playback: Playback?,
    ): Boolean = status != ListStatus.Completed && playback == null

    /**
     * The "Watched <next episode>" button. Hidden while the progress banner is on that very episode;
     * a banner for another episode leaves it alone, because it marks a different episode.
     */
    fun showNextEpisodeWatched(
        next: EpisodeRef,
        playback: Playback?,
    ): Boolean = playback?.episode != next
}
