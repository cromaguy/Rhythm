/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
package chromahub.rhythm.app.features.local.presentation.screens

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.common.ContentLoadingIndicator
import chromahub.rhythm.app.shared.presentation.components.common.DataProcessingLoader
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveOutlinedButton
import chromahub.rhythm.app.shared.presentation.theme.ExpressiveMaterialShape
import chromahub.rhythm.app.shared.presentation.theme.rememberExpressiveShape
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri

// Data classes for explorer functionality
data class ExplorerItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val itemCount: Int,
    val type: ExplorerItemType,
    val song: Song? = null
)

enum class ExplorerItemType {
    STORAGE, FOLDER, FILE
}

@Composable
private fun AnimateIn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 50),
        label = "alpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Box(
        modifier = modifier.graphicsLayer(
            alpha = alpha,
            scaleX = scale,
            scaleY = scale
        )
    ) {
        content()
    }
}

const val FOLDER_NAVIGATION_FORWARD = 1
const val FOLDER_NAVIGATION_BACKWARD = -1

fun resolveFolderNavigationDirection(initialPath: String?, targetPath: String?): Int =
    when {
        initialPath == targetPath -> FOLDER_NAVIGATION_FORWARD
        initialPath == null && targetPath != null -> FOLDER_NAVIGATION_FORWARD
        initialPath != null && targetPath == null -> FOLDER_NAVIGATION_BACKWARD
        initialPath != null && targetPath != null && isDescendantFolderPath(initialPath, targetPath) -> FOLDER_NAVIGATION_FORWARD
        initialPath != null && targetPath != null && isDescendantFolderPath(targetPath, initialPath) -> FOLDER_NAVIGATION_BACKWARD
        else -> FOLDER_NAVIGATION_FORWARD
    }

private fun isDescendantFolderPath(ancestorPath: String, candidatePath: String): Boolean {
    val normalizedAncestor = ancestorPath.replace('\\', '/').trimEnd('/')
    val normalizedCandidate = candidatePath.replace('\\', '/').trimEnd('/')
    if (normalizedAncestor == normalizedCandidate) return false
    return normalizedCandidate.startsWith("$normalizedAncestor/")
}

