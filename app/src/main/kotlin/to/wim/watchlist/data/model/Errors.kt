package to.wim.watchlist.data.model

import to.wim.watchlist.data.api.ApiException
import java.io.IOException

/**
 * What went wrong with a request, in terms a screen can put into the user's language (see
 * `userMessage` in the data package). Raw exception messages ("Unable to resolve host …",
 * "HTTP 503") never reach the screen. Plain Kotlin so it is unit tested.
 */
sealed interface UserError {
    /** SIMKL or TMDB was never reached: no network, a DNS failure or a timeout. */
    data object Offline : UserError

    /** HTTP 401 or 403: the sign-in no longer works. */
    data object SignedOut : UserError

    /** HTTP 404: the title or person is gone. */
    data object NotFound : UserError

    /** HTTP 429: too many requests; a moment later it works again. */
    data object Busy : UserError

    /** HTTP 5xx: the server has a problem of its own. */
    data class ServerDown(
        val code: Int,
    ) : UserError

    /** Any other HTTP error. */
    data class Http(
        val code: Int,
    ) : UserError

    /** Anything else; [detail] is the technical message, for the crash report and support. */
    data class Unexpected(
        val detail: String?,
    ) : UserError

    companion object {
        fun of(e: Throwable): UserError =
            when (e) {
                // Before IOException: ApiException is one, but carries a status code.
                is ApiException ->
                    when {
                        e.unauthorized -> SignedOut
                        e.code == 404 -> NotFound
                        e.code == 429 -> Busy
                        e.code in 500..599 -> ServerDown(e.code)
                        // Code 0: the app's own checks (no client ID, unreadable response).
                        e.code == 0 -> Unexpected(e.message)
                        else -> Http(e.code)
                    }
                is IOException -> Offline
                else -> Unexpected(e.message ?: e.javaClass.simpleName)
            }
    }
}
