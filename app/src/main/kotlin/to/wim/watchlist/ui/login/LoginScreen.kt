package to.wim.watchlist.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import to.wim.watchlist.R
import to.wim.watchlist.data.api.Pkce
import to.wim.watchlist.data.api.SignIn
import to.wim.watchlist.data.userMessage
import to.wim.watchlist.work.NewEpisodesWorker
import to.wim.watchlist.ui.components.openUrl
import to.wim.watchlist.ui.components.rememberApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Sign-in with SIMKL's OAuth 2.0 authorization code flow with PKCE: the user approves the app on
 * simkl.com in the browser, which sends them back through AuthRedirectActivity.
 */
@Composable
fun LoginScreen() {
    val app = rememberApp()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var waitingForBrowser by rememberSaveable { mutableStateOf(false) }

    // The redirect may arrive after Android restarted the app, so the state and code verifier
    // are read back from settings.
    LaunchedEffect(Unit) {
        app.authRedirect.collect { uri ->
            if (uri == null) return@collect
            app.authRedirect.value = null
            val pending = app.settings.pendingAuth()
            val code = uri.getQueryParameter("code")
            val denied = uri.getQueryParameter("error")
            error = null
            when {
                denied != null ->
                    error = resources.getString(R.string.screens_login_denied, uri.getQueryParameter("error_description") ?: denied)
                pending == null || code == null || uri.getQueryParameter("state") != pending.first ->
                    error = resources.getString(R.string.screens_login_redirect_invalid)
                else -> {
                    busy = true
                    val result =
                        try {
                            app.api.exchangeCode(code, pending.second)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            SignIn.Failed(e.userMessage(resources))
                        }
                    when (result) {
                        is SignIn.Approved -> {
                            app.settings.setTokens(result.accessToken, result.refreshToken, result.expiresAt)
                            // Signing out cancelled the scheduled check; with notifications still on, start it again.
                            runCatching { NewEpisodesWorker.restore(context) }
                        }
                        is SignIn.Failed -> error = resources.getString(R.string.screens_login_failed, result.message)
                    }
                    busy = false
                }
            }
            app.settings.clearPendingAuth()
            waitingForBrowser = false
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(Icons.Rounded.LiveTv, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.screens_login_tagline),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                error = null
                scope.launch {
                    try {
                        val verifier = Pkce.randomToken()
                        val state = Pkce.randomToken(16)
                        app.settings.setPendingAuth(state, verifier)
                        val url = app.api.authorizeUrl(state, Pkce.challenge(verifier))
                        waitingForBrowser = true
                        context.openUrl(url)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = resources.getString(R.string.screens_login_failed, e.userMessage(resources))
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Rounded.Login, null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.screens_login_sign_in))
            }
        }
        AnimatedVisibility(waitingForBrowser && !busy) {
            Text(
                stringResource(R.string.screens_login_waiting_browser),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.screens_login_note),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
