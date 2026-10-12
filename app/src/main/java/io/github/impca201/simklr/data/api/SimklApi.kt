package io.github.impca201.simklr.data.api

import io.github.impca201.simklr.BuildConfig
import io.github.impca201.simklr.data.model.EpisodeRef
import io.github.impca201.simklr.data.model.ListStatus
import io.github.impca201.simklr.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant

class ApiException(
    val code: Int,
    message: String,
) : IOException(message) {
    val unauthorized get() = code == 401 || code == 403
}

/** A pending PIN (device code) login: the user enters [userCode] at [verificationUrl]. */
data class PinCode(
    val userCode: String,
    val verificationUrl: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
)

/**
 * Thin client for https://api.simkl.com. Every call sends the client ID (header and query) and,
 * once logged in, the user's bearer token.
 */
class SimklApi(
    private val http: OkHttpClient,
    private val clientId: suspend () -> String?,
    private val token: suspend () -> String?,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun url(
        path: String,
        params: Map<String, String?> = emptyMap(),
    ): HttpUrl {
        val base = if (path.startsWith("http")) path.toHttpUrl() else (BASE + path).toHttpUrl()
        return base.newBuilder().apply {
            clientId()?.let { addQueryParameter("client_id", it) }
            addQueryParameter("app-name", APP_NAME)
            addQueryParameter("app-version", BuildConfig.VERSION_NAME)
            params.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) }
        }.build()
    }

    private suspend fun call(
        method: String,
        path: String,
        params: Map<String, String?> = emptyMap(),
        body: JsonElement? = null,
        auth: Boolean = true,
    ): JsonElement {
        val id = clientId()?.takeIf { it.isNotBlank() } ?: throw ApiException(0, "No SIMKL client ID set")
        val request =
            Request.Builder()
                .url(url(path, params))
                .header("simkl-api-key", id)
                .header("User-Agent", "$APP_NAME/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json")
                .apply {
                    if (auth) token()?.let { header("Authorization", "Bearer $it") }
                    val requestBody = body?.toString()?.toRequestBody(jsonType)
                    when (method) {
                        "POST" -> post(requestBody ?: "{}".toRequestBody(jsonType))
                        else -> get()
                    }
                }.build()
        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) {
                    val message =
                        runCatching { json.parseToJsonElement(text).asObject()?.let { it.str("message") ?: it.str("error") } }.getOrNull()
                    throw ApiException(response.code, message ?: "HTTP ${response.code}")
                }
                if (text.isBlank()) JsonNull else json.parseToJsonElement(text)
            }
        }
    }

    private suspend fun get(
        path: String,
        params: Map<String, String?> = emptyMap(),
        auth: Boolean = true,
    ) = call("GET", path, params, auth = auth)

    private suspend fun post(
        path: String,
        body: JsonElement? = null,
    ) = call("POST", path, body = body)

    // ---- Login (PIN / device code flow; needs only the client ID) ------------------------------

    suspend fun requestPin(): PinCode {
        val obj = get("/oauth/pin", mapOf("redirect" to "urn:ietf:wg:oauth:2.0:oob"), auth = false).asObject()
        val code = obj?.str("user_code") ?: throw ApiException(0, obj?.str("message") ?: "SIMKL did not return a code")
        return PinCode(
            userCode = code,
            verificationUrl = obj.str("verification_url") ?: "https://simkl.com/pin/",
            expiresInSeconds = obj.int("expires_in") ?: 900,
            intervalSeconds = obj.int("interval") ?: 5,
        )
    }

    /** Returns the access token once the user approved the code, null while still pending. */
    suspend fun pollPin(userCode: String): String? {
        val obj = get("/oauth/pin/$userCode", auth = false).asObject()
        return obj?.str("access_token")?.takeIf { obj.str("result") != "KO" }
    }

    // ---- Sync ---------------------------------------------------------------------------------

    suspend fun activities(): JsonObject? = post("/sync/activities").asObject()

    suspend fun allItems(dateFrom: String? = null): JsonElement =
        get(
            "/sync/all-items/",
            mapOf(
                "extended" to "full",
                "episode_watched_at" to "yes",
                "next_watch_info" to "yes",
                "date_from" to dateFrom,
            ),
        )

    private fun idsJson(simklId: Long) = buildJsonObject { putJsonObject("ids") { put("simkl", simklId) } }

    private fun body(
        type: MediaType,
        item: JsonObject,
    ) = buildJsonObject { putJsonArray(type.bodyKey) { add(item) } }

    suspend fun addToList(
        type: MediaType,
        simklId: Long,
        status: ListStatus,
    ) {
        val item = buildJsonObject {
            put("to", status.api)
            putJsonObject("ids") { put("simkl", simklId) }
        }
        post("/sync/add-to-list", body(type, item))
    }

    /** Removes the title from all lists (and its watch history). */
    suspend fun removeFromList(
        type: MediaType,
        simklId: Long,
    ) {
        post("/sync/history/remove", body(type, idsJson(simklId)))
    }

    suspend fun rate(
        type: MediaType,
        simklId: Long,
        rating: Int?,
    ) {
        if (rating == null) {
            post("/sync/ratings/remove", body(type, idsJson(simklId)))
        } else {
            val item = buildJsonObject {
                put("rating", rating)
                put("rated_at", Instant.now().toString())
                putJsonObject("ids") { put("simkl", simklId) }
            }
            post("/sync/ratings", body(type, item))
        }
    }

    /** Marks a movie (or a whole show) as watched. */
    suspend fun markWatched(
        type: MediaType,
        simklId: Long,
    ) {
        val item = buildJsonObject {
            put("watched_at", Instant.now().toString())
            putJsonObject("ids") { put("simkl", simklId) }
        }
        post("/sync/history", body(type, item))
    }

    /** Adds ([watched] = true) or removes episodes from the watch history. */
    suspend fun setEpisodesWatched(
        type: MediaType,
        simklId: Long,
        episodes: List<EpisodeRef>,
        watched: Boolean,
    ) {
        if (episodes.isEmpty()) return
        val now = Instant.now().toString()
        val item = buildJsonObject {
            putJsonObject("ids") { put("simkl", simklId) }
            val (withSeason, withoutSeason) = episodes.partition { it.season != null }
            if (withSeason.isNotEmpty()) {
                putJsonArray("seasons") {
                    withSeason.groupBy { it.season!! }.forEach { (season, eps) ->
                        addJsonObject {
                            put("number", season)
                            putJsonArray("episodes") {
                                eps.forEach { e ->
                                    addJsonObject {
                                        put("number", e.episode)
                                        if (watched) put("watched_at", now)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (withoutSeason.isNotEmpty()) {
                putJsonArray("episodes") {
                    withoutSeason.forEach { e ->
                        addJsonObject {
                            put("number", e.episode)
                            if (watched) put("watched_at", now)
                        }
                    }
                }
            }
        }
        post(if (watched) "/sync/history" else "/sync/history/remove", body(type, item))
    }

    // ---- Catalogue ----------------------------------------------------------------------------

    suspend fun details(
        type: MediaType,
        simklId: Long,
    ): JsonObject? = get("/${type.catalogPath}/$simklId", mapOf("extended" to "full")).asObject()

    suspend fun episodes(
        type: MediaType,
        simklId: Long,
    ): JsonElement = get("/${type.catalogPath}/episodes/$simklId", mapOf("extended" to "full"))

    suspend fun search(
        type: MediaType,
        query: String,
    ): JsonElement = get("/search/${type.searchPath}", mapOf("q" to query, "extended" to "full", "limit" to "40"))

    /** Any public list endpoint (trending, best, premieres, airing) or data.simkl.in file. */
    suspend fun list(
        path: String,
        params: Map<String, String?> = emptyMap(),
    ): JsonElement = get(path, params)

    suspend fun calendar(type: MediaType): JsonElement =
        get(
            when (type) {
                MediaType.Tv -> "https://data.simkl.in/calendar/tv.json"
                MediaType.Anime -> "https://data.simkl.in/calendar/anime.json"
                MediaType.Movie -> "https://data.simkl.in/calendar/movie_release.json"
            },
            auth = false,
        )

    // ---- User ---------------------------------------------------------------------------------

    suspend fun settings(): JsonElement = post("/users/settings")

    suspend fun stats(userId: Long): JsonElement = post("/users/$userId/stats")

    companion object {
        const val BASE = "https://api.simkl.com"
        const val APP_NAME = "simklr"
        const val DEVELOPER_URL = "https://simkl.com/settings/developer/new/"
    }
}
