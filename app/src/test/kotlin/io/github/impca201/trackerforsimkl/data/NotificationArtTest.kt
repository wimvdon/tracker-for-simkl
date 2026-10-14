package io.github.impca201.trackerforsimkl.data

import io.github.impca201.trackerforsimkl.data.model.Images
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationArtTest {
    @Test
    fun usesLibraryPosterAndCachedFanart() {
        val art = Images.notification("12/abc", "99/zzz", "34/def")
        assertEquals("https://wsrv.nl/?url=https://simkl.in/posters/12/abc_m.webp", art.poster)
        assertEquals("https://wsrv.nl/?url=https://simkl.in/fanart/34/def_mobile.webp", art.backdrop)
    }

    @Test
    fun fallsBackToCalendarPoster() {
        assertEquals("https://wsrv.nl/?url=https://simkl.in/posters/99/zzz_m.webp", Images.notification(null, "99/zzz", null).poster)
        assertEquals("https://wsrv.nl/?url=https://simkl.in/posters/99/zzz_m.webp", Images.notification(" ", "99/zzz", null).poster)
    }

    @Test
    fun noPicturesWhenNothingKnown() {
        val art = Images.notification(null, null, null)
        assertNull(art.poster)
        assertNull(art.backdrop)
    }
}
