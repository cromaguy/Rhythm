/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package chromahub.rhythm.app.shared.presentation.components.dialogs

import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.repository.ListeningRecap
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.SettingsBadgePalette
import chromahub.rhythm.app.shared.presentation.components.SettingsPalettes
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.MarqueeText
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShape
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.ui.theme.MusicDimensions
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

private const val RECAP_PAGE_COUNT = 3

private val RECAP_STEP_WORDS = listOf("Hours", "Habits", "Anthems")

private enum class RecapBackdropMotion { SPIN, BREATHE, DRIFT, SWAY }

private data class RecapBackdropBlob(
    val x: Float,
    val y: Float,
    val size: Float,
    val rotation: Float,
    val motion: RecapBackdropMotion,
    val shapeId: String
)

private val RECAP_BACKDROP_BLOBS = listOf(
    RecapBackdropBlob(0f, 0.03f, 0.30f, -5f, RecapBackdropMotion.SPIN, "COOKIE_12"),
    RecapBackdropBlob(0.56f, 0.01f, 0.26f, 4f, RecapBackdropMotion.BREATHE, "SUNNY"),
    RecapBackdropBlob(0.86f, 0.10f, 0.18f, -3f, RecapBackdropMotion.SWAY, "GEM"),
    RecapBackdropBlob(0.06f, 0.24f, 0.22f, 5f, RecapBackdropMotion.DRIFT, "CLOVER_8_LEAF"),
    RecapBackdropBlob(0.44f, 0.30f, 0.28f, -4f, RecapBackdropMotion.BREATHE, "SOFT_BURST"),
    RecapBackdropBlob(0.78f, 0.38f, 0.24f, 3f, RecapBackdropMotion.SPIN, "FLOWER"),
    RecapBackdropBlob(0.02f, 0.46f, 0.26f, -6f, RecapBackdropMotion.DRIFT, "COOKIE_6"),
    RecapBackdropBlob(0.30f, 0.56f, 0.24f, 4f, RecapBackdropMotion.SWAY, "HEART"),
    RecapBackdropBlob(0.70f, 0.64f, 0.28f, -3f, RecapBackdropMotion.SPIN, "COOKIE_9"),
    RecapBackdropBlob(0.06f, 0.74f, 0.26f, 6f, RecapBackdropMotion.DRIFT, "CLOVER_4_LEAF"),
    RecapBackdropBlob(0.44f, 0.86f, 0.28f, -5f, RecapBackdropMotion.BREATHE, "GEM")
)

@Composable
fun ListeningRecapDialog(
    recap: ListeningRecap,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val pagerState = rememberPagerState(pageCount = { RECAP_PAGE_COUNT })

    val background = MaterialTheme.colorScheme.primary
    val foreground = MaterialTheme.colorScheme.onPrimary

    val useDarkSystemIcons = foreground.luminance() > 0.5f
    LaunchedEffect(Unit) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = useDarkSystemIcons
            isAppearanceLightNavigationBars = useDarkSystemIcons
        }
    }

    val goNext: () -> Unit = {
        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
        if (pagerState.currentPage >= RECAP_PAGE_COUNT - 1) {
            onDismiss()
        } else {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
        ) {
            RecapBackdrop(color = foreground)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                RecapBrandHeader(recap = recap, foreground = foreground)

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    val pageOffset = (
                        (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        ).absoluteValue

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = 1f - (pageOffset * 0.55f).coerceAtMost(0.45f)
                                val scale = 1f - (pageOffset * 0.04f).coerceAtMost(0.04f)
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 560.dp)
                            ) {
                                when (page) {
                                    0 -> RecapMinutesPage(recap, pagerState)
                                    1 -> RecapRhythmPage(recap, pagerState)
                                    else -> RecapSoundtrackPage(recap, pagerState)
                                }
                            }
                        }
                    }
                }

                RecapFooter(
                    pagerState = pagerState,
                    onNext = goNext
                )
            }
        }
    }
}

