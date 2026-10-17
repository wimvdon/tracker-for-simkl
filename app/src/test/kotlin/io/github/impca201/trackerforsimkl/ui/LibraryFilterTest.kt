package io.github.impca201.trackerforsimkl.ui

import io.github.impca201.trackerforsimkl.data.model.LibraryItem
import io.github.impca201.trackerforsimkl.data.model.ListStatus
import io.github.impca201.trackerforsimkl.data.model.MediaIds
import io.github.impca201.trackerforsimkl.data.model.MediaType
import io.github.impca201.trackerforsimkl.data.model.AiringState
import io.github.impca201.trackerforsimkl.data.model.MovieShelf
import io.github.impca201.trackerforsimkl.data.model.Ratings
import io.github.impca201.trackerforsimkl.data.model.Shelf
import io.github.impca201.trackerforsimkl.ui.library.AiringFilter
import io.github.impca201.trackerforsimkl.ui.library.LibraryFilter
import io.github.impca201.trackerforsimkl.ui.library.TabSpec
import io.github.impca201.trackerforsimkl.ui.library.RatingFilter
import io.github.impca201.trackerforsimkl.ui.library.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFilterTest {
    private fun anime(
        id: Long,
        title: String,
        animeType: String?,
        year: Int? = null,
        rating: Int? = null,
    ) = LibraryItem(MediaType.Anime, MediaIds(id), title, year = year, status = ListStatus.Watching, userRating = rating, animeType = animeType)

    private val items = listOf(anime(1, "Bleach", "tv", 2004, 7), anime(2, "akira", "movie", 1988), anime(3, "Cowboy Bebop", "TV", 1998, 9))

    @Test
    fun animeTypeFilter() {
        assertFalse(LibraryFilter().active)
        assertEquals(items, LibraryFilter().apply(items))
        val tv = LibraryFilter(animeType = "tv")
        assertTrue(tv.active)
        // SIMKL's type is matched without case.
        assertEquals(listOf(1L, 3L), tv.apply(items).map { it.ids.simkl })
    }

    @Test
    fun ratingFilter() {
        val rated = LibraryFilter(rating = RatingFilter.Rated)
        assertTrue(rated.active)
        assertEquals(listOf(1L, 3L), rated.apply(items).map { it.ids.simkl })
        assertEquals(listOf(2L), LibraryFilter(rating = RatingFilter.Unrated).apply(items).map { it.ids.simkl })
        // Combined with the anime type.
        assertEquals(listOf(3L), LibraryFilter(animeType = "tv", rating = RatingFilter.Rated).apply(items.drop(1)).map { it.ids.simkl })
        assertTrue(LibraryFilter(animeType = "movie", rating = RatingFilter.Rated).apply(items).isEmpty())
    }

    @Test
    fun filterSurvivesSaving() {
        assertEquals(LibraryFilter(animeType = "ova"), LibraryFilter.decode(LibraryFilter(animeType = "ova").encode()))
        assertEquals(LibraryFilter(), LibraryFilter.decode(LibraryFilter().encode()))
        val both = LibraryFilter(animeType = "tv", rating = RatingFilter.Unrated)
        assertEquals(both, LibraryFilter.decode(both.encode()))
        // A value saved by the previous version (anime type only) still reads.
        assertEquals(LibraryFilter(animeType = "ova"), LibraryFilter.decode("ova"))
    }

    @Test
    fun sortOrders() {
        assertEquals(listOf(2L, 1L, 3L), SortOrder.Title.sort(items).map { it.ids.simkl })
        assertEquals(listOf(3L, 1L, 2L), SortOrder.Rating.sort(items).map { it.ids.simkl })
        assertEquals(listOf(1L, 3L, 2L), SortOrder.Release.sort(items).map { it.ids.simkl })
    }

    @Test
    fun episodesLeftSortsClosestToCaughtUpFirst() {
        val a = anime(1, "A", "tv").copy(totalEpisodes = 12, watchedEpisodes = 2)
        val b = anime(2, "B", "tv").copy(totalEpisodes = 12, watchedEpisodes = 11)
        val c = anime(3, "C", "tv").copy(totalEpisodes = 24, watchedEpisodes = 20, notAiredEpisodes = 2)
        assertEquals(listOf(2L, 3L, 1L), SortOrder.EpisodesLeft.sort(listOf(a, b, c)).map { it.ids.simkl })
    }

    @Test
    fun airingFilterUsesTheShowsAiringState() {
        val airing = mapOf(1L to AiringState.Airing, 2L to AiringState.Ended)
        val of = { item: LibraryItem -> airing[item.ids.simkl] ?: AiringState.Unknown }
        assertEquals(listOf(1L), LibraryFilter(airing = AiringFilter.Airing).apply(items, of).map { it.ids.simkl })
        assertEquals(listOf(2L), LibraryFilter(airing = AiringFilter.Ended).apply(items, of).map { it.ids.simkl })
        // Unknown state only shows up under "All".
        assertEquals(items, LibraryFilter().apply(items, of))
        val saved = LibraryFilter(animeType = "tv", rating = RatingFilter.Rated, airing = AiringFilter.Ended)
        assertEquals(saved, LibraryFilter.decode(saved.encode()))
    }

    @Test
    fun unwatchedShelvesOfferNoRatingFilterOrSort() {
        listOf(TabSpec.of(Shelf.UpNext, anime = false), TabSpec.of(Shelf.PlanToWatch, anime = false), TabSpec.of(MovieShelf.PlanToWatch)).forEach { spec ->
            assertFalse(spec.rating)
            assertFalse(SortOrder.Rating in spec.sorts)
            // A rating filter chosen elsewhere doesn't leak in.
            assertEquals(RatingFilter.Any, spec.allowed(LibraryFilter(rating = RatingFilter.Rated)).rating)
            assertEquals(spec.defaultSort, spec.allowed(SortOrder.Rating))
        }
        // Nothing on Want to watch was watched yet.
        assertFalse(SortOrder.Recent in TabSpec.of(Shelf.PlanToWatch, anime = false).sorts)
        assertFalse(SortOrder.Recent in TabSpec.of(MovieShelf.PlanToWatch).sorts)
        // Movies to watch have nothing to filter by, so no filter button.
        assertFalse(TabSpec.of(MovieShelf.PlanToWatch).hasFilters)
    }

    @Test
    fun watchedShelvesOfferRatings() {
        listOf(Shelf.Watched, Shelf.OnHold, Shelf.Dropped).forEach { shelf ->
            val spec = TabSpec.of(shelf, anime = true)
            assertTrue(spec.rating && spec.airing && spec.animeTypes)
            assertTrue(SortOrder.Rating in spec.sorts)
        }
        assertTrue(TabSpec.of(MovieShelf.Completed).rating)
        assertFalse(TabSpec.of(MovieShelf.Completed).airing)
        // Only Anime has the type filter.
        assertFalse(TabSpec.of(Shelf.Watched, anime = false).animeTypes)
    }

    @Test
    fun defaultSorts() {
        assertEquals(SortOrder.Recent, TabSpec.of(Shelf.UpNext, anime = false).defaultSort)
        assertTrue(SortOrder.EpisodesLeft in TabSpec.of(Shelf.UpNext, anime = false).sorts)
        assertEquals(SortOrder.Added, TabSpec.of(Shelf.PlanToWatch, anime = false).defaultSort)
        assertEquals(SortOrder.Recent, TabSpec.of(Shelf.Watched, anime = false).defaultSort)
        assertEquals(SortOrder.Added, TabSpec.of(MovieShelf.PlanToWatch).defaultSort)
        assertEquals(SortOrder.Recent, TabSpec.of(MovieShelf.Completed).defaultSort)
    }

    @Test
    fun myRatingsListsRatedTitlesHighestFirst() {
        val movie = LibraryItem(MediaType.Movie, MediaIds(9), "Heat", status = ListStatus.Completed, userRating = 9)
        val all = items + movie
        assertEquals(listOf(3L, 1L), Ratings.rated(all, MediaType.Anime).map { it.ids.simkl })
        assertEquals(listOf(9L), Ratings.rated(all, MediaType.Movie).map { it.ids.simkl })
        assertTrue(Ratings.rated(all, MediaType.Tv).isEmpty())
    }
}
