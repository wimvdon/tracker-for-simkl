package to.wim.watchlist.data.api

import to.wim.watchlist.BuildConfig
import to.wim.watchlist.data.model.CreditsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale

/** What TMDB said about a token the user entered. */
enum class TmdbKeyCheck { Valid, Rejected, Unreachable }

/**
 * The user's own TMDB "API Read Access Token" (a JWT, sent as a bearer token), as pasted. TMDB's
 * settings page also shows a shorter "API Key"; the app doesn't take that one.
 */
data class TmdbKey(
    val value: String,
) {
    companion object {
        private val token = Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
        private val apiKey = Regex("""\b[0-9a-f]{32}\b""")

        /** The token in what people paste (with "Bearer ", quotes or a label around it); null when there is none. */
        fun parse(input: String?): TmdbKey? = input?.let { token.find(it) }?.value?.let { TmdbKey(it) }

        /** True for TMDB's short "API Key", so the dialog can point to the token instead. */
        fun isApiKey(input: String?): Boolean = input != null && parse(input) == null && apiKey.containsMatchIn(input)
    }
}

/**
 * The `language` TMDB expects ("nl-NL", or just "nl"): Android's full locale tag can carry
 * extensions ("nl-NL-u-mu-celsius") and scripts that TMDB doesn't know.
 */
fun tmdbLanguage(locale: Locale): String = if (locale.country.isBlank()) locale.language else "${locale.language}-${locale.country}"

/**
 * Thin client for https://api.themoviedb.org with the token the user entered in Settings. Read
 * only, called only while a details, person or search screen is open. OkHttp caches responses as
 * TMDB's headers allow.
 */
class TmdbApi(
    private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private suspend fun get(
        key: TmdbKey,
        path: String,
        params: Map<String, String?> = emptyMap(),
    ): Pair<Int, String> {
        val url =
            (BASE + path).toHttpUrl().newBuilder()
                .apply { params.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) } }
                .build()
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", "${SimklApi.APP_NAME}/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer ${key.value}")
                .get()
                .build()
        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response -> response.code to response.body.string() }
        }
    }

    private suspend fun call(
        key: TmdbKey,
        path: String,
        params: Map<String, String?> = emptyMap(),
    ): JsonElement {
        val (code, text) = get(key, path, params)
        if (code !in 200..299) {
            val message = runCatching { json.parseToJsonElement(text).asObject()?.str("status_message") }.getOrNull()
            throw ApiException(code, message ?: "HTTP $code")
        }
        return try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw ApiException(code, "Unexpected response from TMDB")
        }
    }

    /** `/movie/{id}/credits`, or `/tv/{id}/aggregate_credits` (the cast of every season). */
    suspend fun credits(
        key: TmdbKey,
        source: CreditsSource,
    ): JsonElement = call(key, if (source.movie) "/movie/${source.tmdbId}/credits" else "/tv/${source.tmdbId}/aggregate_credits")

    /** A person with their combined (movie and TV) credits, in [language] ("nl-NL") where TMDB has it. */
    suspend fun person(
        key: TmdbKey,
        personId: Long,
        language: String? = null,
        withCredits: Boolean = true,
    ): JsonElement =
        call(key, "/person/$personId", mapOf("append_to_response" to "combined_credits".takeIf { withCredits }, "language" to language))

    /** `/search/person`: people by name, most popular first (first page, 20 results). */
    suspend fun searchPeople(
        key: TmdbKey,
        query: String,
        language: String? = null,
    ): JsonElement = call(key, "/search/person", mapOf("query" to query, "language" to language, "include_adult" to "false"))

    /** Asks TMDB whether [key] works, for the Settings dialog. */
    suspend fun check(key: TmdbKey): TmdbKeyCheck =
        try {
            val (code, _) = get(key, "/authentication")
            when (code) {
                in 200..299 -> TmdbKeyCheck.Valid
                401, 403 -> TmdbKeyCheck.Rejected
                else -> TmdbKeyCheck.Unreachable
            }
        } catch (e: IOException) {
            TmdbKeyCheck.Unreachable
        }

    companion object {
        const val BASE = "https://api.themoviedb.org/3"

        /** Where people request a token (signed in on themoviedb.org). */
        const val KEY_PAGE = "https://www.themoviedb.org/settings/api"

        fun personUrl(personId: Long) = "https://www.themoviedb.org/person/$personId"
    }
}
