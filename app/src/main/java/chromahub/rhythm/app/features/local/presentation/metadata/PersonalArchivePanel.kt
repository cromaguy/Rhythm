// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.presentation.metadata

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.data.metadata.PersonalArchive
import kotlinx.coroutines.launch

@Composable
fun PersonalArchivePanel() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val archive = remember { PersonalArchive(context) }
    var selected by remember { mutableStateOf(archive.tree) }
    var status by remember { mutableStateOf(archive.status) }
    DisposableEffect(archive) {
        val prefs = context.getSharedPreferences("personal_archive", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "status" || key == "lastSync") status = archive.status
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                archive.selectTree(tree); selected = tree.toString(); status = null
            }.onFailure { status = "folder_required" }
        }
    }
    var restoreConfirm by remember { mutableStateOf(false) }
    var restoreMessage by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.personal_archive_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.personal_archive_hint), style = MaterialTheme.typography.bodySmall)
            Text(selected?.let { Uri.decode(it.substringAfterLast('/')) } ?: "Documents/RhythmBackups/T2Rhythm", style = MaterialTheme.typography.labelMedium)
            if (status != null) Text(stringResource(R.string.personal_archive_permission), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { picker.launch(selected?.let(Uri::parse) ?: Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FRhythmBackups")) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.personal_archive_choose))
            }
            OutlinedButton(enabled = selected != null && !syncing, onClick = { restoreConfirm = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.personal_archive_restore)) }
            restoreMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            TextButton(enabled = !syncing, onClick = { scope.launch {
                syncing = true
                try { archive.sync(force = true); status = null } catch (_: Exception) { status = "folder_required" }
                finally { syncing = false }
            } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.personal_archive_sync)) }
        }
    }
    if (restoreConfirm) AlertDialog(onDismissRequest = { restoreConfirm = false }, title = { Text(stringResource(R.string.personal_archive_restore)) },
        text = { Text(stringResource(R.string.personal_archive_restore_hint)) }, confirmButton = { TextButton(onClick = {
            restoreConfirm = false; scope.launch {
                syncing = true
                try { val count = archive.restore(); restoreMessage = context.getString(R.string.personal_archive_restored, count) }
                catch (_: Exception) { status = "folder_required" }
                finally { syncing = false }
            }
        }) { Text(stringResource(android.R.string.ok)) } }, dismissButton = { TextButton(onClick = { restoreConfirm = false }) { Text(stringResource(android.R.string.cancel)) } })
}
