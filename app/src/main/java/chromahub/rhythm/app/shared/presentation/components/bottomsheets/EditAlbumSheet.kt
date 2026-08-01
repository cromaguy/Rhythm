package chromahub.rhythm.app.shared.presentation.components.bottomsheets

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.Album
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.ActionProgressLoader
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.util.ImageUtils
import chromahub.rhythm.app.util.MediaUtils
import chromahub.rhythm.app.network.NetworkClient
import chromahub.rhythm.app.network.YTMusicSearchRequest
import chromahub.rhythm.app.network.YTMusicContext
import chromahub.rhythm.app.network.YTMusicClient
import chromahub.rhythm.app.network.extractAlbumImageUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

private fun resolveAlbumArtworkUri(context: android.content.Context, song: Song): Uri? {
    val currentArtworkUri = song.artworkUri

    if (currentArtworkUri != null &&
        !isMediaStoreAlbumArtworkUriForAlbumEdit(currentArtworkUri) &&
        isUsableArtworkUriForAlbumEdit(currentArtworkUri)
    ) {
        return currentArtworkUri
    }

    val cachedLossless = MediaUtils.getCachedEmbeddedAlbumArtUri(
        cacheDir = context.cacheDir,
        songUri = song.uri,
        lossless = true
    )
    if (cachedLossless != null) {
        return cachedLossless
    }

    val cachedLossy = MediaUtils.getCachedEmbeddedAlbumArtUri(
        cacheDir = context.cacheDir,
        songUri = song.uri,
        lossless = false
    )
    if (cachedLossy != null) {
        return cachedLossy
    }

    val extractedEmbedded = MediaUtils.extractEmbeddedAlbumArt(
        context = context,
        songUri = song.uri,
        cacheDir = context.cacheDir,
        lossless = false
    )
    if (extractedEmbedded != null) {
        return extractedEmbedded
    }

    return currentArtworkUri
}

private fun isMediaStoreAlbumArtworkUriForAlbumEdit(uri: Uri): Boolean {
    val value = uri.toString().lowercase()
    return value.startsWith("content://media/") && value.contains("/audio/albumart")
}

