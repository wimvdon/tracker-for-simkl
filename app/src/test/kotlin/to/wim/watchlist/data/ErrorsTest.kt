package to.wim.watchlist.data

import to.wim.watchlist.data.api.ApiException
import to.wim.watchlist.data.model.UserError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Failed requests are told apart by what the user can do about them, never by their raw message. */
class ErrorsTest {
    @Test
    fun noNetworkIsOffline() {
        assertEquals(UserError.Offline, UserError.of(UnknownHostException("api.simkl.com")))
        assertEquals(UserError.Offline, UserError.of(SocketTimeoutException("timeout")))
        assertEquals(UserError.Offline, UserError.of(IOException("Canceled")))
    }

    @Test
    fun statusCodesAreToldApart() {
        assertEquals(UserError.SignedOut, UserError.of(ApiException(401, "Unauthorized")))
        assertEquals(UserError.SignedOut, UserError.of(ApiException(403, "Forbidden")))
        assertEquals(UserError.NotFound, UserError.of(ApiException(404, "Not found")))
        assertEquals(UserError.Busy, UserError.of(ApiException(429, "Too Many Requests")))
        assertEquals(UserError.ServerDown(503), UserError.of(ApiException(503, "HTTP 503")))
        assertEquals(UserError.Http(418), UserError.of(ApiException(418, "I'm a teapot")))
    }

    @Test
    fun appsOwnChecksKeepTheirDetail() {
        // Code 0 is the app's own ApiException (no client ID, unreadable response), not SIMKL's.
        assertEquals(UserError.Unexpected("This build has no SIMKL client ID"), UserError.of(ApiException(0, "This build has no SIMKL client ID")))
    }

    @Test
    fun anythingElseIsUnexpectedWithItsMessage() {
        assertEquals(UserError.Unexpected("boom"), UserError.of(IllegalStateException("boom")))
        // Without a message the class name is kept, so a crash report still says what it was.
        assertEquals(UserError.Unexpected("NullPointerException"), UserError.of(NullPointerException()))
    }
}
