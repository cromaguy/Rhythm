/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.components.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun RhythmWavyProgressLoader(
    progress: Float?,
    modifier: Modifier = Modifier,
    indicatorColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    trackColor: Color = indicatorColor.copy(alpha = 0.2f),
    centerContent: @Composable BoxScope.() -> Unit = {}
) {
    val progressValue = progress?.coerceIn(0f, 1f)
    val currentProgress by rememberUpdatedState(progressValue)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val prog = currentProgress
        if (prog != null) {
            CircularWavyProgressIndicator(
                progress = { prog },
                modifier = Modifier.fillMaxSize(),
                color = indicatorColor,
                trackColor = trackColor
            )
        } else {
            CircularWavyProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = indicatorColor,
                trackColor = trackColor
            )
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            content = centerContent
        )
    }
}