@Composable
private fun RecapBrandHeader(
    recap: ListeningRecap,
    foreground: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.rhythm_splash_logo),
            contentDescription = null,
            modifier = Modifier.size(70.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Recap",
            style = MaterialTheme.typography.displaySmall.copy(letterSpacing = (-1).sp),
            fontWeight = FontWeight.Black,
            color = foreground
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = recap.year.toString(),
            style = MaterialTheme.typography.displaySmall.copy(letterSpacing = (-1).sp),
            fontWeight = FontWeight.Black,
            color = foreground.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun RecapBackdrop(color: Color) {
    val transition = rememberInfiniteTransition(label = "recap_backdrop")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 46000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val drift by transition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )
    val sway by transition.animateFloat(
        initialValue = 8f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        RECAP_BACKDROP_BLOBS.forEachIndexed { index, blob ->
            val shape = rememberExpressiveShape(blob.shapeId)
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * blob.x, y = maxHeight * blob.y)
                    .size(maxWidth * blob.size)
                    .graphicsLayer {
                        rotationZ = blob.rotation
                        when (blob.motion) {
                            RecapBackdropMotion.SPIN -> rotationZ = rotation
                            RecapBackdropMotion.BREATHE -> {
                                scaleX = pulse
                                scaleY = pulse
                            }
                            RecapBackdropMotion.DRIFT -> translationY = drift
                            RecapBackdropMotion.SWAY -> translationX = sway
                        }
                    }
                    .background(
                        color = color.copy(alpha = if (index % 2 == 0) 0.05f else 0.035f),
                        shape = shape
                    )
            )
        }
    }
}

@Composable
private fun RecapStepWord(page: Int, pagerState: PagerState) {
    val enterFromEnd = remember(page) { page >= pagerState.currentPage }
    var entered by remember(page) { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = 480,
            delayMillis = 120,
            easing = FastOutSlowInEasing
        ),
        label = "recap_step_word"
    )
    LaunchedEffect(page) { entered = true }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(
            text = RECAP_STEP_WORDS[page],
            style = MaterialTheme.typography.displayLarge.copy(letterSpacing = (-2).sp),
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                alpha = progress
                translationX = (1f - progress) * if (enterFromEnd) 80f else -80f
            }
        )
    }
}

@Composable
private fun RecapFooter(
    pagerState: PagerState,
    onNext: () -> Unit
) {
    val isLast = pagerState.currentPage >= RECAP_PAGE_COUNT - 1
    val accent = MaterialTheme.colorScheme.primary
    val foreground = MaterialTheme.colorScheme.onPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(RECAP_PAGE_COUNT) { index ->
                val isActive = pagerState.currentPage == index
                val width by animateDpAsState(
                    targetValue = if (isActive) 22.dp else 8.dp,
                    label = "recap_dot_width"
                )
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(width = width, height = 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) foreground else foreground.copy(alpha = 0.3f)
                        )
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onNext,
            shape = RoundedCornerShape(40.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = foreground,
                contentColor = accent
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = if (isLast) "Done" else "Next",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = if (isLast) RhythmIcons.Check else RhythmIcons.ArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RecapMinutesPage(recap: ListeningRecap, pagerState: PagerState) {
    val foreground = MaterialTheme.colorScheme.onPrimary
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(recap.totalListenedMs)
    val hours = totalMinutes / 60
    val avgMinutes = TimeUnit.MILLISECONDS.toMinutes(recap.averageDailyMs)

    RecapStepWord(page = 0, pagerState = pagerState)
    Spacer(Modifier.height(2.dp))

    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = if (hours > 0) hours.grouped() else totalMinutes.grouped(),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = foreground
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = when {
                hours > 1L -> "hours"
                hours == 1L -> "hour"
                else -> "minutes"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = foreground.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 10.dp)
        )
    }

    Text(
        text = if (hours > 0) {
            "Averaging $avgMinutes minutes a day across ${recap.activeDays} listening days."
        } else {
            "${recap.totalPlays.grouped()} plays across ${recap.activeDays} listening days."
        },
        style = MaterialTheme.typography.bodyLarge,
        color = foreground.copy(alpha = 0.8f)
    )

    Spacer(Modifier.height(20.dp))

    val metrics = listOf(
        RecapMetric(
            icon = MaterialSymbolIcon("schedule", filled = true),
            label = "Daily average",
            value = "$avgMinutes min",
            palette = SettingsPalettes.SkyBlue
        ),
        RecapMetric(
            icon = MaterialSymbolIcon("play_circle", filled = true),
            label = "Total plays",
            value = recap.totalPlays.grouped(),
            palette = SettingsPalettes.Lime
        ),
        RecapMetric(
            icon = MaterialSymbolIcon("artist", filled = true),
            label = "Artists",
            value = recap.uniqueArtists.grouped(),
            palette = SettingsPalettes.Amber
        ),
        RecapMetric(
            icon = MaterialSymbolIcon("album", filled = true),
            label = "Albums",
            value = recap.uniqueAlbums.grouped(),
            palette = SettingsPalettes.Rose
        )
    )

    val totalRows = (metrics.size + 1) / 2
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        metrics.chunked(2).forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowItems.forEachIndexed { columnIndex, metric ->
                    RecapMetricTile(
                        metric = metric,
                        shape = connectedRecapTileShape(rowIndex, columnIndex, totalRows),
                        modifier = Modifier.weight(1f)
                    )
                }
                // If the row has a single item, keep the empty slot so the tile keeps half width
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    recap.biggestDay?.let { day ->
        Spacer(Modifier.height(16.dp))
        RecapHighlightCard(
            icon = MaterialSymbolIcon("calendar_month", filled = true),
            title = day.formattedDay(),
            subtitle = "Your biggest listening day",
            trailing = TimeUnit.MILLISECONDS.toMinutes(recap.biggestDayDurationMs)
                .let { "$it min" }
        )
    }
}

