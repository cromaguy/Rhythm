/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.screens.settings

import android.content.SharedPreferences
import chromahub.rhythm.app.shared.data.model.settings.PlaybackPreferencesStore
import chromahub.rhythm.app.shared.data.model.settings.PresetsPreferencesStore
import chromahub.rhythm.app.shared.data.model.settings.UiPreferencesStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchHelpersTest {

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false
            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = this
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = null }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) data.clear()
                pending.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
            }
        }
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    @After
    fun tearDown() {
        SettingHighlightController.clear()
    }

    @Test
    fun `SettingHighlightController matches by key or title and clears cleanly`() {
        SettingHighlightController.requestHighlight("external_player_isolate_preview", "Isolated Preview Player")
        val targetKey = SettingHighlightController.highlightedKey.value
        val targetTitle = SettingHighlightController.highlightedTitle.value

        assertTrue(SettingHighlightController.matches(targetKey, targetTitle, "external_player_isolate_preview", null))
        assertTrue(SettingHighlightController.matches(targetKey, targetTitle, null, "isolated preview player"))
        assertFalse(SettingHighlightController.matches(targetKey, targetTitle, "other", "Other Setting"))

        SettingHighlightController.clear()
        assertNull(SettingHighlightController.highlightedKey.value)
        assertNull(SettingHighlightController.highlightedTitle.value)
    }

    @Test
    fun `SettingsInlineToggleRegistry toggles mapped boolean settings live`() {
        val prefs = InMemorySharedPreferences()
        val playbackPrefs = PlaybackPreferencesStore(prefs)
        val uiPrefs = UiPreferencesStore(prefs)
        val presetsPrefs = PresetsPreferencesStore(prefs)

        val hapticsBinding = SettingsInlineToggleRegistry.resolveStoreToggle(
            key = "music_haptics_enabled",
            playbackPrefs = playbackPrefs,
            uiPrefs = uiPrefs,
            presetsPrefs = presetsPrefs
        )
        assertNotNull(hapticsBinding)
        hapticsBinding!!.onToggle(true)
        assertTrue(playbackPrefs.musicHapticsEnabled.value)
        hapticsBinding.onToggle(false)
        assertFalse(playbackPrefs.musicHapticsEnabled.value)

        val tickerBinding = SettingsInlineToggleRegistry.resolveStoreToggle(
            key = "status_bar_ticker_enabled",
            playbackPrefs = playbackPrefs,
            uiPrefs = uiPrefs,
            presetsPrefs = presetsPrefs
        )
        assertNotNull(tickerBinding)
        tickerBinding!!.onToggle(true)
        assertTrue(uiPrefs.statusBarTickerEnabled.value)

        val keys = SettingsInlineToggleRegistry.supportedKeys()
        assertTrue("external_player_isolate_preview" in keys)
        assertTrue("unifiedLibraryEnabled" in keys)
        assertTrue("music_haptics_enabled" in keys)
        assertTrue("alac_decoder_enabled" in keys)
        assertTrue("surround_downmix_enabled" in keys)
        assertTrue("main_thread_stall_watchdog" in keys)
        assertNull(SettingsInlineToggleRegistry.resolveStoreToggle("unknown", playbackPrefs, uiPrefs, presetsPrefs))
    }
}
