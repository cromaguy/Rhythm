// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.shared.presentation.components.player

import android.content.Intent
import android.view.TextureView
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    val hasVideo: Boolean get() = video.value != null
    fun toggle() { if (hasVideo) active.value = !active.value else managing.value = true }
}

@Composable
fun MusicVideoToggle(controls: MusicVideoControls, foreground: Color = MaterialTheme.colorScheme.onSurface) {
    val colors = SegmentedButtonDefaults.colors(inactiveContainerColor = Color.Transparent, inactiveContentColor = foreground,
        inactiveBorderColor = foreground.copy(alpha = .5f), activeBorderColor = foreground.copy(alpha = .5f))
    SingleChoiceSegmentedButtonRow {
        SegmentedButton(selected = !controls.active.value, onClick = { controls.active.value = false }, shape = SegmentedButtonDefaults.itemShape(0, 2), colors = colors) {
            Text(stringResource(R.string.personal_audio))
        }
        SegmentedButton(selected = controls.active.value, onClick = { controls.toggle() }, shape = SegmentedButtonDefaults.itemShape(1, 2), colors = colors,
            icon = { Icon(MaterialSymbolIcon("smart_display"), null, Modifier.size(18.dp)) }) {
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
    LaunchedEffect(active, song.uri) { onActiveChange(active && video != null) }
    DisposableEffect(song.uri) { onDispose { onActiveChange(false) } }
    Box(modifier) {
        if (active && video != null) {
            Box(Modifier.fillMaxSize().background(Color.Black).testTag("music_video_surface")) {
                MutedMusicVideo(video, repository, isPlaying, positionMs, Modifier.fillMaxSize()) { currentControls.error.value = context.getString(R.string.mv_playback_error) }
                currentControls.error.value?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.TopCenter).background(Color.Black.copy(alpha = .8f)).padding(12.dp)) }
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
    var error by controls.error
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
private fun MutedMusicVideo(video: MusicVideo, repository: MusicVideoRepository, isPlaying: Boolean, position: () -> Long, modifier: Modifier, onError: () -> Unit) {
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
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) { onError() }
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
            AndroidView(factory = { ctx -> AspectRatioFrameLayout(ctx).apply {
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                val texture = TextureView(ctx)
                addView(texture, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                player.setVideoTextureView(texture)
            } }, update = { it.setAspectRatio(aspectRatio) }, modifier = modifier)
        }
    } else {
        val currentPlaying by rememberUpdatedState(playing)
        val currentError by rememberUpdatedState(onError)
        val web = remember(video.videoId, video.offsetMs) { WebView(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true; settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false; settings.allowContentAccess = false; settings.domStorageEnabled = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface fun failed(code: Int) { post { currentError() } }
            }, "T2Video")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) { view.evaluateJavascript("setPlaying(${currentPlaying});", null) }
                override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) { if (request.isForMainFrame) onError() }
                override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean = true
            }
            val start = ((currentPosition() + video.offsetMs).coerceAtLeast(0) / 1000)
            val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;background:#000"><div id="mv" style="position:absolute;width:100%;height:100%"></div><script src="https://www.youtube.com/iframe_api"></script><script>var player,shouldPlay=${playing};function setPlaying(value){shouldPlay=value;if(player&&player.mute){player.mute();if(value)player.playVideo();else player.pauseVideo();}}function setPosition(time){if(player&&player.getCurrentTime){var duration=player.getDuration();var target=Math.max(0,duration>0?Math.min(time,Math.max(0,duration-0.1)):time);if(Math.abs(player.getCurrentTime()-target)>1.5)player.seekTo(target,true);if(time<0||duration>0&&time>=duration)player.pauseVideo();else if(shouldPlay)player.playVideo();player.mute();}}function onYouTubeIframeAPIReady(){player=new YT.Player('mv',{videoId:'${video.videoId}',host:'https://www.youtube-nocookie.com',playerVars:{playsinline:1,controls:1,mute:1,start:$start,origin:'https://tak2-08.github.io'},events:{onReady:function(){setPlaying(shouldPlay);},onError:function(e){T2Video.failed(e.data);}}});}</script></body></html>"""
            loadDataWithBaseURL("https://tak2-08.github.io/T2Rhythm/", html, "text/html", "UTF-8", null)
        } }
        LaunchedEffect(web, playing, video.offsetMs) {
            web.evaluateJavascript("setPlaying($playing);", null)
            while (true) { web.evaluateJavascript("setPosition(${(currentPosition() + video.offsetMs) / 1000.0});", null); delay(2000) }
        }
        DisposableEffect(web) { onDispose { web.evaluateJavascript("setPlaying(false);", null); web.stopLoading(); web.loadUrl("about:blank"); web.destroy() } }
        key(web) { AndroidView(factory = { web }, modifier = modifier) }
    }
}