@Composable
private fun RecapRhythmPage(recap: ListeningRecap, pagerState: PagerState) {
    val foreground = MaterialTheme.colorScheme.onPrimary
    val peakHour = recap.hourlyDurationsMs.indices.maxByOrNull { recap.hourlyDurationsMs[it] } ?: 0
    val maxBucket = (recap.hourlyDurationsMs.maxOrNull() ?: 0L).coerceAtLeast(1L)

    RecapStepWord(page = 1, pagerState = pagerState)
    Spacer(Modifier.height(2.dp))

    Text(
        text = recap.personality.title,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Black,
        color = foreground
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = recap.personality.subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = foreground.copy(alpha = 0.8f)
    )

    Spacer(Modifier.height(20.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "When you listen",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = foreground,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = CircleShape,
            color = recapSolidColor(0.28f),
            contentColor = foreground
        ) {
            Text(
                text = "Peak ${hourLabel(peakHour)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        recap.hourlyDurationsMs.forEachIndexed { hour, bucketMs ->
            val fraction = (bucketMs.toFloat() / maxBucket.toFloat()).coerceIn(0f, 1f)
            val isPeak = hour == peakHour && bucketMs > 0L
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((10f + fraction * 106f).dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            isPeak -> foreground
                            fraction >= 0.5f -> foreground.copy(alpha = 0.6f)
                            else -> foreground.copy(alpha = 0.25f)
                        }
                    )
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("12a", "6a", "12p", "6p", "12a").forEachIndexed { index, label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = foreground.copy(alpha = 0.7f),
                textAlign = when (index) {
                    0 -> TextAlign.Start
                    4 -> TextAlign.End
                    else -> TextAlign.Center
                },
                modifier = Modifier.weight(1f)
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    val dayName = recap.favoriteDayOfWeek
        ?.getDisplayName(TextStyle.FULL, Locale.getDefault())
        ?: "—"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RecapMetricTile(
            metric = RecapMetric(
                icon = MaterialSymbolIcon("schedule", filled = true),
                label = "Peak hour",
                value = hourLabel(peakHour),
                palette = SettingsPalettes.Cyan
            ),
            shape = connectedRecapTileShape(row = 0, col = 0, totalRows = 1),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        RecapMetricTile(
            metric = RecapMetric(
                icon = MaterialSymbolIcon("calendar_month", filled = true),
                label = "Top day",
                value = dayName,
                palette = SettingsPalettes.Indigo
            ),
            shape = connectedRecapTileShape(row = 0, col = 1, totalRows = 1),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun RecapSoundtrackPage(recap: ListeningRecap, pagerState: PagerState) {
    val foreground = MaterialTheme.colorScheme.onPrimary
    val artistShape = rememberExpressiveShapeFor(
        ExpressiveShapeTarget.ARTIST_ART,
        fallbackShape = CircleShape
    )

    RecapStepWord(page = 2, pagerState = pagerState)
    Spacer(Modifier.height(2.dp))

    recap.topArtist?.let { artist ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = artistShape,
                color = recapSolidColor(0.28f),
                contentColor = foreground,
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = artist.trim().take(1).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = foreground
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TOP ARTIST",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
                    fontWeight = FontWeight.Bold,
                    color = foreground.copy(alpha = 0.75f)
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = foreground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${recap.topArtistPlayCount.grouped()} plays",
                    style = MaterialTheme.typography.bodyMedium,
                    color = foreground.copy(alpha = 0.75f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    recap.topDecade?.let { decade ->
        Surface(
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 24.dp),
            color = recapSolidColor(0.2f),
            contentColor = foreground,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = MaterialSymbolIcon("history", filled = true),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Favourite era",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${decade.decade}s • ${(decade.share * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (recap.topSongs.isNotEmpty()) {
        Text(
            text = "Most played",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = foreground
        )
        Spacer(Modifier.height(10.dp))

        Material3SettingsGroup(
            items = recap.topSongs.take(5).mapIndexed { index, song ->
                Material3SettingsItem(
                    leadingContent = {
                        SongRankBadge(
                            rank = index + 1,
                            albumArtUri = song.albumArtUri,
                            isTop = index == 0
                        )
                    },
                    title = {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = foreground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    description = {
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = foreground.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingContent = {
                        Text(
                            text = "${song.playCount.grouped()} plays",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = foreground
                        )
                    }
                )
            },
            containerColor = recapSolidColor(0.2f)
        )
    }
}

private data class RecapMetric(
    val icon: MaterialSymbolIcon,
    val label: String,
    val value: String,
    val palette: SettingsBadgePalette
)

@Composable
private fun RecapMetricTile(
    metric: RecapMetric,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier
) {
    val tileColor = metric.palette.container
    val contentColor = metric.palette.tint

    Surface(
        shape = shape,
        color = tileColor,
        contentColor = contentColor,
        modifier = modifier
            .fillMaxWidth()
            .height(MusicDimensions.infoTileHeight)
            .clip(shape)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = metric.icon,
                contentDescription = null,
                tint = contentColor.copy(alpha = MusicDimensions.infoTileWatermarkAlpha),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(
                        x = MusicDimensions.infoTileBackdropCut,
                        y = MusicDimensions.infoTileBackdropCut
                    )
                    .size(MusicDimensions.infoTileIconSize)
            )

            MarqueeText(
                text = metric.value,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    color = contentColor,
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
                text = metric.label,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

private fun connectedRecapTileShape(row: Int, col: Int, totalRows: Int): RoundedCornerShape {
    val isFirstRow = row == 0
    val isLastRow = row == totalRows - 1
    return when (col) {
        0 -> when {
            isFirstRow && isLastRow -> RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 8.dp)
            isFirstRow -> RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            isLastRow -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 8.dp)
            else -> RoundedCornerShape(8.dp)
        }
        else -> when {
            isFirstRow && isLastRow -> RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 24.dp)
            isFirstRow -> RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            isLastRow -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 24.dp)
            else -> RoundedCornerShape(8.dp)
        }
    }
}

@Composable
private fun RecapHighlightCard(
    icon: MaterialSymbolIcon,
    title: String,
    subtitle: String,
    trailing: String
) {
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 6.dp),
        color = MaterialTheme.colorScheme.onPrimary,
        contentColor = accent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

@Composable
private fun SongRankBadge(
    rank: Int,
    albumArtUri: String?,
    isTop: Boolean
) {
    val context = LocalContext.current
    val foreground = MaterialTheme.colorScheme.onPrimary
    val artShape = rememberExpressiveShapeFor(
        ExpressiveShapeTarget.SONG_ART,
        fallbackShape = RoundedCornerShape(12.dp)
    )

    if (albumArtUri.isNullOrBlank()) {
        Surface(
            shape = CircleShape,
            color = if (isTop) foreground else recapSolidColor(0.34f),
            contentColor = if (isTop) MaterialTheme.colorScheme.primary else foreground
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black
                )
            }
        }
    } else {
        Surface(
            shape = artShape,
            color = recapSolidColor(0.34f),
            modifier = Modifier.size(40.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun recapSolidColor(blend: Float): Color = lerp(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.onPrimary,
    blend
)

private fun Long.grouped(): String = NumberFormat.getIntegerInstance().format(this)

private fun Int.grouped(): String = NumberFormat.getIntegerInstance().format(this)

private fun hourLabel(hour: Int): String = when {
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}

private fun LocalDate.formattedDay(): String =
    format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
