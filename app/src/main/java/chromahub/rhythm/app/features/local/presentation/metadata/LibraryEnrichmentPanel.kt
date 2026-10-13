// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.presentation.metadata

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.data.metadata.*
import chromahub.rhythm.app.features.local.di.LocalMusicModule
import chromahub.rhythm.app.infrastructure.worker.LibraryEnrichmentWorker
import chromahub.rhythm.app.infrastructure.worker.CatalogWriteWorker
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.core.domain.scan.LibraryFilterPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.metadata.crawler.Provider

@Composable
fun LibraryEnrichmentPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalog = remember { CatalogEnrichmentService.get(context) }
    val options by catalog.preferences.options.collectAsState()
    val settings = remember { AppSettings.getInstance(context) }
    val integrations by settings.integrationsEnabled.collectAsState()
    val workManager = remember { WorkManager.getInstance(context) }
    val jobs by remember { workManager.getWorkInfosForUniqueWorkFlow(LibraryEnrichmentWorker.WORK_NAME) }.collectAsState(emptyList())
    val active = jobs.any { !it.state.isFinished }
    val changes by catalog.changes.collectAsState()
    var advanced by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var plan by remember { mutableStateOf<CatalogBatchPlan?>(null) }
    var pending by remember { mutableStateOf(0) }
    var grants by remember { mutableStateOf(emptyList<android.content.IntentSender>()) }
    var afterGrant by remember { mutableStateOf<(() -> Unit)?>(null) }
    val progress = jobs.firstOrNull { !it.state.isFinished }?.progress
    LaunchedEffect(jobs, changes) {
        withContext(Dispatchers.IO) {
            val snapshot = CatalogBatchPlan.read(context)
            val count = (AutomaticLyricsStore.get(context).pending() + CatalogTagWrites(context).pending()).distinctBy { it.uri }.size
            withContext(Dispatchers.Main) { plan = snapshot; pending = count }
        }
    }
    lateinit var requestNextGrant: () -> Unit
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            grants = grants.drop(1)
            requestNextGrant()
        } else {
            grants = emptyList(); busy = false
            message = context.getString(R.string.catalog_permission_denied)
            val next = afterGrant; afterGrant = null; next?.invoke()
        }
    }
    requestNextGrant = {
        if (grants.isEmpty()) { busy = false; val next = afterGrant; afterGrant = null; next?.invoke() }
        else {
            runCatching { permission.launch(IntentSenderRequest.Builder(grants.first()).build()) }
                .onFailure { busy = false; afterGrant = null; message = context.getString(R.string.catalog_permission_error) }
        }
    }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) message = context.getString(R.string.catalog_permission_denied)
        requestNextGrant()
    }
    fun continueWithConsent() {
        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            legacyPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else requestNextGrant()
    }
    fun begin() {
        busy = true; message = null
        scope.launch {
            try {
                val songs = withContext(Dispatchers.IO) {
                    val policy = LibraryFilterPolicy(settings.mediaScanMode.value, settings.blacklistedSongs.value, settings.blacklistedFolders.value,
                        settings.whitelistedSongs.value, settings.whitelistedFolders.value)
                    LocalMusicModule.provideMusicRepository(context).loadSongs().filter { policy.includes(it.id, it.path ?: it.uri.path) }
                }
                if (songs.isEmpty()) { message = context.getString(R.string.catalog_empty_library); busy = false; return@launch }
                withContext(Dispatchers.IO) {
                    val existing = CatalogBatchPlan.read(context)
                    if (existing == null || existing.next >= existing.keys.size) CatalogBatchPlan.create(context, songs)
                }
                afterGrant = { LibraryEnrichmentWorker.enqueue(context) }
                grants = if (options.embedLyrics || options.writeTags) CatalogAudioPermissions.requests(context, songs) else emptyList()
                if (options.embedLyrics || options.writeTags) continueWithConsent() else requestNextGrant()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { busy = false; message = context.getString(R.string.catalog_error) }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) scope.launch {
            busy = true
            try {
                context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                catalog.preferences.update(options.copy(exportTree = tree.toString()))
                val archive = PersonalArchive(context); archive.selectTree(tree); archive.sync()
                message = context.getString(R.string.personal_archive_synced)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { message = context.getString(R.string.catalog_export_error) }
            finally { busy = false }
        }
    }
    @Composable fun toggle(label: Int, value: Boolean, update: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = value, enabled = !active && !busy, onCheckedChange = update)
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.catalog_batch_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.catalog_batch_hint), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { advanced = !advanced }) { Text(stringResource(R.string.catalog_options_expand)) }
            if (advanced) {
            toggle(R.string.catalog_replace_all, options.replaceTags) { catalog.preferences.update(options.copy(replaceTags = it)) }
            Text(stringResource(R.string.catalog_force_fields_hint), style = MaterialTheme.typography.bodySmall)
            listOf(kr.co.metadata.crawler.CatalogField.TRACK_TITLE, kr.co.metadata.crawler.CatalogField.TRACK_ARTISTS, kr.co.metadata.crawler.CatalogField.TRACK_ALBUM_TITLE, kr.co.metadata.crawler.CatalogField.TRACK_GENRES, kr.co.metadata.crawler.CatalogField.TRACK_RELEASE_DATE, kr.co.metadata.crawler.CatalogField.TRACK_IMAGE, kr.co.metadata.crawler.CatalogField.TRACK_LYRICS).forEach { field ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(field.label(context), Modifier.weight(1f))
                    Checkbox(checked = field in options.forceFields, enabled = !active && !busy && !options.replaceTags, onCheckedChange = { selected -> catalog.preferences.update(options.copy(forceFields = if (selected) options.forceFields + field else options.forceFields - field)) })
                }
            }
            toggle(R.string.catalog_strict_duration, options.strictDuration) { catalog.preferences.update(options.copy(strictDuration = it)) }
            Text(context.getString(R.string.catalog_duration_tolerance, options.durationTolerancePercent), style = MaterialTheme.typography.bodyMedium)
            var tolerance by remember(options.durationTolerancePercent) { mutableFloatStateOf(options.durationTolerancePercent.toFloat()) }
            Slider(value = tolerance, valueRange = 0f..30f, enabled = !active && !busy, onValueChange = { tolerance = it }, onValueChangeFinished = { catalog.preferences.update(options.copy(durationTolerancePercent = tolerance.toDouble())) })
            toggle(R.string.catalog_enabled, options.enabled) { catalog.preferences.update(options.copy(enabled = it)) }
            toggle(R.string.catalog_permanent, options.permanent) { enabled -> scope.launch { catalog.setPermanent(enabled) } }
            toggle(R.string.catalog_write_tags, options.writeTags) { catalog.preferences.update(options.copy(writeTags = it)) }
            toggle(R.string.catalog_auto_lyrics, options.autoLyrics) { catalog.preferences.update(options.copy(autoLyrics = it)) }
            toggle(R.string.catalog_embed_lyrics, options.embedLyrics) { catalog.preferences.update(options.copy(embedLyrics = it)) }
            toggle(R.string.catalog_wifi_only, options.wifiOnly) { catalog.preferences.update(options.copy(wifiOnly = it)) }
            toggle(R.string.catalog_metadata_fields, options.metadata) { catalog.preferences.update(options.copy(metadata = it)) }
            toggle(R.string.catalog_artwork_fields, options.artwork) { catalog.preferences.update(options.copy(artwork = it)) }
            toggle(R.string.catalog_profile_fields, options.profiles) { catalog.preferences.update(options.copy(profiles = it)) }
            toggle(R.string.catalog_lyrics_fields, options.lyrics) { catalog.preferences.update(options.copy(lyrics = it)) }
            Text(stringResource(R.string.catalog_lyrics_follow_settings), style = MaterialTheme.typography.bodySmall)
            }
            if (!integrations) Text(stringResource(R.string.catalog_integrations_off), color = MaterialTheme.colorScheme.error)
            plan?.let { snapshot ->
                val done = progress?.getInt("done", snapshot.next) ?: snapshot.next
                Text(context.getString(R.string.catalog_batch_progress, done, snapshot.keys.size, snapshot.changed, snapshot.skipped, snapshot.failed))
                if (active) LinearProgressIndicator(progress = { if (snapshot.keys.isEmpty()) 0f else done.toFloat() / snapshot.keys.size }, modifier = Modifier.fillMaxWidth())
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Button(enabled = !active && !busy && integrations && options.enabled, onClick = ::begin, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (plan?.let { it.next < it.keys.size } == true) R.string.catalog_resume else R.string.catalog_batch_start))
            }
            if (active) OutlinedButton(onClick = { workManager.cancelUniqueWork(LibraryEnrichmentWorker.WORK_NAME) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.catalog_pause))
            }
            if (pending > 0) OutlinedButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try {
                        val songs = withContext(Dispatchers.IO) { (AutomaticLyricsStore.get(context).pending() + CatalogTagWrites(context).pending()).distinctBy { it.uri } }
                        afterGrant = { CatalogWriteWorker.enqueue(context) }
                        grants = CatalogAudioPermissions.requests(context, songs)
                        continueWithConsent()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { busy = false; afterGrant = null; message = context.getString(R.string.catalog_permission_error) }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(context.getString(R.string.catalog_pending_writes, pending)) }

        }
    }
}
