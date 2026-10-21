package io.github.impca201.trackerforsimkl.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.impca201.trackerforsimkl.R
import io.github.impca201.trackerforsimkl.data.api.TmdbApi
import io.github.impca201.trackerforsimkl.data.api.TmdbKey
import io.github.impca201.trackerforsimkl.data.api.TmdbKeyCheck
import io.github.impca201.trackerforsimkl.ui.components.openUrl
import io.github.impca201.trackerforsimkl.ui.components.rememberApp
import kotlinx.coroutines.launch

/** Why the dialog didn't save what was pasted. */
private enum class TokenProblem { ApiKey, NotToken, Rejected, Unreachable }

/**
 * Enter, change or remove the user's own TMDB Read Access Token. It is checked with TMDB before
 * it is saved, so a typo shows here instead of as missing cast and crew later.
 */
@Composable
fun TmdbKeyDialog(
    current: String?,
    onDismiss: () -> Unit,
) {
    val app = rememberApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A saved API Key (accepted by version 1.6.0) isn't shown: it has to be replaced by the token.
    var text by rememberSaveable { mutableStateOf(TmdbKey.parse(current)?.value.orEmpty()) }
    var checking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<TokenProblem?>(null) }

    fun save() {
        val key = TmdbKey.parse(text)
        if (key == null) {
            problem = if (TmdbKey.isApiKey(text)) TokenProblem.ApiKey else TokenProblem.NotToken
            return
        }
        checking = true
        problem = null
        scope.launch {
            val result = app.tmdb.check(key)
            checking = false
            if (result == TmdbKeyCheck.Valid) {
                app.settings.setTmdbKey(key.value)
                onDismiss()
            } else {
                problem = if (result == TmdbKeyCheck.Rejected) TokenProblem.Rejected else TokenProblem.Unreachable
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.screens_settings_tmdb_key)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.screens_tmdb_dialog_text), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { context.openUrl(TmdbApi.KEY_PAGE) }) { Text(stringResource(R.string.screens_tmdb_get_key)) }
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        problem = null
                    },
                    label = { Text(stringResource(R.string.screens_tmdb_key_label)) },
                    singleLine = true,
                    isError = problem != null,
                    supportingText =
                        problem?.let {
                            {
                                Text(
                                    stringResource(
                                        when (it) {
                                            TokenProblem.ApiKey -> R.string.screens_tmdb_api_key
                                            TokenProblem.NotToken -> R.string.screens_tmdb_not_token
                                            TokenProblem.Rejected -> R.string.screens_tmdb_rejected
                                            TokenProblem.Unreachable -> R.string.screens_tmdb_unreachable
                                        },
                                    ),
                                )
                            }
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Row {
                if (current != null) {
                    TextButton(
                        onClick = {
                            scope.launch { app.settings.setTmdbKey(null) }
                            onDismiss()
                        },
                        enabled = !checking,
                    ) { Text(stringResource(R.string.screens_tmdb_remove)) }
                }
                TextButton(onClick = ::save, enabled = !checking && text.isNotBlank()) {
                    if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.screens_tmdb_save))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.screens_cancel)) } },
    )
}
