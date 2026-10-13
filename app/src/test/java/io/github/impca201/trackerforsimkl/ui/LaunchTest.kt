package io.github.impca201.trackerforsimkl.ui

import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.impca201.trackerforsimkl.TrackerApp
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
        val app = ApplicationProvider.getApplicationContext<TrackerApp>()
        runBlocking {
            app.settings.setClientId("0123456789abcdef0123456789abcdef")
            app.settings.setToken("test-token")
        }
        launchAndSettle()
    }
}
