package io.github.impca201.trackerforsimkl.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.github.impca201.trackerforsimkl.TrackerApp
import io.github.impca201.trackerforsimkl.ui.MainActivity

/**
 * Receives simkl.com's sign-in redirect (trackerforsimkl://oauth?code=…&state=…), hands it to the
 * login screen and brings the app back to the front. Has no UI of its own.
 */
class AuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { (application as TrackerApp).authRedirect.value = it }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
