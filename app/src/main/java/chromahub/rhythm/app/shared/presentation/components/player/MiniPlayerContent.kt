/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.common.AutoScrollingTextOnDemand

/**
 * Animated MiniPlayer metadata (Title, Artist, Duration) with elastic spring morph transitions.
 */
@Composable
fun MiniPlayerContent(
    song: Song?,
    direction: TrackChangeDirection,
    isExpressive: Boolean,
    modifier: Modifier = Modifier,
    titleFontSize: Int = 14,
    artistFontSize: Int = 12,
    showTime: Boolean = false,
    progress: Float = 0f,
    useHoursFormat: Boolean = false,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    subtextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedContent(
            targetState = song,
            transitionSpec = {
                MiniPlayerMotion.createTrackTransition(direction, isExpressive)
            },
            label = "miniPlayerContentTransition"
        ) { currentSong ->
            if (currentSong != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AutoScrollingTextOnDemand(
                        text = currentSong.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = titleFontSize.sp,
                            color = textColor
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainer,
                        enabled = true,
                        textAlign = TextAlign.Start,
                        respectGlobalSetting = true
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoScrollingTextOnDemand(
                            text = currentSong.artist,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = artistFontSize.sp,
                                color = subtextColor
                            ),
                            modifier = if (showTime && currentSong.duration > 0) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                            gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainer,
                            enabled = true,
                            textAlign = TextAlign.Start,
                            respectGlobalSetting = true
                        )

                        if (showTime && currentSong.duration > 0 && progress > 0f) {
                            Spacer(modifier = Modifier.width(6.dp))
                            val currentPos = (progress * currentSong.duration).toLong()
                            Text(
                                text = "${formatDuration(currentPos, useHoursFormat)} / ${formatDuration(currentSong.duration, useHoursFormat)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = subtextColor
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.miniplayer_no_song),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = titleFontSize.sp,
                        color = textColor
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
