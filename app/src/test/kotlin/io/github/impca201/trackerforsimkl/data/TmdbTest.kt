package io.github.impca201.trackerforsimkl.data

import io.github.impca201.trackerforsimkl.data.api.Parsers
import io.github.impca201.trackerforsimkl.data.api.TmdbKey
import io.github.impca201.trackerforsimkl.data.api.TmdbParsers
import io.github.impca201.trackerforsimkl.data.model.AiringState
import io.github.impca201.trackerforsimkl.data.model.CreditsSource
import io.github.impca201.trackerforsimkl.data.model.CrewJob
import io.github.impca201.trackerforsimkl.data.model.Images
import io.github.impca201.trackerforsimkl.data.model.LibraryItem
import io.github.impca201.trackerforsimkl.data.model.ListStatus
import io.github.impca201.trackerforsimkl.data.model.MediaDetails
import io.github.impca201.trackerforsimkl.data.model.MediaIds
import io.github.impca201.trackerforsimkl.data.model.MediaType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** TMDB cast and crew: response mapping (shapes from TMDB's API reference), key handling, sources. */
class TmdbTest {
    private val movieCredits =
        """
        {
          "id": 550,
          "cast": [
            { "id": 287, "name": "Brad Pitt", "character": "Tyler Durden", "profile_path": "/brad.jpg", "order": 1 },
            { "id": 819, "name": "Edward Norton", "character": "The Narrator", "profile_path": "/ed.jpg", "order": 0 },
            { "id": 1283, "name": "Helena Bonham Carter", "character": "Marla Singer", "profile_path": null, "order": 2 },
            { "name": "No ID", "character": "Skipped", "order": 3 }
          ],
          "crew": [
            { "id": 7469, "name": "Jim Uhls", "job": "Screenplay", "department": "Writing", "profile_path": null },
            { "id": 7467, "name": "David Fincher", "job": "Director", "department": "Directing", "profile_path": "/df.jpg" },
            { "id": 7474, "name": "Ross Grayson Bell", "job": "Producer", "department": "Production" },
            { "id": 7467, "name": "David Fincher", "job": "Writer", "department": "Writing", "profile_path": "/df.jpg" },
            { "id": 7468, "name": "Chuck Palahniuk", "job": "Novel", "department": "Writing" }
          ]
        }
        """.trimIndent()

    private val tvCredits =
        """
        {
          "id": 1396,
          "cast": [
            { "id": 17419, "name": "Bryan Cranston", "profile_path": "/bc.jpg", "order": 0, "total_episode_count": 62,
              "roles": [ { "credit_id": "a", "character": "Walter White", "episode_count": 62 } ] },
            { "id": 84497, "name": "Aaron Paul", "order": 1,
              "roles": [ { "character": "Jesse Pinkman", "episode_count": 60 }, { "character": "Jesse", "episode_count": 2 } ] },
            { "id": 99, "name": "Voice Actor", "order": 2, "roles": [ { "character": "Monkey D. Luffy (voice)", "episode_count": 5 } ] }
          ],
          "crew": [
            { "id": 1, "name": "Rare Director", "jobs": [ { "job": "Director", "episode_count": 1 } ], "total_episode_count": 1 },
            { "id": 2, "name": "Main Director", "jobs": [ { "job": "Director", "episode_count": 9 }, { "job": "Writer", "episode_count": 3 } ] },
            { "id": 3, "name": "Composer", "jobs": [ { "job": "Original Music Composer", "episode_count": 62 } ] },
            { "id": 4, "name": "Only Producer", "jobs": [ { "job": "Executive Producer", "episode_count": 62 } ] }
          ]
        }
        """.trimIndent()

    private fun details(
        type: MediaType,
        tmdb: String?,
        animeType: String? = null,
    ) = MediaDetails(
        type = type, ids = MediaIds(1, tmdb = tmdb), title = "T", originalTitle = null, year = null, poster = null, fanart = null,
        overview = null, genres = emptyList(), status = null, airing = AiringState.Unknown, network = null, country = null,
        certification = null, runtime = null, firstAired = null, airs = null, totalEpisodes = null, animeType = animeType,
        director = null, budget = null, revenue = null, ratings = emptyList(), rank = null, trailers = emptyList(),
        recommendations = emptyList(), alternativeTitles = emptyList(),
    )

    @Test
    fun movieCastKeepsBillingOrderAndSkipsEntriesWithoutId() {
        val cast = TmdbParsers.credits(Json.parseToJsonElement(movieCredits)).cast
        assertEquals(listOf("Edward Norton", "Brad Pitt", "Helena Bonham Carter"), cast.map { it.name })
        assertEquals("The Narrator", cast[0].character)
        assertEquals("https://wsrv.nl/?url=https://image.tmdb.org/t/p/w185/ed.jpg", cast[0].photo)
        assertNull(cast[2].photo)
        assertNull(cast[0].episodes)
    }

    @Test
    fun movieCrewMergesJobsPerPersonAndKeepsOnlyKeyJobs() {
        val crew = TmdbParsers.credits(Json.parseToJsonElement(movieCredits)).crew
        assertEquals(listOf("David Fincher", "Jim Uhls", "Chuck Palahniuk"), crew.map { it.name })
        assertEquals(listOf(CrewJob.Director, CrewJob.Writer), crew[0].jobs)
        assertEquals(listOf(CrewJob.Novel), crew[2].jobs)
    }

    @Test
    fun showCastJoinsRolesAndCountsEpisodes() {
        val cast = TmdbParsers.credits(Json.parseToJsonElement(tvCredits)).cast
        assertEquals("Walter White", cast[0].character)
        assertEquals(62, cast[0].episodes)
        assertEquals("Jesse Pinkman / Jesse", cast[1].character)
        assertEquals(62, cast[1].episodes)
    }

    @Test
    fun voiceRolesLoseTheirMarker() {
        assertEquals("Monkey D. Luffy", TmdbParsers.credits(Json.parseToJsonElement(tvCredits)).cast[2].character)
    }

    @Test
    fun manyEpisodeDirectorsLeaveRoomForOtherJobs() {
        val directors = (1..10).joinToString(",") { """{"id": $it, "name": "D$it", "jobs": [{"job": "Director", "episode_count": $it}]}""" }
        val writer = """{"id": 50, "name": "Writer", "jobs": [{"job": "Writer", "episode_count": 1}]}"""
        val crew = TmdbParsers.credits(Json.parseToJsonElement("""{"crew": [$directors, $writer]}""")).crew
        assertEquals(listOf("D10", "D9", "D8", "Writer"), crew.map { it.name })
    }

    @Test
    fun showCrewPutsTheBusiestDirectorFirst() {
        val crew = TmdbParsers.credits(Json.parseToJsonElement(tvCredits)).crew
        assertEquals(listOf("Main Director", "Rare Director", "Composer"), crew.map { it.name })
        assertEquals(listOf(CrewJob.Director, CrewJob.Writer), crew[0].jobs)
    }

    @Test
    fun missingOrOddResponsesGiveEmptyCredits() {
        assertTrue(TmdbParsers.credits(null).isEmpty)
        assertTrue(TmdbParsers.credits(Json.parseToJsonElement("""{"cast": null, "crew": "x"}""")).isEmpty)
    }

    @Test
    fun castIsCapped() {
        val many = (1..40).joinToString(",") { """{"id": $it, "name": "P$it", "order": $it}""" }
        assertEquals(TmdbParsers.MAX_CAST, TmdbParsers.credits(Json.parseToJsonElement("""{"cast": [$many]}""")).cast.size)
    }

    @Test
    fun creditsComeFromTheRightKindOfTmdbTitle() {
        assertEquals(CreditsSource("550", movie = true), CreditsSource.of(details(MediaType.Movie, "550")))
        assertEquals(CreditsSource("1396", movie = false), CreditsSource.of(details(MediaType.Tv, "1396")))
        assertEquals(CreditsSource("37854", movie = false), CreditsSource.of(details(MediaType.Anime, "37854", animeType = "tv")))
        assertEquals(CreditsSource("129", movie = true), CreditsSource.of(details(MediaType.Anime, "129", animeType = "movie")))
        assertNull(CreditsSource.of(details(MediaType.Anime, null)))
        assertNull(CreditsSource.of(details(MediaType.Tv, "tt123")))
    }

    @Test
    fun pastedKeysAreCleanedUp() {
        assertEquals("abc123", TmdbKey.parse("  abc123\n")?.value)
        assertEquals("eyJ.a.b", TmdbKey.parse("Bearer eyJ.a.b")?.value)
        assertEquals("abc123", TmdbKey.parse("\"abc123\"")?.value)
        assertNull(TmdbKey.parse("   "))
        assertNull(TmdbKey.parse(null))
        assertNull(TmdbKey.parse("two words"))
    }

    @Test
    fun readAccessTokensAreToldApartFromApiKeys() {
        assertTrue(TmdbKey("eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJ4In0.sig").isAccessToken)
        assertFalse(TmdbKey("0123456789abcdef0123456789abcdef").isAccessToken)
    }

    @Test
    fun crewJobNames() {
        assertEquals(CrewJob.Writer, CrewJob.of("Screenplay"))
        assertEquals(CrewJob.Camera, CrewJob.of("Director of Photography"))
        assertNull(CrewJob.of("Producer"))
        assertNull(CrewJob.of(null))
    }

    @Test
    fun profilePhotosNeedATmdbPath() {
        assertNull(Images.tmdbProfile(null))
        assertNull(Images.tmdbProfile("noslash.jpg"))
    }

    // Shaped like /person/17419?append_to_response=combined_credits (trimmed live response).
    private val person =
        """
        {
          "id": 17419, "name": "Bryan Cranston", "biography": "", "birthday": "1956-03-07", "deathday": null,
          "place_of_birth": "Hollywood, Los Angeles, California, USA", "profile_path": "/bc.jpg",
          "combined_credits": {
            "cast": [
              { "id": 1396, "media_type": "tv", "name": "Breaking Bad", "first_air_date": "2008-01-20", "poster_path": "/bb.jpg",
                "character": "Walter White", "episode_count": 62, "vote_count": 16000, "genre_ids": [18] },
              { "id": 64808, "media_type": "movie", "title": "Drive", "release_date": "2011-09-15", "character": "Shannon",
                "vote_count": 14146, "genre_ids": [18, 53] },
              { "id": 1, "media_type": "movie", "title": "An Audience with Adele", "release_date": "2021-11-21", "character": "Self",
                "vote_count": 45, "genre_ids": [10402] },
              { "id": 2, "media_type": "tv", "name": "The Tonight Show", "first_air_date": "2014-02-17", "character": "Guest",
                "vote_count": 300, "genre_ids": [10767] },
              { "id": 3, "media_type": "movie", "title": "Untitled Project", "character": "Lead", "vote_count": 0, "genre_ids": [] }
            ],
            "crew": [
              { "id": 1396, "media_type": "tv", "name": "Breaking Bad", "first_air_date": "2008-01-20", "job": "Director", "vote_count": 16000 },
              { "id": 1396, "media_type": "tv", "name": "Breaking Bad", "first_air_date": "2008-01-20", "job": "Producer", "vote_count": 16000 },
              { "id": 4, "media_type": "tv", "name": "Your Honor", "first_air_date": "2020-12-06", "job": "Executive Producer", "vote_count": 600 }
            ]
          }
        }
        """.trimIndent()

    private val cranston get() = TmdbParsers.person(Json.parseToJsonElement(person))!!

    @Test
    fun personKeepsRolesAndJobsAndLeavesOutAppearances() {
        val p = cranston
        assertEquals("Bryan Cranston", p.name)
        assertNull(p.biography)
        assertEquals(LocalDate.of(1956, 3, 7), p.birthday)
        assertEquals("https://wsrv.nl/?url=https://image.tmdb.org/t/p/w342/bc.jpg", p.photo)
        // Unreleased first, then newest; "Self" and talk shows are left out, and so is a title
        // with only jobs the app doesn't show.
        assertEquals(listOf("Untitled Project", "Drive", "Breaking Bad"), p.credits.map { it.title })
        val bb = p.credits.last()
        assertEquals(listOf("Walter White"), bb.characters)
        assertEquals(listOf(CrewJob.Director), bb.jobs)
        assertEquals(62, bb.episodes)
        assertFalse(bb.movie)
        assertEquals("https://wsrv.nl/?url=https://image.tmdb.org/t/p/w185/bb.jpg", bb.poster)
        assertEquals("https://www.themoviedb.org/tv/1396", bb.tmdbUrl)
    }

    @Test
    fun knownForIsMostVotedFirst() {
        assertEquals(listOf("Breaking Bad", "Drive"), cranston.knownFor(2).map { it.title })
    }

    @Test
    fun ageCountsToTodayOrToTheirDeath() {
        val p = cranston
        assertEquals(69, p.age(LocalDate.of(2026, 3, 6)))
        assertEquals(70, p.age(LocalDate.of(2026, 3, 7)))
        assertEquals(50, p.copy(deathday = LocalDate.of(2006, 3, 7)).age(LocalDate.of(2026, 1, 1)))
        assertNull(p.copy(birthday = null).age(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun personWithoutIdOrNameIsNull() {
        assertNull(TmdbParsers.person(Json.parseToJsonElement("""{"name": "X"}""")))
        assertNull(TmdbParsers.person(null))
    }

    @Test
    fun biographyFallback() {
        assertEquals("Actor.", TmdbParsers.biography(Json.parseToJsonElement("""{"biography": " Actor. "}""")))
        assertNull(TmdbParsers.biography(Json.parseToJsonElement("""{"biography": ""}""")))
    }

    @Test
    fun creditsMatchLibraryTitlesOfTheSameKind() {
        val show = LibraryItem(MediaType.Tv, MediaIds(10, tmdb = "1396"), "Breaking Bad", status = ListStatus.Watching)
        val movieWithSameNumber = LibraryItem(MediaType.Movie, MediaIds(11, tmdb = "64808"), "Drive", status = ListStatus.Watching)
        val animeMovie = LibraryItem(MediaType.Anime, MediaIds(12, tmdb = "64808"), "Some anime movie", status = ListStatus.Watching)
        val items = listOf(animeMovie, show, movieWithSameNumber)
        val credits = cranston.credits
        assertEquals(10L, credits.first { it.title == "Breaking Bad" }.match(items)?.ids?.simkl)
        assertEquals(11L, credits.first { it.title == "Drive" }.match(items)?.ids?.simkl)
        assertNull(credits.first { it.title == "Untitled Project" }.match(items))
        // A TMDB movie never matches a TV show with the same number.
        val tvWithMovieId = LibraryItem(MediaType.Tv, MediaIds(13, tmdb = "64808"), "Other", status = ListStatus.Watching)
        assertNull(credits.first { it.title == "Drive" }.match(listOf(tvWithMovieId)))
    }

    @Test
    fun simklLookupByTmdbId() {
        val found =
            Parsers.lookup(
                Json.parseToJsonElement("""[{"type": "anime", "title": "One Piece", "poster": "74/abc", "year": 1999, "ids": {"simkl": 38636, "slug": "one-piece"}}]"""),
                movie = false,
            )
        assertEquals(MediaType.Anime, found?.type)
        assertEquals(38636L, found?.simklId)
        assertEquals("74/abc", found?.poster)
        // No type: the kind that was asked for.
        assertEquals(MediaType.Movie, Parsers.lookup(Json.parseToJsonElement("""[{"title": "Drive", "ids": {"simkl": 5}}]"""), movie = true)?.type)
        assertNull(Parsers.lookup(Json.parseToJsonElement("[]"), movie = true))
        assertNull(Parsers.lookup(null, movie = true))
    }
}
