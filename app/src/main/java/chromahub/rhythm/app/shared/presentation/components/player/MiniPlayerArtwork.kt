/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.ShimmerBox
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.util.M3ImageUtils

/**
 * Animated MiniPlayer artwork thumbnail with elastic spring morph transitions on track change.
 */
@Composable
fun MiniPlayerArtwork(
    song: Song?,
    direction: TrackChangeDirection,
    isExpressive: Boolean,
    cornerRadius: Int,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    customShape: Shape? = null
) {
    val artShape = customShape ?: rememberExpressiveShapeFor(
        target = ExpressiveShapeTarget.MINI_PLAYER,
        fallbackShape = RoundedCornerShape(cornerRadius.dp)
    )

    Surface(
        modifier = modifier.size(size),
        shape = artShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        AnimatedContent(
            targetState = song,
            transitionSpec = {
                MiniPlayerMotion.createTrackTransition(direction, isExpressive)
            },
            label = "miniPlayerArtworkTransition"
        ) { currentSong ->
            if (currentSong != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ShimmerBox(modifier = Modifier.fillMaxSize())
                    M3ImageUtils.TrackImage(
                        imageUrl = currentSong.artworkUri,
                        trackName = currentSong.title,
                        modifier = Modifier.fillMaxSize(),
                        applyExpressiveShape = false
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = RhythmIcons.Album,
                        contentDescription = null,
                        modifier = Modifier.size(size * 0.5f),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
