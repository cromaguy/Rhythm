/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.components.bottomsheets

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.data.repository.MusicRepository
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.LyricsData
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType
import chromahub.rhythm.app.shared.presentation.components.common.MarqueeText
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonType
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.lyrics.LyricShareLine
import chromahub.rhythm.app.shared.presentation.components.lyrics.LyricsShareCardRenderer
import chromahub.rhythm.app.shared.presentation.components.lyrics.LyricsShareCardSheet
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.ImageUtils
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SongShareRequest(
    val song: Song,
    val lyrics: LyricsData? = null,
    val currentPositionMs: Long = 0L
)

val LocalSongShareHandler = staticCompositionLocalOf<((SongShareRequest) -> Unit)?> { null }

@Composable
fun SongShareHost(
    lyricsResolver: (suspend (Song) -> LyricsData?)? = null,
    content: @Composable () -> Unit
) {
    var pendingShareRequest by remember { mutableStateOf<SongShareRequest?>(null) }
    var activeLyricsCard by remember { mutableStateOf<Pair<SongShareRequest, LyricsData>?>(null) }

    val openShareSheet: (SongShareRequest) -> Unit = remember {
        { request ->
            pendingShareRequest = request
        }
    }

    CompositionLocalProvider(LocalSongShareHandler provides openShareSheet) {
        content()

        pendingShareRequest?.let { request ->
            SongShareBottomSheet(
                request = request,
                lyricsResolver = lyricsResolver,
                onOpenLyricsCard = { req, resolvedLyrics ->
                    pendingShareRequest = null
                    activeLyricsCard = req to resolvedLyrics
                },
                onDismiss = { pendingShareRequest = null }
            )
        }

        activeLyricsCard?.let { (request, resolvedLyrics) ->
            LyricsShareCardSheet(
                song = request.song,
                lyrics = resolvedLyrics,
                currentPositionMs = request.currentPositionMs,
                onDismiss = { activeLyricsCard = null }
            )
        }
    }
}

