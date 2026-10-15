// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.presentation.metadata

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.data.experience.MobilityContext
import chromahub.rhythm.app.shared.presentation.components.player.CoverGestures

@Composable fun CoverGestureSettings() {
    val context = LocalContext.current
    var seconds by remember { mutableFloatStateOf(CoverGestures.seconds(context).toFloat()) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.cover_gestures), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.cover_gesture_hint), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.cover_skip_seconds, seconds.toInt()), style = MaterialTheme.typography.labelLarge)
        Slider(seconds, onValueChange = { seconds = it }, valueRange = 5f..60f, steps = 54,
            onValueChangeFinished = { CoverGestures.seconds(context, seconds.toInt()) })
    }
}

@Composable fun MobilityContextSettings() {
    val context = LocalContext.current
    val mobility = remember { MobilityContext.get(context) }
    var enabled by remember { mutableStateOf(mobility.enabled) }
    var mode by remember { mutableStateOf(mobility.manual) }
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.mobility_enabled), Modifier.weight(1f))
            Switch(enabled, onCheckedChange = { enabled = it; mobility.configure(it, mode) })
        }
        Text(stringResource(R.string.mobility_hint), style = MaterialTheme.typography.bodySmall)
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(stringResource(R.string.mobility_mode) + " · " + mobilityLabel(mode)) }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                listOf("auto", "still", "walking", "cycling", "vehicle", "airborne").forEach { value ->
                    DropdownMenuItem(text = { Text(mobilityLabel(value)) }, onClick = { mode = value; expanded = false; mobility.configure(enabled, value) })
                }
            }
        }
    }
}
@Composable private fun mobilityLabel(mode: String) = stringResource(when (mode) {
    "still" -> R.string.mobility_still; "walking" -> R.string.mobility_walking; "cycling" -> R.string.mobility_cycling
    "vehicle" -> R.string.mobility_vehicle; "airborne" -> R.string.mobility_airborne; else -> R.string.mobility_auto
})
