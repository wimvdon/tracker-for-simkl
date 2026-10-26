package io.github.impca201.trackerforsimkl.data.api

import io.github.impca201.trackerforsimkl.BuildConfig
import io.github.impca201.trackerforsimkl.data.model.EpisodeRef
import io.github.impca201.trackerforsimkl.data.model.ListStatus
import io.github.impca201.trackerforsimkl.data.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
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
import io.github.impca201.trackerforsimkl.data.SettingsStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ApiException(
    val code: Int,
    message: String,
) : IOException(message) {
    val unauthorized get() = code == 401 || code == 403
}

/** The outcome of exchanging a sign-in code for tokens. */
sealed interface SignIn {
    data class Approved(
        val accessToken: String,
        val refreshToken: String?,
        /** Epoch ms, or null when SIMKL gives no expiry. */
        val expiresAt: Long?,
    ) : SignIn

    data class Failed(
        val message: String,
    ) : SignIn
}

/**
 * Thin client for https://api.simkl.com, following SIMKL's API documentation (github.com/SIMKL/api,
 * now api.simkl.org). Every call sends the `simkl-api-key` header plus `client_id`, and once logged
 * in the user's bearer token. data.simkl.in files (trending, calendars) get no query parameters.
 */
class SimklApi(
    private val http: OkHttpClient,
    private val settings: SettingsStore,
) {
    private val refreshLock = Mutex()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Pair<Int, String>>>()

    /** The app's own SIMKL client ID, baked in at build time (not a secret, per SIMKL's API rules). */
    private fun clientId(): String? = BuildConfig.SIMKL_CLIENT_ID.ifBlank { null }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun url(
        path: String,
        params: Map<String, String?> = emptyMap(),
    ): HttpUrl {
        val base = if (path.startsWith("http")) path.toHttpUrl() else (BASE + path).toHttpUrl()
        // The static data files ignore query strings and SIMKL asks not to add any.
        if (base.host == DATA_HOST) return base
        return base.newBuilder().apply {
            clientId()?.let { addQueryParameter("client_id", it) }
            addQueryParameter("app-name", APP_NAME)
            addQueryParameter("app-version", BuildConfig.VERSION_NAME)
            params.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) }
        }.build()
    }

    /**
     * The access token to send. An AUTH V2 token is refreshed shortly before it expires
     * (V2 access tokens last about a week; the refresh token 180 days from its last use).
     */
    private suspend fun accessToken(forceRefresh: Boolean = false): String? {
        val token = settings.token() ?: return null
        val refresh = settings.refreshToken() ?: return token
        val expiresAt = settings.tokenExpiresAt()
        val due = forceRefresh || (expiresAt != null && System.currentTimeMillis() > expiresAt - TimeUnit.HOURS.toMillis(12))
        if (!due) return token
        return refreshLock.withLock {
            // Another call may have refreshed while this one waited.
            val current = settings.token()
            if (current != token) return@withLock current
            val result = postForm("/oauth2/token", mapOf("grant_type" to "refresh_token", "client_id" to clientId(), "refresh_token" to refresh))
            // Logged out (or in again) while the refresh was running: do not bring the old session back.
            if (settings.token() != token) return@withLock settings.token()
            val obj = result.second.asObject()
            val newToken = obj?.str("access_token")
            if (result.first in 200..299 && newToken != null) {
                settings.setTokens(newToken, obj.str("refresh_token") ?: refresh, obj.long("expires_in")?.let { System.currentTimeMillis() + it * 1000 })
                newToken
            } else {
                token
            }
        }
    }

    private suspend fun call(
        method: String,
        path: String,
        params: Map<String, String?> = emptyMap(),
        body: JsonElement? = null,
        auth: Boolean = true,
        retried: Boolean = false,
    ): JsonElement {
        val id = clientId()?.takeIf { it.isNotBlank() } ?: throw ApiException(0, "This build has no SIMKL client ID")
        val bearer = if (auth) accessToken() else null
        val request =
            Request.Builder()
                .url(url(path, params))
                .header("simkl-api-key", id)
                .header("User-Agent", "$APP_NAME/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .apply {
                    bearer?.let { header("Authorization", "Bearer $it") }
                    val requestBody = body?.toString()?.toRequestBody(jsonType)
                    when (method) {
                        "POST" -> post(requestBody ?: "{}".toRequestBody(jsonType))
                        "DELETE" -> delete()
                        else -> get()
                    }
                }.build()
        val (code, text) = if (method == "GET") shared(request) else send(request)
        // An expired AUTH V2 token: refresh once and retry.
        if (code == 401 && bearer != null && !retried && settings.refreshToken() != null) {
            accessToken(forceRefresh = true)
            return call(method, path, params, body, auth, retried = true)
        }
        if (code !in 200..299) {
            val message = runCatching { json.parseToJsonElement(text).asObject()?.let { it.str("message") ?: it.str("error") } }.getOrNull()
            throw ApiException(code, message ?: "HTTP $code")
        }
        if (text.isBlank()) return JsonNull
        // A 2xx that is not JSON (e.g. a captive portal or proxy page) is reported as an API error.
        return try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw ApiException(code, "Unexpected response from SIMKL")
        }
    }

    private suspend fun send(request: Request): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response -> response.code to response.body.string() }
        }

    /**
     * A GET for a URL that is already being fetched waits for that response instead of sending the
     * same request again. When the first caller is cancelled, a waiting one sends it itself.
     */
    private suspend fun shared(request: Request): Pair<Int, String> {
        val key = request.url.toString() + " " + request.header("Authorization").orEmpty()
        while (true) {
            val mine = CompletableDeferred<Pair<Int, String>>()
            val running = inFlight.putIfAbsent(key, mine)
            if (running != null) {
                try {
                    return running.await()
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    continue
                }
            }
            try {
                return send(request).also { mine.complete(it) }
            } catch (e: Throwable) {
                mine.completeExceptionally(e)
                throw e
            } finally {
                inFlight.remove(key, mine)
            }
        }
    }

    /** A form-encoded POST to the AUTH V2 /oauth2 endpoints, which take the client ID in the body. */
    private suspend fun postForm(
        path: String,
        form: Map<String, String?>,
    ): Pair<Int, JsonElement> {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> if (v != null) add(k, v) } }.build()
        val request =
            Request.Builder()
                .url(BASE + path)
                .header("User-Agent", "$APP_NAME/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json")
                .post(body)
                .build()
        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                val text = response.body.string()
                response.code to (runCatching { json.parseToJsonElement(text) }.getOrNull() ?: JsonNull)
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

    // ---- Sign-in -----------------------------------------------------------------------------

    /**
     * The simkl.com page of the mobile sign-in (OAuth 2.0 authorization code with PKCE): the user
     * approves there and SIMKL sends them back to [REDIRECT_URI] with a code for [exchangeCode].
     */
    suspend fun authorizeUrl(
        state: String,
        codeChallenge: String,
    ): String {
        val id = clientId()?.takeIf { it.isNotBlank() } ?: throw ApiException(0, "This build has no SIMKL client ID")
        return AUTHORIZE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", id)
            .addQueryParameter("redirect_uri", REDIRECT_URI)
            .addQueryParameter("scope", SCOPE)
            .addQueryParameter("state", state)
            .addQueryParameter("code_challenge", codeChallenge)
            .addQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()
    }

    /** Trades the code from the sign-in redirect for tokens; no client secret, the verifier proves it's us. */
    suspend fun exchangeCode(
        code: String,
        codeVerifier: String,
    ): SignIn {
        val (status, element) =
            postForm(
                "/oauth2/token",
                mapOf(
                    "grant_type" to "authorization_code",
                    "client_id" to clientId(),
                    "code" to code,
                    "redirect_uri" to REDIRECT_URI,
                    "code_verifier" to codeVerifier,
                ),
            )
        val obj = element.asObject()
        return tokenResult(status, obj)
            ?: SignIn.Failed(obj?.str("error_description") ?: obj?.str("error") ?: "SIMKL sign-in failed (HTTP $status)")
    }

    /** Approved for a token response with write access, Failed for a read-only one, null otherwise. */
    private fun tokenResult(
        status: Int,
        obj: JsonObject?,
    ): SignIn? {
        if (obj == null || status !in 200..299) return null
        val token = obj.str("access_token") ?: return null
        val scope = obj.str("scope")
        if (scope != null && "media:write" !in scope.split(" ")) {
            return SignIn.Failed("SIMKL granted read-only access. Sign in again and allow Tracker for Simkl to update your lists.")
        }
        return SignIn.Approved(token, obj.str("refresh_token"), obj.long("expires_in")?.let { System.currentTimeMillis() + it * 1000 })
    }

    // ---- Sync ---------------------------------------------------------------------------------

    suspend fun activities(): JsonObject? = post("/sync/activities").asObject()

    /** Everything in the user's lists, or what changed since [dateFrom]. Null JSON when the lists are empty. */
    suspend fun allItems(dateFrom: String? = null): JsonElement =
        get(
            "/sync/all-items/",
            mapOf(
                "extended" to "full",
                "episode_watched_at" to "yes",
                "next_watch_info" to "yes",
                "memos" to "yes",
                "date_from" to dateFrom,
            ),
        )

    /** The minified list (only ids) used to find items removed from the lists. */
    suspend fun allItemIds(): JsonElement = get("/sync/all-items/", mapOf("extended" to "simkl_ids_only"))

    /** Ratings changed since [dateFrom] (same item format as /sync/all-items). */
    suspend fun ratings(dateFrom: String?): JsonElement = call("POST", "/sync/ratings/", mapOf("date_from" to dateFrom))

    /** Paused, partly watched episodes and movies ("Continue watching"). */
    suspend fun playback(): JsonElement = get("/sync/playback")

    /** Removes a paused session's saved progress. */
    suspend fun deletePlayback(id: Long) {
        call("DELETE", "/sync/playback/$id")
    }

    /**
     * Release status and community ratings of every title in the user's lists, all types in one
     * call. The user's own ratings come with /sync/all-items instead.
     */
    suspend fun watchlistRatings(): JsonElement =
        get("/ratings/all", mapOf("user_watchlist" to "all", "fields" to "simkl,ext,rank,release_status"))

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

    /** "Watching now": SIMKL marks it watched by itself when the runtime is over. */
    suspend fun checkIn(
        type: MediaType,
        simklId: Long,
        episode: EpisodeRef?,
    ) {
        val body =
            buildJsonObject {
                putJsonObject(if (type == MediaType.Movie) "movie" else "show") { putJsonObject("ids") { put("simkl", simklId) } }
                if (episode != null) {
                    putJsonObject("episode") {
                        put("season", episode.season ?: 1)
                        put("number", episode.episode)
                    }
                }
            }
        post("/scrobble/checkin", body)
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
    ): JsonElement = get("/search/${type.searchPath}", mapOf("q" to query, "extended" to "full", "page" to "1", "limit" to "50"))

    /**
     * GET /search/id: the SIMKL title with this TMDB ID. TMDB movie and TV IDs overlap, so the
     * kind is passed along ("movie" or "show"; anime series are shows).
     */
    suspend fun searchByTmdb(
        tmdbId: String,
        movie: Boolean,
    ): JsonElement = get("/search/id", mapOf("tmdb" to tmdbId, "type" to if (movie) "movie" else "show"))

    /** A random title (POST /search/random), skipping what the user watched. Returns {"id", "url"}. */
    suspend fun random(type: MediaType): JsonElement = call("POST", "/search/random/", mapOf("service" to "simkl", "type" to type.searchPath))

    /** Any public list endpoint (best, premieres, airing) or data.simkl.in file. */
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
        private const val DATA_HOST = "data.simkl.in"
        const val APP_NAME = "tracker-for-simkl"
        const val AUTHORIZE_URL = "https://simkl.com/oauth2/authorize"

        /** Registered as the redirect URI of the app's SIMKL API app; AuthRedirectActivity receives it. */
        const val REDIRECT_URI = "trackerforsimkl://oauth"
        private const val SCOPE = "media:read media:write"
    }
}
