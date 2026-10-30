package to.wim.watchlist.data

import to.wim.watchlist.data.api.Parsers
import to.wim.watchlist.data.model.AiringState
import to.wim.watchlist.data.model.EpisodeRef
import to.wim.watchlist.data.model.ListStatus
import to.wim.watchlist.data.model.MediaType
import to.wim.watchlist.data.model.Shelf
import to.wim.watchlist.data.model.Shelves
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
        assertEquals(Shelf.Watched, Shelves.shelfOf(b, now))
        assertEquals(AiringState.Airing, Shelves.airingOf(b, null, null))
        assertEquals(Shelf.Watched, Shelves.shelfOf(items.first { it.type == MediaType.Anime }, now))
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
    fun summaryKeepsTheEpisodeCountAsANumber() {
        // The screen words the count in the user's language, so it is not English text in the subtitle.
        val s =
            Parsers.summaries(
                Json.parseToJsonElement("""[{"title":"X","year":2001,"network":"HBO","total_episodes":12,"ids":{"simkl":7}}]"""),
                MediaType.Tv,
            ).single()
        assertEquals(12, s.episodes)
        assertEquals("2001 · HBO", s.subtitle)
        assertNull(Parsers.summaries(Json.parseToJsonElement("""[{"title":"Y","total_episodes":0,"ids":{"simkl":8}}]"""), MediaType.Tv).single().episodes)
    }

    @Test
    fun overviewsLoseTagsAndEntities() {
        assertEquals("Tom & Jerry \"go\" 'home' now…\nNext line", Parsers.plainText("<p>Tom &amp; Jerry &quot;go&quot; &#39;home&#39; now&hellip;</p><br/>Next <b>line</b>"))
        assertEquals("A & B", Parsers.plainText("A &#x26; B"))
        // Unknown entities and plain ampersands stay as they are; empty text is null.
        assertEquals("R&D &foo; x", Parsers.plainText("R&D &foo; x"))
        assertNull(Parsers.plainText("<br>  "))
    }

    @Test
    fun profileWithoutANameIsEmptyNotEnglish() {
        val user = Parsers.user(Json.parseToJsonElement("""{"user":{"avatar":"a"},"account":{"id":42,"type":"free"}}"""))
        assertEquals(42L, user?.id)
        assertEquals("", user?.name)
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

    // The following inputs are the example responses of SIMKL's API documentation.

    @Test
    fun animeDetailsFromDocs() {
        val json =
            """
            {"title":"Hunter x Hunter","year":2011,"type":"anime","ids":{"simkl":40398,"slug":"hunter-x-hunter","mal":"11061","anilist":"11061"},
             "en_title":null,"rank":7,"poster":"83/83975f751784587","fanart":"83/839789583130112","first_aired":"2011-10-02T02:00:00Z",
             "airs":{"day":"Sunday","time":"11:00 AM","timezone":"Asia/Tokyo"},"runtime":25,"certification":null,
             "overview":"A remake.<br><br>Gon is a youth.","genres":["Action"],"country":"JP","total_episodes":148,"status":"ended",
             "network":"NTV","anime_type":"tv","ratings":{"simkl":{"rating":8.9,"votes":248},"mal":{"rating":9.1,"votes":324100,"rank":7}},
             "trailers":[{"name":null,"youtube":"t0xuqi2qvxY","size":720}],
             "users_recommendations":[{"title":"Tate no Yuusha no Nariagari","en_title":"The Rising of the Shield Hero","year":2018,
               "poster":"74/74755663f6cfe2818","anime_type":"tv","users_percent":"7%","users_percent":5,"ids":{"simkl":695799}}]}
            """.trimIndent()
        val d = Parsers.details(Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject, MediaType.Anime)!!
        assertEquals(AiringState.Ended, d.airing)
        assertEquals("A remake.\n\nGon is a youth.", d.overview)
        assertEquals(MediaType.Anime, d.recommendations.single().type)
        assertEquals("11061", d.ids.mal)
        assertNull(d.trailers.single().name)
    }

    @Test
    fun watchlistRatingsFromDocs() {
        val json =
            """
            [{"id":268470,"type":"movie","release_status":"released","release_year":2014,"rank":10628,"simkl":{"rating":7.1,"votes":151,"droprate":"1%"}},
             {"id":2331788,"type":"anime","release_status":"ongoing","simkl":{"rating":7,"votes":19}},
             {"id":5,"type":"tv","release_status":"returning series"},
             {"id":6,"type":"tv","release_status":"ended"},
             {"id":7,"release_status":"ended"}]
            """.trimIndent()
        // /ratings/all: one list for every type, keyed like the library items.
        val map = Parsers.watchlistRatings(Json.parseToJsonElement(json))
        assertEquals(AiringState.Airing, AiringState.fromApi(map["Anime:2331788"]?.first))
        assertEquals(7.0, map["Anime:2331788"]?.second)
        assertEquals(AiringState.Airing, AiringState.fromApi(map["Tv:5"]?.first))
        assertEquals(AiringState.Ended, AiringState.fromApi(map["Tv:6"]?.first))
        assertEquals("released", map["Movie:268470"]?.first)
        // Without a type it can't be matched to a list item.
        assertEquals(4, map.size)
    }

    @Test
    fun activityStampsFromDocs() {
        val json =
            """
            {"all":"2026-10-24T15:46:17Z","settings":{"all":"2026-01-02T00:00:00Z"},
             "tv_shows":{"all":"x","rated_at":"r1","playback":"p1","removed_from_list":"d1"},
             "anime":{"rated_at":"r2","playback":"p2"},
             "movies":{"playback":"p3"}}
            """.trimIndent()
        val stamps = Parsers.activityStamps(Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject)
        assertEquals("2026-10-24T15:46:17Z", stamps.all)
        assertEquals("2026-01-02T00:00:00Z", stamps.settings)
        assertEquals("p1|p2|p3", stamps.playback)
        assertEquals("r1|r2|", stamps.rated)
        assertEquals("d1||", stamps.removed)
        // A movie paused elsewhere moves the joined playback stamp.
        val moved = Parsers.activityStamps(Json.parseToJsonElement(json.replace("p3", "p4")) as kotlinx.serialization.json.JsonObject)
        assertTrue(moved.playback != stamps.playback)
        assertNull(Parsers.activityStamps(null).settings)
    }

    @Test
    fun smallResponsesFromDocs() {
        assertEquals(179868L, Parsers.randomId(Json.parseToJsonElement("""{"id":179868,"url":"https://simkl.com/movies/179868/terminator-genisys"}""")))
        val keys =
            Parsers.libraryKeys(
                Json.parseToJsonElement("""{"shows":[{"show":{"ids":{"simkl":583436,"slug":"emerald-city"}}}],"movies":[{"movie":{"ids":{"simkl":55328}}}]}"""),
            )
        assertEquals(setOf("Tv:583436", "Movie:55328"), keys)
        assertTrue(Parsers.library(Json.parseToJsonElement("null")).isEmpty())
        val stats =
            Parsers.stats(
                Json.parseToJsonElement(
                    """
                    {"total_mins":908554,"movies":{"total_mins":171969,"plantowatch":{"mins":18938,"count":173},"notinteresting":{"mins":12969,"count":123},
                     "completed":{"mins":171852,"count":1558}},
                     "tv":{"total_mins":242560,"watching":{"watched_episodes_count":2612,"count":41,"left_to_watch_mins":411040},
                     "completed":{"watched_episodes_count":2111,"count":55}},
                     "watched_last_week":{"total_mins":130}}
                    """.trimIndent(),
                ),
            )!!
        val movies = stats.types.first { it.type == MediaType.Movie }
        assertEquals(123, movies.counts.first { it.status == ListStatus.Dropped }.count)
        val tv = stats.types.first { it.type == MediaType.Tv }
        assertEquals(4723, tv.watchedEpisodes)
        assertEquals(411040L, tv.leftToWatchMinutes)
        assertEquals(130L, stats.lastWeekMinutes)
    }

    @Test
    fun continueFromLatestEpisode() {
        // Watched S05E01-S07E02 (all of season 7), skipped seasons 1 to 4: SIMKL's next is S01E01.
        val item =
            to.wim.watchlist.data.model.LibraryItem(
                type = MediaType.Tv,
                ids = to.wim.watchlist.data.model.MediaIds(1),
                title = "Show",
                status = ListStatus.Watching,
                nextToWatch = to.wim.watchlist.data.model.NextEpisode(EpisodeRef(1, 1)),
                watchedEpisodes = 4,
                totalEpisodes = 20,
                watched =
                    listOf(
                        to.wim.watchlist.data.model.WatchedSeason(5, listOf(1)),
                        to.wim.watchlist.data.model.WatchedSeason(6, listOf(1)),
                        to.wim.watchlist.data.model.WatchedSeason(7, listOf(1, 2)),
                    ),
            )
        assertTrue(Shelves.hasGap(item))
        assertEquals(EpisodeRef(7, 2), Shelves.latestWatched(item))
        fun ep(
            s: Int,
            e: Int,
            aired: Boolean = true,
        ) = to.wim.watchlist.data.model.Episode(s, e, null, null, null, null, aired, false)
        val caughtUp = Shelves.latestNextFrom(item, listOf(ep(1, 1), ep(7, 1), ep(7, 2), ep(8, 1, aired = false)))
        assertNull(caughtUp.next)
        // Off: SIMKL's own next episode keeps it in Up next. On: caught up, so Watched.
        assertEquals(Shelf.UpNext, Shelves.shelfOf(item, fromLatest = false, latest = caughtUp))
        assertEquals(Shelf.Watched, Shelves.shelfOf(item, fromLatest = true, latest = caughtUp))
        val newEpisode = Shelves.latestNextFrom(item, listOf(ep(1, 1), ep(7, 2), ep(8, 1)))
        assertEquals(EpisodeRef(8, 1), newEpisode.next?.ref)
        assertEquals(Shelf.UpNext, Shelves.shelfOf(item, fromLatest = true, latest = newEpisode))
        assertEquals(EpisodeRef(8, 1), Shelves.nextEpisode(item, true, newEpisode)?.ref)
        // A stale lookup (different watched count) is ignored until recomputed.
        assertEquals(EpisodeRef(1, 1), Shelves.nextEpisode(item, true, newEpisode.copy(watchedCount = 3))?.ref)
    }

    @Test
    fun playbackFromDocs() {
        val json =
            """
            [{"id":123,"progress":45.5,"paused_at":"2024-01-15T10:30:00.000Z","type":"episode",
              "episode":{"season":1,"episode":5,"title":"Episode 5","tvdb_season":1,"tvdb_number":5},
              "show":{"title":"Breaking Bad","year":2008,"ids":{"simkl":12345,"slug":"breaking-bad"}}},
             {"id":124,"progress":75,"paused_at":"2024-01-15T11:15:00.000Z","type":"movie",
              "movie":{"title":"Inception","year":2010,"ids":{"simkl":67890}}}]
            """.trimIndent()
        val list = Parsers.playback(Json.parseToJsonElement(json)) { id -> if (id == 12345L) MediaType.Anime else null }
        assertEquals(2, list.size)
        val episode = list.first { it.id == 123L }
        assertEquals(MediaType.Anime, episode.type)
        // Anime episodes are matched without a season number.
        assertEquals(EpisodeRef(null, 5), episode.episode)
        assertEquals(45.5, episode.progress, 0.0)
        val movie = list.first { it.id == 124L }
        assertEquals(MediaType.Movie, movie.type)
        assertNull(movie.episode)
    }

    @Test
    fun continueFromLatestCountsOnlyAiredEpisodes() {
        // "Below Deck": seasons 1-3 skipped, 4-5 watched; SIMKL already points at S06E01, which airs
        // tomorrow and has no date in the library, while the counts include the skipped seasons.
        val item =
            to.wim.watchlist.data.model.LibraryItem(
                type = MediaType.Tv,
                ids = to.wim.watchlist.data.model.MediaIds(2),
                title = "Below Deck",
                status = ListStatus.Watching,
                nextToWatch = to.wim.watchlist.data.model.NextEpisode(EpisodeRef(6, 1)),
                watchedEpisodes = 3,
                totalEpisodes = 10,
                watched =
                    listOf(
                        to.wim.watchlist.data.model.WatchedSeason(4, listOf(1, 2)),
                        to.wim.watchlist.data.model.WatchedSeason(5, listOf(1)),
                    ),
            )
        assertFalse(Shelves.hasGap(item))
        // SIMKL's counts alone put it in Up next, so the setting looks it up.
        assertEquals(Shelf.UpNext, Shelves.shelfOf(item))
        assertTrue(Shelves.needsLatestLookup(item))
        fun ep(
            s: Int,
            e: Int,
            aired: Boolean = true,
        ) = to.wim.watchlist.data.model.Episode(s, e, null, null, null, null, aired, false)
        val latest = Shelves.latestNextFrom(item, listOf(ep(1, 1), ep(4, 1), ep(4, 2), ep(5, 1), ep(6, 1, aired = false)))
        assertNull(latest.next)
        assertEquals(Shelf.Watched, Shelves.shelfOf(item, fromLatest = true, latest = latest))
        // Once it has aired it is up next again.
        val aired = Shelves.latestNextFrom(item, listOf(ep(4, 1), ep(4, 2), ep(5, 1), ep(6, 1)))
        assertEquals(Shelf.UpNext, Shelves.shelfOf(item, fromLatest = true, latest = aired))
        assertEquals(EpisodeRef(6, 1), Shelves.nextEpisode(item, true, aired)?.ref)
    }

    @Test
    fun showsWatchedElsewhereAreLookedUpBeforeTheSyncIsShown() {
        fun show(
            id: Long,
            watched: List<Int>,
            total: Int = 10,
        ) = to.wim.watchlist.data.model.LibraryItem(
            type = MediaType.Tv,
            ids = to.wim.watchlist.data.model.MediaIds(id),
            title = "Show $id",
            status = ListStatus.Watching,
            nextToWatch = to.wim.watchlist.data.model.NextEpisode(EpisodeRef(1, 1)),
            watchedEpisodes = watched.size,
            totalEpisodes = total,
            watched = listOf(to.wim.watchlist.data.model.WatchedSeason(2, watched)),
        )
        val before = listOf(show(1, listOf(1, 2)), show(2, listOf(1)), show(3, listOf(1)))
        // Show 1: an episode was watched in another app. Shows 2 and 3: unchanged.
        val after = listOf(show(1, listOf(1, 2, 3)), show(2, listOf(1)), show(3, listOf(1)), show(4, listOf(1)))
        val known =
            mapOf(
                "Tv:1" to to.wim.watchlist.data.model.LatestNext(2, null),
                "Tv:2" to to.wim.watchlist.data.model.LatestNext(1, null),
            )
        // Only show 1 changed; show 4 is new (looked up in the background as before).
        assertEquals(listOf("Tv:1"), Shelves.changedLatestLookups(before, after, known).map { it.key })
        // Already looked up for the new count: nothing to do.
        val updated = known + ("Tv:1" to to.wim.watchlist.data.model.LatestNext(3, null))
        assertTrue(Shelves.changedLatestLookups(before, after, updated).isEmpty())
    }
}
