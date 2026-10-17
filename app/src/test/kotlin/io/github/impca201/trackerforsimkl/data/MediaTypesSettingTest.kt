package io.github.impca201.trackerforsimkl.data

import io.github.impca201.trackerforsimkl.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTypesSettingTest {
    private val all = MediaType.entries.toSet()
    private val nav = NavConfig(NavTab.defaultOrder, NavTab.defaultEnabled)

    @Test
    fun everyTypeIsOnByDefault() {
        assertEquals(listOf(MediaType.Tv, MediaType.Anime, MediaType.Movie), AppSettings().typeList)
        assertEquals(NavTab.defaultOrder, AppSettings().tabs)
    }

    @Test
    fun libraryTabsOfTypesTurnedOffAreLeftOut() {
        val settings = AppSettings(types = setOf(MediaType.Movie))
        assertEquals(listOf(NavTab.Movies, NavTab.Discover, NavTab.Calendar), settings.tabs)
        assertEquals(listOf(MediaType.Movie), settings.typeList)
    }

    @Test
    fun theBottomBarNeverEndsUpEmpty() {
        val onlyTv = NavConfig(NavTab.defaultOrder, setOf(NavTab.Tv))
        assertEquals(listOf(NavTab.Movies), onlyTv.visible(setOf(MediaType.Movie, MediaType.Anime)))
    }

    @Test
    fun theLastTypeThatIsOnStaysOn() {
        assertEquals(setOf(MediaType.Movie), setOf(MediaType.Movie).toggled(MediaType.Movie, on = false))
        assertEquals(setOf(MediaType.Tv, MediaType.Anime), all.toggled(MediaType.Movie, on = false))
        assertEquals(all, setOf(MediaType.Tv, MediaType.Anime).toggled(MediaType.Movie, on = true))
    }

    @Test
    fun movingSkipsTabsThatAreNotListed() {
        // Tv, Movies, Anime, Discover, Calendar with movies off: Anime moves up past the hidden Movies tab.
        val types = setOf(MediaType.Tv, MediaType.Anime)
        val shown = nav.available(types)
        assertEquals(listOf(NavTab.Tv, NavTab.Anime, NavTab.Discover, NavTab.Calendar), shown)
        val moved = nav.moved(NavTab.Anime, to = 0, shown)
        assertEquals(listOf(NavTab.Anime, NavTab.Movies, NavTab.Tv, NavTab.Discover, NavTab.Calendar), moved.order)
        assertEquals(listOf(NavTab.Anime, NavTab.Tv, NavTab.Discover, NavTab.Calendar), moved.available(types))
        // Nothing to move past at either end.
        assertEquals(nav, nav.moved(NavTab.Tv, to = -1, nav.order))
        assertEquals(nav, nav.moved(NavTab.Calendar, to = nav.order.size, nav.order))
    }

    @Test
    fun draggingMovesATabSeveralPlacesAtOnce() {
        // Calendar dragged to the top; the hidden Movies tab keeps its place.
        val types = setOf(MediaType.Tv, MediaType.Anime)
        val shown = nav.available(types)
        val moved = nav.moved(NavTab.Calendar, to = 0, shown)
        assertEquals(listOf(NavTab.Calendar, NavTab.Movies, NavTab.Tv, NavTab.Anime, NavTab.Discover), moved.order)
        assertEquals(listOf(NavTab.Calendar, NavTab.Tv, NavTab.Anime, NavTab.Discover), moved.available(types))
    }
}
