// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.presentation.metadata

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.data.experience.*
import chromahub.rhythm.app.features.local.data.metadata.readBytesBounded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun PersonalExperiencePanel() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val ledger = remember { ListeningLedger.get(context) }
    val revision by ledger.changes.collectAsState()
    val storageIssue by ledger.storageIssue.collectAsState()
    var month by remember { mutableStateOf(YearMonth.now().toString()) }
    var months by remember { mutableStateOf(emptyList<ListeningLedger.Monthly>()) }
    var insight by remember { mutableStateOf<ListeningInsights?>(null) }
    var chooseMonth by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var tasteReviews by remember { mutableStateOf(TasteMemory.get(context).reviewsEnabled) }
    var ruleUpdates by remember { mutableStateOf(ExperienceRulesWorker.enabled(context)) }
    var collectionRatings by remember { mutableStateOf(emptyMap<String, Double>()) }
    LaunchedEffect(revision) { collectionRatings = withContext(Dispatchers.IO) { RecommendationLearning.get(context).model().collections } }
    var personalOptions by remember { mutableStateOf(PersonalOptions()) }
    var autoplay by remember { mutableStateOf(PersonalCollections.autoplay(context)) }
    LaunchedEffect(revision) { personalOptions = withContext(Dispatchers.IO) { PersonalCollections.options(context) }; autoplay = PersonalCollections.autoplay(context) }
    var profileVersion by remember { mutableStateOf(ExperienceProfile.read(context).version) }
    LaunchedEffect(month, revision) {
        val result = withContext(Dispatchers.IO) {
            ListeningInsightCache.request(context, month)
            ListeningInsightCache.read(context, month) to ledger.months()
        }
        insight = result.first; months = result.second
    }
    val importRules = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val profile = withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytesBounded(64_000).toString(Charsets.UTF_8) } ?: error("Cannot read rules")
                    ExperienceProfile.apply(context, text)
                }
                ExperienceRulesWorker.configure(context, false); ruleUpdates = false
                profileVersion = profile.version; message = context.getString(R.string.personal_rules_applied)
            } catch (_: Exception) { message = context.getString(R.string.personal_rules_invalid) }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.personal_experience_title), style = MaterialTheme.typography.titleMedium)
            if (storageIssue != null) Text(stringResource(R.string.personal_history_storage_error), color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.personal_experience_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { chooseMonth = true }, modifier = Modifier.fillMaxWidth()) { Text(month + " · " + stringResource(R.string.personal_month_choose)) }
            insight?.let { data ->
                Text(context.getString(R.string.personal_month_summary, data.totalPlays, data.habits.size, data.monthly.values.sum() / 60_000), style = MaterialTheme.typography.titleSmall)
                data.artists.entries.take(3).forEach { Text(context.getString(R.string.personal_dimension_minutes, it.key, it.value / 60_000), style = MaterialTheme.typography.bodyMedium) }
                data.genres.entries.take(3).forEach { Text(context.getString(R.string.personal_dimension_minutes, it.key, it.value / 60_000), style = MaterialTheme.typography.bodySmall) }
                data.outputs.entries.sortedByDescending { it.value }.take(3).forEach { (route, duration) ->
                    val label = context.getString(when (route) { "bluetooth" -> R.string.context_route_bluetooth; "wired" -> R.string.context_route_wired; "usb" -> R.string.context_route_usb; "cast" -> R.string.context_route_cast; else -> R.string.context_route_speaker })
                    Text(context.getString(R.string.personal_dimension_minutes, label, duration / 60_000), style = MaterialTheme.typography.bodySmall)
                }
                if (data.places.isNotEmpty()) Text(context.getString(R.string.context_place_groups, data.places.size), style = MaterialTheme.typography.bodySmall)
                val peak = data.hourly.indices.maxByOrNull { data.hourly[it] }
                if (peak != null && data.hourly[peak] > 0) Text(context.getString(R.string.personal_peak_hour, peak), style = MaterialTheme.typography.bodySmall)
                data.transitions.entries.sortedByDescending { it.value }.take(3).forEach {
                    Text(it.key.first + " → " + it.key.second + " · " + it.value, style = MaterialTheme.typography.bodySmall)
                }
                data.habits.take(3).forEach { habit ->
                    Text(habit.title + " · " + context.getString(R.string.personal_song_habit, habit.plays, habit.completed, habit.earlySkips, habit.maxConsecutiveRepeats), style = MaterialTheme.typography.bodySmall)
                    habit.averageCompletionRatio?.let { Text(context.getString(R.string.personal_completion_average, it * 100), style = MaterialTheme.typography.labelMedium) }
                    habit.medianGapMs?.let { Text(context.getString(R.string.personal_interval, it / 3600_000), style = MaterialTheme.typography.labelSmall) }
                    habit.firstListenDelayMs?.let { Text(context.getString(R.string.personal_first_listen_delay, it / 3600_000), style = MaterialTheme.typography.labelSmall) }
                }
                if (data.totalPlays == 0) Text(stringResource(R.string.personal_history_empty), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.personal_insight_background), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.personal_autoplay), Modifier.weight(1f))
                Switch(autoplay, onCheckedChange = { value -> autoplay = value; scope.launch { withContext(Dispatchers.IO) { PersonalCollections.update(context) { it.copy(autoplay = value) } } } })
            }
            Text(stringResource(R.string.personal_autoplay_hint), style = MaterialTheme.typography.bodySmall)
            val excluded = personalOptions.hidden + personalOptions.snoozed.filterValues { it > System.currentTimeMillis() }.keys
            if (excluded.isNotEmpty()) {
                Text(stringResource(R.string.personal_exclusions), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.personal_exclusions_hint), style = MaterialTheme.typography.bodySmall)
                excluded.take(30).forEach { key ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(personalOptions.labels[key] ?: key.substringAfterLast('/'), Modifier.weight(1f), maxLines = 2)
                        TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { PersonalCollections.restore(context, key) } } }) { Text(stringResource(R.string.personal_restore)) }
                    }
                }
            }
            if (collectionRatings.isNotEmpty()) {
                Text(stringResource(R.string.personal_mix_feedbacks), style = MaterialTheme.typography.titleSmall)
                collectionRatings.forEach { (id, reward) ->
                    val mode = id.substringAfter("collection:").substringBefore(':')
                    val title = context.getString(when (mode) { "DAYLIST" -> R.string.personal_daylist; "DAILY_MIX" -> R.string.personal_daily_mix; "REDISCOVERY" -> R.string.personal_rediscovery; "SIMILAR" -> R.string.personal_similar; "FAVORITES" -> R.string.personal_liked_mix; else -> R.string.personal_fewer_repeats })
                    Text(title + id.substringAfter("collection:$mode:").takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1.0 to R.string.personal_like, 0.0 to R.string.personal_dislike).forEach { (value, label) ->
                            FilterChip(selected = reward == value, onClick = { scope.launch { withContext(Dispatchers.IO) { RecommendationLearning.get(context).collectionFeedback(id, emptyList(), if (reward == value) null else value) } } }, label = { Text(stringResource(label)) })
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.taste_reviews_enabled), Modifier.weight(1f))
                Switch(tasteReviews, onCheckedChange = { value -> tasteReviews = value; scope.launch(Dispatchers.IO) { TasteMemory.get(context).reviewsEnabled(value) } })
            }
            Text(stringResource(R.string.taste_reviews_description), style = MaterialTheme.typography.bodySmall)
            MobilityContextSettings()
            ListeningContextPanel()
            HorizontalDivider()
            Text(stringResource(R.string.personal_audio_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { AudioAnalysisWorker.enqueue(context); message = context.getString(R.string.personal_audio_scheduled) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.personal_audio_analyze))
            }
            Text(context.getString(R.string.personal_rules_version, profileVersion), style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.personal_rules_updates), Modifier.weight(1f))
                Switch(checked = ruleUpdates, onCheckedChange = { ruleUpdates = it; ExperienceRulesWorker.configure(context, it) })
            }
            SoftUpdatePanel()
            TextButton(onClick = { importRules.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.personal_rules_import)) }
            Text(stringResource(R.string.personal_native_updates_hint), style = MaterialTheme.typography.bodySmall)
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (chooseMonth) AlertDialog(onDismissRequest = { chooseMonth = false }, title = { Text(stringResource(R.string.personal_month_choose)) },
        text = { androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 320.dp)) {
            items(months.size) { i -> TextButton(onClick = { month = months[i].month; chooseMonth = false }, modifier = Modifier.fillMaxWidth()) {
                Text(months[i].month + " · " + months[i].plays)
            } }
        } }, confirmButton = { TextButton(onClick = { chooseMonth = false }) { Text(stringResource(android.R.string.cancel)) } })
}
