package io.github.impca201.simklr.ui.login

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.impca201.simklr.data.AppSettings
import io.github.impca201.simklr.data.api.PinCode
import io.github.impca201.simklr.data.api.SimklApi
import io.github.impca201.simklr.ui.components.openUrl
import io.github.impca201.simklr.ui.components.rememberApp
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
    var pin by remember { mutableStateOf<PinCode?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Poll until the user approved the code (or it expired).
    LaunchedEffect(pin) {
        val code = pin ?: return@LaunchedEffect
        val deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(code.intervalSeconds.coerceAtLeast(2) * 1000L)
            val token = runCatching { app.api.pollPin(code.userCode) }.getOrNull()
            if (token != null) {
                app.settings.setToken(token)
                return@LaunchedEffect
            }
        }
        pin = null
        error = "The code expired. Try again."
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Rounded.LiveTv, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text("SIMKLr", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(
            "Track your TV shows, anime and movies with SIMKL.",
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
                        Text("Your SIMKL API key", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "SIMKLr talks to SIMKL with your own free API app. Create one on SIMKL " +
                            "(any name; redirect URI: urn:ietf:wg:oauth:2.0:oob), then paste its Client ID here.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { context.openUrl(SimklApi.DEVELOPER_URL) }) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Create a SIMKL app")
                    }
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it.trim() },
                        label = { Text("Client ID") },
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
                    ) { Text("Continue") }
                }
            }
        } else {
            val code = pin
            if (code == null) {
                Button(
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                pin = app.api.requestPin()
                            } catch (e: Exception) {
                                error = "Could not start the login: ${e.message}. Check the Client ID."
                            }
                            busy = false
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
                        Text("Sign in with SIMKL")
                    }
                }
            } else {
                PinCard(code)
            }
            TextButton(onClick = {
                pin = null
                editingClientId = true
            }) { Text("Change API key") }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PinCard(code: PinCode) {
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
            Text("Enter this code on SIMKL", style = MaterialTheme.typography.titleMedium)
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
                    Text("Copy")
                }
                Button(onClick = { context.openUrl(code.verificationUrl.trimEnd('/') + "/" + code.userCode) }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Open SIMKL")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Waiting for approval…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
