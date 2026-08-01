package chromahub.rhythm.app.shared.presentation.components.bottomsheets

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType
import chromahub.rhythm.app.shared.presentation.components.common.ButtonGroupStyle
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.RhythmDetailActionButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonType
import chromahub.rhythm.app.shared.presentation.components.common.ActionProgressLoader
import chromahub.rhythm.app.network.NetworkClient
import chromahub.rhythm.app.network.YTMusicSearchRequest
import chromahub.rhythm.app.network.YTMusicContext
import chromahub.rhythm.app.network.YTMusicClient
import chromahub.rhythm.app.network.extractAlbumImageUrl
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Button
import chromahub.rhythm.app.util.ImageUtils
import chromahub.rhythm.app.util.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import chromahub.rhythm.app.R
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.compose.ui.text.style.TextAlign

private fun resolveBatchEditArtworkUri(context: android.content.Context, song: Song): Uri? {
    val currentArtworkUri = song.artworkUri

    if (currentArtworkUri != null &&
        !isMediaStoreAlbumArtworkUriForBatch(currentArtworkUri) &&
        isUsableArtworkUriForBatch(currentArtworkUri)
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

private fun isMediaStoreAlbumArtworkUriForBatch(uri: Uri): Boolean {
    val value = uri.toString().lowercase()
    return value.startsWith("content://media/") && value.contains("/audio/albumart")
}

private fun isUsableArtworkUriForBatch(uri: Uri): Boolean {
    return when (uri.scheme) {
        "file", null -> uri.path?.let { File(it).exists() } == true
        else -> true
    }
}

/**
 * Bottom sheet for batch editing metadata tags on multiple selected songs.
 * Each field has a checkbox to enable/disable it. Only enabled fields are applied.
 * UI matches the SongInfoBottomSheet metadata editor style.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BatchEditTagsSheet(
    selectedSongs: List<Song>,
    onDismiss: () -> Unit,
    onSave: (
        artist: String?,
        album: String?,
        genre: String?,
        year: Int?,
        artworkUri: Uri?,
        removeArtwork: Boolean,
        onProgress: (Int, Int) -> Unit,
        onComplete: (successCount: Int, failCount: Int) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    var editArtist by remember { mutableStateOf(false) }
    var editAlbum by remember { mutableStateOf(false) }
    var editGenre by remember { mutableStateOf(false) }
    var editYear by remember { mutableStateOf(false) }
    var editArtwork by remember { mutableStateOf(false) }

    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var removeArtwork by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isFetchingOnlineArt by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri = uri
        if (uri != null) {
            removeArtwork = false
        }
    }

    val enabledFieldCount = listOf(
        editArtist,
        editAlbum,
        editGenre,
        editYear,
        editArtwork
    ).count { it }

    val previewSong = selectedSongs.firstOrNull()
    var resolvedArtworkPreviewUri by remember(previewSong?.id, previewSong?.artworkUri) {
        mutableStateOf(previewSong?.artworkUri)
    }

    LaunchedEffect(previewSong?.id, previewSong?.artworkUri, previewSong?.uri) {
        resolvedArtworkPreviewUri = if (previewSong == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                resolveBatchEditArtworkUri(context, previewSong)
            }
        }
    }

    val artworkPreviewUri = when {
        removeArtwork -> null
        selectedImageUri != null -> selectedImageUri
        else -> resolvedArtworkPreviewUri
    }

    var showWarningDialog by remember { mutableStateOf(false) }

    fun handleSave() {
        if (!editArtist && !editAlbum && !editGenre && !editYear && !editArtwork) {
            Toast.makeText(context, R.string.batchedittagssheet_enable_at_least_one, Toast.LENGTH_SHORT).show()
            return
        }
        val hasValidInput = (editArtist && artist.trim().isNotBlank()) ||
            (editAlbum && album.trim().isNotBlank()) ||
            (editGenre && genre.trim().isNotBlank()) ||
            (editYear && year.toIntOrNull() != null) ||
            (editArtwork && (selectedImageUri != null || removeArtwork))

        if (!hasValidInput) {
            Toast.makeText(context, R.string.batchedittagssheet_please_enter_a_value, Toast.LENGTH_SHORT).show()
            return
        }
        showWarningDialog = true
    }

    fun proceedWithSave() {
        isSaving = true
        onSave(
            if (editArtist) artist.trim().takeIf { it.isNotBlank() } else null,
            if (editAlbum) album.trim().takeIf { it.isNotBlank() } else null,
            if (editGenre) genre.trim().takeIf { it.isNotBlank() } else null,
            if (editYear) year.toIntOrNull() else null,
            if (editArtwork) selectedImageUri else null,
            if (editArtwork) removeArtwork else false,
            { current, total ->
                progress = if (total > 0) current.toFloat() / total else 0f
            },
            { successCount, failCount ->
                isSaving = false
                onDismiss()
                val msg = if (failCount == 0) "Updated $successCount songs successfully"
                          else "Updated $successCount songs, $failCount failed"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

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
                .fillMaxHeight()
                .navigationBarsPadding()
                .padding(vertical = 8.dp)
        ) {
            StandardBottomSheetHeader(
                title = stringResource(R.string.batchedittagssheet_batch_edit_tags),
                subtitle = "${selectedSongs.size} songs selected • $enabledFieldCount fields enabled",
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = RhythmIcons.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.batchedittagssheet_enable_fields_you_want),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                text = stringResource(R.string.batchedittagssheet_tags),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            BatchEditField(
                                label = "Artist",
                                icon = RhythmIcons.ArtistFilled,
                                enabled = editArtist,
                                value = artist,
                                onEnabledChange = { editArtist = it },
                                onValueChange = { artist = it }
                            )

                            BatchEditField(
                                label = "Album",
                                icon = RhythmIcons.AlbumFilled,
                                enabled = editAlbum,
                                value = album,
                                onEnabledChange = { editAlbum = it },
                                onValueChange = { album = it }
                            )

                            BatchEditField(
                                label = "Genre",
                                icon = RhythmIcons.Category,
                                enabled = editGenre,
                                value = genre,
                                onEnabledChange = { editGenre = it },
                                onValueChange = { genre = it }
                            )

                            BatchEditField(
                                label = "Year",
                                icon = RhythmIcons.DateRange,
                                enabled = editYear,
                                value = year,
                                onEnabledChange = { editYear = it },
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() } && input.length <= 4) {
                                        year = input
                                    }
                                },
                                keyboardType = KeyboardType.Number
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
                            OutlinedTextField(
                                value = when {
                                    removeArtwork -> "Artwork will be removed"
                                    selectedImageUri != null -> "New artwork selected"
                                    else -> "No artwork change"
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.settings_artwork)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = RhythmIcons.Image,
                                        contentDescription = null,
                                        tint = if (editArtwork) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    Checkbox(
                                        checked = editArtwork,
                                        onCheckedChange = { enabled ->
                                            editArtwork = enabled
                                            if (!enabled) {
                                                selectedImageUri = null
                                                removeArtwork = false
                                            }
                                        }
                                    )
                                },
                                enabled = editArtwork,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )

                            AnimatedVisibility(visible = editArtwork) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(96.dp)
                                            .clip(RoundedCornerShape(16.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .apply(
                                                    ImageUtils.buildImageRequest(
                                                        artworkPreviewUri,
                                                        selectedSongs.firstOrNull()?.title ?: "Batch",
                                                        context.cacheDir,
                                                        M3PlaceholderType.TRACK
                                                    )
                                                )
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = stringResource(R.string.batchedittagssheet_batch_artwork_preview),
                                            modifier = Modifier.fillMaxWidth(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                                                        Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        RhythmDetailActionButton(
                                            onClick = { imagePickerLauncher.launch("image/*") },
                                            height = 44.dp,
                                            isFirst = true,
                                            isLast = false,
                                            type = RhythmButtonType.Filled,
                                            icon = RhythmIcons.Image,
                                            iconSize = 18.dp,
                                            text = if (selectedImageUri != null) "Change" else "Select",
                                            fontWeight = FontWeight.Medium
                                        )

                                        RhythmDetailActionButton(
                                            onClick = {
                                                selectedImageUri = null
                                                removeArtwork = true
                                            },
                                            height = 44.dp,
                                            isFirst = false,
                                            isLast = true,
                                            type = RhythmButtonType.Tonal,
                                            icon = RhythmIcons.Delete,
                                            iconSize = 18.dp,
                                            text = stringResource(R.string.content_desc_remove),
                                            fontWeight = FontWeight.Medium,
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }

                                    if (NetworkClient.isYTMusicApiEnabled()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Button(
                                            onClick = {
                                                isFetchingOnlineArt = true
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    try {
                                                        val apiService = NetworkClient.ytmusicApiService
                                                        if (apiService != null) {
                                                            val searchQueryArtist = if (editArtist && artist.isNotBlank()) artist else (previewSong?.artist ?: "")
                                                            val searchQueryAlbum = if (editAlbum && album.isNotBlank()) album else (previewSong?.album ?: "")
                                                            val searchQuery = "${searchQueryAlbum.trim()} ${searchQueryArtist.trim()}"
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
                                                                    val tempFile = File(context.cacheDir, "temp_artwork_fetched_batch_${previewSong?.id ?: "temp"}.jpg")
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
                                            enabled = !isFetchingOnlineArt && !isSaving && (
                                                (editArtist && artist.isNotBlank()) ||
                                                (editAlbum && album.isNotBlank()) ||
                                                (!previewSong?.artist.isNullOrBlank()) ||
                                                (!previewSong?.album.isNullOrBlank())
                                            ),
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
                        text = stringResource(R.string.metadata_saving) + " ${(progress * 100).toInt()}%",
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
                    enabled = !isSaving && !isFetchingOnlineArt,
                    icon = RhythmIcons.Close,
                    text = stringResource(R.string.ui_cancel)
                )

                RhythmButtonWeighted(
                    onClick = { handleSave() },
                    weight = 1f,
                    isLast = true,
                    enabled = !isSaving && !isFetchingOnlineArt,
                    icon = RhythmIcons.Check,
                    text = stringResource(R.string.ui_apply)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
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

@Composable
private fun BatchEditField(
    label: String,
    icon: MaterialSymbolIcon,
    enabled: Boolean,
    value: String,
    onEnabledChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            Checkbox(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}
