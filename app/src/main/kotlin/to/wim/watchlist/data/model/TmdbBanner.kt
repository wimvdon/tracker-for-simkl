package to.wim.watchlist.data.model

/**
 * The card on a details page that offers to add a TMDB token, so cast and crew show there.
 * Plain Kotlin so it is unit tested.
 */
object TmdbBanner {
    /**
     * Shown only where it helps: no working token yet, and the title is one TMDB knows (it has a
     * TMDB ID), so adding a token would show its cast right away. "Not now" hides it until the app
     * restarts ([hiddenForNow]); "Don't show again" for good ([hiddenForever]).
     */
    fun show(
        hasToken: Boolean,
        details: MediaDetails?,
        hiddenForNow: Boolean,
        hiddenForever: Boolean,
    ): Boolean = !hasToken && !hiddenForNow && !hiddenForever && details != null && CreditsSource.of(details) != null
}
