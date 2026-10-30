package to.wim.watchlist.data

import to.wim.watchlist.data.model.MediaType
import to.wim.watchlist.data.model.SearchSubject
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchSubjectTest {
    @Test
    fun allTypesOnNamesEverything() {
        assertEquals(SearchSubject.All, SearchSubject.of(MediaType.entries))
    }

    @Test
    fun aTypeThatIsOffIsLeftOut() {
        assertEquals(SearchSubject.TvAndMovies, SearchSubject.of(listOf(MediaType.Tv, MediaType.Movie)))
        assertEquals(SearchSubject.TvAndAnime, SearchSubject.of(listOf(MediaType.Tv, MediaType.Anime)))
        assertEquals(SearchSubject.AnimeAndMovies, SearchSubject.of(listOf(MediaType.Anime, MediaType.Movie)))
    }

    @Test
    fun oneTypeOnNamesOnlyThatType() {
        assertEquals(SearchSubject.Tv, SearchSubject.of(listOf(MediaType.Tv)))
        assertEquals(SearchSubject.Anime, SearchSubject.of(listOf(MediaType.Anime)))
        assertEquals(SearchSubject.Movies, SearchSubject.of(listOf(MediaType.Movie)))
    }

    @Test
    fun orderDoesNotMatter() {
        assertEquals(SearchSubject.TvAndMovies, SearchSubject.of(listOf(MediaType.Movie, MediaType.Tv)))
    }

    @Test
    fun noTypesFallsBackToEverything() {
        assertEquals(SearchSubject.All, SearchSubject.of(emptyList()))
    }
}
