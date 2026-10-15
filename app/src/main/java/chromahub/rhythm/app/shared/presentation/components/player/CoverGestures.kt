// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.shared.presentation.components.player

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon

object CoverGestures {
    fun seconds(context: Context) = context.getSharedPreferences("cover_gestures", Context.MODE_PRIVATE).getInt("seconds", 10).coerceIn(5, 60)
    fun seconds(context: Context, value: Int) { check(context.getSharedPreferences("cover_gestures", Context.MODE_PRIVATE).edit().putInt("seconds", value.coerceIn(5, 60)).commit()) }
    fun zone(x: Float, width: Float): Int = when { width <= 0 || x < 0 || x > width -> 0; x < width * .25f -> -1; x > width * .75f -> 1; else -> 0 }
    fun seek(position: Float, duration: Long, direction: Int, seconds: Int): Float = if (duration <= 0) position else
        (position + direction * seconds.coerceIn(5, 60) * 1000f / duration).coerceIn(0f, 1f)
}

@Composable
fun CoverGestureFeedback(token: Int, direction: Int, seconds: Int, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(token) { if (token > 0) { visible = true; kotlinx.coroutines.delay(850); visible = false } }
    Box(modifier, contentAlignment = if (direction < 0) Alignment.CenterStart else if (direction > 0) Alignment.CenterEnd else Alignment.Center) {
        AnimatedVisibility(visible, enter = fadeIn(tween(100)) + scaleIn(tween(240), initialScale = .5f), exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 1.2f)) {
            if (direction == 0) {
                Icon(MaterialSymbolIcon("favorite", filled = true), stringResource(R.string.cover_liked),
                    tint = Color(0xFFFF6584), modifier = Modifier.padding(24.dp).size(72.dp))
            } else Column(Modifier.padding(24.dp).background(Color.Black.copy(alpha = .4f), CircleShape).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(MaterialSymbolIcon(if (direction < 0) "replay" else "fast_forward", filled = true),
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                Text(stringResource(R.string.cover_seek, if (direction < 0) "−" else "+", seconds),
                    color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
