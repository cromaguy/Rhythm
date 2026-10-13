// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.metadata

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.co.metadata.crawler.Provider

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
    val providers: Set<Provider> = setOf(Provider.BUGS_MOBILE, Provider.GENIE, Provider.MELON),
)

class CatalogPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("catalog_options", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(CatalogOptions(
        enabled = prefs.getBoolean("enabled", true), permanent = prefs.getBoolean("permanent", true),
        autoLyrics = prefs.getBoolean("autoLyrics", true), embedLyrics = prefs.getBoolean("embedLyrics", true),
        wifiOnly = prefs.getBoolean("wifiOnly", true), writeTags = prefs.getBoolean("writeTags", true), metadata = prefs.getBoolean("metadata", true),
        artwork = prefs.getBoolean("artwork", true), profiles = prefs.getBoolean("profiles", true),
        lyrics = prefs.getBoolean("lyrics", true), exportTree = prefs.getString("exportTree", null),
        providers = prefs.getStringSet("providers", null)?.mapNotNull { runCatching { Provider.valueOf(it) }.getOrNull() }?.toSet()
            ?: setOf(Provider.BUGS_MOBILE, Provider.GENIE, Provider.MELON),
    ))
    val options = mutable.asStateFlow()
    fun update(value: CatalogOptions) {
        prefs.edit {
            putBoolean("enabled", value.enabled); putBoolean("permanent", value.permanent)
            putBoolean("autoLyrics", value.autoLyrics); putBoolean("embedLyrics", value.embedLyrics)
            putBoolean("writeTags", value.writeTags); putBoolean("wifiOnly", value.wifiOnly); putBoolean("metadata", value.metadata)
            putBoolean("artwork", value.artwork); putBoolean("profiles", value.profiles); putBoolean("lyrics", value.lyrics)
            putString("exportTree", value.exportTree); putStringSet("providers", value.providers.map { it.name }.toSet())
        }
        mutable.value = value
    }
}
