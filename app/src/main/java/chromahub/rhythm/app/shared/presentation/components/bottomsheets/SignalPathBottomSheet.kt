/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chromahub.rhythm.app.infrastructure.audio.SignalPathReport
import chromahub.rhythm.app.shared.presentation.components.common.MarqueeText
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.ui.theme.MusicDimensions

/**
 * End-to-end Audio Signal Path inspection sheet opened from Playback Quality badges and Player
 * quality chips. Renders the pipeline as grouped Rhythm tiles, a fidelity verdict and the live
 * Bit-Perfect USB verification results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalPathBottomSheet(
    report: SignalPathReport,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
) {
    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.TWO_PANE_DIALOG,
        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp)
        ) {
            StandardBottomSheetHeader(
                title = report.verdictTitle,
                subtitle = "${report.fidelityScore}% fidelity",
                visible = true
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = report.verdictSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SignalPathSection(
                    title = "Signal path",
                    icon = MaterialSymbolIcon("route", filled = true),
                    checks = report.checks
                )
            }
        }
    }
}

@Composable
private fun SignalPathSection(
    title: String,
    icon: MaterialSymbolIcon,
    checks: List<chromahub.rhythm.app.infrastructure.audio.SignalPathCheck>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            checks.forEachIndexed { index, check ->
                SignalPathTile(
                    label = check.stage,
                    value = check.detail,
                    passed = check.passed,
                    shape = connectedSignalTileShape(index, checks.lastIndex)
                )
            }
        }
    }
}

@Composable
private fun SignalPathTile(
    label: String,
    value: String,
    passed: Boolean,
    shape: RoundedCornerShape
) {
    val accent = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val tileColor = lerp(MaterialTheme.colorScheme.surfaceContainer, accent, 0.12f)

    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape),
        shape = shape,
        color = tileColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MusicDimensions.infoTileHeight)
        ) {
            Icon(
                imageVector = MaterialSymbolIcon(
                    if (passed) "check_circle" else "error",
                    filled = true
                ),
                contentDescription = null,
                tint = accent.copy(alpha = MusicDimensions.infoTileWatermarkAlpha),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(
                        x = MusicDimensions.infoTileBackdropCut,
                        y = MusicDimensions.infoTileBackdropCut
                    )
                    .size(MusicDimensions.infoTileIconSize)
            )

            MarqueeText(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                ),
                gradientEdgeColor = tileColor,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = MusicDimensions.infoTileInset,
                        end = MusicDimensions.infoTileInset,
                        bottom = MusicDimensions.infoTileInset
                    )
            )

            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = accent,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(
                        top = MusicDimensions.infoTileInset,
                        start = MusicDimensions.infoTileInset,
                        end = MusicDimensions.infoTileInset
                    )
            )
        }
    }
}

/** Connected tile group used by the signal path list: rounded on the outer ends, tight in between. */
private fun connectedSignalTileShape(index: Int, lastIndex: Int): RoundedCornerShape {
    val outer = 24.dp
    val inner = 8.dp
    return when {
        index == 0 && index == lastIndex -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == lastIndex -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = inner, bottomEnd = inner)
    }
}
