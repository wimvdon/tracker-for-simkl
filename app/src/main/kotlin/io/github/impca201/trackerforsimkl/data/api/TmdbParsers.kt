package io.github.impca201.trackerforsimkl.data.api

import io.github.impca201.trackerforsimkl.data.model.CastMember
import io.github.impca201.trackerforsimkl.data.model.Credits
import io.github.impca201.trackerforsimkl.data.model.CrewJob
import io.github.impca201.trackerforsimkl.data.model.CrewMember
import io.github.impca201.trackerforsimkl.data.model.Images
import io.github.impca201.trackerforsimkl.data.model.Person
import io.github.impca201.trackerforsimkl.data.model.PersonCredit
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate

/** Maps TMDB JSON to the app's models. Pure functions, so they are unit tested. */
object TmdbParsers {
    const val MAX_CAST = 20
    const val MAX_CREW = 12

    /** People per job, so a show's many episode directors don't push out its writers and composer. */
    const val MAX_PER_JOB = 3

    /**
     * Credits from `/movie/{id}/credits` (one `character` and `job` per entry) or
     * `/tv/{id}/aggregate_credits` (`roles` and `jobs` lists with episode counts, every season).
     * The cast keeps TMDB's billing order; the crew keeps only [CrewJob]s, one entry per person and
     * at most [MAX_PER_JOB] people per job.
     */
    fun credits(element: JsonElement?): Credits {
        val obj = element.asObject() ?: return Credits(emptyList(), emptyList())
        val cast =
            obj.arr("cast").objects()
                .sortedBy { it.int("order") ?: Int.MAX_VALUE }
                .mapNotNull { castMember(it) }
                .distinctBy { it.personId }
                .take(MAX_CAST)
        return Credits(cast, crew(obj.arr("crew").objects()))
    }

    private fun castMember(o: JsonObject): CastMember? {
        val id = o.long("id") ?: return null
        val name = o.str("name") ?: return null
        val roles = o.arr("roles").objects()
        val character =
            if (roles.isNotEmpty()) {
                roles.mapNotNull { role(it.str("character")) }.distinct().joinToString(" / ").ifBlank { null }
            } else {
                role(o.str("character"))
            }
        val episodes = o.int("total_episode_count") ?: roles.mapNotNull { it.int("episode_count") }.takeIf { it.isNotEmpty() }?.sum()
        return CastMember(id, name, character, Images.tmdbProfile(o.str("profile_path")), episodes)
    }

    /** TMDB marks voice roles with an English " (voice)"; the row is about who plays whom. */
    private fun role(character: String?): String? = character?.removeSuffix(" (voice)")?.trim()?.ifBlank { null }

    private fun crew(entries: List<JsonObject>): List<CrewMember> {
        // A movie lists a person once per job; a show lists them once with all their jobs.
        class Entry(val id: Long, val name: String, val photo: String?, val jobs: MutableMap<CrewJob, Int>, val first: Int)
        val people = LinkedHashMap<Long, Entry>()
        entries.forEachIndexed { index, o ->
            val id = o.long("id") ?: return@forEachIndexed
            val name = o.str("name") ?: return@forEachIndexed
            val jobs =
                o.arr("jobs")?.let { list -> list.objects().mapNotNull { j -> CrewJob.of(j.str("job"))?.let { it to (j.int("episode_count") ?: 0) } } }
                    ?: listOfNotNull(CrewJob.of(o.str("job"))?.let { it to 0 })
            if (jobs.isEmpty()) return@forEachIndexed
            val entry = people.getOrPut(id) { Entry(id, name, Images.tmdbProfile(o.str("profile_path")), mutableMapOf(), index) }
            jobs.forEach { (job, count) -> entry.jobs[job] = (entry.jobs[job] ?: 0) + count }
        }
        // Each person counts under their main (first listed) job.
        fun Entry.main() = jobs.keys.minBy { it.ordinal }
        return people.values
            .sortedWith(
                compareBy<Entry> { it.main().ordinal }
                    // Within a job, who did the most episodes first; movies keep TMDB's order.
                    .thenByDescending { e -> e.jobs[e.main()] ?: 0 }
                    .thenBy { it.first },
            )
            .groupBy { it.main() }
            .values
            .flatMap { it.take(MAX_PER_JOB) }
            .take(MAX_CREW)
            .map { e -> CrewMember(e.id, e.name, e.jobs.keys.sortedBy { it.ordinal }, e.photo) }
    }

    // TMDB genres of talk shows and news; appearances there aren't roles.
    private val notRoles = setOf(10767, 10763)
    private val selfRole = Regex("^(self|himself|herself|themselves)\\b", RegexOption.IGNORE_CASE)

    /**
     * `/person/{id}?append_to_response=combined_credits`. Appearances as themselves and in talk or
     * news shows are left out, as are crew jobs the app doesn't show; a title they both played in
     * and worked on is one credit. Newest first, unreleased titles (no date) on top.
     */
    fun person(element: JsonElement?): Person? {
        val obj = element.asObject() ?: return null
        val id = obj.long("id") ?: return null
        val name = obj.str("name") ?: return null
        val all = obj.obj("combined_credits")
        class Entry(val credit: PersonCredit, val characters: MutableList<String>, val jobs: MutableSet<CrewJob>)
        val titles = LinkedHashMap<String, Entry>()
        fun add(o: JsonObject, character: String?, job: CrewJob?) {
            val movie =
                when (o.str("media_type")) {
                    "movie" -> true
                    "tv" -> false
                    else -> return
                }
            val tmdbId = o.str("id") ?: return
            val title = o.str("title") ?: o.str("name") ?: return
            val entry =
                titles.getOrPut("$movie:$tmdbId") {
                    val credit =
                        PersonCredit(
                            tmdbId = tmdbId,
                            movie = movie,
                            title = title,
                            date = o.str("release_date") ?: o.str("first_air_date"),
                            poster = Images.tmdbPoster(o.str("poster_path")),
                            characters = emptyList(),
                            jobs = emptyList(),
                            votes = o.int("vote_count") ?: 0,
                            episodes = o.int("episode_count"),
                        )
                    Entry(credit, mutableListOf(), mutableSetOf())
                }
            character?.let { if (it !in entry.characters) entry.characters += it }
            job?.let { entry.jobs += it }
        }
        all?.arr("cast").objects().forEach { o ->
            val character = role(o.str("character"))
            val genres = o.arr("genre_ids")?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() }.orEmpty()
            if (character != null && selfRole.containsMatchIn(character)) return@forEach
            if (genres.any { it in notRoles }) return@forEach
            add(o, character, null)
        }
        all?.arr("crew").objects().forEach { o -> CrewJob.of(o.str("job"))?.let { add(o, null, it) } }
        val credits =
            titles.values
                .map { e -> e.credit.copy(characters = e.characters, jobs = e.jobs.sortedBy { it.ordinal }) }
                .sortedWith(compareBy<PersonCredit> { it.date != null }.thenByDescending { it.date })
        return Person(
            id = id,
            name = name,
            photo = Images.tmdbProfile(obj.str("profile_path"), large = true),
            biography = obj.str("biography")?.trim()?.ifBlank { null },
            birthday = date(obj.str("birthday")),
            deathday = date(obj.str("deathday")),
            placeOfBirth = obj.str("place_of_birth"),
            credits = credits,
        )
    }

    /** Only the biography, from a second request in English when the app's language has none. */
    fun biography(element: JsonElement?): String? = element.asObject()?.str("biography")?.trim()?.ifBlank { null }

    private fun date(value: String?): LocalDate? = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
