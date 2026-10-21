package io.github.impca201.trackerforsimkl.data.api

import io.github.impca201.trackerforsimkl.data.model.CastMember
import io.github.impca201.trackerforsimkl.data.model.Credits
import io.github.impca201.trackerforsimkl.data.model.CrewJob
import io.github.impca201.trackerforsimkl.data.model.CrewMember
import io.github.impca201.trackerforsimkl.data.model.Images
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
}
