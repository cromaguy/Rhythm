package chromahub.rhythm.app.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chromahub.rhythm.app.data.PlaybackLocation
import chromahub.rhythm.app.data.Playlist
import chromahub.rhythm.app.data.Song
import chromahub.rhythm.app.ui.components.WaveSlider
import chromahub.rhythm.app.ui.components.RhythmIcons
import chromahub.rhythm.app.ui.theme.PlayerButtonColor
import chromahub.rhythm.app.ui.components.M3PlaceholderType
import chromahub.rhythm.app.util.ImageUtils
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.ReorderableLazyListState
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import java.util.Locale
import kotlin.math.abs
import chromahub.rhythm.app.ui.components.M3CircularLoader
import android.view.animation.OvershootInterpolator
import chromahub.rhythm.app.ui.components.SyncedLyricsView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import java.util.concurrent.TimeUnit // Import TimeUnit for duration formatting
import chromahub.rhythm.app.ui.screens.QueueBottomSheet
import chromahub.rhythm.app.ui.screens.LibraryTab
import chromahub.rhythm.app.ui.screens.AddToPlaylistBottomSheet
import chromahub.rhythm.app.ui.screens.DeviceOutputBottomSheet
import chromahub.rhythm.app.ui.screens.SongInfoBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    song: Song?,
    isPlaying: Boolean,
    progress: Float,
    location: PlaybackLocation?,
    queuePosition: Int = 1,
    queueTotal: Int = 1,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onBack: () -> Unit,
    onLocationClick: () -> Unit,
    onQueueClick: () -> Unit,
    locations: List<PlaybackLocation> = emptyList(),
    onLocationSelect: (PlaybackLocation) -> Unit = {},
    volume: Float = 0.7f,
    isMuted: Boolean = false,
    onVolumeChange: (Float) -> Unit = {},
    onToggleMute: () -> Unit = {},
    onMaxVolume: () -> Unit = {},
    onRefreshDevices: () -> Unit = {},
    onStopDeviceMonitoring: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    isShuffleEnabled: Boolean = false,
    repeatMode: Int = 0,
    isFavorite: Boolean = false,
    showLyrics: Boolean = true,
    onlineOnlyLyrics: Boolean = false,
    lyrics: chromahub.rhythm.app.data.LyricsData? = null,
    isLoadingLyrics: Boolean = false,
    playlists: List<Playlist> = emptyList(),
    queue: List<Song> = emptyList(),
    onSongClick: (Song) -> Unit = {},
    onRemoveFromQueue: (Song) -> Unit = {},
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    onAddSongsToQueue: () -> Unit = {},
    onNavigateToLibrary: (LibraryTab) -> Unit = {},
    showAddToPlaylistSheet: Boolean = false,
    onAddToPlaylistSheetDismiss: () -> Unit = {},
    onAddSongToPlaylist: (Song, String) -> Unit = { _, _ -> },
    onCreatePlaylist: (String) -> Unit = { _ -> },
    onShowCreatePlaylistDialog: () -> Unit = {} ,
    onClearQueue: () -> Unit = {},
    appSettings: chromahub.rhythm.app.data.AppSettings? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    
    // Get AppSettings for volume control setting
    val appSettingsInstance = appSettings ?: chromahub.rhythm.app.data.AppSettings.getInstance(context)
    val useSystemVolume by appSettingsInstance.useSystemVolume.collectAsState()
    
    // System volume state
    var systemVolume by remember { mutableFloatStateOf(0.5f) }
    
    // Monitor system volume changes
    LaunchedEffect(useSystemVolume) {
        if (useSystemVolume) {
            while (true) {
                val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                val newSystemVolume = if (maxVolume > 0) currentVolume.toFloat() / maxVolume.toFloat() else 0f
                
                if (newSystemVolume != systemVolume) {
                    systemVolume = newSystemVolume
                }
                
                kotlinx.coroutines.delay(500) // Check every 500ms
            }
        }
    }
    
    // Calculate screen dimensions
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    
    // Enhanced screen size detection for better responsiveness
    val isCompactHeight = configuration.screenHeightDp < 600
    val isLargeHeight = configuration.screenHeightDp > 800
    val isCompactWidth = configuration.screenWidthDp < 400
    
    // Dynamic sizing based on screen dimensions
    val albumArtSize = when {
        isCompactHeight -> 0.55f
        isLargeHeight -> 0.65f
        else -> 0.6f
    }
    
    // Animation for album art entry
    var showAlbumArt by remember { mutableStateOf(false) }
    
    // Toggle between album art and lyrics
    var showLyricsView by remember { mutableStateOf(false) }
    
    // Reset lyrics view when lyrics are disabled
    LaunchedEffect(showLyrics) {
        if (!showLyrics && showLyricsView) {
            showLyricsView = false
        }
    }
    
    // Bottom sheet states
    val queueSheetState = rememberModalBottomSheetState()
    val addToPlaylistSheetState = rememberModalBottomSheetState()
    val deviceOutputSheetState = rememberModalBottomSheetState()
    var showQueueSheet by remember { mutableStateOf(false) }
    var showDeviceOutputSheet by remember { mutableStateOf(false) }
    var showSongInfoSheet by remember { mutableStateOf(false) }
    
    // For swipe down gesture detection - improved parameters
    var targetOffsetY by remember { mutableStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(
        targetValue = targetOffsetY,
        animationSpec = tween(
            durationMillis = 300,
            easing = { fraction ->
                OvershootInterpolator(2.0f).getInterpolation(fraction)
            }
        ),
        label = "animatedOffsetY"
    )
    val swipeThreshold = 120f
    
    // Animation for dismiss swipe gesture
    val dismissProgress = (animatedOffsetY / swipeThreshold).coerceIn(0f, 1f)
    val contentAlpha by animateFloatAsState(
        targetValue = 1f - (dismissProgress * 0.4f),
        label = "contentAlpha"
    )
    
    // Scale effect for swipe
    val contentScale by animateFloatAsState(
        targetValue = 1f - (dismissProgress * 0.05f),
        label = "contentScale"
    )
    
    // For dismissing animation
    var isDismissing by remember { mutableStateOf(false) }
    
    // Handle dismissing animation
    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            delay(50)
            onBack()
        }
    }
    
    // Function to format duration from milliseconds to mm:ss format
    val formatDuration = { durationMs: Long ->
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) -
                TimeUnit.MINUTES.toSeconds(minutes)
        String.format("%d:%02d", minutes, seconds)
    }
    
    // Calculate current and total time
    val currentTimeMs = ((song?.duration ?: 0) * progress).toLong()
    val totalTimeMs = song?.duration ?: 0
    
    // Format current and total time
    val currentTimeFormatted = formatDuration(currentTimeMs)
    val totalTimeFormatted = formatDuration(totalTimeMs)
    
    LaunchedEffect(song?.id) {
        // Reset animation when song changes
        showAlbumArt = false
        delay(100)
        showAlbumArt = true
        
        // Reset to album art view when song changes
        showLyricsView = false
    }
    
    // Start device monitoring when player screen is shown and stop when closed
    LaunchedEffect(Unit) {
        onRefreshDevices()
    }
    
    DisposableEffect(Unit) {
        onDispose {
            onStopDeviceMonitoring()
        }
    }
    
    val albumScale by animateFloatAsState(
        targetValue = if (showAlbumArt) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "albumScale"
    )
    
    val albumAlpha by animateFloatAsState(
        targetValue = if (showAlbumArt) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "albumAlpha"
    )
    
    // Find which playlist the current song belongs to
    val songPlaylist = remember(song, playlists) {
        if (song == null) null
        else playlists.find { playlist ->
            playlist.songs.any { it.id == song.id }
        }
    }

    // Show bottom sheets if needed
    if (showQueueSheet && song != null) {
        QueueBottomSheet(
            currentSong = song,
            queue = queue,
            currentQueueIndex = queuePosition - 1,
            onSongClick = { selectedSong -> 
                // Play the selected song
                onSongClick(selectedSong)
                // Hide the sheet after selection
                scope.launch {
                    queueSheetState.hide()
                }.invokeOnCompletion {
                    if (!queueSheetState.isVisible) {
                        showQueueSheet = false
                    }
                }
            },
            onDismiss = { showQueueSheet = false },
            onRemoveSong = { songToRemove ->
                // Remove the song from the queue
                onRemoveFromQueue(songToRemove)
            },
            onMoveQueueItem = { fromIndex, toIndex ->
                // Move queue item in the actual queue
                onMoveQueueItem(fromIndex, toIndex)
            },
            onAddSongsClick = {
                // Dismiss the queue sheet first
                scope.launch {
                    queueSheetState.hide()
                }.invokeOnCompletion {
                    if (!queueSheetState.isVisible) {
                        // Navigate to the LibraryScreen (Songs tab) with a hint about adding songs
                        onNavigateToLibrary(LibraryTab.SONGS)
                    }
                }
            },
            onClearQueue = {
                // Clear the queue and dismiss the sheet
                onClearQueue()
                scope.launch {
                    queueSheetState.hide()
                }.invokeOnCompletion {
                    if (!queueSheetState.isVisible) {
                        showQueueSheet = false
                    }
                }
            },
            sheetState = queueSheetState
        )
    }
    
    if (showAddToPlaylistSheet && song != null) {
        AddToPlaylistBottomSheet(
            song = song,
            playlists = playlists,
            onDismissRequest = onAddToPlaylistSheetDismiss,
            onAddToPlaylist = { playlist ->
                // Add the current song to the selected playlist
                playlist.id?.let { playlistId ->
                    // Actually add the song to the playlist
                    onAddSongToPlaylist(song, playlistId)
                    // Hide the bottom sheet after adding
                    scope.launch {
                        addToPlaylistSheetState.hide()
                    }.invokeOnCompletion {
                        if (!addToPlaylistSheetState.isVisible) {
                            onAddToPlaylistSheetDismiss()
                        }
                    }
                }
            },
            onCreateNewPlaylist = {
                // Hide the sheet first
                scope.launch {
                    addToPlaylistSheetState.hide()
                }.invokeOnCompletion {
                    if (!addToPlaylistSheetState.isVisible) {
                        onAddToPlaylistSheetDismiss()
                        // Now show the create playlist dialog
                        // We'll handle this in the parent component
                        onShowCreatePlaylistDialog()
                    }
                }
            },
            sheetState = addToPlaylistSheetState
        )
    }

    // Device Output Bottom Sheet
    if (showDeviceOutputSheet) {
        LaunchedEffect(showDeviceOutputSheet) {
            if (showDeviceOutputSheet) {
                onRefreshDevices()
            }
        }
        
        DeviceOutputBottomSheet(
            locations = locations,
            currentLocation = location,
            volume = volume,
            isMuted = isMuted,
            onLocationSelect = { selectedLocation ->
                onLocationSelect(selectedLocation)
                // Hide the sheet after selection
                scope.launch {
                    deviceOutputSheetState.hide()
                }.invokeOnCompletion {
                    if (!deviceOutputSheetState.isVisible) {
                        showDeviceOutputSheet = false
                        onStopDeviceMonitoring()
                    }
                }
            },
            onVolumeChange = onVolumeChange,
            onToggleMute = onToggleMute,
            onMaxVolume = onMaxVolume,
            onRefreshDevices = onRefreshDevices,
            onDismiss = { 
                showDeviceOutputSheet = false
                onStopDeviceMonitoring()
            },
            appSettings = appSettings ?: chromahub.rhythm.app.data.AppSettings.getInstance(context),
            sheetState = deviceOutputSheetState
        )
    }

    // Song Info Bottom Sheet
    if (showSongInfoSheet && song != null) {
        SongInfoBottomSheet(
            song = song,
            onDismiss = { showSongInfoSheet = false }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Add swipe indicator pill - similar to mini player
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        )
                        
                        // Small spacing after the pill
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        AnimatedVisibility(
                            visible = song != null,
                            enter = fadeIn() + slideInVertically { it / 2 },
                            exit = fadeOut() + slideOutVertically { it / 2 }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Enhanced NOW PLAYING with better styling
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "NOW PLAYING",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 1.4.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                                
                                if (song != null && songPlaylist != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Enhanced playlist name with better styling
                                    Text(
                                        text = songPlaylist.name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 0.25.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp) // Reduced from 48.dp
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    // Increased horizontal padding for better edge spacing
                    Box(modifier = Modifier.padding(start = 16.dp)) {
                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onBack()
                            },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Back,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                actions = {
                    // About button on the top right
                    Box(modifier = Modifier.padding(end = 16.dp)) {
                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showSongInfoSheet = true
                            },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                imageVector =  Icons.Rounded.Info,
                                contentDescription = "About song",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        modifier = Modifier
            .graphicsLayer {
                // Apply translation and alpha effects based on swipe gesture
                translationY = animatedOffsetY
                alpha = contentAlpha
                scaleX = contentScale
                scaleY = contentScale
            }
    ) { paddingValues ->
        // Use Box as the root container to allow absolute positioning
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(Unit) {
                    var dragAmountY = 0f

                    detectVerticalDragGestures(
                        onDragStart = {
                            dragAmountY = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()

                            // Only track downward swipes for dismissal
                            if (dragAmount > 0) {
                                dragAmountY += dragAmount
                                // Apply resistance effect - slower movement as user drags further
                                targetOffsetY = (dragAmountY * 0.6f).coerceIn(0f, screenHeight / 2)
                            }
                        },
                        onDragEnd = {
                            // If we've dragged enough, close the player with haptic feedback
                            if (dragAmountY > swipeThreshold) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isDismissing = true
                            } else {
                                targetOffsetY = 0f
                            }
                        },
                        onDragCancel = {
                            // Reset position immediately if not dismissing
                            targetOffsetY = 0f
                        }
                    )
                }
        ) {
            // Add swipe down indicator text when dragging down
            if (animatedOffsetY > swipeThreshold * 0.3f) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    val textAlpha =
                        ((animatedOffsetY - (swipeThreshold * 0.3f)) / (swipeThreshold * 0.7f)).coerceIn(
                            0f,
                            1f
                        )
                    Text(
                        text = "Release to close",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = textAlpha),
                        modifier = Modifier.graphicsLayer {
                            this.alpha = textAlpha
                        }
                    )
                }
            }

            // MAIN CHANGE: Use a Column that fills the available space but anchors content to the bottom
            // This makes all content stick to the bottom of the screen
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Main content column - optimized spacing to reduce vertical padding
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(
                            horizontal = if (isCompactWidth) 8.dp else 12.dp, // Reduced horizontal padding
                            vertical = if (isCompactHeight) 2.dp else 4.dp    // Further reduced vertical padding
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (isCompactHeight) Arrangement.Center else Arrangement.SpaceEvenly
                ) {
                    // Optimized dynamic spacing - reduced overall spacing
                    val contentSpacing = when {
                        isCompactHeight -> 1.dp   // Further reduced from 2.dp
                        isLargeHeight -> 4.dp     // Further reduced from 8.dp  
                        else -> 2.dp              // Further reduced from 4.dp
                    }

                    // Album artwork or lyrics view with smooth transitions
                    AnimatedVisibility(
                        visible = !showLyricsView,
                        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                            animationSpec = tween(
                                300
                            )
                        ),
                        exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(
                            animationSpec = tween(300)
                        )
                    ) {
                        // Album artwork with optimized padding
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth(albumArtSize)
                                .padding(
                                    horizontal = if (isCompactWidth) 2.dp else 4.dp, // Further reduced horizontal padding
                                    vertical = 0.dp // Removed vertical padding
                                ),
                            shape = RoundedCornerShape(if (isCompactHeight) 20.dp else 28.dp),
                            elevation = CardDefaults.elevatedCardElevation(
                                defaultElevation = if (isCompactHeight) 4.dp else 8.dp
                            ),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                            ) {
                                // Album artwork with animations
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX = albumScale,
                                            scaleY = albumScale,
                                            alpha = albumAlpha
                                        )
                                ) {
                                    if (song?.artworkUri != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .apply(
                                                    ImageUtils.buildImageRequest(
                                                        song.artworkUri,
                                                        song.title,
                                                        context.cacheDir,
                                                        M3PlaceholderType.TRACK
                                                    )
                                                )
                                                .build(),
                                            contentDescription = "Album artwork for ${song.title}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(if (isCompactHeight) 20.dp else 28.dp))
                                        )
                                    } else {
                                        // Fallback album art
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = RhythmIcons.Album,
                                                contentDescription = "Album art",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.fillMaxSize(0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Lyrics view with smooth fade animation only
                    AnimatedVisibility(
                        visible = showLyricsView,
                        enter = fadeIn(animationSpec = tween(durationMillis = 400, easing = androidx.compose.animation.core.EaseInOut)),
                        exit = fadeOut(animationSpec = tween(durationMillis = 300, easing = androidx.compose.animation.core.EaseInOut))
                    ) {
                        // Lyrics view - optimized sizing with reduced padding
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(
                                    when {
                                        isCompactHeight -> 140.dp  // Reduced from 160.dp
                                        isLargeHeight -> 280.dp    // Reduced from 320.dp
                                        else -> 200.dp             // Reduced from 240.dp
                                    }
                                )
                                .padding(
                                    horizontal = if (isCompactWidth) 2.dp else 4.dp, // Further reduced horizontal padding
                                    vertical = 0.dp // Removed vertical padding
                                ),
                            shape = RoundedCornerShape(if (isCompactHeight) 20.dp else 28.dp),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 0.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            )
                        ) {
                            // Background with album art blur effect
                            Box(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Background image with blur effect
                                if (song?.artworkUri != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(song.artworkUri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        alpha = 0.6f,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(if (isCompactHeight) 20.dp else 28.dp))
                                            .graphicsLayer {
                                                // Scale up slightly to create a softer look
                                                scaleX = 1.1f
                                                scaleY = 1.1f
                                            }
                                    )
                                } else {
                                    // Fallback gradient background when no album art
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.primaryContainer.copy(
                                                            alpha = 0.3f
                                                        ),
                                                        MaterialTheme.colorScheme.secondaryContainer.copy(
                                                            alpha = 0.2f
                                                        ),
                                                        MaterialTheme.colorScheme.tertiaryContainer.copy(
                                                            alpha = 0.1f
                                                        )
                                                    )
                                                ),
                                                RoundedCornerShape(28.dp)
                                            )
                                    )
                                }

                                // Overlay with semi-transparent background for text readability
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                                )
                                            ),
                                            shape = RoundedCornerShape(28.dp)
                                        )
                                )

                                // Additional subtle overlay for better text contrast
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.15f)
                                                ),
                                                radius = 500f
                                            ),
                                            shape = RoundedCornerShape(28.dp)
                                        )
                                )

                                // Content area with lyrics - optimized padding
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(
                                            when {
                                                isCompactHeight -> 12.dp  // Reduced from 16.dp
                                                isLargeHeight -> 20.dp     // Reduced from 32.dp
                                                else -> 16.dp              // Reduced from 24.dp
                                            }
                                        ),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    when {
                                        isLoadingLyrics -> {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                M3CircularLoader(
                                                    modifier = Modifier.size(48.dp),
                                                    fourColor = true
                                                )
                                            }
                                        }

                                        lyrics == null ||
                                                !lyrics.hasLyrics() ||
                                                lyrics.isErrorMessage() -> {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Icon(
                                                        imageVector = RhythmIcons.MusicNote,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurface.copy(
                                                            alpha = 0.8f
                                                        ),
                                                        modifier = Modifier.size(48.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Text(
                                                        text = if (onlineOnlyLyrics)
                                                            "Currently no lyrics are available for this song.\n"
                                                        else
                                                            "No lyrics available for this song.",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(
                                                            alpha = 0.8f
                                                        ),
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }

                                        else -> {
                                            // Extract appropriate lyrics text from LyricsData object
                                            val lyricsText = remember(lyrics) {
                                                lyrics?.getBestLyrics() ?: ""
                                            }

                                            val parsedLyrics = remember(lyricsText) {
                                                chromahub.rhythm.app.util.LyricsParser.parseLyrics(
                                                    lyricsText
                                                )
                                            }

                                            if (parsedLyrics.isNotEmpty()) {
                                                // Use SyncedLyricsView for synchronized lyrics
                                                SyncedLyricsView(
                                                    lyrics = lyricsText,
                                                    currentPlaybackTime = currentTimeMs,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                // Fallback to plain text lyrics if not synchronized
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .verticalScroll(rememberScrollState()),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = lyricsText,
                                                        style = MaterialTheme.typography.bodyLarge.copy(
                                                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.6f,
                                                            fontWeight = FontWeight.Medium,
                                                            letterSpacing = 0.5.sp
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 8.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isCompactHeight) 1.dp else 2.dp)) // Reduced spacer

                    AnimatedVisibility(
                        visible = song != null,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it }
                    ) {
                        if (song != null) {
                            // Optimized song info with reduced padding
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(
                                    horizontal = if (isCompactWidth) 12.dp else 16.dp, // Reduced padding
                                    vertical = 0.dp // Removed vertical padding completely
                                )
                            ) {
                                // Song title with enhanced styling - force single line
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.15.sp,
                                        fontSize = if (isCompactHeight) 22.sp else 28.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1, // Always single line
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp) // Reduced horizontal padding
                                )

                                Spacer(modifier = Modifier.height(if (isCompactHeight) 2.dp else 4.dp)) // Minimal spacing

                                // Artist and album with enhanced styling - force single line
                                Text(
                                    text = buildString {
                                        append(song.artist)
                                        if (!song.album.isNullOrBlank() && song.album != song.artist) {
                                            append(" • ")
                                            append(song.album)
                                        }
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.4.sp,
                                        fontSize = if (isCompactHeight) 14.sp else 16.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1, // Always single line
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp) // Reduced horizontal padding
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isCompactHeight) 2.dp else 4.dp)) // Much reduced from contentSpacing

                    // Optimized progress slider and time indicators - reduced padding
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (isCompactWidth) 12.dp else 16.dp, // Reduced padding
                                vertical = 0.dp // Removed vertical padding
                            )
                    ) {
                        // Progress slider
                        WaveSlider(
                            value = progress,
                            onValueChange = onSeek,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp), // Reduced padding
                            waveColor = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            isPlaying = isPlaying
                        )

                        Spacer(modifier = Modifier.height(if (isCompactHeight) 1.dp else 2.dp)) // Reduced spacing

                        // Time indicators with optimized styling and layout
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Current time with enhanced styling
                            Surface(
                                shape = RoundedCornerShape(if (isCompactHeight) 4.dp else 6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(1.dp) // Reduced padding
                            ) {
                                Text(
                                    text = currentTimeFormatted,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        fontSize = if (isCompactHeight) 12.sp else 14.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        horizontal = if (isCompactHeight) 4.dp else 6.dp, // Reduced padding
                                        vertical = 1.dp
                                    )
                                )
                            }

                            // Total time with enhanced styling
                            Surface(
                                shape = RoundedCornerShape(if (isCompactHeight) 4.dp else 6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(1.dp) // Reduced padding
                            ) {
                                Text(
                                    text = totalTimeFormatted,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        fontSize = if (isCompactHeight) 12.sp else 14.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        horizontal = if (isCompactHeight) 4.dp else 6.dp, // Reduced padding
                                        vertical = 1.dp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 6.dp)) // Reduced spacing before controls

                    // Player controls - optimized spacing and sizing
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (isCompactWidth) 12.dp else 16.dp, // Reduced padding
                                vertical = 0.dp // Removed vertical padding
                            ),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dynamic button sizes based on screen
                        val smallButtonSize = if (isCompactHeight) 40.dp else 46.dp
                        val mediumButtonSize = if (isCompactHeight) 64.dp else 72.dp
                        val largeButtonSize = if (isCompactHeight) 72.dp else 80.dp
                        val iconSizeSmall = if (isCompactHeight) 24.dp else 32.dp
                        val iconSizeMedium = if (isCompactHeight) 28.dp else 36.dp
                        val iconSizeLarge = if (isCompactHeight) 32.dp else 40.dp

                        // Skip previous button
                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSkipPrevious()
                            },
                            modifier = Modifier.size(smallButtonSize),
                            shape = RoundedCornerShape(if (isCompactHeight) 20.dp else 28.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = RhythmIcons.SkipPrevious,
                                contentDescription = "Previous track",
                                modifier = Modifier.size(iconSizeSmall)
                            )
                        }

                        // Skip backward 10 seconds button
                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSeek(
                                    progress - (10000f / (song?.duration ?: 1))
                                )
                            },
                            modifier = Modifier.size(mediumButtonSize),
                            shape = RoundedCornerShape(if (isCompactHeight) 20.dp else 28.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Replay10,
                                contentDescription = "Replay 10 seconds",
                                modifier = Modifier.size(iconSizeMedium)
                            )
                        }

                        // Play/pause button (larger)
                        FilledIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlayPause()
                            },
                            modifier = Modifier.size(largeButtonSize),
                            shape = if (isPlaying) RoundedCornerShape(if (isCompactHeight) 20.dp else 28.dp) else CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) RhythmIcons.Pause else RhythmIcons.Play,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(iconSizeLarge)
                            )
                        }

                        // Skip forward 10 seconds button
                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSeek(
                                    progress + (10000f / (song?.duration ?: 1))
                                )
                            },
                            modifier = Modifier.size(mediumButtonSize),
                            shape = RoundedCornerShape(if (isCompactHeight) 20.dp else 28.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Forward10,
                                contentDescription = "Forward 10 seconds",
                                modifier = Modifier.size(iconSizeMedium)
                            )
                        }

                        // Skip next button
                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSkipNext()
                            },
                            modifier = Modifier.size(smallButtonSize),
                            shape = RoundedCornerShape(if (isCompactHeight) 20.dp else 28.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = RhythmIcons.SkipNext,
                                contentDescription = "Next track",
                                modifier = Modifier.size(iconSizeSmall)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 8.dp)) // Reduced spacing

                    // Action buttons row - optimized layout with reduced spacing
                    if (!isCompactHeight) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = if (isCompactWidth) 16.dp else 20.dp // Reduced padding
                                ),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Optimized button size for action buttons
                            val actionButtonSize = if (isLargeHeight) 48.dp else 44.dp // Reduced sizes

                            // Shuffle button with badge if enabled
                            BadgedBox(
                                badge = {
                                    if (isShuffleEnabled) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            ) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleShuffle()
                                    },
                                    modifier = Modifier.size(actionButtonSize)
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.Shuffle,
                                        contentDescription = "Toggle shuffle",
                                        tint = if (isShuffleEnabled)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            // Favorite button
                            FilledTonalIconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleFavorite()
                                },
                                modifier = Modifier.size(actionButtonSize),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) RhythmIcons.FavoriteFilled else RhythmIcons.Favorite,
                                    contentDescription = "Toggle favorite",
                                    tint = if (isFavorite)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Lyrics toggle button (only show if lyrics are enabled)
                            if (showLyrics) {
                                FilledTonalIconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showLyricsView = !showLyricsView
                                    },
                                    modifier = Modifier.size(actionButtonSize),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.Player.Lyrics,
                                        contentDescription = "Toggle lyrics",
                                        tint = if (showLyricsView)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAddToPlaylist()
                                },
                                modifier = Modifier.size(actionButtonSize),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.AddToPlaylist,
                                    contentDescription = "Add to playlist"
                                )
                            }

                            // Repeat button with badge for mode
                            BadgedBox(
                                badge = {
                                    if (repeatMode > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ) {
                                            if (repeatMode == 1) {
                                                Text("1", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            ) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleRepeat()
                                    },
                                    modifier = Modifier.size(actionButtonSize)
                                ) {
                                    val icon = when (repeatMode) {
                                        1 -> RhythmIcons.RepeatOne
                                        2 -> RhythmIcons.Repeat
                                        else -> RhythmIcons.Repeat
                                    }

                                    val tint = when (repeatMode) {
                                        0 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        else -> MaterialTheme.colorScheme.primary
                                    }

                                    Icon(
                                        imageVector = icon,
                                        contentDescription = "Toggle repeat mode",
                                        tint = tint
                                    )
                                }
                            }
                        }
                    }

                } // Closing brace for the main content Column

                // Bottom buttons - optimized responsive design with reduced padding
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (isCompactWidth) 8.dp else 12.dp,   // Reduced padding
                                vertical = if (isCompactHeight) 8.dp else 12.dp    // Reduced padding
                            ),
                        horizontalArrangement = Arrangement.spacedBy(
                            if (isCompactWidth) 6.dp else 8.dp // Reduced spacing
                        )
                    ) {
                        // Device Output button with rounded pill shape - optimized padding
                        Card(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showDeviceOutputSheet = true
                            },
                            shape = RoundedCornerShape(if (isCompactHeight) 20.dp else 24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 0.dp
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        vertical = if (isCompactHeight) 8.dp else 10.dp,    // Reduced padding
                                        horizontal = if (isCompactWidth) 8.dp else 12.dp    // Reduced padding
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                // Choose icon based on device type
                                val icon = when {
                                    location?.id?.startsWith("bt_") == true -> RhythmIcons.BluetoothFilled
                                    location?.id == "wired_headset" -> RhythmIcons.HeadphonesFilled
                                    location?.id == "speaker" -> RhythmIcons.SpeakerFilled
                                    else -> RhythmIcons.Location
                                }

                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(if (isCompactHeight) 20.dp else 24.dp)
                                )
                                
                                if (!isCompactWidth) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = location?.name ?: "Device Output",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontSize = if (isCompactHeight) 12.sp else 14.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                        val displayVolume = if (useSystemVolume) systemVolume else volume
                                        val volumeText = if (useSystemVolume) "System" else "App"
                                        Text(
                                            text = "${(displayVolume * 100).toInt()}% $volumeText",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = if (isCompactHeight) 10.sp else 12.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // Queue button - optimized design with reduced padding
                        Card(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (song != null) {
                                    showQueueSheet = true
                                } else {
                                    onQueueClick()
                                }
                            },
                            shape = RoundedCornerShape(if (isCompactHeight) 20.dp else 24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 0.dp
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        vertical = if (isCompactHeight) 8.dp else 10.dp,    // Reduced padding
                                        horizontal = if (isCompactWidth) 8.dp else 12.dp    // Reduced padding
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Queue,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(if (isCompactHeight) 20.dp else 24.dp)
                                )
                                
                                if (!isCompactWidth) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Queue",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontSize = if (isCompactHeight) 12.sp else 14.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "$queuePosition of $queueTotal",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = if (isCompactHeight) 10.sp else 12.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
