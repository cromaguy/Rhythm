// SPDX-License-Identifier: GPL-3.0-or-later

package chromahub.rhythm.app.features.local.presentation.metadata

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.data.metadata.CatalogImportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.metadata.crawler.*
import java.io.File

/** Import only explicitly checked fields into the editor; the editor retains its Save action. */
@Composable
fun CatalogLookupDialog(
    initialQuery: String,
    lyricsOnly: Boolean = false,
    onDismiss: () -> Unit,
    onApply: (CatalogDocument, Uri?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { CatalogImportRepository() }
    var query by remember { mutableStateOf(initialQuery) }
    var kind by remember { mutableStateOf(EntityKind.TRACK) }
    var provider by remember { mutableStateOf(Provider.BUGS_MOBILE) }
    var hits by remember { mutableStateOf(emptyList<SearchHit>()) }
    var document by remember { mutableStateOf<CatalogDocument?>(null) }
    var selected by remember { mutableStateOf(emptySet<CatalogField>()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }

    fun selectable(field: CatalogField) = if (lyricsOnly) field == CatalogField.TRACK_LYRICS else field !in setOf(
        CatalogField.TRACK_LYRICS, CatalogField.ARTIST_DESCRIPTION,
        CatalogField.ARTIST_DEBUT_DATE, CatalogField.ARTIST_IMAGE,
    )

    fun preview(result: CatalogDocument?) {
        document = result
        selected = result?.values?.filter { selectable(it.field) }?.map { it.field }?.toSet().orEmpty()
        if (result == null || result.values.isEmpty()) message = context.getString(R.string.catalog_no_metadata)
        else if (lyricsOnly && result[CatalogField.TRACK_LYRICS].isNullOrBlank()) {
            message = context.getString(R.string.catalog_no_lyrics)
        }
    }

    fun runOperation(action: suspend () -> Unit) {
        job?.cancel()
        val current = ++generation
        busy = true
        message = null
        job = scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = context.getString(R.string.catalog_error) }
            finally { if (generation == current) busy = false }
        }
    }

    val studio = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val name = result.data?.getStringExtra(SelectorStudioActivity.EXTRA_RESULT_FILE)
            if (name != null && name.matches(Regex("catalog-result-[a-f0-9-]+\\.json"))) {
                runOperation {
                    val imported = withContext(Dispatchers.IO) {
                        val file = File(context.cacheDir, name)
                        try {
                            require(file.length() <= 1_000_000)
                            CatalogDocumentCodec.decode(file.readText())
                        } finally { file.delete() }
                    }
                    preview(imported)
                }
            }
        }
    }

    Dialog(onDismissRequest = { ++generation; job?.cancel(); onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (lyricsOnly) R.string.catalog_find_lyrics else R.string.catalog_find_metadata), style = MaterialTheme.typography.titleLarge)
                if (!lyricsOnly) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(EntityKind.TRACK, EntityKind.ALBUM, EntityKind.ARTIST).forEach { option ->
                        FilterChip(selected = kind == option, enabled = !busy,
                            onClick = { kind = option; document = null; hits = emptyList(); message = null },
                            label = { Text(option.label(context)) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Provider.BUGS_MOBILE, Provider.GENIE, Provider.MELON).forEach { option ->
                        FilterChip(selected = provider == option, enabled = !busy,
                            onClick = { provider = option; document = null; hits = emptyList(); message = null },
                            label = { Text(when (option) {
                                Provider.BUGS_MOBILE -> "Bugs"
                                Provider.GENIE -> "Genie"
                                else -> "Melon"
                            }) })
                    }
                }
                OutlinedTextField(value = query, enabled = !busy, onValueChange = { query = it },
                    label = { Text(stringResource(R.string.catalog_query)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && query.isNotBlank(), onClick = {
                        runOperation {
                            document = null
                            hits = repository.search(provider, kind, query)
                            if (hits.isEmpty()) message = context.getString(R.string.catalog_no_results)
                        }
                    }) { Text(stringResource(R.string.catalog_search)) }
                    OutlinedButton(enabled = !busy, onClick = {
                        studio.launch(Intent(context, SelectorStudioActivity::class.java)
                            .putExtra(SelectorStudioActivity.EXTRA_KIND, kind.name))
                    }) { Text(stringResource(R.string.catalog_custom_site)) }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val current = document
                    if (current == null) items(hits, key = { "${it.provider}:${it.providerId}" }) { hit ->
                        ListItem(headlineContent = { Text(hit.title) }, supportingContent = { Text(listOfNotNull(hit.artists, hit.detailUrl).joinToString("\n")) },
                            modifier = Modifier.clickable(enabled = !busy) { runOperation { preview(repository.load(hit)) } })
                    }
                    else {
                        item {
                            Text(stringResource(R.string.catalog_preview_hint), style = MaterialTheme.typography.bodySmall)
                            TextButton(enabled = !busy, onClick = { document = null; message = null }) {
                                Text(stringResource(R.string.catalog_results))
                            }
                        }
                        items(current.values, key = { it.field }) { value ->
                            Row(Modifier.fillMaxWidth()) {
                                if (selectable(value.field)) Checkbox(checked = value.field in selected, enabled = !busy,
                                    onCheckedChange = { checked -> selected = if (checked) selected + value.field else selected - value.field })
                                Column(Modifier.weight(1f)) {
                                    Text(value.field.label(context), style = MaterialTheme.typography.labelLarge)
                                    Text(value.value.take(if (value.field == CatalogField.TRACK_LYRICS) 1200 else 500), style = MaterialTheme.typography.bodyMedium)
                                    Text(value.sourceUrl, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        ++generation
                        job?.cancel()
                        onDismiss()
                    }) { Text(stringResource(R.string.catalog_close)) }
                    Button(enabled = !busy && selected.isNotEmpty(), onClick = {
                        val filtered = document?.let { it.copy(values = it.values.filter { value -> value.field in selected }) }
                            ?: return@Button
                        runOperation {
                            val image = filtered.values.firstOrNull { it.field.image }
                            val artwork = image?.let { repository.artwork(context, it.value) }
                            onApply(filtered, artwork)
                        }
                    }) { Text(stringResource(R.string.catalog_apply)) }
                }
            }
        }
    }
}
