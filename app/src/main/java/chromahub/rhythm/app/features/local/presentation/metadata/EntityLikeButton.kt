// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.presentation.metadata

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.data.experience.*
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Independent entity preference, deliberately unrelated to the song favourites playlist. */
@Composable
fun EntityLikeButton(kind: TasteKind, entityKey: String, label: String) {
    val context = LocalContext.current
    val memory = remember { TasteMemory.get(context) }
    val revision by memory.changes.collectAsState()
    var state by remember(kind, entityKey) { mutableStateOf<TasteState?>(null) }
    var explanation by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var writeFailed by remember { mutableStateOf(false) }
    val errorHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> writeFailed = true; busy = false }
    // Entry into an album/artist detail is explicit navigation. Recomposition is not exposure.
    LaunchedEffect(kind, entityKey) { state = withContext(Dispatchers.IO) { memory.inspect(kind, entityKey, label) } }
    LaunchedEffect(kind, entityKey, revision) { state = withContext(Dispatchers.IO) { memory.state(kind, entityKey) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (memory.reviewsEnabled && state?.pending == true) TextButton(onClick = { explanation = true }) {
            Text(stringResource(R.string.taste_review_short), style = MaterialTheme.typography.labelSmall)
        }
        IconButton(enabled = !busy, onClick = {
            busy = true
            scope.launch(errorHandler) { try { state = withContext(Dispatchers.IO) { memory.set(kind, entityKey, label, !memory.visibleLiked(state)) } } finally { busy = false } }
        }) {
            Icon(MaterialSymbolIcon("favorite", filled = memory.visibleLiked(state)),
                stringResource(if (memory.visibleLiked(state)) R.string.taste_unlike_entity else R.string.taste_like_entity, label), modifier = Modifier.size(24.dp),
                tint = if (memory.visibleLiked(state)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (writeFailed) AlertDialog(onDismissRequest = { writeFailed = false },
        text = { Text(stringResource(R.string.adaptive_operation_failed)) },
        confirmButton = { TextButton(onClick = { writeFailed = false }) { Text(stringResource(android.R.string.ok)) } })
    if (explanation) AlertDialog(onDismissRequest = { explanation = false },
        title = { Text(stringResource(R.string.taste_review_title)) },
        text = { Text(stringResource(R.string.taste_review_explanation)) },
        confirmButton = { TextButton(onClick = { explanation = false }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = {
            explanation = false
            scope.launch(errorHandler) { state = withContext(Dispatchers.IO) { memory.set(kind, entityKey, label, false) } }
        }) { Text(stringResource(R.string.taste_unlike_permanently)) } })
}
