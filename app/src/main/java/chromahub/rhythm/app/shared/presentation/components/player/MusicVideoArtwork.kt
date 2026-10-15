// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.shared.presentation.components.player

import android.content.Intent
import android.view.TextureView
import android.view.ViewGroup
import android.view.Gravity
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.data.experience.RecommendationLearning
import chromahub.rhythm.app.features.local.data.video.*
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Stable
class MusicVideoControls {
    val active = mutableStateOf(false)
    val managing = mutableStateOf(false)
    val feedback = mutableStateOf(false)
    val video = mutableStateOf<MusicVideo?>(null)
    val error = mutableStateOf<String?>(null)
    val loading = mutableStateOf(false)
    val retry = mutableIntStateOf(0)
    val hasVideo: Boolean get() = video.value != null
    fun toggle() { if (hasVideo) active.value = !active.value else managing.value = true }
}

@Composable
fun MusicVideoToggle(controls: MusicVideoControls, foreground: Color = MaterialTheme.colorScheme.onSurface) {
    val loadingLabel = stringResource(R.string.mv_loading)
    val colors = SegmentedButtonDefaults.colors(inactiveContainerColor = Color.Transparent, inactiveContentColor = foreground,
        inactiveBorderColor = foreground.copy(alpha = .5f), activeBorderColor = foreground.copy(alpha = .5f))
    SingleChoiceSegmentedButtonRow {
        SegmentedButton(selected = !controls.active.value, onClick = { controls.active.value = false }, shape = SegmentedButtonDefaults.itemShape(0, 2), colors = colors) {
            Text(stringResource(R.string.personal_audio))
        }
        SegmentedButton(selected = controls.active.value, onClick = { controls.toggle() }, shape = SegmentedButtonDefaults.itemShape(1, 2), colors = colors,
            modifier = Modifier.semantics { if (controls.active.value && controls.loading.value) stateDescription = loadingLabel },
            icon = {
                if (controls.active.value && controls.loading.value) CircularProgressIndicator(Modifier.size(18.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                else Icon(MaterialSymbolIcon("smart_display"), null, Modifier.size(18.dp))
            }) {
            Text(stringResource(R.string.personal_video))
        }
    }
}

/** Rendering can disappear for lyrics/backdrop layouts; session state and dialogs must not. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable
fun MusicVideoArtwork(song: Song, isPlaying: Boolean, positionMs: () -> Long, modifier: Modifier = Modifier, onActiveChange: (Boolean) -> Unit = {}, controls: MusicVideoControls? = null) {
    val currentControls = controls ?: remember(song.uri) { MusicVideoControls() }
    if (controls == null) MusicVideoSession(song, currentControls)
    val context = LocalContext.current
    val repository = remember(context) { MusicVideoRepository(context) }
    val video = currentControls.video.value
    val active = currentControls.active.value
    var loading by currentControls.loading
    val attempt = currentControls.retry.intValue
    LaunchedEffect(active, video?.source, video?.videoId, video?.fileName, attempt) {
        onActiveChange(active && video != null)
        currentControls.error.value = null
        loading = active && video != null
    }
    LaunchedEffect(active, video, attempt, loading) {
        if (active && video != null && loading) {
            delay(25_000)
            if (loading) { loading = false; currentControls.error.value = context.getString(R.string.mv_load_timeout) }
        }
    }
    DisposableEffect(song.uri) { onDispose { onActiveChange(false); loading = false } }
    Box(modifier) {
        if (active && video != null) key(video.songKey, video.source, video.videoId, video.fileName, attempt) {
            // Leave the cover underneath visible until the player is ready, or if it fails.
            Box(Modifier.fillMaxSize().alpha(if (loading || currentControls.error.value != null) 0f else 1f)
                .background(Color.Black).testTag("music_video_surface")) {
                MutedMusicVideo(video, repository, isPlaying, positionMs, Modifier.fillMaxSize()) { name, value ->
                    when (name) {
                        "ready" -> { loading = false; currentControls.error.value = null }
                        else -> {
                            loading = false
                            currentControls.error.value = context.getString(when {
                                name == "network" -> R.string.mv_network_error
                                name == "blocked" -> R.string.mv_autoplay_blocked
                                value == "153" -> R.string.mv_identity_error
                                value == "100" -> R.string.mv_unavailable
                                value == "101" || value == "150" -> R.string.mv_embed_disabled
                                else -> R.string.mv_playback_error
                            })
                        }
                    }
                }
            }
        }
    }
}

/** Status belongs beside the selector, outside the fading video and its touch surface. */
@Composable
fun MusicVideoStatus(controls: MusicVideoControls) {
    if (!controls.active.value || !controls.hasVideo) return
    controls.error.value?.let { message ->
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().testTag("music_video_error")) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(message, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { controls.retry.intValue++ }) { Text(stringResource(R.string.mv_retry)) }
                    TextButton(onClick = { controls.managing.value = true }) { Text(stringResource(R.string.mv_manage)) }
                }
            }
        }
    }
}

