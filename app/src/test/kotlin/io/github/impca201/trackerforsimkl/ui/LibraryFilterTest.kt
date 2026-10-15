package io.github.impca201.trackerforsimkl.ui

import io.github.impca201.trackerforsimkl.data.model.LibraryItem
import io.github.impca201.trackerforsimkl.data.model.ListStatus
import io.github.impca201.trackerforsimkl.data.model.MediaIds
import io.github.impca201.trackerforsimkl.data.model.MediaType
import io.github.impca201.trackerforsimkl.ui.library.LibraryFilter
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
    fun filterSurvivesSaving() {
        assertEquals(LibraryFilter(animeType = "ova"), LibraryFilter.decode(LibraryFilter(animeType = "ova").encode()))
        assertEquals(LibraryFilter(), LibraryFilter.decode(LibraryFilter().encode()))
    }

    @Test
    fun sortOrders() {
        assertEquals(listOf(2L, 1L, 3L), SortOrder.Title.sort(items).map { it.ids.simkl })
        assertEquals(listOf(3L, 1L, 2L), SortOrder.Rating.sort(items).map { it.ids.simkl })
        assertEquals(listOf(1L, 3L, 2L), SortOrder.Release.sort(items).map { it.ids.simkl })
    }
}
