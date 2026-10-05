package top.nkbe.npatch.wrappermanager

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AboutScreen(model: UpdateViewModel, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    val icon = remember { context.packageManager.getApplicationIcon(context.packageName).toBitmap(128, 128) }
    var browserMissing by remember { mutableStateOf(false) }
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            browserMissing = false
        } catch (_: ActivityNotFoundException) {
            browserMissing = true
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(64.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.current_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.app_updates), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = model::checkForUpdates, enabled = state != UpdateState.Checking,
            modifier = Modifier.widthIn(min = 200.dp)) {
            Icon(Icons.Outlined.SystemUpdate, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(if (state == UpdateState.Checking) R.string.checking_updates else R.string.check_updates))
        }
        when (val result = state) {
            UpdateState.Idle -> Text(stringResource(R.string.updates_not_checked), color = MaterialTheme.colorScheme.onSurfaceVariant)
            UpdateState.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is UpdateState.Current -> Text(stringResource(R.string.app_up_to_date))
            is UpdateState.Available -> {
                Text(stringResource(R.string.new_version_available, result.release.version),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { open(result.release.downloadUrl) }) {
                        Icon(Icons.Outlined.GetApp, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.download_update))
                    }
                    TextButton(onClick = { open(result.release.pageUrl) }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.release_details))
                    }
                }
            }
            is UpdateState.Failed -> Text(stringResource(when (result.reason) {
                UpdateFailure.NETWORK -> R.string.update_network_error
                UpdateFailure.RATE_LIMIT -> R.string.update_rate_limit
                UpdateFailure.NO_RELEASE -> R.string.update_no_release
                UpdateFailure.SERVICE -> R.string.update_service_error
                UpdateFailure.INVALID_RELEASE -> R.string.update_invalid_release
            }), color = MaterialTheme.colorScheme.error)
        }
        HorizontalDivider()
        Text(stringResource(R.string.app_project), style = MaterialTheme.typography.titleMedium)
        Text("Matchman33/APKLoom", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { open(RELEASES_URL) }) {
                Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.release_history))
            }
            OutlinedButton(onClick = { open(PROJECT_URL) }) {
                Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.source_repository))
            }
        }
        Text(stringResource(R.string.app_license), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (browserMissing) Text(stringResource(R.string.browser_unavailable), color = MaterialTheme.colorScheme.error)
    }
}