/** Mounted once by the player, independently of visible artwork or lyrics. */
@Composable
fun MusicVideoSession(song: Song, controls: MusicVideoControls) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val repository = remember { MusicVideoRepository(context) }
    val key = song.uri.toString()
    var video by controls.video
    var active by controls.active
    var chooser by controls.managing
    var feedback by controls.feedback
    var pendingFileKey by remember { mutableStateOf<String?>(null) }
    var pendingFileTitle by remember { mutableStateOf("") }
    var busy by remember(key) { mutableStateOf(false) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var url by remember(key) { mutableStateOf("") }
    var results by remember(key) { mutableStateOf(emptyList<VideoCandidate>()) }
    val currentKey by rememberUpdatedState(key)
    LaunchedEffect(key) { video = withContext(Dispatchers.IO) { repository.get(key) } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val captured = pendingFileKey ?: key
            busy = true
            scope.launch {
                try {
                    val imported = repository.import(captured, uri, pendingFileTitle.ifBlank { song.title })
                    if (currentKey == captured) { video = imported; active = true; chooser = false; error = null }
                } catch (_: Exception) { if (currentKey == captured) error = context.getString(R.string.mv_import_error) }
                finally { if (currentKey == captured) busy = false }
            }
        }
    }
    if (feedback) AlertDialog(onDismissRequest = { feedback = false }, title = { Text(stringResource(R.string.context_feedback)) },
        text = { Column {
            Text(stringResource(R.string.context_feedback_hint))
            TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { chromahub.rhythm.app.features.local.data.experience.PersonalCollections.snooze(context, key, song.title) }; feedback = false } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.personal_snooze)) }
            TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { chromahub.rhythm.app.features.local.data.experience.PersonalCollections.hide(context, key, song.title) }; feedback = false } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.personal_hide)) }
            listOf(1.0 to R.string.context_feedback_more, 0.0 to R.string.context_feedback_less, null to R.string.context_feedback_reset).forEach { (reward, label) ->
                TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { RecommendationLearning.get(context).feedback(key, reward) }; feedback = false } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(label)) }
            }
        } }, confirmButton = { TextButton(onClick = { feedback = false }) { Text(stringResource(android.R.string.cancel)) } })
    if (chooser) AlertDialog(onDismissRequest = { if (!busy) chooser = false }, title = { Text(stringResource(R.string.mv_watch)) },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(stringResource(R.string.mv_hint), style = MaterialTheme.typography.bodySmall) }
            item { OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.mv_link)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { Button(enabled = !busy && MusicVideoLinks.youtubeId(url) != null, onClick = { scope.launch { video = withContext(Dispatchers.IO) { repository.link(key, url, song.title) }; active = true; chooser = false; error = null } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mv_save_link)) } }
            item { OutlinedButton(enabled = !busy, onClick = { busy = true; error = null; scope.launch {
                try { results = repository.search(song); if (results.isEmpty()) error = context.getString(R.string.mv_search_empty) }
                catch (_: Exception) { error = context.getString(R.string.mv_search_error) }
                finally { busy = false }
            } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mv_search)) } }
            item { TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/results?search_query=" + android.net.Uri.encode("${song.artist} ${song.title} official music video")))) } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mv_search_browser)) } }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
            items(results, key = { it.id }) { hit -> TextButton(onClick = { scope.launch { video = withContext(Dispatchers.IO) { repository.link(key, "https://youtu.be/${hit.id}", hit.title) }; active = true; chooser = false; error = null } }) { Text(hit.title + "\n" + hit.artist) } }
            item { OutlinedButton(enabled = !busy, onClick = { pendingFileKey = key; pendingFileTitle = song.title; pick.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mv_add_file)) } }
            if (video != null) {
                if (video!!.source == "local" && !video!!.videoOnly) item { Text(stringResource(R.string.mv_original_smaller), style = MaterialTheme.typography.bodySmall) }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { video?.let { v -> scope.launch { val changed = v.copy(offsetMs = (v.offsetMs - 5000).coerceAtLeast(-600_000)); withContext(Dispatchers.IO) { repository.save(changed) }; video = changed } } }) { Text("−5 " + stringResource(R.string.mv_seconds)) }
                    Text((video!!.offsetMs / 1000).toString() + " " + stringResource(R.string.mv_seconds))
                    TextButton(onClick = { video?.let { v -> scope.launch { val changed = v.copy(offsetMs = (v.offsetMs + 5000).coerceAtMost(600_000)); withContext(Dispatchers.IO) { repository.save(changed) }; video = changed } } }) { Text("+5 " + stringResource(R.string.mv_seconds)) }
                } }
                if (video!!.source == "youtube") item { TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=${video!!.videoId}"))) } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mv_open_external)) } }
                item { Text(video!!.title + " · " + if (video!!.source == "local") video!!.fileName?.substringAfterLast('.').orEmpty().uppercase() else "YouTube", style = MaterialTheme.typography.bodySmall) }
                item { TextButton(enabled = !busy, onClick = { scope.launch { withContext(Dispatchers.IO) { repository.remove(key) }; video = null; active = false; chooser = false } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mv_remove)) } }
            }
        } }, confirmButton = { TextButton(enabled = !busy, onClick = { chooser = false }) { Text(stringResource(android.R.string.cancel)) } })
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable
private fun MutedMusicVideo(video: MusicVideo, repository: MusicVideoRepository, isPlaying: Boolean, position: () -> Long, modifier: Modifier, onEvent: (String, String) -> Unit) {
    val context = LocalContext.current; val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    val playing = foreground && isPlaying
    val currentPosition by rememberUpdatedState(position)
    if (video.source == "local") {
        val player = remember(video.fileName) { ExoPlayer.Builder(context.applicationContext).build().apply {
            volume = 0f; setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, false)
            repository.uri(video)?.let { setMediaItem(MediaItem.fromUri(it)); prepare() }
        } }
        var aspectRatio by remember(player) { mutableStateOf(0f) }
        DisposableEffect(player) {
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) { onEvent("error", "") }
                override fun onRenderedFirstFrame() { onEvent("ready", "") }
                override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                    if (size.height > 0) aspectRatio = size.width.toFloat() * size.pixelWidthHeightRatio / size.height
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener); player.release() }
        }
        LaunchedEffect(player, playing, video.offsetMs) {
            var initialSeek = true
            while (true) {
                val requested = currentPosition() + video.offsetMs
                val duration = player.duration
                player.playWhenReady = playing && requested >= 0 && (duration <= 0 || requested < duration)
                if (duration > 0) {
                    val target = requested.coerceIn(0, (duration - 250).coerceAtLeast(0))
                    if (initialSeek || kotlin.math.abs(player.currentPosition - target) > 1500) {
                        player.seekTo(target); initialSeek = false
                    }
                }
                delay(500)
            }
        }
        key(player) {
            AndroidView(factory = { ctx -> FrameLayout(ctx).apply {
                clipChildren = true; clipToPadding = true
                val content = AspectRatioFrameLayout(ctx).apply {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    val texture = TextureView(ctx)
                    addView(texture, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    player.setVideoTextureView(texture)
                }
                addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
            } }, update = { (it.getChildAt(0) as AspectRatioFrameLayout).setAspectRatio(aspectRatio) }, modifier = modifier)
        }
    } else {
        video.videoId?.let { YouTubeMusicVideo(it, video.offsetMs, playing, { currentPosition() }, modifier, onEvent) }
    }
}
