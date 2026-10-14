package io.github.impca201.trackerforsimkl.data

import io.github.impca201.trackerforsimkl.data.api.Parsers
import io.github.impca201.trackerforsimkl.data.model.AirTime
import io.github.impca201.trackerforsimkl.data.model.EpisodeRef
import io.github.impca201.trackerforsimkl.data.model.LibraryItem
import io.github.impca201.trackerforsimkl.data.model.ListStatus
import io.github.impca201.trackerforsimkl.data.model.LocalAir
import io.github.impca201.trackerforsimkl.data.model.MediaIds
import io.github.impca201.trackerforsimkl.data.model.MediaType
import io.github.impca201.trackerforsimkl.data.model.NextEpisode
import io.github.impca201.trackerforsimkl.data.model.Shelves
import io.github.impca201.trackerforsimkl.data.model.ShowStatus
import io.github.impca201.trackerforsimkl.data.model.WatchedSeason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class DetailLogicTest {
    // Wednesday 14 October 2026.
    private val now = Instant.parse("2026-10-14T10:00:00Z")
    private val brussels = ZoneId.of("Europe/Brussels")

    @Test
    fun airTimeIsShownInThePhonesTimeZone() {
        // Next Tuesday is 20 October: 15:30 in New York (summer time) is 21:30 in Brussels.
        val air = AirTime("Tuesday", "03:30 PM", "America/New_York")
        assertEquals(LocalAir(DayOfWeek.TUESDAY, LocalTime.of(21, 30)), air.inZone(brussels, now))
        // After Europe's clocks change (25 October) but before America's (1 November) it is 20:30.
        assertEquals(LocalAir(DayOfWeek.TUESDAY, LocalTime.of(20, 30)), air.inZone(brussels, Instant.parse("2026-10-26T10:00:00Z")))
    }

    @Test
    fun airTimeCanMoveToAnotherDay() {
        // Sunday 11:00 in Tokyo is Saturday 22:00 in New York.
        val air = AirTime("Sunday", "11:00 AM", "Asia/Tokyo")
        assertEquals(LocalAir(DayOfWeek.SATURDAY, LocalTime.of(22, 0)), air.inZone(ZoneId.of("America/New_York"), now))
        // 24-hour times and short day names work too.
        assertEquals(LocalAir(DayOfWeek.MONDAY, LocalTime.of(22, 0)), AirTime("mon", "21:00", "Europe/London").inZone(brussels, now))
    }

    @Test
    fun airTimeWithoutReadableDayOrZone() {
        // A day that is not one weekday: only the time is converted.
        assertEquals(LocalAir(null, LocalTime.of(3, 0)), AirTime("Weekdays", "21:00", "America/New_York").inZone(brussels, now))
        // Unknown time zone or time: no conversion, the screen shows SIMKL's text.
        val unknownZone = AirTime("Tuesday", "21:00", "Nowhere/City")
        assertNull(unknownZone.inZone(brussels, now))
        assertEquals("Tuesday 21:00 (Nowhere/City)", unknownZone.raw)
        assertNull(AirTime("Tuesday", "late", "Europe/Brussels").inZone(brussels, now))
        assertNull(AirTime(null, null, "Europe/Brussels").inZone(brussels, now))
    }

    @Test
    fun parsesAirTime() {
        val json = """{"title":"Mijn Restaurant","ids":{"simkl":1},"status":"airing","airs":{"day":"Tuesday","time":"03:30 PM","timezone":"America/New_York"}}"""
        val d = Parsers.details(Json.parseToJsonElement(json) as JsonObject, MediaType.Tv)!!
        assertEquals(AirTime("Tuesday", "03:30 PM", "America/New_York"), d.airs)
        val none = Parsers.details(Json.parseToJsonElement("""{"title":"X","ids":{"simkl":1},"airs":{}}""") as JsonObject, MediaType.Tv)!!
        assertNull(none.airs)
    }

    @Test
    fun showStatuses() {
        assertEquals(ShowStatus.Airing, ShowStatus.fromApi("airing"))
        assertEquals(ShowStatus.Airing, ShowStatus.fromApi("Ongoing"))
        assertEquals(ShowStatus.Ended, ShowStatus.fromApi("ended"))
        assertEquals(ShowStatus.Cancelled, ShowStatus.fromApi("Canceled"))
        assertEquals(ShowStatus.Returning, ShowStatus.fromApi("returning series"))
        assertEquals(ShowStatus.Tba, ShowStatus.fromApi("tba"))
        assertEquals(ShowStatus.Upcoming, ShowStatus.fromApi("upcoming"))
        assertEquals(ShowStatus.InProduction, ShowStatus.fromApi("post production"))
        assertEquals(ShowStatus.Planned, ShowStatus.fromApi("rumored"))
        assertEquals(ShowStatus.Released, ShowStatus.fromApi("released"))
        assertNull(ShowStatus.fromApi("something new"))
        assertNull(ShowStatus.fromApi(null))
    }

    @Test
    fun episodeListOpensOnTheLatestWatchedSeason() {
        val seasons = listOf(1, 2, 3, 4, 5, 6, 0)
        // Seasons 1-3 skipped: SIMKL's next episode is S01E01, but the user watches season 5.
        val item =
            LibraryItem(
                type = MediaType.Tv,
                ids = MediaIds(1),
                title = "Mijn Restaurant",
                status = ListStatus.Watching,
                nextToWatch = NextEpisode(EpisodeRef(1, 1)),
                watched = listOf(WatchedSeason(0, listOf(1)), WatchedSeason(4, listOf(1, 2)), WatchedSeason(5, listOf(1, 2, 3))),
            )
        assertEquals(5, Shelves.startSeason(item, null, seasons))
        // A paused episode wins.
        assertEquals(6, Shelves.startSeason(item, EpisodeRef(6, 1), seasons))
        // Nothing watched yet: SIMKL's next episode, else the first season.
        val fresh = item.copy(watched = emptyList(), nextToWatch = NextEpisode(EpisodeRef(2, 1)))
        assertEquals(2, Shelves.startSeason(fresh, null, seasons))
        assertEquals(1, Shelves.startSeason(null, null, seasons))
        // A season that isn't in the list (hidden or not loaded) is skipped.
        assertEquals(1, Shelves.startSeason(item, null, listOf(1, 2)))
        assertNull(Shelves.startSeason(item, null, emptyList()))
    }
}
