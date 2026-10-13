package io.github.impca201.trackerforsimkl.ui.login

import androidx.compose.animation.AnimatedVisibility
import io.github.impca201.trackerforsimkl.data.api.Pkce
import kotlinx.coroutines.CancellationException
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.impca201.trackerforsimkl.R
import io.github.impca201.trackerforsimkl.data.AppSettings
import io.github.impca201.trackerforsimkl.data.api.LoginCode
import io.github.impca201.trackerforsimkl.data.api.LoginPoll
import io.github.impca201.trackerforsimkl.data.api.SimklApi
import io.github.impca201.trackerforsimkl.ui.components.openUrl
import io.github.impca201.trackerforsimkl.ui.components.rememberApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * First run: (1) the SIMKL client ID of the user's own API app, (2) the PIN login, where the user
 * approves the code on simkl.com. The PIN flow needs no client secret.
 */
@Composable
fun LoginScreen(settings: AppSettings) {
    val app = rememberApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editingClientId by rememberSaveable { mutableStateOf(settings.clientId.isBlank()) }
    var clientId by rememberSaveable { mutableStateOf(settings.clientId) }
    var pin by remember { mutableStateOf<LoginCode?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showGuide by rememberSaveable { mutableStateOf(false) }

    var waitingForBrowser by rememberSaveable { mutableStateOf(false) }

    // The browser sign-in comes back through AuthRedirectActivity, possibly after Android
    // restarted the app, so the state and code verifier are read back from settings.
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
                    error = context.getString(R.string.screens_login_denied, uri.getQueryParameter("error_description") ?: denied)
                pending == null || code == null || uri.getQueryParameter("state") != pending.first ->
                    error = context.getString(R.string.screens_login_redirect_invalid)
                else -> {
                    busy = true
                    when (val result = runCatching { app.api.exchangeCode(code, pending.second) }.getOrElse { LoginPoll.Failed(it.message.orEmpty()) }) {
                        is LoginPoll.Approved -> app.settings.setTokens(result.accessToken, result.refreshToken, result.expiresAt)
                        is LoginPoll.Failed -> error = context.getString(R.string.screens_login_start_failed, result.message)
                        else -> Unit
                    }
                    busy = false
                }
            }
            app.settings.clearPendingAuth()
            waitingForBrowser = false
        }
    }

    // Code sign-in: poll until the user approved the code (or it expired).
    LaunchedEffect(pin) {
        val code = pin ?: return@LaunchedEffect
        val deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L
        var interval = code.intervalSeconds.coerceAtLeast(1)
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            when (val status = runCatching { app.api.pollLogin(code) }.getOrNull() ?: continue) {
                LoginPoll.Pending -> Unit
                LoginPoll.SlowDown -> interval += 5
                LoginPoll.Expired -> break
                is LoginPoll.Approved -> {
                    app.settings.setTokens(status.accessToken, status.refreshToken, status.expiresAt)
                    return@LaunchedEffect
                }
                is LoginPoll.Failed -> {
                    pin = null
                    error = status.message
                    return@LaunchedEffect
                }
            }
        }
        pin = null
        error = context.getString(R.string.screens_login_code_expired)
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Rounded.LiveTv, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.screens_login_tagline),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        if (editingClientId) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.screens_login_api_key_title), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        stringResource(R.string.screens_login_api_key_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { showGuide = true }) {
                            Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.guide_open))
                        }
                        OutlinedButton(onClick = { context.openUrl(SimklApi.DEVELOPER_URL) }) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.create_simkl_app), Modifier.size(18.dp))
                        }
                    }
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it.trim() },
                        label = { Text(stringResource(R.string.screens_client_id)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                app.settings.setClientId(clientId)
                                editingClientId = false
                                error = null
                            }
                        },
                        enabled = clientId.length >= 16,
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(stringResource(R.string.screens_login_continue)) }
                }
            }
        } else {
            val code = pin
            if (code == null) {
                Button(
                    onClick = {
                        error = null
                        scope.launch {
                            try {
                                // Mobile sign-in: OAuth 2.0 authorization code with PKCE in the browser.
                                val verifier = Pkce.randomToken()
                                val state = Pkce.randomToken(16)
                                app.settings.setPendingAuth(state, verifier)
                                val url = app.api.authorizeUrl(state, Pkce.challenge(verifier))
                                waitingForBrowser = true
                                context.openUrl(url)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = context.getString(R.string.screens_login_start_failed, e.message.orEmpty())
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
                // For SIMKL apps of the "TV, devices & command line" type, and older AUTH V1 apps.
                TextButton(
                    onClick = {
                        busy = true
                        error = null
                        waitingForBrowser = false
                        scope.launch {
                            try {
                                pin = app.api.startLogin()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = context.getString(R.string.screens_login_start_failed, e.message.orEmpty())
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                ) { Text(stringResource(R.string.screens_login_with_code)) }
            } else {
                PinCard(code)
            }
            Row {
                TextButton(onClick = {
                    pin = null
                    editingClientId = true
                }) { Text(stringResource(R.string.screens_login_change_api_key)) }
                TextButton(onClick = { showGuide = true }) { Text(stringResource(R.string.guide_title)) }
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
    if (showGuide) AuthGuideDialog { showGuide = false }
}

@Composable
private fun PinCard(code: LoginCode) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.screens_login_enter_code), style = MaterialTheme.typography.titleMedium)
            Text(
                code.userCode,
                fontFamily = FontFamily.Monospace,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("SIMKL code", code.userCode))
                }) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.screens_login_copy))
                }
                Button(onClick = { context.openUrl(code.openUrl) }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.screens_login_open_simkl))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.screens_login_waiting), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
