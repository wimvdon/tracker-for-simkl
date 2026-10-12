package io.github.impca201.simklr.data

import io.github.impca201.simklr.data.api.Parsers
import io.github.impca201.simklr.data.model.AiringState
import io.github.impca201.simklr.data.model.EpisodeRef
import io.github.impca201.simklr.data.model.ListStatus
import io.github.impca201.simklr.data.model.MediaType
import io.github.impca201.simklr.data.model.Shelf
import io.github.impca201.simklr.data.model.Shelves
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ParsersTest {
    private val allItems =
        """
        {
          "shows": [
            {
              "added_to_watchlist_at": "2024-01-01T10:00:00Z",
              "last_watched_at": "2024-05-01T20:00:00Z",
              "user_rating": 8,
              "status": "watching",
              "last_watched": "S01E02",
              "next_to_watch": "S01E03",
              "watched_episodes_count": 2,
              "total_episodes_count": 10,
              "not_aired_episodes_count": 0,
              "show": { "title": "Show A", "poster": "12/abc", "year": 2023, "ids": { "simkl": 101, "slug": "show-a", "imdb": "tt1", "tmdb": 55 } },
              "seasons": [ { "number": 1, "episodes": [ { "number": 1, "watched_at": "x" }, { "number": 2 } ] } ]
            },
            {
              "status": "watching",
              "watched_episodes_count": 8,
              "total_episodes_count": 10,
              "not_aired_episodes_count": 2,
              "next_to_watch_info": { "title": "Finale", "season": 2, "episode": 9, "date": "2099-01-01T00:00:00Z" },
              "show": { "title": "Show B", "ids": { "simkl": "102" } }
            },
            { "status": "bogus", "show": { "title": "Ignored", "ids": { "simkl": 1 } } }
          ],
          "anime": [
            {
              "status": "completed",
              "anime_type": "tv",
              "show": { "title": "Anime C", "ids": { "simkl": 201, "mal": "999" } },
              "episodes": [ { "number": 1 }, { "number": 2 } ]
            }
          ],
          "movies": [
            { "status": "plantowatch", "movie": { "title": "Movie D", "year": 2020, "ids": { "simkl": 301 } } }
          ]
        }
        """.trimIndent()

    @Test
    fun parsesLibrary() {
        val items = Parsers.library(Json.parseToJsonElement(allItems))
        assertEquals(4, items.size)
        val a = items.first { it.ids.simkl == 101L }
        assertEquals(MediaType.Tv, a.type)
        assertEquals("Show A", a.title)
        assertEquals("55", a.ids.tmdb)
        assertEquals(EpisodeRef(1, 3), a.nextToWatch?.ref)
        assertTrue(a.isWatched(1, 2))
        assertFalse(a.isWatched(1, 3))
        assertEquals(8, a.userRating)
        assertEquals(8, a.airedUnwatched)

        val b = items.first { it.ids.simkl == 102L }
        assertEquals(EpisodeRef(2, 9), b.nextToWatch?.ref)
        assertEquals("Finale", b.nextToWatch?.title)

        val c = items.first { it.type == MediaType.Anime }
        assertEquals(ListStatus.Completed, c.status)
        assertTrue(c.isWatched(null, 2))
        assertEquals(2, c.watchedEpisodes)

        val d = items.first { it.type == MediaType.Movie }
        assertEquals(ListStatus.PlanToWatch, d.status)
        assertEquals(2020, d.year)
    }

    @Test
    fun shelves() {
        val items = Parsers.library(Json.parseToJsonElement(allItems))
        val now = Instant.parse("2024-06-01T00:00:00Z")
        assertEquals(Shelf.UpNext, Shelves.shelfOf(items.first { it.ids.simkl == 101L }, now))
        // Next episode airs in the future: waiting, and still airing.
        val b = items.first { it.ids.simkl == 102L }
        assertEquals(Shelf.Waiting, Shelves.shelfOf(b, now))
        assertEquals(AiringState.Airing, Shelves.airingOf(b, null, null))
        assertEquals(Shelf.Completed, Shelves.shelfOf(items.first { it.type == MediaType.Anime }, now))
    }

    @Test
    fun applyEpisodesMovesNext() {
        val a = Parsers.library(Json.parseToJsonElement(allItems)).first { it.ids.simkl == 101L }
        val updated = LibraryRepository.applyEpisodes(a, listOf(EpisodeRef(1, 3)), true, null)
        assertTrue(updated.isWatched(1, 3))
        assertEquals(3, updated.watchedEpisodes)
        assertEquals(EpisodeRef(1, 4), updated.nextToWatch?.ref)
        val undone = LibraryRepository.applyEpisodes(updated, listOf(EpisodeRef(1, 3)), false, null)
        assertFalse(undone.isWatched(1, 3))
        assertEquals(2, undone.watchedEpisodes)
    }

    @Test
    fun parsesDetailsAndSummaries() {
        val details =
            Parsers.details(
                Json.parseToJsonElement(
                    """
                    {"title":"Show A","year":2023,"ids":{"simkl":101,"slug":"show-a"},"status":"ended","genres":["drama"],
                     "ratings":{"simkl":{"rating":8.3,"votes":1200},"imdb":{"rating":8.1,"votes":50000}},
                     "airs":{"day":"Monday","time":"21:00","timezone":"America/New_York"},
                     "trailers":[{"name":"Trailer","youtube":"abc"}],
                     "users_recommendations":[{"title":"Rec","ids":{"simkl":5}}],
                     "alt_titles":[{"name":"Show Alpha"},"Show A"]}
                    """.trimIndent(),
                ) as kotlinx.serialization.json.JsonObject,
                MediaType.Tv,
            )
        assertNotNull(details)
        details!!
        assertEquals(AiringState.Ended, details.airing)
        assertEquals(2, details.ratings.size)
        assertEquals("abc", details.trailers.single().youtubeId)
        assertEquals(5L, details.recommendations.single().simklId)
        assertEquals(listOf("Show Alpha"), details.alternativeTitles)

        val search =
            Parsers.summaries(
                Json.parseToJsonElement("""[{"title":"X","year":2001,"poster":"p","ids":{"simkl_id":7,"slug":"x"}},{"title":"No id"}]"""),
                MediaType.Movie,
            )
        assertEquals(1, search.size)
        assertEquals(7L, search.single().simklId)
    }

    @Test
    fun episodeRefs() {
        assertEquals(EpisodeRef(1, 5), EpisodeRef.parse("S01E05"))
        assertEquals(EpisodeRef(null, 12), EpisodeRef.parse("E12"))
        assertNull(EpisodeRef.parse("nope"))
        assertEquals("S02E10", EpisodeRef(2, 10).label)
    }

    @Test
    fun airingStates() {
        assertEquals(AiringState.Ended, AiringState.fromApi("ended"))
        assertEquals(AiringState.Ended, AiringState.fromApi("Canceled"))
        assertEquals(AiringState.Airing, AiringState.fromApi("airing"))
        assertEquals(AiringState.Airing, AiringState.fromApi("tba"))
        assertEquals(AiringState.Unknown, AiringState.fromApi(null))
    }
}
