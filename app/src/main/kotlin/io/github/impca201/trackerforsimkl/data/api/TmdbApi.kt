package io.github.impca201.trackerforsimkl.data.api

import io.github.impca201.trackerforsimkl.BuildConfig
import io.github.impca201.trackerforsimkl.data.model.CreditsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** What TMDB said about a key the user entered. */
enum class TmdbKeyCheck { Valid, Rejected, Unreachable }

/**
 * The user's own TMDB key, as pasted: either a v3 "API Key" (sent as `api_key`) or a v4
 * "API Read Access Token" (a JWT, sent as a bearer token). TMDB's settings page shows both.
 */
data class TmdbKey(
    val value: String,
) {
    val isAccessToken: Boolean get() = value.count { it == '.' } == 2

    companion object {
        /** Trims what people paste along with the key; null when nothing is left. */
        fun parse(input: String?): TmdbKey? {
            val text = input?.trim()?.removePrefix("Bearer ")?.trim()?.trim('"')?.trim() ?: return null
            return text.takeIf { it.isNotEmpty() && it.none(Char::isWhitespace) }?.let { TmdbKey(it) }
        }
    }
}

/**
 * Thin client for https://api.themoviedb.org with the key the user entered in Settings. Read
 * only, called only while a details page is open. OkHttp caches responses as TMDB's headers allow.
 */
class TmdbApi(
    private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private suspend fun get(
        key: TmdbKey,
        path: String,
    ): Pair<Int, String> {
        val url: HttpUrl =
            (BASE + path).toHttpUrl().newBuilder()
                .apply { if (!key.isAccessToken) addQueryParameter("api_key", key.value) }
                .build()
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", "${SimklApi.APP_NAME}/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json")
                .apply { if (key.isAccessToken) header("Authorization", "Bearer ${key.value}") }
                .get()
                .build()
        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response -> response.code to response.body.string() }
        }
    }

    private suspend fun call(
        key: TmdbKey,
        path: String,
    ): JsonElement {
        val (code, text) = get(key, path)
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
    ): JsonElement {
        val query = listOfNotNull("append_to_response=combined_credits".takeIf { withCredits }, language?.let { "language=$it" })
        return call(key, "/person/$personId" + if (query.isEmpty()) "" else query.joinToString("&", prefix = "?"))
    }

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

        /** Where people request a key (signed in on themoviedb.org). */
        const val KEY_PAGE = "https://www.themoviedb.org/settings/api"

        fun personUrl(personId: Long) = "https://www.themoviedb.org/person/$personId"
    }
}
