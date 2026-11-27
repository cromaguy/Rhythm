/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package chromahub.rhythm.app.shared.presentation.components.dialogs

import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
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
                                    0 -> RecapMinutesPage(recap)
                                    1 -> RecapRhythmPage(recap)
                                    else -> RecapSoundtrackPage(recap)
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
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.width(12.dp))
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
private fun RecapEyebrow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.8.sp),
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
    )
}

@Composable
private fun RecapMinutesPage(recap: ListeningRecap) {
    val foreground = MaterialTheme.colorScheme.onPrimary
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(recap.totalListenedMs)
    val hours = totalMinutes / 60
    val avgMinutes = TimeUnit.MILLISECONDS.toMinutes(recap.averageDailyMs)

    RecapEyebrow("${recap.year} IN LISTENING")
    Spacer(Modifier.height(8.dp))

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
            value = "$avgMinutes min"
        ),
        RecapMetric(
            icon = MaterialSymbolIcon("play_circle", filled = true),
            label = "Total plays",
            value = recap.totalPlays.grouped()
        ),
        RecapMetric(
            icon = MaterialSymbolIcon("artist", filled = true),
            label = "Artists",
            value = recap.uniqueArtists.grouped()
        ),
        RecapMetric(
            icon = MaterialSymbolIcon("album", filled = true),
            label = "Albums",
            value = recap.uniqueAlbums.grouped()
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        metrics.chunked(2).forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowItems.forEachIndexed { columnIndex, metric ->
                    RecapMetricTile(
                        metric = metric,
                        shape = recapTileShape(rowIndex * 2 + columnIndex, metrics.size),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
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
private fun RecapRhythmPage(recap: ListeningRecap) {
    val foreground = MaterialTheme.colorScheme.onPrimary
    val peakHour = recap.hourlyDurationsMs.indices.maxByOrNull { recap.hourlyDurationsMs[it] } ?: 0
    val maxBucket = (recap.hourlyDurationsMs.maxOrNull() ?: 0L).coerceAtLeast(1L)

    RecapEyebrow("LISTENING RHYTHM")
    Spacer(Modifier.height(8.dp))

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
            color = foreground.copy(alpha = 0.16f),
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
                value = hourLabel(peakHour)
            ),
            shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 6.dp, bottomEnd = 6.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        RecapMetricTile(
            metric = RecapMetric(
                icon = MaterialSymbolIcon("calendar_month", filled = true),
                label = "Top day",
                value = dayName
            ),
            shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 24.dp, bottomEnd = 24.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun RecapSoundtrackPage(recap: ListeningRecap) {
    val foreground = MaterialTheme.colorScheme.onPrimary
    val artistShape = rememberExpressiveShapeFor(
        ExpressiveShapeTarget.ARTIST_ART,
        fallbackShape = CircleShape
    )

    RecapEyebrow("YOUR SOUNDTRACK")
    Spacer(Modifier.height(12.dp))

    recap.topArtist?.let { artist ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = artistShape,
                color = foreground.copy(alpha = 0.16f),
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
            color = foreground.copy(alpha = 0.16f),
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
            containerColor = foreground.copy(alpha = 0.12f)
        )
    }
}

private data class RecapMetric(
    val icon: MaterialSymbolIcon,
    val label: String,
    val value: String
)

@Composable
private fun RecapMetricTile(
    metric: RecapMetric,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier
) {
    val foreground = MaterialTheme.colorScheme.onPrimary

    Surface(
        shape = shape,
        color = foreground.copy(alpha = 0.12f),
        contentColor = foreground,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = foreground.copy(alpha = 0.18f),
                contentColor = foreground
            ) {
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = metric.icon,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = metric.value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = metric.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = foreground.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
            color = if (isTop) foreground else foreground.copy(alpha = 0.18f),
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
            color = foreground.copy(alpha = 0.18f),
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

private fun recapTileShape(index: Int, totalItems: Int): RoundedCornerShape {
    if (totalItems <= 1) {
        return RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    }
    if (totalItems == 2) {
        return if (index == 0) {
            RoundedCornerShape(topStart = 24.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        } else {
            RoundedCornerShape(topStart = 6.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        }
    }
    return when (index) {
        0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        else -> RoundedCornerShape(6.dp)
    }
}

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