@Composable
fun SingleCardExplorerContent(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onShowSongInfo: (Song) -> Unit,
    onPlayQueue: (List<Song>) -> Unit,
    onPlayQueueFromIndex: (List<Song>, Int) -> Unit,
    onShuffleQueue: (List<Song>) -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    appSettings: AppSettings,
    currentPath: String?,
    onPathChanged: (String?) -> Unit,
    onFolderSongsChanged: (List<Song>) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    reloadTrigger: Int = 0,
    onCreatePlaylist: (String) -> Unit = { _ -> },
    musicViewModel: MusicViewModel,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (Song) -> Unit = {},
    onSongSelectionToggle: (Song) -> Unit = {},
    multiSelectionState: chromahub.rhythm.app.features.local.presentation.viewmodel.MultiSelectionStateHolder? = null
) {
    val context = LocalContext.current
    val activity = context as Activity
    
    // State for creating playlist from folder
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var folderSongsForPlaylist by remember { mutableStateOf<List<Song>>(emptyList()) }
    var playlistNamePrefix by remember { mutableStateOf("") }
    
    val playlists by musicViewModel.playlists.collectAsState()

    // Check storage permission based on Android version
    val hasStoragePermission = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    var showPermissionDialog by remember { mutableStateOf(false) }
    var isLoadingDirectory by remember { mutableStateOf(false) }
    var isInitialLoading by remember { mutableStateOf(true) }

    // Handle permission result in a LaunchedEffect
    LaunchedEffect(hasStoragePermission) {
        if (!hasStoragePermission) {
            showPermissionDialog = true
        }
    }

    // Permission not granted - show request UI
    if (!hasStoragePermission || showPermissionDialog) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = RhythmIcons.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Text(
                        text = context.getString(R.string.storage_permission_required),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = context.getString(R.string.storage_permission_desc),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)

                            when {
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                                    ActivityCompat.requestPermissions(
                                        activity,
                                        arrayOf(Manifest.permission.READ_MEDIA_AUDIO),
                                        1001
                                    )
                                }
                                else -> {
                                    ActivityCompat.requestPermissions(
                                        activity,
                                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                                        1001
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (hasStoragePermission) RhythmIcons.Check else MaterialSymbolIcon("lock"),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasStoragePermission) "Permission Granted" else "Grant Permission",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = context.getString(R.string.storage_permission_audio_only),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    val audioExtensions = remember {
        setOf("mp3", "flac", "m4a", "mp4", "aac", "ogg", "wav", "wma", "aiff", "aif", "opus", "opa", "mkv", "mka", "ac3", "ac4", "eac", "eac3", "dts", "dtshd", "dtsx", "truehd", "alac", "m4b", "oga", "mid", "midi", "adts", "ape", "wv", "tta", "tak", "dsf", "dff", "dsd", "mhm", "mhm1")
    }

    var songPathMap by remember { mutableStateOf<Map<String, Song>>(emptyMap()) }
    var isPathMapLoading by remember { mutableStateOf(true) }
    var songPathMapVersion by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(songs) {
        isPathMapLoading = true
        songPathMap = withContext(Dispatchers.Default) {
            val map = mutableMapOf<String, Song>()
            songs.forEach { song ->
                try {
                    val path = song.getSongPath(context)
                    if (path != null && path.isNotEmpty()) {
                        val normalizedPath = path.replace("//", "/").trimEnd('/')
                        map[normalizedPath] = song
                    }
                } catch (e: Exception) {
                    // Skip
                }
            }
            android.util.Log.d("LibraryScreen", "Pre-computed path map with ${map.size} songs out of ${songs.size} total")
            map
        }
        songPathMapVersion++
        isPathMapLoading = false
    }

    var currentItems by remember { mutableStateOf<List<ExplorerItem>>(emptyList()) }
    val pinnedFolders by appSettings.pinnedFolders.collectAsState()
    val breadcrumbScrollState = rememberLazyListState()

    LaunchedEffect(currentItems, currentPath) {
        if (currentItems.isNotEmpty()) {
            isLoadingDirectory = false
            isInitialLoading = false
        }
    }

    val directoryCache = remember { mutableMapOf<String?, List<ExplorerItem>>() }
    var lastCacheVersion by remember { mutableIntStateOf(-1) }
    var debounceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(reloadTrigger) {
        if (reloadTrigger > 0) {
            directoryCache.clear()
            val cacheKey = currentPath
            directoryCache.remove(cacheKey)
            debounceJob?.cancel()
            
            if (currentPath == null) {
                val storageItems = getStorageRoots(context)
                currentItems = storageItems
                directoryCache[cacheKey] = storageItems
                isLoadingDirectory = false
            } else {
                if (isPathMapLoading) {
                    isLoadingDirectory = true
                    return@LaunchedEffect
                }
                
                isLoadingDirectory = true
                try {
                    val items = withContext(Dispatchers.IO) {
                        getDirectoryContentsOptimized(currentPath, songPathMap, context)
                    }
                    val sortedItems = items.sortedWith(
                        compareBy<ExplorerItem> { it.type != ExplorerItemType.FOLDER }
                            .thenBy { it.name.lowercase() }
                    )
                    currentItems = sortedItems
                    directoryCache[cacheKey] = sortedItems
                } catch (e: Exception) {
                    currentItems = emptyList()
                } finally {
                    isLoadingDirectory = false
                }
            }
        }
    }

    LaunchedEffect(currentPath, songPathMap) {
        debounceJob?.cancel()
        val cacheKey = currentPath
        
        if (currentPath == null) {
            isLoadingDirectory = true
            val storageItems = withContext(Dispatchers.IO) {
                getStorageRoots(context)
            }
            currentItems = storageItems
            directoryCache[cacheKey] = storageItems
            isLoadingDirectory = false
            isInitialLoading = false
        } else {
            if (isPathMapLoading) {
                isLoadingDirectory = true
                return@LaunchedEffect
            }
            
            if (lastCacheVersion != songPathMapVersion) {
                android.util.Log.d("LibraryScreen", "SongPathMap version changed, clearing cache")
                directoryCache.clear()
                lastCacheVersion = songPathMapVersion
            }
            
            val cached = directoryCache[cacheKey]
            if (cached != null) {
                isLoadingDirectory = false
                currentItems = cached
            } else {
                isLoadingDirectory = true
                currentItems = emptyList()
                
                debounceJob = launch {
                    try {
                        val items = withContext(Dispatchers.IO) {
                            getDirectoryContentsOptimized(currentPath, songPathMap, context)
                        }
                        
                        val sortedItems = items.sortedWith(
                            compareBy<ExplorerItem> { it.type != ExplorerItemType.FOLDER }
                                .thenBy { it.name.lowercase() }
                        )
                        
                        if (isActive) {
                            currentItems = sortedItems
                            if (directoryCache.size >= 20) {
                                directoryCache.remove(directoryCache.keys.first())
                            }
                            directoryCache[cacheKey] = sortedItems
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            android.util.Log.e("LibraryScreen", "Error loading directory $currentPath", e)
                            isInitialLoading = false
                            val previousCache = directoryCache[cacheKey]
                            if (previousCache != null) {
                                currentItems = previousCache
                            } else {
                                currentItems = emptyList()
                            }
                        }
                    } finally {
                        if (isActive) {
                            isLoadingDirectory = false
                        }
                    }
                }
            }
        }
    }

    val currentFolderSongs = remember(currentItems) {
        currentItems.filter { it.type == ExplorerItemType.FILE && it.song != null }
            .mapNotNull { it.song }
    }

    LaunchedEffect(currentFolderSongs) {
        onFolderSongsChanged(currentFolderSongs)
    }

    BackHandler(enabled = currentPath != null && !isSelectionMode) {
        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
        onPathChanged(getParentPath(currentPath!!))
    }

    AnimatedContent(
        targetState = currentPath,
        label = "ExplorerFolderNavigation",
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val direction = resolveFolderNavigationDirection(
                initialPath = initialState,
                targetPath = targetState
            )
            val slideIn = slideInHorizontally { width ->
                if (direction == FOLDER_NAVIGATION_FORWARD) width else -width
            } + fadeIn()
            val slideOut = slideOutHorizontally { width ->
                if (direction == FOLDER_NAVIGATION_FORWARD) -width else width
            } + fadeOut()

            slideIn.togetherWith(slideOut)
        }
    ) { activePath ->
        val itemsForActivePath = directoryCache[activePath] ?: if (activePath == currentPath) currentItems else emptyList()
        val isPathLoading = (isLoadingDirectory || isInitialLoading || (isPathMapLoading && activePath != null)) && itemsForActivePath.isEmpty()
        val pathFolderSongs = remember(itemsForActivePath) {
            itemsForActivePath.filter { it.type == ExplorerItemType.FILE && it.song != null }.mapNotNull { it.song }
        }
        val localListState = if (activePath == currentPath) listState else rememberLazyListState()

        LazyColumn(
            state = localListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = bottomPadding + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (isPathLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ContentLoadingIndicator(
                                modifier = Modifier.size(48.dp)
                            )

                            Text(
                                text = if (isInitialLoading && activePath == null) {
                                    "Initializing Explorer..."
                                } else if (isPathMapLoading) {
                                    "Indexing music files..."
                                } else {
                                    "Loading directory..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!isInitialLoading && activePath == null && itemsForActivePath.any { it.type == ExplorerItemType.STORAGE }) {
                val storageItems = itemsForActivePath.filter { it.type == ExplorerItemType.STORAGE }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Storage,
                            contentDescription = stringResource(R.string.cd_storage),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = context.getString(R.string.library_storage_locations),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                itemsIndexed(
                    items = storageItems,
                    key = { _, item -> "storage_${item.path}" }
                ) { index, item ->
                    ExplorerItemCard(
                        item = item,
                        onItemClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            onPathChanged(item.path)
                        },
                        onSongClick = onSongClick,
                        onAddToPlaylist = onAddToPlaylist,
                        onAddToQueue = onAddToQueue,
                        onShowSongInfo = onShowSongInfo,
                        onAddToBlacklist = { song -> appSettings.addToBlacklist(song.id) },
                        haptics = haptics,
                        isPinned = false,
                        onPinToggle = null,
                        onPlayFolder = null,
                        onAddFolderToQueue = null,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        itemShape = groupedLibraryItemShape(index, storageItems.size)
                    )
                }
            }

            if (!isInitialLoading && activePath == null && pinnedFolders.isNotEmpty()) {
                val existingPinnedFolders = pinnedFolders.filter { pinnedPath ->
                    try {
                        val file = File(pinnedPath)
                        file.exists() && file.isDirectory && file.canRead()
                    } catch (e: Exception) {
                        false
                    }
                }

                if (existingPinnedFolders.isNotEmpty()) {
                    val pinnedFolderItems = existingPinnedFolders.map { folderPath ->
                        val folderName = File(folderPath).name
                        val itemCount = countAudioFilesInDirectoryShallow(File(folderPath), audioExtensions)
                        ExplorerItem(
                            name = folderName,
                            path = folderPath,
                            isDirectory = true,
                            itemCount = itemCount,
                            type = ExplorerItemType.FOLDER,
                            song = null
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Pushpin,
                                contentDescription = stringResource(R.string.cd_pinned),
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = context.getString(R.string.library_pinned_folders),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    itemsIndexed(
                        items = pinnedFolderItems,
                        key = { _, item -> "pinned_${item.path}" }
                    ) { index, item ->
                        ExplorerItemCard(
                            item = item,
                            onItemClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                onPathChanged(item.path)
                            },
                            onSongClick = onSongClick,
                            onAddToPlaylist = onAddToPlaylist,
                            onAddToQueue = onAddToQueue,
                            onShowSongInfo = onShowSongInfo,
                            onAddToBlacklist = { song -> appSettings.addToBlacklist(song.id) },
                            haptics = haptics,
                            isPinned = true,
                            onPinToggle = {
                                appSettings.removeFolderFromPinned(item.path)
                            },
                            onPlayFolder = { folderItem ->
                                val folderSongs = findSongsInFolder(folderItem.path, songPathMap, songs, context)
                                if (folderSongs.isNotEmpty()) {
                                    folderSongsForPlaylist = folderSongs
                                    playlistNamePrefix = folderItem.name
                                    showCreatePlaylistDialog = true
                                }
                            },
                            onAddFolderToQueue = { folderItem ->
                                val folderSongs = findSongsInFolder(folderItem.path, songPathMap, songs, context)
                                if (folderSongs.isNotEmpty()) {
                                    musicViewModel.addSongsToQueue(folderSongs)
                                }
                            },
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            itemShape = groupedLibraryItemShape(index, pinnedFolderItems.size)
                        )
                    }
                }
            }

            if (!isPathLoading && activePath != null) {
                itemsIndexed(
                    items = itemsForActivePath,
                    key = { _, item -> 
                        if (item.type == ExplorerItemType.FILE && item.song != null) {
                            "song_${item.song.id}"
                        } else {
                            "${item.type}_${item.path}_${item.name}"
                        }
                    }
                ) { index, item ->
                    ExplorerItemCard(
                        item = item,
                        onItemClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)

                            when (item.type) {
                                ExplorerItemType.STORAGE, ExplorerItemType.FOLDER -> {
                                    onPathChanged(item.path)
                                }
                                ExplorerItemType.FILE -> {
                                    item.song?.let { song ->
                                        if (isSelectionMode) {
                                            onSongSelectionToggle(song)
                                        } else {
                                            val songIndex = pathFolderSongs.indexOfFirst { it.id == song.id }
                                            if (songIndex >= 0) {
                                                onPlayQueueFromIndex(pathFolderSongs, songIndex)
                                            } else {
                                                onSongClick(song)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onSongClick = onSongClick,
                        onAddToPlaylist = onAddToPlaylist,
                        onAddToQueue = onAddToQueue,
                        onShowSongInfo = onShowSongInfo,
                        onAddToBlacklist = { song -> appSettings.addToBlacklist(song.id) },
                        haptics = haptics,
                        isPinned = pinnedFolders.contains(item.path),
                        onPinToggle = if (item.type == ExplorerItemType.FOLDER) {
                            {
                                if (pinnedFolders.contains(item.path)) {
                                    appSettings.removeFolderFromPinned(item.path)
                                } else {
                                    appSettings.addFolderToPinned(item.path)
                                }
                            }
                        } else null,
                        onPlayFolder = if (item.type == ExplorerItemType.FOLDER) {
                            { folderItem ->
                                val folderSongs = findSongsInFolder(folderItem.path, songPathMap, songs, context)
                                if (folderSongs.isNotEmpty()) {
                                    folderSongsForPlaylist = folderSongs
                                    playlistNamePrefix = folderItem.name
                                    showCreatePlaylistDialog = true
                                }
                            }
                        } else null,
                        onAddFolderToQueue = if (item.type == ExplorerItemType.FOLDER) {
                            { folderItem ->
                                val folderSongs = findSongsInFolder(folderItem.path, songPathMap, songs, context)
                                if (folderSongs.isNotEmpty()) {
                                    musicViewModel.addSongsToQueue(folderSongs)
                                }
                            }
                        } else null,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        itemShape = groupedLibraryItemShape(index, itemsForActivePath.size),
                        isSelected = item.song?.let { selectedSongIds.contains(it.id) } ?: false,
                        isSelectionMode = isSelectionMode,
                        selectionIndex = item.song?.let { multiSelectionState?.getSelectionIndex(it.id) },
                        onLongPress = { item.song?.let { onSongLongPress(it) } }
                    )
                }
            }

            if (!isInitialLoading && itemsForActivePath.isEmpty() && !isPathLoading) {
                item {
                    val cookieShape = rememberExpressiveShape(ExpressiveMaterialShape.COOKIE_12)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 28.dp, vertical = 32.dp)
                            ) {
                                Surface(
                                    shape = cookieShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = MaterialSymbolIcon("folder_off"),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = if (activePath == null)
                                        context.getString(R.string.explorer_no_storage)
                                    else
                                        context.getString(R.string.explorer_empty_folder),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = if (activePath == null)
                                        context.getString(R.string.explorer_no_storage_desc)
                                    else
                                        context.getString(R.string.explorer_empty_folder_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = RoundedCornerShape(12.dp),
                                    tonalElevation = 0.dp,
                                    modifier = Modifier.padding(top = 20.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = context.getString(R.string.library_check_permissions),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (activePath != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    ExpressiveOutlinedButton(
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                            onPathChanged(getParentPath(activePath))
                                        },
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Back,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = context.getString(R.string.library_go_back),
                                            style = MaterialTheme.typography.labelMedium
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
    
    // Create playlist dialog for folder
    if (showCreatePlaylistDialog) {
        val scope = rememberCoroutineScope()
        var playlistName by remember { mutableStateOf(playlistNamePrefix) }
        var isCreating by remember { mutableStateOf(false) }
        var isError by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = {
                if (!isCreating) {
                    showCreatePlaylistDialog = false
                    folderSongsForPlaylist = emptyList()
                    playlistNamePrefix = ""
                }
            },
            icon = {
                Icon(
                    imageVector = RhythmIcons.AddToPlaylist,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    "Create Playlist from Folder",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (isCreating) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        ) {
                            DataProcessingLoader(
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = context.resources.getQuantityString(R.plurals.library_creating_playlist, folderSongsForPlaylist.size, folderSongsForPlaylist.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            "Add ${folderSongsForPlaylist.size} ${if (folderSongsForPlaylist.size == 1) "song" else "songs"} to a new playlist",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = playlistName,
                            onValueChange = { 
                                playlistName = it
                                isError = it.isBlank()
                            },
                            label = { Text(stringResource(R.string.playlist_name_field)) },
                            isError = isError,
                            supportingText = {
                                if (isError) {
                                    Text(
                                        text = context.getString(R.string.library_playlist_name_empty),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (!isCreating) {
                    Button(
                        onClick = {
                            if (playlistName.isBlank()) {
                                isError = true
                            } else {
                                isCreating = true
                                scope.launch {
                                    try {
                                        musicViewModel.createPlaylist(playlistName, folderSongsForPlaylist)
                                        Log.d("LibraryScreen", "Successfully created playlist '$playlistName' with ${folderSongsForPlaylist.size} songs")
                                        showCreatePlaylistDialog = false
                                        folderSongsForPlaylist = emptyList()
                                        playlistNamePrefix = ""
                                        isCreating = false
                                    } catch (e: Exception) {
                                        Log.e("LibraryScreen", "Error creating playlist", e)
                                        isCreating = false
                                    }
                                }
                            }
                        },
                        enabled = playlistName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = RhythmIcons.AddToPlaylist,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_create))
                    }
                }
            },
            dismissButton = {
                if (!isCreating) {
                    OutlinedButton(
                        onClick = {
                            showCreatePlaylistDialog = false
                            folderSongsForPlaylist = emptyList()
                            playlistNamePrefix = ""
                        }
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_cancel))
                    }
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

fun getStorageRoots(context: android.content.Context): List<ExplorerItem> {
    val items = mutableListOf<ExplorerItem>()

    try {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P || Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            android.util.Log.d("LibraryScreen", "Using Android 9/10 specific storage access method")
            
            val externalFilesDirs = context.getExternalFilesDirs(null)
            externalFilesDirs.forEachIndexed { index, dir ->
                if (dir != null) {
                    try {
                        var storageRoot = dir
                        var depth = 0
                        while (storageRoot.parent != null && depth < 10) {
                            val parent = storageRoot.parentFile
                            if (parent == null || parent.name == "storage" || parent.absolutePath == "/storage") {
                                break
                            }
                            storageRoot = parent
                            depth++
                        }
                        
                        val canAccess = try {
                            storageRoot.exists() && (storageRoot.canRead() || storageRoot.list() != null)
                        } catch (e: SecurityException) {
                            android.util.Log.w("LibraryScreen", "Cannot access storage at ${storageRoot.absolutePath}", e)
                            false
                        }
                        
                        if (canAccess && !items.any { it.path == storageRoot.absolutePath }) {
                            val storageName = if (index == 0) "Internal Storage" else "SD Card ${if (index > 1) index else ""}".trim()
                            items.add(ExplorerItem(
                                name = storageName,
                                path = storageRoot.absolutePath,
                                isDirectory = true,
                                itemCount = 0,
                                type = ExplorerItemType.STORAGE,
                                song = null
                            ))
                            android.util.Log.d("LibraryScreen", "Added storage root: $storageName at ${storageRoot.absolutePath}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("LibraryScreen", "Error processing external dir: ${dir.absolutePath}", e)
                    }
                }
            }
            
            if (items.isEmpty() || Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
                try {
                    @Suppress("DEPRECATION")
                    val primaryExternal = Environment.getExternalStorageDirectory()
                    if (primaryExternal.exists() && !items.any { it.path == primaryExternal.absolutePath }) {
                        items.add(0, ExplorerItem(
                            name = "Internal Storage",
                            path = primaryExternal.absolutePath,
                            isDirectory = true,
                            itemCount = 0,
                            type = ExplorerItemType.STORAGE,
                            song = null
                        ))
                        android.util.Log.d("LibraryScreen", "Added primary external storage via Environment API")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("LibraryScreen", "Cannot access primary external storage via Environment API", e)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val internalStorage = Environment.getExternalStorageDirectory()
            if (internalStorage.exists()) {
                items.add(ExplorerItem(
                    name = "Internal Storage",
                    path = internalStorage.absolutePath,
                    isDirectory = true,
                    itemCount = 0,
                    type = ExplorerItemType.STORAGE,
                    song = null
                ))
            }

            val externalDirs = context.getExternalFilesDirs(null)
            externalDirs.forEachIndexed { index, dir ->
                if (dir != null && index > 0) {
                    var sdCardRoot = dir
                    var depth = 0
                    while (sdCardRoot.parent != null && depth < 10) {
                        sdCardRoot = sdCardRoot.parentFile ?: break
                        depth++
                        if (sdCardRoot.parent == "/storage" || sdCardRoot.parentFile?.name == "storage") {
                            break
                        }
                    }
                    
                    if (sdCardRoot.exists() && sdCardRoot.canRead()) {
                        val storageName = "SD Card ${if (index > 1) index else ""}"
                        items.add(ExplorerItem(
                            name = storageName.trim(),
                            path = sdCardRoot.absolutePath,
                            isDirectory = true,
                            itemCount = 0,
                            type = ExplorerItemType.STORAGE,
                            song = null
                        ))
                    }
                }
            }
            
            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                storageDir.listFiles()?.forEach { file ->
                    if (file.isDirectory && 
                        file.name != "emulated" && 
                        file.name != "self" && 
                        !file.name.startsWith(".") &&
                        file.canRead() &&
                        !items.any { it.path == file.absolutePath }) {
                        
                        items.add(ExplorerItem(
                            name = "Removable Storage (${file.name})",
                            path = file.absolutePath,
                            isDirectory = true,
                            itemCount = 0,
                            type = ExplorerItemType.STORAGE,
                            song = null
                        ))
                    }
                }
            }
        }
        
        android.util.Log.d("LibraryScreen", "Found ${items.size} storage roots")
    } catch (e: Exception) {
        android.util.Log.e("LibraryScreen", "Error getting storage roots", e)
    }

    return items
}

fun getParentDirectory(uriString: String): String {
    return try {
        val uri = (uriString).toUri()
        val path = uri.path ?: ""
        val lastSlashIndex = path.lastIndexOf('/')
        if (lastSlashIndex > 0) {
            path.substring(0, lastSlashIndex)
        } else {
            path
        }
    } catch (e: Exception) {
        ""
    }
}

fun getParentPath(path: String): String? {
    val lastSlashIndex = path.lastIndexOf('/')
    return if (lastSlashIndex > 0) {
        path.substring(0, lastSlashIndex)
    } else {
        null
    }
}

fun getRootDirectories(songs: List<Song>): List<ExplorerItem> {
    val directories = mutableListOf<String>()

    songs.forEach { song ->
        try {
            val uri = (song.uri.toString()).toUri()
            val path = uri.path ?: ""
            val dirPath = path.substringBeforeLast('/', "")

            if (dirPath.isNotEmpty()) {
                val normalizedDir = dirPath.replace("//", "/")
                if (!directories.contains(normalizedDir)) {
                    directories.add(normalizedDir)
                }
            }
        } catch (e: Exception) {
            // Skip
        }
    }

    return directories.map { dirPath ->
        val itemCount = songs.count { song ->
            try {
                val songPath = (song.uri.toString()).toUri().path ?: ""
                val songDir = songPath.substringBeforeLast('/', "")
                songDir == dirPath
            } catch (e: Exception) {
                false
            }
        }

        val dirName = dirPath.substringAfterLast('/').takeIf { it.isNotEmpty() } ?: dirPath

        ExplorerItem(
            name = dirName,
            path = dirPath,
            isDirectory = true,
            itemCount = itemCount,
            type = ExplorerItemType.FOLDER,
            song = null
        )
    }
}

fun getAudioFileCountSongsInDirectory(
    songs: List<Song>,
    directoryPath: String,
    audioExtensions: Set<String>
): Int {
    return songs.count { song ->
        try {
            val songPath = (song.uri.toString()).toUri().path ?: ""
            val normalizedSongPath = songPath.replace("//", "/")
            val normalizedDirPath = directoryPath.replace("//", "/")

            normalizedSongPath.startsWith(normalizedDirPath) && normalizedSongPath != normalizedDirPath
        } catch (e: Exception) {
            false
        }
    }
}

fun getDirectoryContentsOptimized(directoryPath: String, songPathMap: Map<String, Song>, context: android.content.Context): List<ExplorerItem> {
    val startTime = System.currentTimeMillis()
    android.util.Log.d("LibraryScreen", "Loading directory: $directoryPath (Android ${Build.VERSION.SDK_INT})")
    
    val items = mutableListOf<ExplorerItem>()
    val normalizedDirPath = directoryPath.replace("//", "/").trimEnd('/')
    val audioExtensions = setOf("mp3", "flac", "m4a", "mp4", "aac", "ogg", "wav", "wma", "aiff", "aif", "opus", "opa", "mkv", "mka", "ac3", "ac4", "eac", "eac3", "dts", "dtshd", "dtsx", "truehd", "alac", "m4b", "oga", "mid", "midi", "adts", "ape", "wv", "tta", "tak", "dsf", "dff", "dsd", "mhm", "mhm1")
    
    val subdirectorySongCounts = mutableMapOf<String, Int>()
    val directoriesWithSongs = mutableSetOf<String>()
    val songsInCurrentDir = mutableListOf<Pair<String, Song>>()
    
    songPathMap.forEach { (normalizedSongPath, song) ->
        try {
            val parentDir = File(normalizedSongPath).parent?.replace("//", "/")?.trimEnd('/') ?: return@forEach
            
            if (parentDir == normalizedDirPath) {
                songsInCurrentDir.add(normalizedSongPath to song)
            } else if (parentDir.startsWith("$normalizedDirPath/")) {
                val relativePath = parentDir.removePrefix("$normalizedDirPath/")
                val firstSlash = relativePath.indexOf('/')
                val immediateChild = if (firstSlash > 0) {
                    relativePath.substring(0, firstSlash)
                } else {
                    relativePath
                }
                val childPath = "$normalizedDirPath/$immediateChild"
                subdirectorySongCounts[childPath] = (subdirectorySongCounts[childPath] ?: 0) + 1
                directoriesWithSongs.add(childPath)
            }
        } catch (e: Exception) {
            // Skip
        }
    }
    
    android.util.Log.d("LibraryScreen", "Found ${songsInCurrentDir.size} songs in current dir, ${subdirectorySongCounts.size} subdirs with songs")
    
    try {
        val directory = File(directoryPath)
        if (!directory.exists()) {
            android.util.Log.d("LibraryScreen", "Directory does not exist: $directoryPath")
            return items
        }
        
        val files = try {
            directory.listFiles()
        } catch (e: SecurityException) {
            android.util.Log.w("LibraryScreen", "Cannot list files for $directoryPath due to permissions, using MediaStore fallback", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("LibraryScreen", "Error listing files for $directoryPath", e)
            null
        }
        
        if (files != null) {
            android.util.Log.d("LibraryScreen", "File system listing succeeded, found ${files.size} items")
            files.forEach { file ->
                try {
                    if (file.isDirectory) {
                        if (!file.name.startsWith(".")) {
                            val folderPath = file.absolutePath.replace("//", "/").trimEnd('/')
                            var audioCount = subdirectorySongCounts[folderPath] ?: 0
                            
                            if (audioCount == 0) {
                                try {
                                    val canonicalPath = file.canonicalPath.replace("//", "/").trimEnd('/')
                                    audioCount = subdirectorySongCounts[canonicalPath] ?: 0
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }
                            
                            if (audioCount == 0) {
                                val folderPrefix = "$folderPath/"
                                audioCount = songPathMap.keys.count { it.startsWith(folderPrefix) }
                            }
                            
                            if (audioCount > 0) {
                                items.add(ExplorerItem(
                                    name = file.name,
                                    path = file.absolutePath,
                                    isDirectory = true,
                                    itemCount = audioCount,
                                    type = ExplorerItemType.FOLDER,
                                    song = null
                                ))
                            }
                        }
                    } else if (file.isFile) {
                        val extension = file.extension.lowercase()
                        if (extension in audioExtensions) {
                            val normalizedPath = file.absolutePath.replace("//", "/")
                            var song = songPathMap[normalizedPath]
                            
                            if (song == null) {
                                song = songPathMap[file.absolutePath]
                            }
                            
                            if (song == null) {
                                val lowercasePath = normalizedPath.lowercase()
                                song = songPathMap.entries.find { 
                                    it.key.lowercase() == lowercasePath 
                                }?.value
                            }
                            
                            if (song != null) {
                                items.add(ExplorerItem(
                                    name = song.title,
                                    path = file.absolutePath,
                                    isDirectory = false,
                                    itemCount = 1,
                                    type = ExplorerItemType.FILE,
                                    song = song
                                ))
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.d("LibraryScreen", "Skipping file ${file.name}: ${e.message}")
                }
            }
            
            if (items.none { !it.isDirectory } && songsInCurrentDir.isNotEmpty()) {
                android.util.Log.d("LibraryScreen", "File system scan missed songs, using pre-computed list")
                songsInCurrentDir.forEach { (path, song) ->
                    items.add(ExplorerItem(
                        name = song.title,
                        path = path,
                        isDirectory = false,
                        itemCount = 1,
                        type = ExplorerItemType.FILE,
                        song = song
                    ))
                }
            }
            
            val existingFolderPaths = items.filter { it.isDirectory }.map { it.path.replace("//", "/").trimEnd('/') }.toSet()
            directoriesWithSongs.forEach { subdirPath ->
                val normalizedSubdirPath = subdirPath.replace("//", "/").trimEnd('/')
                if (normalizedSubdirPath !in existingFolderPaths) {
                    val subdirName = File(subdirPath).name
                    val songCount = subdirectorySongCounts[subdirPath] ?: 0
                    if (songCount > 0) {
                        android.util.Log.d("LibraryScreen", "Adding missed folder: $subdirPath with $songCount songs")
                        items.add(ExplorerItem(
                            name = subdirName,
                            path = subdirPath,
                            isDirectory = true,
                            itemCount = songCount,
                            type = ExplorerItemType.FOLDER,
                            song = null
                        ))
                    }
                }
            }
        } else {
            android.util.Log.w("LibraryScreen", "Using MediaStore-only fallback for $directoryPath")
            
            songsInCurrentDir.forEach { (path, song) ->
                items.add(ExplorerItem(
                    name = song.title,
                    path = path,
                    isDirectory = false,
                    itemCount = 1,
                    type = ExplorerItemType.FILE,
                    song = song
                ))
            }
            
            directoriesWithSongs.sorted().forEach { subdirPath ->
                val subdirName = File(subdirPath).name
                val songCount = subdirectorySongCounts[subdirPath] ?: 0
                
                if (songCount > 0) {
                    items.add(ExplorerItem(
                        name = subdirName,
                        path = subdirPath,
                        isDirectory = true,
                        itemCount = songCount,
                        type = ExplorerItemType.FOLDER,
                        song = null
                    ))
                }
            }
            
            android.util.Log.d("LibraryScreen", "MediaStore fallback found ${items.filter { !it.isDirectory }.size} songs and ${items.filter { it.isDirectory }.size} folders")
        }
    } catch (e: Exception) {
        android.util.Log.e("LibraryScreen", "Error reading directory: $directoryPath", e)
    }
    
    val elapsed = System.currentTimeMillis() - startTime
    android.util.Log.d("LibraryScreen", "Loaded ${items.size} items in ${elapsed}ms")
    
    return items
}

fun getDirectoryContentsOptimized_OLD(directoryPath: String, audioExtensions: Set<String>, songs: List<Song>, context: android.content.Context): List<ExplorerItem> {
    val items = mutableListOf<ExplorerItem>()

    try {
        val directory = File(directoryPath)
        if (!directory.exists()) {
            return items
        }

        val songsByPath = java.util.concurrent.ConcurrentHashMap<String, Song>()
        val dirPath = directoryPath.replace("//", "/").trimEnd('/')
        
        val relevantSongs = songs.asSequence()
            .filter { song ->
                try {
                    val uriPath = song.uri.path
                    uriPath != null && uriPath.contains(dirPath, ignoreCase = true)
                } catch (e: Exception) {
                    false
                }
            }
            .toList()
        
        relevantSongs.forEach { song ->
            try {
                val filePath = song.getSongPath(context)
                if (filePath != null) {
                    val normalizedPath = filePath.replace("//", "/")
                    songsByPath[normalizedPath] = song
                }
            } catch (e: Exception) {
                // Skip
            }
        }

        val files = try {
            directory.listFiles()
        } catch (e: SecurityException) {
            android.util.Log.d("LibraryScreen", "Cannot list files directly for $directoryPath, using MediaStore fallback")
            val songsInDir = relevantSongs
            val subdirs = mutableSetOf<String>()
            songsInDir.forEach { song ->
                try {
                    val songPath = song.getSongPath(context) ?: return@forEach
                    val normalizedSongPath = songPath.replace("//", "/")
                    val normalizedDirPath = dirPath.trimEnd('/')
                    
                    val relativePath = if (normalizedSongPath.startsWith("$normalizedDirPath/")) {
                        normalizedSongPath.removePrefix("$normalizedDirPath/")
                    } else {
                        normalizedSongPath.removePrefix(normalizedDirPath).removePrefix("/")
                    }
                    
                    val firstSlash = relativePath.indexOf('/')
                    if (firstSlash > 0) {
                        subdirs.add(relativePath.substring(0, firstSlash))
                    } else if (firstSlash < 0 && relativePath.isNotEmpty()) {
                        val extension = File(songPath).extension.lowercase()
                        if (extension in audioExtensions) {
                            items.add(ExplorerItem(
                                name = song.title,
                                path = songPath,
                                isDirectory = false,
                                itemCount = 1,
                                type = ExplorerItemType.FILE,
                                song = song
                            ))
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LibraryScreen", "Error processing song ${song.title}", e)
                }
            }
            
            subdirs.forEach { subdir ->
                val subdirPath = "$dirPath/$subdir"
                val audioCount = songsInDir.count { song ->
                    try {
                        val songPath = song.getSongPath(context)
                        songPath != null && songPath.replace("//", "/").startsWith("$subdirPath/")
                    } catch (e: Exception) {
                        false
                    }
                }
                if (audioCount > 0) {
                    items.add(ExplorerItem(
                        name = subdir,
                        path = subdirPath,
                        isDirectory = true,
                        itemCount = audioCount,
                        type = ExplorerItemType.FOLDER,
                        song = null
                    ))
                }
            }
            
            return items
        }
        
        val maxFiles = 200
        val filesToProcess = files?.let { fileArray ->
            if (fileArray.size > maxFiles) {
                fileArray.sortedWith(compareBy(
                    { !it.isDirectory },
                    { !audioExtensions.contains(it.extension.lowercase()) }
                )).take(maxFiles)
            } else {
                fileArray.toList()
            }
        } ?: emptyList()
        
        filesToProcess.forEach { file ->
            try {
                if (file.isDirectory) {
                    if (!file.name.startsWith(".")) {
                        val audioCount = countMediaStoreAudioFilesInDirectoryShallow(file, songsByPath)
                        items.add(ExplorerItem(
                            name = file.name,
                            path = file.absolutePath,
                            isDirectory = true,
                            itemCount = audioCount,
                            type = ExplorerItemType.FOLDER,
                            song = null
                        ))
                    }
                } else if (file.isFile) {
                    val extension = file.extension.lowercase()
                    if (extension in audioExtensions) {
                        val normalizedPath = file.absolutePath.replace("//", "/")
                        var song = songsByPath[normalizedPath]
                        
                        if (song == null) {
                            song = songsByPath[file.absolutePath]
                        }
                        
                        if (song != null) {
                            items.add(ExplorerItem(
                                name = song.title,
                                path = file.absolutePath,
                                isDirectory = false,
                                itemCount = 1,
                                type = ExplorerItemType.FILE,
                                song = song
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.d("LibraryScreen", "Skipping file ${file.name}: ${e.message}")
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("LibraryScreen", "Error reading directory: $directoryPath", e)
    }

    return items
}

fun Song.getSongPath(context: android.content.Context): String? {
    if (!path.isNullOrBlank()) return path
    return getFilePathFromUri(uri, context)
}

fun findSongsInFolder(
    folderPath: String,
    songPathMap: Map<String, Song>,
    songs: List<Song>,
    context: android.content.Context
): List<Song> {
    val normalizedFolderPath = folderPath.replace("//", "/").trimEnd('/')
    val prefixWithSlash = "$normalizedFolderPath/"
    if (songPathMap.isNotEmpty()) {
        return songPathMap.filterKeys { it.startsWith(prefixWithSlash) }.values.toList()
    }
    return songs.filter { song ->
        try {
            val songPath = song.getSongPath(context) ?: ""
            val normalizedSongPath = songPath.replace("//", "/")
            normalizedSongPath.startsWith(prefixWithSlash)
        } catch (e: Exception) {
            false
        }
    }
}

fun buildSongPathMap(songs: List<Song>, context: android.content.Context): Map<String, Song> {
    val pathMap = mutableMapOf<String, Song>()
    
    songs.forEach { song ->
        try {
            val path = song.getSongPath(context)
            if (path != null && path.isNotEmpty()) {
                val normalizedPath = path.replace("//", "/").trimEnd('/')
                pathMap[normalizedPath] = song
            }
        } catch (e: Exception) {
            // Skip
        }
    }
    
    return pathMap
}

fun getFilePathFromUri(uri: android.net.Uri, context: android.content.Context): String? {
    return try {
        val projection = arrayOf(android.provider.MediaStore.Audio.Media.DATA)
        val cursor = context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                it.getString(columnIndex)
            } else null
        }
    } catch (e: Exception) {
        null
    }
}

fun countAudioFilesInDirectory(directory: File, audioExtensions: Set<String>): Int {
    var count = 0

    try {
        val files = directory.listFiles()
        files?.forEach { file ->
            if (file.isDirectory) {
                count += countAudioFilesInDirectory(file, audioExtensions)
            } else if (file.isFile) {
                val extension = file.extension.lowercase()
                if (extension in audioExtensions) {
                    count++
                }
            }
        }
    } catch (e: Exception) {
        // Skip
    }

    return count
}

fun countAudioFilesInDirectoryShallow(directory: File, audioExtensions: Set<String>): Int {
    var count = 0

    try {
        val files = directory.listFiles()
        files?.forEach { file ->
            if (file.isFile) {
                val extension = file.extension.lowercase()
                if (extension in audioExtensions) {
                    count++
                }
            }
        }
    } catch (e: Exception) {
        // Skip
    }

    return count
}

fun countMediaStoreAudioFilesInDirectoryShallow(directory: File, songsByPath: Map<String, Song>): Int {
    var count = 0

    try {
        val files = directory.listFiles()
        files?.forEach { file ->
            if (file.isFile) {
                if (songsByPath.containsKey(file.absolutePath)) {
                    count++
                }
            }
        }
    } catch (e: Exception) {
        // Skip
    }

    return count
}

fun hasAudioContentRecursive(path: String, songs: List<Song>, context: android.content.Context, maxDepth: Int = 3): Boolean {
    if (maxDepth <= 0) return false
    
    return try {
        val directory = File(path)
        if (!directory.exists() || !directory.isDirectory) {
            return false
        }
        
        val normalizedDirPath = path.replace("//", "/").trimEnd('/')
        songs.any { song ->
            try {
                val songPath = song.getSongPath(context) ?: return@any false
                val normalizedSongPath = songPath.replace("//", "/")
                normalizedSongPath.startsWith("$normalizedDirPath/")
            } catch (e: Exception) {
                false
            }
        }
    } catch (e: Exception) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderItem(
    folderName: String,
    songCount: Int,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(68.dp),
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 0.dp,
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = RhythmIcons.Folder,
                        contentDescription = stringResource(R.string.cd_folder),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(18.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = RhythmIcons.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "$songCount ${if (songCount == 1) "track" else "tracks"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = RhythmIcons.Forward,
                        contentDescription = stringResource(R.string.cd_open_folder),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ExplorerBreadcrumb(
    path: String,
    onNavigateTo: (String) -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val rawSegments = path.split("/").filter { it.isNotEmpty() }

    val displaySegments: List<Pair<String, String>> = run {
        if (rawSegments.size >= 3 && rawSegments[0].equals("storage", true)
            && rawSegments[1].equals("emulated", true) && rawSegments[2] == "0"
        ) {
            val basePath = "/storage/emulated/0"
            val rest = if (rawSegments.size > 3) rawSegments.subList(3, rawSegments.size) else emptyList()
            val segments = mutableListOf<Pair<String, String>>()
            segments.add("Internal Storage" to basePath)
            var current = basePath
            for (s in rest) {
                current = "$current/$s"
                segments.add(s to current)
            }
            segments
        } else {
            val segments = mutableListOf<Pair<String, String>>()
            var current = ""
            for (s in rawSegments) {
                current = "$current/$s"
                segments.add(s to current)
            }
            segments
        }
    }

    LaunchedEffect(displaySegments) {
        if (displaySegments.isNotEmpty()) {
            val lastIndex = (displaySegments.size * 2) + 1
            scrollState.animateScrollToItem(lastIndex.coerceAtLeast(0))
        }
    }

    LazyRow(
        state = scrollState,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        item {
            val homeScale by animateFloatAsState(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "homeScale"
            )

            Surface(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    onGoHome()
                },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .height(36.dp)
                    .graphicsLayer {
                        scaleX = homeScale
                        scaleY = homeScale
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = RhythmIcons.Home,
                        contentDescription = stringResource(R.string.settings_home_screen),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = context.getString(R.string.library_home),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        displaySegments.forEachIndexed { index, pair ->
            val (segmentDisplay, segmentPath) = pair

            item {
                Icon(
                    imageVector = MaterialSymbolIcon("chevron_right"),
                    contentDescription = stringResource(R.string.cd_navigate),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            val currentPath = segmentPath
            val isLastSegment = index == displaySegments.lastIndex

            item {
                val chipScale by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "chipScale_$index"
                )

                val chipBackgroundColor by animateColorAsState(
                    targetValue = if (isLastSegment)
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
                    else
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "chipBackground_$index"
                )

                Surface(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        onNavigateTo(currentPath)
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = chipBackgroundColor,
                    border = if (isLastSegment) BorderStroke(
                        1.5.dp,
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                    ) else null,
                    modifier = Modifier
                        .height(36.dp)
                        .graphicsLayer {
                            scaleX = chipScale
                            scaleY = chipScale
                        }
                ) {
                    val displayText = if (segmentDisplay.length > 15) {
                        segmentDisplay.take(12) + "..."
                    } else {
                        segmentDisplay
                    }

                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isLastSegment) {
                            Icon(
                                imageVector = RhythmIcons.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isLastSegment) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isLastSegment)
                                MaterialTheme.colorScheme.onTertiaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (isLastSegment) {
                            Icon(
                                imageVector = RhythmIcons.Location,
                                contentDescription = stringResource(R.string.cd_current_location),
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerItemCard(
    item: ExplorerItem,
    onItemClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onShowSongInfo: (Song) -> Unit,
    onAddToBlacklist: (Song) -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    modifier: Modifier = Modifier,
    itemShape: RoundedCornerShape? = null,
    isPinned: Boolean = false,
    onPinToggle: (() -> Unit)? = null,
    onPlayFolder: ((ExplorerItem) -> Unit)? = null,
    onAddFolderToQueue: ((ExplorerItem) -> Unit)? = null,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    selectionIndex: Int? = null,
    onLongPress: () -> Unit = {}
) {
    val context = LocalContext.current

    when (item.type) {
        ExplorerItemType.STORAGE -> {
            Card(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    onItemClick()
                },
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp, vertical = 2.dp),
                shape = itemShape ?: RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(68.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 0.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (item.name) {
                                    "Internal Storage" -> RhythmIcons.Storage
                                    else -> MaterialSymbolIcon("sd_storage")
                                },
                                contentDescription = stringResource(R.string.explorer_item_icon_description, item.name),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(18.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = context.getString(R.string.library_tap_browse),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = RhythmIcons.Forward,
                                contentDescription = stringResource(R.string.explorertabcontent_browse_storage),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        ExplorerItemType.FOLDER -> {
            Card(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    onItemClick()
                },
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp, vertical = 2.dp),
                shape = itemShape ?: RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = RhythmIcons.Folder,
                                contentDescription = stringResource(R.string.explorertabcontent_folder_icon),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = RhythmIcons.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${item.itemCount} ${if (item.itemCount == 1) "track" else "tracks"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onPinToggle != null) {
                            IconButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    onPinToggle()
                                }
                            ) {
                                Icon(
                                    imageVector = if (isPinned) RhythmIcons.Pushpin else RhythmIcons.PinOutline,
                                    contentDescription = if (isPinned) "Unpin folder" else "Pin folder",
                                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        var showMenu by remember { mutableStateOf(false) }
                        if (onPlayFolder != null || onAddFolderToQueue != null) {
                            Box {
                                FilledIconButton(
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        showMenu = true
                                    },
                                    modifier = Modifier
                                        .width(32.dp)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(50),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.More,
                                        contentDescription = stringResource(R.string.cd_folder_options),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        showMenu = false
                                    },
                                    modifier = Modifier
                                        .widthIn(min = 220.dp)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(5.dp),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    if (onPlayFolder != null) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainer,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = stringResource(R.string.explorertabcontent_play_folder_as_playlist),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                },
                                                leadingIcon = {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                                        shape = CircleShape,
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = RhythmIcons.AddToPlaylist,
                                                            contentDescription = null,
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .padding(6.dp)
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    showMenu = false
                                                    onPlayFolder(item)
                                                }
                                            )
                                        }
                                    }

                                    if (onAddFolderToQueue != null) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainer,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = stringResource(R.string.explorertabcontent_add_all_to_queue),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                },
                                                leadingIcon = {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                                        shape = CircleShape,
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = RhythmIcons.Queue,
                                                            contentDescription = null,
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .padding(6.dp)
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    showMenu = false
                                                    onAddFolderToQueue(item)
                                                }
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

        ExplorerItemType.FILE -> {
            val song = item.song
            if (song != null) {
                LibrarySongItemWrapper(
                    song = song,
                    onClick = { onItemClick() },
                    onMoreClick = { onAddToPlaylist(song) },
                    onAddToQueue = { onAddToQueue(song) },
                    onShowSongInfo = { onShowSongInfo(song) },
                    onAddToBlacklist = { onAddToBlacklist(song) },
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    haptics = haptics,
                    itemShape = itemShape ?: RoundedCornerShape(16.dp),
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode,
                    selectionIndex = selectionIndex,
                    onLongPress = onLongPress
                )
            }
        }
    }
}
