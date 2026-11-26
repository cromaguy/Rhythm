/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package chromahub.rhythm.app.shared.presentation.components.dialogs

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import chromahub.rhythm.app.shared.data.repository.ListeningRecap
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun ListeningRecapDialog(
    recap: ListeningRecap,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val palettes = listOf(
        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer),
        listOf(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.primaryContainer),
        listOf(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.surfaceContainerHighest)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val activeColors = palettes[pagerState.currentPage % palettes.size]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(activeColors))
                .statusBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (idx in 0 until 3) {
                        LinearProgressIndicator(
                            progress = { if (idx <= pagerState.currentPage) 1f else 0.25f },
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(CircleShape)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = RhythmIcons.Close,
                            contentDescription = "Close recap",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    when (page) {
                        0 -> RecapMinutesSlide(recap)
                        1 -> RecapPersonalitySlide(recap)
                        else -> RecapTopMusicSlide(recap)
                    }
                }

                Text(
                    text = "Swipe between stories • Page ${pagerState.currentPage + 1} of 3",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun RecapMinutesSlide(recap: ListeningRecap) {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(recap.totalListenedMs)
    val totalHours = totalMinutes / 60L
    val avgMinutes = TimeUnit.MILLISECONDS.toMinutes(recap.averageDailyMs)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "${recap.year} RHYTHM RECAP",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.6.sp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "$totalMinutes min",
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black)
        )
        Text(
            text = if (totalHours > 0) "That's $totalHours hours across ${recap.activeDays} active days."
            else "${recap.totalPlays} plays across ${recap.activeDays} active days.",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        )
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RecapMetricTile("Daily Avg", "$avgMinutes min", Modifier.weight(1f))
            RecapMetricTile("Artists", "${recap.uniqueArtists}", Modifier.weight(1f))
            RecapMetricTile("Albums", "${recap.uniqueAlbums}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun RecapPersonalitySlide(recap: ListeningRecap) {
    val peakHour = recap.hourlyDurationsMs.indices.maxByOrNull { recap.hourlyDurationsMs[it] } ?: 0
    val maxBucket = (recap.hourlyDurationsMs.maxOrNull() ?: 1L).coerceAtLeast(1L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "LISTENING RHYTHM",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.6.sp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = recap.personality.title,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = recap.personality.subtitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            recap.hourlyDurationsMs.forEach { bucketMs ->
                val fraction = (bucketMs.toFloat() / maxBucket.toFloat()).coerceIn(0.08f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((90f * fraction).dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + 0.65f * fraction))
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val dayName = recap.favoriteDayOfWeek?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: "—"
        Text(
            text = "Peak hour: %02d:00  •  Top day: %s".format(Locale.US, peakHour, dayName),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun RecapTopMusicSlide(recap: ListeningRecap) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "YOUR SOUNDTRACK",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.6.sp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(10.dp))
        recap.topArtist?.let { artist ->
            Text(
                text = artist,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black)
            )
            Text(
                text = "Most played artist (${recap.topArtistPlayCount} plays)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
            )
        }
        recap.topDecade?.let { decade ->
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
            ) {
                Text(
                    text = "Favorite Era: ${decade.decade}s (${(decade.share * 100).toInt()}% of plays)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        recap.topSongs.take(4).forEachIndexed { idx, song ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.36f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "#${idx + 1}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            text = "${song.artist} • ${song.playCount} plays",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecapMetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.36f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )
        }
    }
}
