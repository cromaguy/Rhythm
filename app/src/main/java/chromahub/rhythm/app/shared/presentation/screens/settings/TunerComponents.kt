/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.screens.settings



import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import androidx.compose.foundation.layout.PaddingValues
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import chromahub.rhythm.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Slider
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import chromahub.rhythm.app.BuildConfig
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.data.repository.PlaybackStatsRepository
import chromahub.rhythm.app.shared.data.repository.StatsTimeRange
import chromahub.rhythm.app.util.GsonUtils
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlin.system.exitProcess
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.common.ButtonGroupStyle
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveScrollBar
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveButtonGroup
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveGroupButton
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.StandardBottomSheetHeader
import chromahub.rhythm.app.shared.presentation.components.common.StyledProgressBar
import chromahub.rhythm.app.shared.presentation.components.common.ProgressStyle
import chromahub.rhythm.app.shared.presentation.components.common.ThumbStyle
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.LicensesBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.UpdateBottomSheet
import chromahub.rhythm.app.ui.utils.LazyListStateSaver
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeProvider
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapes
import chromahub.rhythm.app.shared.presentation.components.common.buildSplashBackdropShapes
import chromahub.rhythm.app.shared.presentation.components.common.SplashBackgroundOrbs
import chromahub.rhythm.app.shared.presentation.viewmodel.AppUpdaterViewModel
import chromahub.rhythm.app.shared.presentation.viewmodel.AppVersion
import chromahub.rhythm.app.ui.theme.getFontPreviewStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import chromahub.rhythm.app.utils.FontLoader
import chromahub.rhythm.app.ui.theme.parseCustomColorScheme
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.text.HtmlCompat
import chromahub.rhythm.app.shared.presentation.components.common.M3FourColorCircularLoader
import chromahub.rhythm.app.shared.presentation.components.player.PlayingEqIcon
import chromahub.rhythm.app.shared.presentation.components.dialogs.CreatePlaylistDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.BulkPlaylistExportDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaylistImportDialog
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShape
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaylistOperationProgressDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaylistOperationResultDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.AppRestartDialog
import chromahub.rhythm.app.shared.presentation.components.player.PlayerChipOrderBottomSheet
import chromahub.rhythm.app.features.local.presentation.components.settings.HomeSectionOrderBottomSheet
import chromahub.rhythm.app.features.local.presentation.components.settings.LibraryTabOrderBottomSheet
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem

@Composable
fun TunerSettingRow(item: SettingItem) {
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val hapticFeedbackEnabled by appSettings.hapticFeedbackEnabled.collectAsState()
    
    // Animation states
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tuner_setting_scale"
    )
    
    val iconBackgroundColor by animateColorAsState(
        targetValue = when {
            !item.enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            item.toggleState == true -> MaterialTheme.colorScheme.primaryContainer
            isPressed -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tuner_icon_bg_color"
    )
    
    val iconTintColor by animateColorAsState(
        targetValue = when {
            !item.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            item.toggleState == true -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tuner_icon_tint_color"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = if (item.enabled) 1f else 0.6f
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (item.enabled && item.onClick != null && item.toggleState == null) {
                    Modifier.clickable(onClick = {
                        isPressed = true
                        HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.HEAVY)
                        item.onClick()
                    })
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon container with expressive design
        item.icon?.let { icon ->
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(34.dp),
                color = iconBackgroundColor,
                tonalElevation = if (item.toggleState == true) 2.dp else 0.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = item.title,
                        modifier = Modifier.size(24.dp),
                        tint = iconTintColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (item.enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            item.description?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    },
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
            }
        }

        if (item.toggleState != null && item.onClick != null) {
            Icon(
                imageVector = MaterialSymbolIcon("arrow_forward_ios", filled = true),
                contentDescription = context.getString(R.string.cd_navigate),
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            TunerAnimatedSwitch(
                checked = item.toggleState,
                onCheckedChange = {
                    if (!item.enabled) return@TunerAnimatedSwitch
                    item.onToggleChange?.invoke(it)
                },
                enabled = item.enabled
            )
        } else if (item.toggleState != null) {
            TunerAnimatedSwitch(
                checked = item.toggleState,
                onCheckedChange = {
                    if (!item.enabled) return@TunerAnimatedSwitch
                    item.onToggleChange?.invoke(it)
                },
                enabled = item.enabled
            )
        } else if (item.onClick != null) {
            Icon(
                imageVector = MaterialSymbolIcon("arrow_forward_ios", filled = true),
                contentDescription = context.getString(R.string.cd_navigate),
                modifier = Modifier.size(20.dp),
                tint = if (item.enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                }
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerAnimatedSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    Switch(
        checked = checked,
        onCheckedChange = {
            HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
            onCheckedChange(it)
        },
        enabled = enabled,
        modifier = modifier,
        thumbContent = {
            AnimatedContent(
                targetState = checked,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(110))
                },
                label = "switch_icon_animation"
            ) { isChecked ->
                Icon(
                    imageVector = if (isChecked) RhythmIcons.Check else RhythmIcons.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        }
    )
}



@Composable
fun TunerSettingCard(
    title: String,
    description: String,
    icon: MaterialSymbolIcon,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && checked == null) {
                    Modifier.clickable(onClick = {
                        HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.MEDIUM)
                        onClick()
                    })
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (checked != null && onCheckedChange != null) {
                TunerAnimatedSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            } else if (onClick != null) {
                Icon(
                    imageVector = MaterialSymbolIcon("arrow_forward_ios", filled = true),
                    contentDescription = context.getString(R.string.cd_navigate),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}



fun toMaterial3SettingsItem(
    context: Context,
    item: SettingItem,
    hapticFeedback: HapticFeedback? = null,
    description: (@Composable () -> Unit)? = item.description?.let { desc -> { Text(desc) } }


): Material3SettingsItem {
    return Material3SettingsItem(
        icon = item.icon,
        title = { Text(item.title) },
        description = description,
        trailingContent = if (item.toggleState != null && item.onClick != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MaterialSymbolIcon("arrow_forward_ios", filled = true),
                        contentDescription = context.getString(R.string.cd_navigate),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    TunerAnimatedSwitch(
                        checked = item.toggleState,
                        onCheckedChange = {
                            if (!item.enabled) return@TunerAnimatedSwitch
                            item.onToggleChange?.invoke(it)
                        },
                        enabled = item.enabled
                    )
                }
            }
        } else if (item.toggleState != null) {
            {
                TunerAnimatedSwitch(
                    checked = item.toggleState,
                    onCheckedChange = {
                        if (!item.enabled) return@TunerAnimatedSwitch
                        item.onToggleChange?.invoke(it)
                    },
                    enabled = item.enabled
                )
            }
        } else if (item.onClick != null) {
            {
                Icon(
                    imageVector = MaterialSymbolIcon("arrow_forward_ios", filled = true),
                    contentDescription = context.getString(R.string.cd_navigate),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            null
        },
        isHighlighted = item.toggleState == true,
        enabled = item.enabled,
        onClick = when {
            item.onClick != null -> {
                {
                    if (item.enabled) {
                        hapticFeedback?.let { haptic ->
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                        }
                        item.onClick.invoke()
                    }
                }
            }

            item.toggleState != null && item.onToggleChange != null -> {
                {
                    if (item.enabled) {
                        hapticFeedback?.let { haptic ->
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        }
                        item.onToggleChange.invoke(!item.toggleState)
                    }
                }
            }

            else -> null
        }
    )
}