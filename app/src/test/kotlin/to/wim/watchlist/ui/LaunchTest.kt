package to.wim.watchlist.ui

import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import to.wim.watchlist.WatchlistApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Opens the app like a user does; any exception during start-up or composition fails the test. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LaunchTest {
    private fun launchAndSettle() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // DataStore loads on a background thread; let it emit and the UI recompose.
            repeat(20) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(100)
            }
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }

    @Test
    fun freshInstallShowsLogin() = launchAndSettle()

    @Test
    fun loggedInShowsMainScreen() {
        val app = ApplicationProvider.getApplicationContext<WatchlistApp>()
        runBlocking {
            app.settings.setToken("test-token")
        }
        launchAndSettle()
    }
}
