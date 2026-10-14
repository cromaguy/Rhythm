/*
 * Copyright (C) 2025 nift4 (Gramophone)
 * Modified for Rhythm by Anjishnu Nandi (cromaguy)
 *
 * SPDX-FileCopyrightText: 2025 nift4 <https://github.com/FoedusProgramme/Gramophone>
 * SPDX-FileCopyrightText: 2025-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.widget.glance

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import chromahub.rhythm.app.R
import chromahub.rhythm.app.activities.MainActivity
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RhythmLyricsWidget : GlanceAppWidget() {

    companion object {
        const val KEY_LYRIC_LINES = "lyric_lines"
        const val KEY_ACTIVE_INDEX = "active_index"
        const val KEY_SONG_TITLE = "song_title"
        const val KEY_ARTIST_NAME = "artist_name"
        const val KEY_IS_PLAYING = "is_playing"
        const val KEY_ARTWORK_URI = "artwork_uri"
    }

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val currentPrefs = currentState<Preferences>()
            val songTitle = currentPrefs[stringPreferencesKey(KEY_SONG_TITLE)] ?: ""
            val artistName = currentPrefs[stringPreferencesKey(KEY_ARTIST_NAME)] ?: ""
            val isPlaying = currentPrefs[booleanPreferencesKey(KEY_IS_PLAYING)] ?: false
            val lyricLinesStr = currentPrefs[stringPreferencesKey(KEY_LYRIC_LINES)] ?: ""
            val activeIndex = currentPrefs[intPreferencesKey(KEY_ACTIVE_INDEX)] ?: -1
            val artworkUriString = currentPrefs[stringPreferencesKey(KEY_ARTWORK_URI)]

            // Artwork loading with cache retained for background updating synchronization
            var bitmap by remember(artworkUriString) {
                mutableStateOf<Bitmap?>(artworkUriString?.let { RhythmMusicWidget.getCachedBitmap(it) })
            }

            if (bitmap == null && !artworkUriString.isNullOrBlank()) {
                val glanceContext = LocalContext.current
                LaunchedEffect(artworkUriString) {
                    try {
                        val loaded = withContext(Dispatchers.IO) {
                            val imageLoader = coil.Coil.imageLoader(glanceContext)
                            val request = ImageRequest.Builder(glanceContext)
                                .data(artworkUriString)
                                .size(Size(120, 120))
                                .build()
                            val result = imageLoader.execute(request)
                            val loadedBmp = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                            if (loadedBmp != null) {
                                RhythmMusicWidget.cacheBitmap(artworkUriString, loadedBmp)
                            }
                            loadedBmp
                        }
                        if (loaded != null) {
                            bitmap = loaded
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("RhythmLyricsWidget", "Error loading artwork", e)
                    }
                }
            }

            val currentSize = LocalSize.current

            GlanceTheme {
                WidgetContent(
                    songTitle = songTitle,
                    artistName = artistName,
                    isPlaying = isPlaying,
                    lyricLinesStr = lyricLinesStr,
                    activeIndex = activeIndex,
                    size = currentSize
                )
            }
        }
    }

    @Composable
    private fun PlayPauseButton(
        modifier: GlanceModifier = GlanceModifier,
        isPlaying: Boolean,
        iconColor: ColorProvider = GlanceTheme.colors.onPrimary,
        backgroundColor: ColorProvider = GlanceTheme.colors.primary,
        iconSize: androidx.compose.ui.unit.Dp = 24.dp,
        cornerRadius: androidx.compose.ui.unit.Dp = 24.dp
    ) {
        Box(
            modifier = modifier
                .background(backgroundColor)
                .cornerRadius(cornerRadius)
                .clickable(actionRunCallback<PlayPauseAction>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(iconColor)
            )
        }
    }

    @Composable
    private fun NextButton(
        modifier: GlanceModifier = GlanceModifier,
        iconColor: ColorProvider = GlanceTheme.colors.onTertiary,
        backgroundColor: ColorProvider = GlanceTheme.colors.tertiary,
        iconSize: androidx.compose.ui.unit.Dp = 24.dp,
        cornerRadius: androidx.compose.ui.unit.Dp = 24.dp
    ) {
        Box(
            modifier = modifier
                .background(backgroundColor)
                .cornerRadius(cornerRadius)
                .clickable(actionRunCallback<SkipNextAction>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_skip_next),
                contentDescription = LocalContext.current.getString(R.string.onboarding_next),
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(iconColor)
            )
        }
    }

    @Composable
    private fun PreviousButton(
        modifier: GlanceModifier = GlanceModifier,
        iconColor: ColorProvider = GlanceTheme.colors.onTertiary,
        backgroundColor: ColorProvider = GlanceTheme.colors.tertiary,
        iconSize: androidx.compose.ui.unit.Dp = 24.dp,
        cornerRadius: androidx.compose.ui.unit.Dp = 24.dp
    ) {
        Box(
            modifier = modifier
                .background(backgroundColor)
                .cornerRadius(cornerRadius)
                .clickable(actionRunCallback<SkipPreviousAction>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_skip_previous),
                contentDescription = LocalContext.current.getString(R.string.animatedplaybackcontrols_previous),
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(iconColor)
            )
        }
    }

    @Composable
    private fun WidgetContent(
        songTitle: String,
        artistName: String,
        isPlaying: Boolean,
        lyricLinesStr: String,
        activeIndex: Int,
        size: DpSize
    ) {
        val lines = if (lyricLinesStr.isNotEmpty()) lyricLinesStr.split("##LINE##") else emptyList()
        val prevLine = if (activeIndex > 0) lines.getOrNull(activeIndex - 1) else null
        val activeLine = lines.getOrNull(activeIndex)
        val nextLine = lines.getOrNull(activeIndex + 1)
        val hasSong = songTitle.isNotBlank() && songTitle != LocalContext.current.getString(R.string.app_name)
        val isCompact = size.height < 120.dp

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(GlanceTheme.colors.widgetBackground)
        ) {

            // Main content container utilizing the full widget width
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // App branding row (always shown)
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_notification),
                        contentDescription = LocalContext.current.getString(R.string.app_name),
                        modifier = GlanceModifier.size(14.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
                    )
                    Spacer(GlanceModifier.width(5.dp))
                    Text(
                        text = LocalContext.current.getString(R.string.app_name),
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.primary
                        ),
                        maxLines = 1
                    )
                }

                Spacer(GlanceModifier.height(6.dp))

                // Song info row
                if (hasSong) {
                    Text(
                        text = songTitle,
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onSurface
                        ),
                        maxLines = 1
                    )
                    if (artistName.isNotEmpty()) {
                        Text(
                            text = artistName,
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = GlanceTheme.colors.onSurfaceVariant
                            ),
                            maxLines = 1
                        )
                    }
                    Spacer(GlanceModifier.height(6.dp))
                }

                // Lyrics area
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxWidth()
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (hasSong) {
                        if (lines.isNotEmpty()) {
                            if (!isCompact && prevLine != null) {
                                Text(
                                    text = prevLine,
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        color = GlanceTheme.colors.onSurfaceVariant
                                    ),
                                    maxLines = 1,
                                    modifier = GlanceModifier.padding(vertical = 1.dp)
                                )
                            }
                            if (activeLine != null) {
                                Text(
                                    text = activeLine,
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GlanceTheme.colors.primary
                                    ),
                                    maxLines = if (isCompact) 1 else 2,
                                    modifier = GlanceModifier.padding(vertical = 2.dp)
                                )
                            } else {
                                Text(
                                    text = androidx.glance.LocalContext.current.getString(chromahub.rhythm.app.R.string.ui_instrumental),
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GlanceTheme.colors.primary
                                    ),
                                    maxLines = 1
                                )
                            }
                            if (!isCompact && nextLine != null) {
                                Text(
                                    text = nextLine,
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        color = GlanceTheme.colors.onSurfaceVariant
                                    ),
                                    maxLines = 1,
                                    modifier = GlanceModifier.padding(vertical = 1.dp)
                                )
                            }
                        } else {
                            Text(
                                text = LocalContext.current.getString(R.string.rhythmlyricswidget_no_lyrics_available),
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = GlanceTheme.colors.onSurfaceVariant
                                ),
                                maxLines = 1
                            )
                        }
                    } else {
                        Text(
                            text = LocalContext.current.getString(R.string.rhythmlyricswidget_tagline),
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = GlanceTheme.colors.onSurfaceVariant
                            ),
                            maxLines = 1
                        )
                        Spacer(GlanceModifier.height(3.dp))
                        Text(
                            text = LocalContext.current.getString(R.string.rhythmlyricswidget_open_to_play),
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = GlanceTheme.colors.onSurfaceVariant
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    // Controls row (only when song is playing)
                    if (hasSong) {
                        Spacer(GlanceModifier.height(6.dp))
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalAlignment = Alignment.End
                        ) {
                            PreviousButton(
                                modifier = GlanceModifier.size(36.dp),
                                iconSize = 20.dp,
                                cornerRadius = 18.dp
                            )
                            Spacer(GlanceModifier.width(10.dp))
                            val playButtonCornerRadius = if (isPlaying) 14.dp else 24.dp
                            PlayPauseButton(
                                modifier = GlanceModifier.size(40.dp),
                                isPlaying = isPlaying,
                                iconSize = 22.dp,
                                cornerRadius = playButtonCornerRadius
                            )
                            Spacer(GlanceModifier.width(10.dp))
                            NextButton(
                                modifier = GlanceModifier.size(36.dp),
                                iconSize = 20.dp,
                                cornerRadius = 18.dp
                            )
                        }
                    }
                }
            }
        }
    }
