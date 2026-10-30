package to.wim.watchlist.data.model

/**
 * What the empty search screen invites you to find. It names only the types turned on in
 * Settings › Content, so it doesn't promise anime when anime is off. Kept out of the composable
 * so it can be unit tested.
 */
enum class SearchSubject {
    All,
    TvAndAnime,
    TvAndMovies,
    AnimeAndMovies,
    Tv,
    Anime,
    Movies,
    ;

    companion object {
        fun of(types: Collection<MediaType>): SearchSubject {
            val tv = MediaType.Tv in types
            val anime = MediaType.Anime in types
            val movies = MediaType.Movie in types
            return when {
                tv && anime && movies -> All
                tv && anime -> TvAndAnime
                tv && movies -> TvAndMovies
                anime && movies -> AnimeAndMovies
                tv -> Tv
                anime -> Anime
                movies -> Movies
                // Settings keep at least one type on; an empty list falls back to everything.
                else -> All
            }
        }
    }
}
