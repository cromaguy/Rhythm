// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.presentation.metadata

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.data.experience.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ListeningContextPanel() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val collector = remember { ListeningContext.get(context) }
    val ledger = remember { ListeningLedger.get(context) }; val revision by ledger.changes.collectAsState()
    val sound = remember { ListeningSound.get(context) }
    var soundEnabled by remember { mutableStateOf(sound.enabled) }
    var soundDenied by remember { mutableStateOf(false) }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        soundDenied = !granted; soundEnabled = granted; sound.configure(granted)
    }
    val weather = remember { ListeningWeather.get(context) }
    var weatherEnabled by remember { mutableStateOf(weather.enabled) }
    var enabled by remember { mutableStateOf(collector.enabled) }; var precise by remember { mutableStateOf(collector.precise) }
    var place by remember { mutableStateOf(collector.place) }
    var model by remember { mutableStateOf(RecommendationModel()) }; var situation by remember { mutableStateOf(collector.snapshot()) }
    var permissionDenied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        enabled = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true || collector.permitted()
        precise = precise && grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        permissionDenied = !enabled; collector.configure(enabled, precise)
        situation = collector.snapshot()
    }
    LaunchedEffect(revision) { model = withContext(Dispatchers.IO) { RecommendationLearning.get(context).model() }; situation = collector.snapshot() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.context_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.context_hint), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.context_location), Modifier.weight(1f))
            Switch(enabled, onCheckedChange = { value ->
                if (!value) { enabled = false; collector.configure(enabled = false) }
                else if (collector.permitted()) { enabled = true; collector.configure(enabled = true) }
                else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
            })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.context_precise), Modifier.weight(1f))
            Switch(precise, enabled = enabled, onCheckedChange = { value ->
                precise = value
                if (value) permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                else collector.configure(precise = false)
            })
        }
        Text(stringResource(R.string.context_location_hint), style = MaterialTheme.typography.bodySmall)
        if (permissionDenied) Text(stringResource(R.string.context_permission_denied), color = MaterialTheme.colorScheme.error)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.personal_weather), Modifier.weight(1f))
            Switch(weatherEnabled, enabled = enabled, onCheckedChange = { weatherEnabled = it; weather.configure(it) })
        }
        Text(stringResource(R.string.personal_weather_hint), style = MaterialTheme.typography.bodySmall)
        situation.weather?.let { w ->
            val label = context.getString(when(w.group) { "clear" -> R.string.personal_weather_clear; "cloudy" -> R.string.personal_weather_cloudy; "fog" -> R.string.personal_weather_fog; "snow" -> R.string.personal_weather_snow; "storm" -> R.string.personal_weather_storm; else -> R.string.personal_weather_rain })
            Text(context.getString(R.string.personal_weather_value, label, w.temperatureC), style = MaterialTheme.typography.labelLarge)
        } ?: Text(stringResource(R.string.personal_weather_unknown), style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.personal_noise), Modifier.weight(1f))
            Switch(soundEnabled, onCheckedChange = { value ->
                if (!value || sound.permitted()) { soundEnabled = value; sound.configure(value) }
                else microphone.launch(Manifest.permission.RECORD_AUDIO)
            })
        }
        Text(stringResource(R.string.personal_noise_hint), style = MaterialTheme.typography.bodySmall)
        if (soundDenied) Text(stringResource(R.string.personal_noise_denied), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        OutlinedTextField(place, { place = it.take(80) }, label = { Text(stringResource(R.string.context_place)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { collector.configure(place = place); situation = collector.snapshot() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.context_place_save)) }
        Text(context.getString(R.string.context_output, context.getString(when (situation.routeType) { "bluetooth" -> R.string.context_route_bluetooth; "wired" -> R.string.context_route_wired; "usb" -> R.string.context_route_usb; "cast" -> R.string.context_route_cast; else -> R.string.context_route_speaker })), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.context_output_hint), style = MaterialTheme.typography.labelSmall)
        Text(context.getString(R.string.context_learning_summary, model.samples, model.positive, model.negative), style = MaterialTheme.typography.bodySmall)
        if (model.samples >= 10) Text(context.getString(R.string.context_learning_error, model.squaredError / model.samples, model.baselineError / model.samples), style = MaterialTheme.typography.labelSmall)
        Text(stringResource(R.string.context_learning_hint), style = MaterialTheme.typography.bodySmall)
    }
}
