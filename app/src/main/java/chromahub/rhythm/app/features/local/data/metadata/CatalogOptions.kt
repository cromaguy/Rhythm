// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.metadata

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.co.metadata.crawler.Provider
import kr.co.metadata.crawler.CatalogField

data class CatalogOptions(
    val enabled: Boolean = true,
    val permanent: Boolean = true,
    val autoLyrics: Boolean = true,
    val embedLyrics: Boolean = true,
    val wifiOnly: Boolean = true,
    val writeTags: Boolean = true,
    val metadata: Boolean = true,
    val artwork: Boolean = true,
    val profiles: Boolean = true,
    val lyrics: Boolean = true,
    val exportTree: String? = null,
    val replaceTags: Boolean = false,
    val forceFields: Set<CatalogField> = emptySet(),
    val strictDuration: Boolean = true,
    val durationTolerancePercent: Double = 3.0,
    val artworkSource: String = "MUSIC_SITES",
    val providers: Set<Provider> = setOf(Provider.BUGS_MOBILE, Provider.MELON, Provider.GENIE),
) {
    fun replaces(field: CatalogField) = replaceTags || field in forceFields
}

class CatalogPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("catalog_options", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(CatalogOptions(
        replaceTags = prefs.getBoolean("replaceTags", false), strictDuration = prefs.getBoolean("strictDuration", true),
        forceFields = prefs.getStringSet("forceFields", emptySet()).orEmpty().mapNotNull { runCatching { CatalogField.valueOf(it) }.getOrNull() }.toSet(),
        durationTolerancePercent = prefs.getFloat("durationTolerancePercent", 3f).toDouble().takeIf { it.isFinite() && it in 0.0..30.0 } ?: 3.0,
        artworkSource = prefs.getString("artworkSource", "MUSIC_SITES") ?: "MUSIC_SITES",
        enabled = prefs.getBoolean("enabled", true), permanent = prefs.getBoolean("permanent", true),
        autoLyrics = prefs.getBoolean("autoLyrics", true), embedLyrics = prefs.getBoolean("embedLyrics", true),
        wifiOnly = prefs.getBoolean("wifiOnly", true), writeTags = prefs.getBoolean("writeTags", true), metadata = prefs.getBoolean("metadata", true),
        artwork = prefs.getBoolean("artwork", true), profiles = prefs.getBoolean("profiles", true),
        lyrics = prefs.getBoolean("lyrics", true), exportTree = prefs.getString("exportTree", null),
        providers = prefs.getStringSet("providers", null)?.mapNotNull { runCatching { Provider.valueOf(it) }.getOrNull() }?.toSet()
            ?: setOf(Provider.BUGS_MOBILE, Provider.MELON, Provider.GENIE),
    ))
    val options = mutable.asStateFlow()
    fun update(value: CatalogOptions) {
        prefs.edit {
            putBoolean("replaceTags", value.replaceTags); putBoolean("strictDuration", value.strictDuration); putString("artworkSource", value.artworkSource)
            putStringSet("forceFields", value.forceFields.map { it.name }.toSet())
            putFloat("durationTolerancePercent", value.durationTolerancePercent.toFloat())
            putBoolean("enabled", value.enabled); putBoolean("permanent", value.permanent)
            putBoolean("autoLyrics", value.autoLyrics); putBoolean("embedLyrics", value.embedLyrics)
            putBoolean("writeTags", value.writeTags); putBoolean("wifiOnly", value.wifiOnly); putBoolean("metadata", value.metadata)
            putBoolean("artwork", value.artwork); putBoolean("profiles", value.profiles); putBoolean("lyrics", value.lyrics)
            putString("exportTree", value.exportTree); putStringSet("providers", value.providers.map { it.name }.toSet())
        }
        mutable.value = value
    }
}
