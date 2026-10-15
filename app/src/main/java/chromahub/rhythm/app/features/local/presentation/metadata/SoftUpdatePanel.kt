// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.presentation.metadata

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.data.experience.*
import kotlinx.coroutines.launch

@Composable fun SoftUpdatePanel() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(ExperienceProfile.read(context).version) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.soft_update_current, version), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(enabled = !busy, onClick = {
            busy = true
            scope.launch { try {
                ExperienceRulesWorker.checkNow(context)
                version = ExperienceProfile.read(context).version; result = R.string.soft_update_done
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { result = R.string.soft_update_error }
            finally { busy = false } }
        }) { Text(stringResource(R.string.soft_update_check)) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(stringResource(result ?: R.string.personal_native_updates_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
