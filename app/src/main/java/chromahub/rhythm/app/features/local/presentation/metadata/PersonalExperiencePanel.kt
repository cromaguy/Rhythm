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
    var ruleUpdates by remember { mutableStateOf(ExperienceRulesWorker.enabled(context)) }
    var profileVersion by remember { mutableStateOf(ExperienceProfile.read(context).version) }
    LaunchedEffect(month, revision) {
        withContext(Dispatchers.IO) {
            ledger.recover()
            val selected = YearMonth.parse(month); val zone = ZoneId.systemDefault()
            val start = selected.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = selected.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            val result = ListeningInsightAnalysis.analyze(ledger.records(start, end) + ledger.records(start, end, "action"))
            val allMonths = ledger.months()
            withContext(Dispatchers.Main) { insight = result; months = allMonths }
        }
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
                val peak = data.hourly.indices.maxByOrNull { data.hourly[it] }
                if (peak != null && data.hourly[peak] > 0) Text(context.getString(R.string.personal_peak_hour, peak), style = MaterialTheme.typography.bodySmall)
                data.transitions.entries.sortedByDescending { it.value }.take(3).forEach {
                    Text(it.key.first + " → " + it.key.second + " · " + it.value, style = MaterialTheme.typography.bodySmall)
                }
                data.habits.take(3).forEach { habit ->
                    Text(habit.title + " · " + context.getString(R.string.personal_song_habit, habit.plays, habit.completed, habit.earlySkips, habit.maxConsecutiveRepeats), style = MaterialTheme.typography.bodySmall)
                    habit.medianGapMs?.let { Text(context.getString(R.string.personal_interval, it / 3600_000), style = MaterialTheme.typography.labelSmall) }
                    habit.firstListenDelayMs?.let { Text(context.getString(R.string.personal_first_listen_delay, it / 3600_000), style = MaterialTheme.typography.labelSmall) }
                }
                if (data.totalPlays == 0) Text(stringResource(R.string.personal_history_empty), style = MaterialTheme.typography.bodySmall)
            }
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
