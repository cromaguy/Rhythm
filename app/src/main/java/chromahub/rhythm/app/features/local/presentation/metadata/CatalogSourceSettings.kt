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
import chromahub.rhythm.app.features.local.data.metadata.CatalogEnrichmentService
import chromahub.rhythm.app.shared.data.model.AppSettings
import kr.co.metadata.crawler.Provider

@Composable
fun CatalogSourceSettings() {
    val context = LocalContext.current
    val preferences = remember { CatalogEnrichmentService.get(context).preferences }
    val options by preferences.options.collectAsState()
    val settings = remember { AppSettings.getInstance(context) }
    val online by settings.integrationsEnabled.collectAsState()
    val lyrics by settings.musicCrawlerLyricsApiEnabled.collectAsState()
    var selectArtwork by remember { mutableStateOf(false) }
    val sources = listOf("MUSIC_SITES" to stringResource(R.string.catalog_site_priority), "BUGS" to "Bugs · 벅스",
        "MELON" to "Melon · 멜론", "GENIE" to "Genie · 지니", "LEGACY" to stringResource(R.string.catalog_legacy_artwork),
        "LOCAL_ONLY" to stringResource(R.string.catalog_local_artwork))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.catalog_sources_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.catalog_sources_shared), style = MaterialTheme.typography.bodySmall)
            listOf(Provider.BUGS_MOBILE to "Bugs · 벅스", Provider.MELON to "Melon · 멜론", Provider.GENIE to "Genie · 지니").forEach { (provider, label) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.catalog_site_capabilities), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = provider in options.providers, enabled = online,
                        onCheckedChange = { preferences.update(options.copy(providers = if (it) options.providers + provider else options.providers - provider)) })
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.catalog_lyrics_source), Modifier.weight(1f))
                Switch(checked = lyrics, enabled = online, onCheckedChange = settings::setMusicCrawlerLyricsApiEnabled)
            }
            OutlinedButton(onClick = { selectArtwork = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.catalog_artwork_source) + ": " + (sources.firstOrNull { it.first == options.artworkSource }?.second ?: sources.first().second))
            }
            Text(stringResource(R.string.catalog_site_priority), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (selectArtwork) AlertDialog(onDismissRequest = { selectArtwork = false }, title = { Text(stringResource(R.string.catalog_artwork_source)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { sources.forEach { (key, label) ->
            TextButton(onClick = { preferences.update(options.copy(artworkSource = key)); selectArtwork = false }, modifier = Modifier.fillMaxWidth()) {
                RadioButton(selected = options.artworkSource == key, onClick = null)
                Text(label, Modifier.weight(1f).padding(start = 8.dp))
            }
        } } }, confirmButton = { TextButton(onClick = { selectArtwork = false }) { Text(stringResource(android.R.string.cancel)) } })
}

@Composable
fun CatalogLibrarySettingsScreen(onBackClick: () -> Unit) {
    chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen(
        title = stringResource(R.string.catalog_library_title), showBackButton = true, onBackClick = onBackClick
    ) { modifier ->
        androidx.compose.foundation.lazy.LazyColumn(modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp + chromahub.rhythm.app.ui.LocalMiniPlayerPadding.current.calculateBottomPadding()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { LibraryEnrichmentPanel() }
            item { CatalogSourceSettings() }
            item { PersonalArchivePanel() }
            item { PersonalExperiencePanel() }
        }
    }
}
