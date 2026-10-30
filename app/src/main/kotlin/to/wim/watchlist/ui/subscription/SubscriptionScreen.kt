package to.wim.watchlist.ui.subscription

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import to.wim.watchlist.R
import to.wim.watchlist.data.SubscriptionRepository
import to.wim.watchlist.data.model.PlayStatus
import to.wim.watchlist.ui.components.AppIcon
import to.wim.watchlist.ui.components.openUrl
import to.wim.watchlist.ui.components.rememberApp
import kotlinx.coroutines.launch

/**
 * Shown instead of the app while there is no active subscription: what it costs (the free trial
 * first, when Google Play still offers it), a button to Google Play's purchase sheet, and the
 * terms Google Play requires an app to state (when it charges, that it renews, how to cancel).
 */
@Composable
fun SubscriptionScreen() {
    val app = rememberApp()
    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by app.subscription.state.collectAsState()
    val offer = state.offer

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        AppIcon(Modifier.size(88.dp))
        Text(
            stringResource(if (offer?.trialDays != null) R.string.sub_title_trial else R.string.sub_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Feature(Icons.Rounded.CheckCircle, stringResource(R.string.sub_feature_up_next))
            Feature(Icons.Rounded.CalendarMonth, stringResource(R.string.sub_feature_calendar))
            Feature(Icons.Rounded.Notifications, stringResource(R.string.sub_feature_notifications))
        }
        Spacer(Modifier.height(8.dp))
        when {
            state.status == PlayStatus.Pending -> Note(stringResource(R.string.sub_pending))
            state.status == PlayStatus.Unavailable -> Note(stringResource(R.string.sub_unavailable))
            offer == null && state.status == PlayStatus.Inactive -> Note(stringResource(R.string.sub_not_loaded))
            offer == null -> CircularProgressIndicator()
            else -> {
                Text(
                    offer.trialDays?.let { pluralStringResource(R.plurals.sub_price_trial, it, it, offer.yearlyPrice) }
                        ?: stringResource(R.string.sub_price, offer.yearlyPrice),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { activity?.let { app.subscription.purchase(it, offer) } },
                    enabled = !state.purchasing,
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().height(56.dp),
                ) {
                    if (state.purchasing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(if (offer.trialDays != null) R.string.sub_start_trial else R.string.sub_subscribe))
                    }
                }
                if (state.purchaseFailed) {
                    Text(stringResource(R.string.sub_failed), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                Text(
                    stringResource(if (offer.trialDays != null) R.string.sub_terms_trial else R.string.sub_terms),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        OutlinedButton(onClick = { scope.launch { app.subscription.refresh() } }) { Text(stringResource(R.string.sub_check_again)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { context.openUrl(SubscriptionRepository.manageUrl(context.packageName)) }) {
                Text(stringResource(R.string.sub_manage))
            }
            TextButton(onClick = { scope.launch { app.logout() } }) { Text(stringResource(R.string.screens_profile_log_out)) }
        }
    }
}

@Composable
private fun Feature(
    icon: ImageVector,
    text: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
}
