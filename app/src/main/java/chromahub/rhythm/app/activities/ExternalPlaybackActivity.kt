package chromahub.rhythm.app.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import chromahub.rhythm.app.R
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import chromahub.rhythm.app.shared.presentation.components.common.AutoScrollingTextOnDemand
import chromahub.rhythm.app.shared.presentation.components.common.WaveSlider
import chromahub.rhythm.app.ui.theme.RhythmTheme
import chromahub.rhythm.app.util.MediaUtils
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.presentation.viewmodel.ThemeViewModel
import chromahub.rhythm.app.activities.MainActivity
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import androidx.compose.ui.res.stringResource

/**
 * Compact bottom sheet activity for playing external audio files.
 * Provides a minimal, focused UI without jumping to the main app.
 */
class ExternalPlaybackActivity : ComponentActivity() {
    private val TAG = "ExternalPlaybackActivity"
    private val musicViewModel: MusicViewModel by viewModels()
    private val themeViewModel: ThemeViewModel by viewModels()
    private var playbackRequestJob: Job? = null
    private var lastHandledUri: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Connect eagerly and handle the incoming intent immediately for snappier startup.
        lifecycleScope.launch {
            musicViewModel.connectToMediaService()
            handleIntent(intent)
        }
        
        setContent {
            val useSystemTheme by themeViewModel.useSystemTheme.collectAsState()
            val darkMode by themeViewModel.darkMode.collectAsState()
            val useDynamicColors by themeViewModel.useDynamicColors.collectAsState()
            
            RhythmTheme(
                darkTheme = if (useSystemTheme) isSystemInDarkTheme() else darkMode,
                dynamicColor = true
            ) {
                ExternalPlaybackBottomSheet(
                    musicViewModel = musicViewModel,
                    onDismiss = { finish() },
                    onOpenFullPlayer = {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        musicViewModel.connectToMediaService()
        handleIntent(intent)
    }

    override fun onDestroy() {
        playbackRequestJob?.cancel()
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent) {
        Log.d(TAG, "handleIntent called, action=${intent.action}")
        if (intent.action != Intent.ACTION_VIEW) {
            Log.w(TAG, "Unsupported intent action for external playback: ${intent.action}")
            return
        }

        val uri = intent.data ?: run {
            Log.e(TAG, "Intent data is null")
            Toast.makeText(this, R.string.externalplaybackactivity_unable_to_open_audio, Toast.LENGTH_SHORT).show()
            return
        }

        if (!isValidAudioUri(uri)) {
            Log.w(TAG, "Rejected non-audio or unreadable URI: $uri")
            Toast.makeText(this, R.string.externalplaybackactivity_unsupported_or_unreadable_audio, Toast.LENGTH_SHORT).show()
            return
        }

        val uriKey = uri.toString()
        if (playbackRequestJob?.isActive == true && lastHandledUri == uriKey) {
            Log.d(TAG, "Duplicate playback request ignored for URI: $uri")
            return
        }

        lastHandledUri = uriKey
        playbackRequestJob?.cancel()
        playbackRequestJob = lifecycleScope.launch {
            musicViewModel.connectToMediaService()
            playExternalFile(uri)
        }
    }

    private fun isValidAudioUri(uri: Uri): Boolean {
        return try {
            val mimeType = MediaUtils.getMimeType(applicationContext, uri)
            mimeType?.startsWith("audio/") == true ||
                mimeType?.contains("matroska", ignoreCase = true) == true ||
                uri.toString().let { uriStr ->
                    uriStr.endsWith(".mp3", ignoreCase = true) ||
                        uriStr.endsWith(".m4a", ignoreCase = true) ||
                        uriStr.endsWith(".alac", ignoreCase = true) ||
                        uriStr.endsWith(".wav", ignoreCase = true) ||
                        uriStr.endsWith(".ogg", ignoreCase = true) ||
                        uriStr.endsWith(".flac", ignoreCase = true) ||
                        uriStr.endsWith(".aac", ignoreCase = true) ||
                        uriStr.endsWith(".opus", ignoreCase = true) ||
                        uriStr.endsWith(".opa", ignoreCase = true) ||
                        uriStr.endsWith(".wma", ignoreCase = true) ||
                        uriStr.endsWith(".mkv", ignoreCase = true) ||
                        uriStr.endsWith(".mka", ignoreCase = true) ||
                        uriStr.endsWith(".ac3", ignoreCase = true) ||
                        uriStr.endsWith(".ac4", ignoreCase = true) ||
                        uriStr.endsWith(".oga", ignoreCase = true) ||
                        uriStr.endsWith(".mid", ignoreCase = true) ||
                        uriStr.endsWith(".midi", ignoreCase = true) ||
                        uriStr.endsWith(".adts", ignoreCase = true) ||
                        uriStr.endsWith(".m4b", ignoreCase = true) ||
                        uriStr.endsWith(".ape", ignoreCase = true) ||
                        uriStr.endsWith(".wv", ignoreCase = true) ||
                        uriStr.endsWith(".tta", ignoreCase = true) ||
                        uriStr.endsWith(".tak", ignoreCase = true) ||
                        uriStr.endsWith(".aiff", ignoreCase = true) ||
                        uriStr.endsWith(".aif", ignoreCase = true) ||
                        uriStr.endsWith(".dsf", ignoreCase = true) ||
                        uriStr.endsWith(".dff", ignoreCase = true)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating external URI: $uri", e)
            false
        }
    }

    private suspend fun playExternalFile(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "playExternalFile: Starting, URI=$uri")

            // Check if file exists in MediaStore
            val existingSong = MediaUtils.findSongInMediaStore(this@ExternalPlaybackActivity, uri)

            val targetSong = if (existingSong != null) {
                Log.d(TAG, "Found existing song in MediaStore: ${existingSong.title}")
                existingSong
            } else {
                Log.d(TAG, "Extracting metadata from external file")
                MediaUtils.extractMetadataFromUri(this@ExternalPlaybackActivity, uri)
            }

            Log.d(TAG, "Resolved external song: title=${targetSong.title}, artist=${targetSong.artist}, uri=${targetSong.uri}")

            withContext(Dispatchers.Main) {
                Log.d(TAG, "Calling playExternalAudioFile")
                musicViewModel.playExternalAudioFile(targetSong)

                // Small delay to let playback start, then check state
                delay(500)
                Log.d(TAG, "After playback start - isPlaying=${musicViewModel.isPlaying.value}, currentSong=${musicViewModel.currentSong.value?.title}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing external file", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ExternalPlaybackActivity, R.string.externalplaybackactivity_failed_to_play_audio, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalPlaybackBottomSheet(
    musicViewModel: MusicViewModel,
    onDismiss: () -> Unit,
    onOpenFullPlayer: () -> Unit
) {
    val currentSong by musicViewModel.currentSong.collectAsState()
    val isPlaying by musicViewModel.isPlaying.collectAsState()
    val isBuffering by musicViewModel.isBuffering.collectAsState()
    val isSeeking by musicViewModel.isSeeking.collectAsState()
    val progress by musicViewModel.progress.collectAsState()

    // Animation states
    var showContent by remember { mutableStateOf(false) }
    var awaitingSong by remember { mutableStateOf(true) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }

    LaunchedEffect(progress, isScrubbing) {
        if (!isScrubbing) {
            scrubProgress = progress.coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(currentSong, isBuffering, isSeeking) {
        if (currentSong != null) {
            awaitingSong = false
            return@LaunchedEffect
        }

        if (!awaitingSong && !isBuffering && !isSeeking) {
            // Avoid dismissing on transient nulls during controller/service churn.
            delay(1200)
            if (currentSong == null && !isBuffering && !isSeeking) {
                onDismiss()
            }
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .systemBarsPadding(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = Color.Transparent,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Box {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
                                0.4f to MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                                0.7f to MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                                1f to MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    )
            ) {
                // Album art background (doesn't affect layout)
                currentSong?.artworkUri?.let { artUri ->
                    AsyncImage(
                        model = artUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(0, 0) {
                                    placeable.place(0, 0)
                                }
                            }
                            .fillMaxSize()
                            .blur(20.dp)
                            .graphicsLayer { alpha = 0.3f }
                    )
                }

                // Additional gradient overlay for depth
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                )
                            )
                        )
                )

                // Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                // Drag handle
//                Box(
//                    modifier = Modifier
//                        .width(48.dp)
//                        .height(4.dp)
//                        .clip(RoundedCornerShape(2.dp))
//                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
//                )

                Spacer(modifier = Modifier.height(20.dp))

                // Rhythm Logo and Name with splash screen style
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    // Logo with breathing animation like splash screen
                    val infiniteTransition = rememberInfiniteTransition(label = "logoBreathing")
                    val logoBreathing by infiniteTransition.animateFloat(
                        initialValue = 0.97f,
                        targetValue = 1.03f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 2500,
                                easing = EaseInOutSine
                            ),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "logoBreathing"
                    )

                    Image(
                        painter = painterResource(R.drawable.rhythm_splash_logo),
                        contentDescription = stringResource(R.string.updates_rhythm_logo_cd),
                        modifier = Modifier
                            .size(50.dp)
//                            .graphicsLayer {
//                                scaleX = logoBreathing
//                                scaleY = logoBreathing
//                            }
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.cd_rhythm_splash),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.settings_about_music_player),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                if (currentSong == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    // Album Art and Song Info Row with enhanced styling
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        tonalElevation = 1.dp
                    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Album Art with shadow
//                        currentSong?.artworkUri?.let { artUri ->
//                            AsyncImage(
//                                model = artUri,
//                                contentDescription = "Album Art",
//                                modifier = Modifier
//                                    .size(88.dp)
//                                    .shadow(0.dp, RoundedCornerShape(16.dp))
//                                    .clip(RoundedCornerShape(16.dp)),
//                                contentScale = ContentScale.Crop
//                            )
//                        } ?: Surface(
//                            modifier = Modifier.size(88.dp),
//                            shape = RoundedCornerShape(16.dp),
//                            color = MaterialTheme.colorScheme.surfaceVariant,
//                            tonalElevation = 2.dp
//                        ) {
//                            Box(contentAlignment = Alignment.Center) {
//                                Icon(
//                                    painter = painterResource(R.drawable.ic_music_note),
//                                    contentDescription = null,
//                                    modifier = Modifier.size(36.dp),
//                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
//                                )
//                            }
//                        }

                        // Song Info with better hierarchy
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            AutoScrollingTextOnDemand(
                                text = currentSong?.title ?: "Unknown Title",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            AutoScrollingTextOnDemand(
                                text = currentSong?.artist ?: "Unknown Artist",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            AutoScrollingTextOnDemand(
                                text = currentSong?.album ?: "Unknown album",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                ),
                                gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Progress Section with better spacing
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    tonalElevation = 1.dp
                ) {
                    currentSong?.let { song ->
                        val displayedProgress = if (isScrubbing) scrubProgress else progress

                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            WaveSlider(
                                value = displayedProgress,
                                onValueChange = { newProgress ->
                                    isScrubbing = true
                                    scrubProgress = newProgress.coerceIn(0f, 1f)
                                },
                                onValueChangeFinished = {
                                    val targetPositionMs = (song.duration * scrubProgress).toLong()
                                    val currentPositionMs =
                                        (song.duration * progress.coerceIn(0f, 1f)).toLong()
                                    if (abs(targetPositionMs - currentPositionMs) >= 750L) {
                                        musicViewModel.seekTo(targetPositionMs)
                                    }
                                    isScrubbing = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                isPlaying = isPlaying
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Time Row with better design
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatTime((song.duration * displayedProgress).toLong()),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = formatTime(song.duration),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Playback Controls Row - Enhanced with better visual hierarchy
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Button with better styling
//                    Surface(
//                        onClick = {
//                            musicViewModel.skipToPrevious()
//                        },
//                        modifier = Modifier.size(68.dp),
//                        shape = CircleShape,
//                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
//                        tonalElevation = 2.dp
//                    ) {
//                        Box(contentAlignment = Alignment.Center) {
//                            Icon(
//                                painter = painterResource(R.drawable.ic_skip_previous),
//                                contentDescription = "Previous",
//                                tint = MaterialTheme.colorScheme.onSurface,
//                                modifier = Modifier.size(30.dp)
//                            )
//                        }
//                    }

                    // Play/Pause Button - Larger and more prominent
                    val buttonWidth by animateDpAsState(
                        targetValue = if (isPlaying || isBuffering) 76.dp else 160.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "playPauseWidth"
                    )

                    Surface(
                        onClick = {
                            if (!isBuffering) {
                                musicViewModel.togglePlayPause()
                            }
                        },
                        modifier = Modifier
                            .height(76.dp)
                            .width(buttonWidth),
                        shape = RoundedCornerShape(38.dp),
                        color = MaterialTheme.colorScheme.primary,
                        tonalElevation = 3.dp,
                        shadowElevation = 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isBuffering) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(30.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(
                                            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                                        ),
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                AnimatedVisibility(
                                    visible = !isPlaying && !isBuffering,
                                    enter = fadeIn() + expandHorizontally(),
                                    exit = fadeOut() + shrinkHorizontally()
                                ) {
                                    Text(
                                        text = stringResource(R.string.cd_play),
                                        modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Next Button with better styling
//                    Surface(
//                        onClick = {
//                            musicViewModel.skipToNext()
//                        },
//                        modifier = Modifier.size(68.dp),
//                        shape = CircleShape,
//                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
//                        tonalElevation = 2.dp
//                    ) {
//                        Box(contentAlignment = Alignment.Center) {
//                            Icon(
//                                painter = painterResource(R.drawable.ic_skip_next),
//                                contentDescription = "Next",
//                                tint = MaterialTheme.colorScheme.onSurface,
//                                modifier = Modifier.size(30.dp)
//                            )
//                        }
//                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Open Rhythm Button - Enhanced design
                FilledTonalButton(
                    onClick = onOpenFullPlayer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.externalplaybackactivity_open_rhythm),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }

            }
        }
    }}
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