private data class SongShareOption(
    val title: String,
    val subtitle: String,
    val icon: MaterialSymbolIcon,
    val enabled: Boolean = true,
    val isLoading: Boolean = false,
    val isAccent: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun SongShareBottomSheet(
    request: SongShareRequest,
    lyricsResolver: (suspend (Song) -> LyricsData?)? = null,
    onOpenLyricsCard: (SongShareRequest, LyricsData) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val song = request.song
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    LaunchedEffect(sheetState) {
        sheetState.expand()
    }

    val uriText = song.uri.toString()
    val canShareAudioFile = !uriText.startsWith("http://", ignoreCase = true) &&
        !uriText.startsWith("https://", ignoreCase = true) &&
        !uriText.startsWith("streaming://", ignoreCase = true)

    val initialValidLyrics = remember(request.lyrics) {
        request.lyrics?.takeIf {
            it.hasLyrics() && !it.isErrorMessage() && LyricsShareCardRenderer.extractShareableLines(it).isNotEmpty()
        }
    }
    var resolvedLyrics by remember(song.id, initialValidLyrics) { mutableStateOf(initialValidLyrics) }
    var isLoadingLyrics by remember(song.id, initialValidLyrics) { mutableStateOf(initialValidLyrics == null) }

    LaunchedEffect(song.id, initialValidLyrics) {
        if (initialValidLyrics != null) {
            resolvedLyrics = initialValidLyrics
            isLoadingLyrics = false
            return@LaunchedEffect
        }
        isLoadingLyrics = true
        val fetched = runCatching {
            if (lyricsResolver != null) {
                lyricsResolver(song)
            } else {
                withContext(Dispatchers.IO) {
                    MusicRepository(context.applicationContext).fetchLyrics(
                        artist = song.artist,
                        title = song.title,
                        songId = song.id,
                        songUri = song.uri,
                        sourcePreference = AppSettings.getInstance(context).lyricsSourcePreference.value,
                        forceRefresh = false
                    )
                }
            }
        }.getOrNull()
        resolvedLyrics = fetched?.takeIf {
            it.hasLyrics() && !it.isErrorMessage() && LyricsShareCardRenderer.extractShareableLines(it).isNotEmpty()
        }
        isLoadingLyrics = false
    }

    val shareableLines = remember(resolvedLyrics) {
        LyricsShareCardRenderer.extractShareableLines(resolvedLyrics)
    }
    val hasShareableLyrics = !isLoadingLyrics && resolvedLyrics != null && shareableLines.isNotEmpty()

    val artworkShape = rememberExpressiveShapeFor(
        ExpressiveShapeTarget.SONG_ART,
        fallbackShape = RoundedCornerShape(14.dp)
    )

    val lyricsCardSubtitle = when {
        isLoadingLyrics -> stringResource(R.string.share_lyrics_loading)
        hasShareableLyrics -> stringResource(R.string.share_option_lyrics_card_desc)
        else -> stringResource(R.string.share_lyrics_unavailable)
    }
    val lyricsTextSubtitle = when {
        isLoadingLyrics -> stringResource(R.string.share_lyrics_loading)
        hasShareableLyrics -> stringResource(R.string.share_option_lyrics_text_desc)
        else -> stringResource(R.string.share_lyrics_unavailable)
    }

    val options = listOf(
        SongShareOption(
            title = stringResource(R.string.share_option_audio_file),
            subtitle = stringResource(
                if (canShareAudioFile) R.string.share_option_audio_file_desc
                else R.string.share_option_audio_file_streaming_desc
            ),
            icon = RhythmIcons.Music.Audiotrack,
            enabled = canShareAudioFile,
            isAccent = canShareAudioFile,
            onClick = {
                shareSongAudioFile(context, song)
                onDismiss()
            }
        ),
        SongShareOption(
            title = stringResource(R.string.share_option_lyrics_card),
            subtitle = lyricsCardSubtitle,
            icon = RhythmIcons.Player.Lyrics,
            enabled = hasShareableLyrics,
            isLoading = isLoadingLyrics,
            onClick = {
                val lyricsData = resolvedLyrics ?: return@SongShareOption
                onOpenLyricsCard(request, lyricsData)
            }
        ),
        SongShareOption(
            title = stringResource(R.string.share_option_lyrics_text),
            subtitle = lyricsTextSubtitle,
            icon = MaterialSymbolIcon("format_quote", filled = true),
            enabled = hasShareableLyrics,
            isLoading = isLoadingLyrics,
            onClick = {
                shareLyricsAsText(context, song, shareableLines)
                onDismiss()
            }
        ),
        SongShareOption(
            title = stringResource(R.string.share_option_track_info),
            subtitle = stringResource(R.string.share_option_track_info_desc),
            icon = RhythmIcons.Info,
            enabled = true,
            onClick = {
                shareSongInfoText(context, song)
                onDismiss()
            }
        )
    )

    val scrollState = rememberScrollState()

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
        scrollState = scrollState,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = artworkShape,
                    tonalElevation = 0.dp
                ) {
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
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.action_share),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    MarqueeText(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    )
                    MarqueeText(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            AdaptiveSheetScrollContainer(
                scrollState = scrollState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) { endPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(start = 24.dp, end = 24.dp + endPadding, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    options.forEachIndexed { index, option ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed && option.enabled) 0.98f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "share_option_scale"
                        )

                        val containerColor = when {
                            !option.enabled -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
                            option.isAccent -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        val titleColor = when {
                            !option.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            option.isAccent -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        val subtitleColor = when {
                            !option.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            option.isAccent -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val iconTint = when {
                            !option.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            option.isAccent -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.primary
                        }

                        Card(
                            onClick = {
                                if (option.enabled) {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM)
                                    option.onClick()
                                }
                            },
                            enabled = option.enabled,
                            shape = groupedBottomSheetItemShape(index, options.size),
                            colors = CardDefaults.cardColors(
                                containerColor = containerColor,
                                disabledContainerColor = containerColor
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            interactionSource = interactionSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = titleColor
                                    )
                                    Text(
                                        text = option.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = subtitleColor
                                    )
                                }
                                if (option.isLoading) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                RhythmGroupedButton(
                    modifier = Modifier.fillMaxWidth(),
                    size = RhythmButtonSize.Large
                ) {
                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            onDismiss()
                        },
                        type = RhythmButtonType.Tonal,
                        size = RhythmButtonSize.Large,
                        text = stringResource(R.string.ui_cancel),
                        isFirst = true,
                        isLast = true
                    )
                }
            }
        }
    }
}

fun shareSongAudioFile(context: Context, song: Song) {
    try {
        val resolvedUri = if (song.uri.scheme == "file" && !song.uri.path.isNullOrBlank()) {
            runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    File(song.uri.path!!)
                )
            }.getOrDefault(song.uri)
        } else {
            song.uri
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, resolvedUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, context.getString(R.string.action_share))
        )
    } catch (_: Exception) {
        Toast.makeText(context, R.string.materialplayerscreen_unable_to_share_file, Toast.LENGTH_SHORT).show()
    }
}

private fun shareLyricsAsText(context: Context, song: Song, lines: List<LyricShareLine>) {
    val body = lines.joinToString("\n") { it.text }.trim()
    if (body.isBlank()) {
        Toast.makeText(context, R.string.share_lyrics_unavailable, Toast.LENGTH_SHORT).show()
        return
    }
    val payload = buildString {
        append(body)
        append("\n\n— ")
        append(song.title)
        if (song.artist.isNotBlank()) {
            append(" • ")
            append(song.artist)
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "${song.title} — ${song.artist}")
        putExtra(Intent.EXTRA_TEXT, payload)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share_option_lyrics_text))
    )
}

private fun shareSongInfoText(context: Context, song: Song) {
    val infoText = buildString {
        append(song.title)
        if (song.artist.isNotBlank()) {
            append(" — ")
            append(song.artist)
        }
        if (song.album.isNotBlank()) {
            append(" (")
            append(song.album)
            append(")")
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, song.title)
        putExtra(Intent.EXTRA_TEXT, infoText)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share_option_track_info))
    )
}
