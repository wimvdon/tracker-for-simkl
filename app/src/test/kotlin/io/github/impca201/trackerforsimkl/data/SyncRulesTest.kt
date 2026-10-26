package io.github.impca201.trackerforsimkl.data

import io.github.impca201.trackerforsimkl.data.model.AiringState
import io.github.impca201.trackerforsimkl.data.model.Episode
import io.github.impca201.trackerforsimkl.data.model.SyncRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit

class SyncRulesTest {
    private val now = Instant.parse("2026-10-26T12:00:00Z").toEpochMilli()
    private fun minutesAgo(m: Long) = now - TimeUnit.MINUTES.toMillis(m)
    private fun hoursAgo(h: Long) = now - TimeUnit.HOURS.toMillis(h)

    @Test
    fun automaticSyncAtMostEveryFifteenMinutes() {
        assertFalse(SyncRules.autoSyncDue(minutesAgo(1), now))
        assertFalse(SyncRules.autoSyncDue(minutesAgo(14), now))
        assertTrue(SyncRules.autoSyncDue(minutesAgo(15), now))
        // Never synced.
        assertTrue(SyncRules.autoSyncDue(0, now))
    }

    @Test
    fun communityRatingsAtMostOnceADay() {
        assertFalse(SyncRules.communityRatingsDue(hoursAgo(1), now))
        assertFalse(SyncRules.communityRatingsDue(hoursAgo(23), now))
        assertTrue(SyncRules.communityRatingsDue(hoursAgo(24), now))
        assertTrue(SyncRules.communityRatingsDue(0, now))
    }

    @Test
    fun episodeListsAreKept() {
        // Nothing kept (never fetched, or forgotten after a /sync/all-items delta): download.
        assertTrue(SyncRules.episodesDue(null, now, AiringState.Ended))
        // Still airing (or not known): once a day.
        assertFalse(SyncRules.episodesDue(hoursAgo(6), now, AiringState.Airing))
        assertTrue(SyncRules.episodesDue(hoursAgo(25), now, AiringState.Airing))
        assertTrue(SyncRules.episodesDue(hoursAgo(25), now, AiringState.Unknown))
        // Ended: kept until the show changes or is opened.
        assertFalse(SyncRules.episodesDue(hoursAgo(24 * 20), now, AiringState.Ended))
        // Opened by the user: refreshed, unless that was within the hour.
        assertFalse(SyncRules.episodesDue(minutesAgo(30), now, AiringState.Ended, opened = true))
        assertTrue(SyncRules.episodesDue(minutesAgo(61), now, AiringState.Ended, opened = true))
    }

    @Test
    fun keptEpisodesThatAiredSinceAreMarkedAired() {
        fun episode(
            number: Int,
            date: String?,
            aired: Boolean,
        ) = Episode(1, number, null, null, null, date, aired, special = false)

        val kept =
            listOf(
                episode(1, "2026-10-01T00:00:00Z", aired = true),
                // Was upcoming when downloaded, aired yesterday.
                episode(2, "2026-10-25T00:00:00Z", aired = false),
                episode(3, "2026-11-01T00:00:00Z", aired = false),
                episode(4, null, aired = false),
            )
        val result = SyncRules.airedBy(kept, Instant.ofEpochMilli(now))
        assertEquals(listOf(true, true, false, false), result.map { it.aired })
    }
}
