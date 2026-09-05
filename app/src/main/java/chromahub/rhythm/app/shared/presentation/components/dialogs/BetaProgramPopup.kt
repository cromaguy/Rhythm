/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package chromahub.rhythm.app.shared.presentation.components.dialogs

import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.StandardBottomSheetHeader
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.groupedBottomSheetItemShape
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import androidx.compose.ui.res.stringResource
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils

private data class BetaFeature(
    val icon: MaterialSymbolIcon,
    val title: String,
    val description: String
)

@Composable
fun BetaProgramBottomSheet(
    showDialog: Boolean,
    onDismiss: () -> Unit
) {
    if (showDialog) {
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )

        val betaFeatures = listOf(
            BetaFeature(
                icon = MaterialSymbolIcon("flight_takeoff", filled = true),
                title = stringResource(R.string.betaprogrampopup_early_access),
                description = stringResource(R.string.betaprogrampopup_early_access_desc)
            ),
            BetaFeature(
                icon = MaterialSymbolIcon("edit_note", filled = true),
                title = stringResource(R.string.betaprogrampopup_shape_the_future),
                description = stringResource(R.string.betaprogrampopup_shape_the_future_desc)
            ),
            BetaFeature(
                icon = MaterialSymbolIcon("message", filled = true),
                title = stringResource(R.string.betaprogrampopup_direct_feedback),
                description = stringResource(R.string.betaprogrampopup_direct_feedback_desc)
            ),
        )

        RhythmAdaptiveModalSheet(
            adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(),
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.primary
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp
        ) {
            StandardBottomSheetHeader(
                title = stringResource(R.string.beta_program),
                subtitle = stringResource(R.string.betaprogrampopup_youre_part_of_an),
                visible = true
            )

            val scrollState = rememberScrollState()

            AdaptiveSheetScrollContainer(
                scrollState = scrollState,
                modifier = Modifier.fillMaxWidth()
            ) { endPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(start = 24.dp, end = 24.dp + endPadding, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    betaFeatures.forEachIndexed { index, feature ->
                        BetaFeatureCard(
                            feature = feature,
                            shape = groupedBottomSheetItemShape(index, betaFeatures.size)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CTA Button (Pinned at bottom)
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            ) {
                Button(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = RhythmIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.betaprogrampopup_start_exploring),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun BetaProgramPopup(
    showDialog: Boolean,
    onDismiss: () -> Unit
) {
    BetaProgramBottomSheet(
        showDialog = showDialog,
        onDismiss = onDismiss
    )
}

@Composable
private fun BetaFeatureCard(
    feature: BetaFeature,
    shape: Shape
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = feature.title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