private fun isUsableArtworkUriForAlbumEdit(uri: Uri): Boolean {
    return when (uri.scheme) {
        "file", null -> uri.path?.let { File(it).exists() } == true
        else -> true
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditAlbumSheet(
    album: Album,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        artist: String,
        artworkUri: Uri?,
        removeArtwork: Boolean,
        onProgress: (Int, Int) -> Unit,
        onComplete: (successCount: Int, failCount: Int) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    var albumTitle by remember(album.title) { mutableStateOf(album.title) }
    var albumArtist by remember(album.artist) { mutableStateOf(album.artist) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var removeArtwork by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isFetchingOnlineArt by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri = uri
        if (uri != null) {
            removeArtwork = false
        }
    }

    val previewSong = album.songs.firstOrNull()
    var resolvedArtworkPreviewUri by remember(album.id, album.artworkUri) {
        mutableStateOf(album.artworkUri)
    }

    LaunchedEffect(previewSong?.id, previewSong?.artworkUri, previewSong?.uri) {
        resolvedArtworkPreviewUri = if (previewSong == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                album.artworkUri ?: resolveAlbumArtworkUri(context, previewSong)
            }
        }
    }

    val artworkPreviewUri = when {
        removeArtwork -> null
        selectedImageUri != null -> selectedImageUri
        else -> resolvedArtworkPreviewUri
    }

    fun handleSave() {
        if (albumTitle.trim().isBlank()) {
            Toast.makeText(context, "Album Title cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        if (albumArtist.trim().isBlank()) {
            Toast.makeText(context, "Album Artist cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        showWarningDialog = true
    }

    fun proceedWithSave() {
        isSaving = true
        onSave(
            albumTitle.trim(),
            albumArtist.trim(),
            selectedImageUri,
            removeArtwork,
            { current, total ->
                progress = if (total > 0) current.toFloat() / total else 0f
            },
            { successCount, failCount ->
                isSaving = false
                onDismiss()
                val msg = if (failCount == 0) "Updated album successfully"
                          else "Updated album with $failCount failures"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val isLandscapeTablet = isTablet && configuration.screenWidthDp > configuration.screenHeightDp

    if (isLandscapeTablet) {
        Dialog(
            onDismissRequest = { if (!isSaving) onDismiss() },
            properties = DialogProperties(
                dismissOnBackPress = !isSaving,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                shape = RoundedCornerShape(32.dp),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left side: Artwork editing
                        Surface(
                            modifier = Modifier
                                .weight(0.4f)
                                .fillMaxHeight(),
                            color = Color.Transparent
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 24.dp, start = 32.dp, end = 16.dp, bottom = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_artwork),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                Box(
                                    modifier = Modifier
                                        .size(220.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .apply(
                                                ImageUtils.buildImageRequest(
                                                    artworkPreviewUri,
                                                    albumTitle.takeIf { it.isNotBlank() } ?: "Album",
                                                    context.cacheDir,
                                                    M3PlaceholderType.ALBUM
                                                )
                                            )
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Artwork Preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { imagePickerLauncher.launch("image/*") },
                                        enabled = !isSaving,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Image,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (selectedImageUri != null) "Change" else "Select")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            selectedImageUri = null
                                            removeArtwork = true
                                        },
                                        enabled = !isSaving,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.blacklist_button_remove))
                                    }
                                }

                                if (NetworkClient.isYTMusicApiEnabled()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            isFetchingOnlineArt = true
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try {
                                                    val apiService = NetworkClient.ytmusicApiService
                                                    if (apiService != null) {
                                                        val searchQuery = "${albumTitle.trim()} ${albumArtist.trim()}"
                                                        var imageUrl: String? = null
                                                        
                                                        // Try searching with Album filter first
                                                        val albumSearchRequest = YTMusicSearchRequest(
                                                            context = YTMusicContext(YTMusicClient()),
                                                            query = searchQuery,
                                                            params = "EgWKAQIYAWoKEAoQAxAEEAkQBQ%3D%3D"
                                                        )
                                                        val albumResponse = apiService.search(request = albumSearchRequest)
                                                        if (albumResponse.isSuccessful) {
                                                            imageUrl = albumResponse.body()?.extractAlbumImageUrl()
                                                        }
                                                        
                                                        // Fallback to Song filter if album filter yielded nothing
                                                        if (imageUrl.isNullOrEmpty()) {
                                                            val songSearchRequest = YTMusicSearchRequest(
                                                                context = YTMusicContext(YTMusicClient()),
                                                                query = searchQuery,
                                                                params = "EgWKAQIIAWoKEAoQAxAEEAkQBQ%3D%3D"
                                                            )
                                                            val songResponse = apiService.search(request = songSearchRequest)
                                                            if (songResponse.isSuccessful) {
                                                                imageUrl = songResponse.body()?.extractAlbumImageUrl()
                                                            }
                                                        }

                                                        if (!imageUrl.isNullOrEmpty()) {
                                                            val okRequest = okhttp3.Request.Builder().url(imageUrl).build()
                                                                val okResponse = NetworkClient.genericHttpClient.newCall(okRequest).execute()
                                                                if (okResponse.isSuccessful) {
                                                                    val bytes = okResponse.body.bytes()
                                                                    val tempFile = File(context.cacheDir, "temp_artwork_fetched_album_${album.id}.jpg")
                                                                    tempFile.writeBytes(bytes)
                                                                    withContext(Dispatchers.Main) {
                                                                        selectedImageUri = Uri.fromFile(tempFile)
                                                                        removeArtwork = false
                                                                        Toast.makeText(context, R.string.songinfobottomsheet_artwork_fetched_successfully_click, Toast.LENGTH_SHORT).show()
                                                                    }
                                                                } else {
                                                                    withContext(Dispatchers.Main) {
                                                                        Toast.makeText(context, R.string.songinfobottomsheet_failed_to_download_artwork_1, Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                            } else {
                                                                withContext(Dispatchers.Main) {
                                                                    Toast.makeText(context, R.string.songinfobottomsheet_no_artwork_found_for, Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        } else {
                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, R.string.songinfobottomsheet_online_api_service_unavailable, Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, context.getString(R.string.error_fetching_artwork, e.message ?: ""), Toast.LENGTH_LONG).show()
                                                    }
                                                } finally {
                                                    withContext(Dispatchers.Main) {
                                                        isFetchingOnlineArt = false
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isFetchingOnlineArt && !isSaving && albumTitle.isNotBlank() && albumArtist.isNotBlank(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isFetchingOnlineArt) {
                                            ActionProgressLoader(
                                                size = 18.dp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.songinfobottomsheet_fetching))
                                        } else {
                                            Icon(
                                                imageVector = MaterialSymbolIcon("cloud_download", filled = true),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.songinfobottomsheet_fetch_online_art))
                                        }
                                    }
                                }
                            }
                        }

                        // Right side: Form fields and apply/cancel buttons
                        Surface(
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxHeight(),
                            color = Color.Transparent
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Header with close button
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 24.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.edit_album_title),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.edit_album_desc),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = onDismiss,
                                        enabled = !isSaving,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Close,
                                            contentDescription = stringResource(R.string.ui_close),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(horizontal = 16.dp),
                                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    tonalElevation = 1.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = albumTitle,
                                            onValueChange = { albumTitle = it },
                                            label = { Text(stringResource(R.string.edit_album_title_label)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = RhythmIcons.AlbumFilled,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            enabled = !isSaving,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            singleLine = true
                                        )

                                        OutlinedTextField(
                                            value = albumArtist,
                                            onValueChange = { albumArtist = it },
                                            label = { Text(stringResource(R.string.edit_album_artist_label)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = RhythmIcons.ArtistFilled,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            enabled = !isSaving,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            singleLine = true
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Progress
                                        AnimatedVisibility(visible = isSaving) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                LinearWavyProgressIndicator(
                                                    progress = { progress },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    text = stringResource(R.string.edit_album_saving) + " ${(progress * 100).toInt()}%",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.weight(1f))

                                        // Apply & Cancel Buttons
                                        RhythmGroupedButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            size = RhythmButtonSize.Large
                                        ) {
                                            RhythmButtonWeighted(
                                                onClick = onDismiss,
                                                weight = 1f,
                                                isFirst = true,
                                                enabled = !isSaving,
                                                icon = RhythmIcons.Close,
                                                text = stringResource(R.string.ui_cancel)
                                            )

                                            RhythmButtonWeighted(
                                                onClick = { handleSave() },
                                                weight = 1f,
                                                isLast = true,
                                                enabled = !isSaving,
                                                icon = RhythmIcons.Check,
                                                text = stringResource(R.string.ui_apply)
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
    } else {
        ModalBottomSheet(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            onDismissRequest = { if (!isSaving) onDismiss() },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp)
            ) {
                StandardBottomSheetHeader(
                    title = stringResource(R.string.edit_album_title),
                    subtitle = stringResource(R.string.edit_album_desc),
                    visible = true,
                    modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 1.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = albumTitle,
                                    onValueChange = { albumTitle = it },
                                    label = { Text(stringResource(R.string.edit_album_title_label)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = RhythmIcons.AlbumFilled,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    enabled = !isSaving,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = albumArtist,
                                    onValueChange = { albumArtist = it },
                                    label = { Text(stringResource(R.string.edit_album_artist_label)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = RhythmIcons.ArtistFilled,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    enabled = !isSaving,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    singleLine = true
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 1.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_artwork),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .align(Alignment.CenterHorizontally),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .apply(
                                                ImageUtils.buildImageRequest(
                                                    artworkPreviewUri,
                                                    albumTitle.takeIf { it.isNotBlank() } ?: "Album",
                                                    context.cacheDir,
                                                    M3PlaceholderType.ALBUM
                                                )
                                            )
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Artwork Preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { imagePickerLauncher.launch("image/*") },
                                        enabled = !isSaving,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Image,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (selectedImageUri != null) "Change" else "Select")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            selectedImageUri = null
                                            removeArtwork = true
                                        },
                                        enabled = !isSaving,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.blacklist_button_remove))
                                    }
                                }

                                if (NetworkClient.isYTMusicApiEnabled()) {
                                    Button(
                                        onClick = {
                                            isFetchingOnlineArt = true
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try {
                                                    val apiService = NetworkClient.ytmusicApiService
                                                    if (apiService != null) {
                                                        val searchQuery = "${albumTitle.trim()} ${albumArtist.trim()}"
                                                        var imageUrl: String? = null
                                                        
                                                        // Try searching with Album filter first
                                                        val albumSearchRequest = YTMusicSearchRequest(
                                                            context = YTMusicContext(YTMusicClient()),
                                                            query = searchQuery,
                                                            params = "EgWKAQIYAWoKEAoQAxAEEAkQBQ%3D%3D"
                                                        )
                                                        val albumResponse = apiService.search(request = albumSearchRequest)
                                                        if (albumResponse.isSuccessful) {
                                                            imageUrl = albumResponse.body()?.extractAlbumImageUrl()
                                                        }
                                                        
                                                        // Fallback to Song filter if album filter yielded nothing
                                                        if (imageUrl.isNullOrEmpty()) {
                                                            val songSearchRequest = YTMusicSearchRequest(
                                                                context = YTMusicContext(YTMusicClient()),
                                                                query = searchQuery,
                                                                params = "EgWKAQIIAWoKEAoQAxAEEAkQBQ%3D%3D"
                                                            )
                                                            val songResponse = apiService.search(request = songSearchRequest)
                                                            if (songResponse.isSuccessful) {
                                                                imageUrl = songResponse.body()?.extractAlbumImageUrl()
                                                            }
                                                        }

                                                        if (!imageUrl.isNullOrEmpty()) {
                                                            val okRequest = okhttp3.Request.Builder().url(imageUrl).build()
                                                                val okResponse = NetworkClient.genericHttpClient.newCall(okRequest).execute()
                                                                if (okResponse.isSuccessful) {
                                                                    val bytes = okResponse.body.bytes()
                                                                    val tempFile = File(context.cacheDir, "temp_artwork_fetched_album_${album.id}.jpg")
                                                                    tempFile.writeBytes(bytes)
                                                                    withContext(Dispatchers.Main) {
                                                                        selectedImageUri = Uri.fromFile(tempFile)
                                                                        removeArtwork = false
                                                                        Toast.makeText(context, R.string.songinfobottomsheet_artwork_fetched_successfully_click, Toast.LENGTH_SHORT).show()
                                                                    }
                                                                } else {
                                                                    withContext(Dispatchers.Main) {
                                                                        Toast.makeText(context, R.string.songinfobottomsheet_failed_to_download_artwork_1, Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                            } else {
                                                                withContext(Dispatchers.Main) {
                                                                    Toast.makeText(context, R.string.songinfobottomsheet_no_artwork_found_for, Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        } else {
                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, R.string.songinfobottomsheet_online_api_service_unavailable, Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, context.getString(R.string.error_fetching_artwork, e.message ?: ""), Toast.LENGTH_LONG).show()
                                                    }
                                                } finally {
                                                    withContext(Dispatchers.Main) {
                                                        isFetchingOnlineArt = false
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isFetchingOnlineArt && !isSaving && albumTitle.isNotBlank() && albumArtist.isNotBlank(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isFetchingOnlineArt) {
                                            ActionProgressLoader(
                                                size = 18.dp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.songinfobottomsheet_fetching))
                                        } else {
                                            Icon(
                                                imageVector = MaterialSymbolIcon("cloud_download", filled = true),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.songinfobottomsheet_fetch_online_art))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Progress
                AnimatedVisibility(visible = isSaving) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearWavyProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.edit_album_saving) + " ${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Buttons
                RhythmGroupedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    size = RhythmButtonSize.Large
                ) {
                    RhythmButtonWeighted(
                        onClick = onDismiss,
                        weight = 1f,
                        isFirst = true,
                        enabled = !isSaving,
                        icon = RhythmIcons.Close,
                        text = stringResource(R.string.ui_cancel)
                    )

                    RhythmButtonWeighted(
                        onClick = { handleSave() },
                        weight = 1f,
                        isLast = true,
                        enabled = !isSaving,
                        icon = RhythmIcons.Check,
                        text = stringResource(R.string.ui_apply)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = RhythmIcons.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.bottomsheet_irreversible))
                }
            },
            text = {
                Column {
                    Text(
                        "The changes you're about to make will permanently modify the audio files' metadata.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This action cannot be undone. Make sure you have a backup if needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWarningDialog = false
                        proceedWithSave()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = RhythmIcons.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.bottomsheet_proceed))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { 
                        showWarningDialog = false
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
        )
    }
}
