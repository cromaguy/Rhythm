/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.data.model.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reusable [SharedPreferences]-backed reactive [StateFlow] properties eliminating boilerplate
 * across modular preference stores.
 */
abstract class PreferenceStoreBase(protected val prefs: SharedPreferences) {

    protected inner class BooleanPref(private val key: String, private val defaultValue: Boolean) {
        private val flow = MutableStateFlow(prefs.getBoolean(key, defaultValue))
        val state: StateFlow<Boolean> = flow.asStateFlow()
        fun get(): Boolean = flow.value
        fun set(value: Boolean) {
            prefs.edit { putBoolean(key, value) }
            flow.value = value
        }
        fun reload() {
            flow.value = prefs.getBoolean(key, defaultValue)
        }
    }

    protected inner class IntPref(private val key: String, private val defaultValue: Int) {
        private val flow = MutableStateFlow(prefs.getInt(key, defaultValue))
        val state: StateFlow<Int> = flow.asStateFlow()
        fun get(): Int = flow.value
        fun set(value: Int) {
            prefs.edit { putInt(key, value) }
            flow.value = value
        }
        fun reload() {
            flow.value = prefs.getInt(key, defaultValue)
        }
    }

    protected inner class FloatPref(private val key: String, private val defaultValue: Float) {
        private val flow = MutableStateFlow(prefs.getFloat(key, defaultValue))
        val state: StateFlow<Float> = flow.asStateFlow()
        fun get(): Float = flow.value
        fun set(value: Float) {
            prefs.edit { putFloat(key, value) }
            flow.value = value
        }
        fun reload() {
            flow.value = prefs.getFloat(key, defaultValue)
        }
    }

    protected inner class LongPref(private val key: String, private val defaultValue: Long) {
        private val flow = MutableStateFlow(
            runCatching { prefs.getLong(key, defaultValue) }.getOrElse { defaultValue }
        )
        val state: StateFlow<Long> = flow.asStateFlow()
        fun get(): Long = flow.value
        fun set(value: Long) {
            prefs.edit { putLong(key, value) }
            flow.value = value
        }
        fun reload() {
            flow.value = runCatching { prefs.getLong(key, defaultValue) }.getOrElse { defaultValue }
        }
    }

    protected inner class StringPref(private val key: String, private val defaultValue: String) {
        private val flow = MutableStateFlow(prefs.getString(key, defaultValue) ?: defaultValue)
        val state: StateFlow<String> = flow.asStateFlow()
        fun get(): String = flow.value
        fun set(value: String) {
            prefs.edit { putString(key, value) }
            flow.value = value
        }
        fun reload() {
            flow.value = prefs.getString(key, defaultValue) ?: defaultValue
        }
    }

    protected inner class NullableStringPref(private val key: String) {
        private val flow = MutableStateFlow(prefs.getString(key, null))
        val state: StateFlow<String?> = flow.asStateFlow()
        fun get(): String? = flow.value
        fun set(value: String?) {
            if (value == null) prefs.edit { remove(key) } else prefs.edit { putString(key, value) }
            flow.value = value
        }
        fun reload() {
            flow.value = prefs.getString(key, null)
        }
    }
}

class PlaybackPreferencesStore(prefs: SharedPreferences) : PreferenceStoreBase(prefs) {
    private val _sampleRateCapHz = IntPref("audio_sample_rate_cap_hz", 0)
    private val _surroundDownmixEnabled = BooleanPref("surround_downmix_enabled", false)
    private val _alacDecoderEnabled = BooleanPref("alac_decoder_enabled", true)
    private val _musicHapticsEnabled = BooleanPref("music_haptics_enabled", false)
    private val _musicHapticsMode = StringPref("music_haptics_mode", "BASS_AND_BEATS")
    private val _musicHapticsIntensity = FloatPref("music_haptics_intensity", 0.7f)
    private val _musicHapticsMuteOnExternal = BooleanPref("music_haptics_mute_external", true)
    private val _musicHapticsUseSystemGenerator = BooleanPref("music_haptics_system_generator", false)
    private val _autoMixBpmSyncEnabled = BooleanPref("automix_bpm_sync_enabled", true)

    val sampleRateCapHz = _sampleRateCapHz.state
    val surroundDownmixEnabled = _surroundDownmixEnabled.state
    val alacDecoderEnabled = _alacDecoderEnabled.state
    val musicHapticsEnabled = _musicHapticsEnabled.state
    val musicHapticsMode = _musicHapticsMode.state
    val musicHapticsIntensity = _musicHapticsIntensity.state
    val musicHapticsMuteOnExternal = _musicHapticsMuteOnExternal.state
    val musicHapticsUseSystemGenerator = _musicHapticsUseSystemGenerator.state
    val autoMixBpmSyncEnabled = _autoMixBpmSyncEnabled.state

    fun setSampleRateCapHz(hz: Int) = _sampleRateCapHz.set(hz.coerceAtLeast(0))
    fun setSurroundDownmixEnabled(enabled: Boolean) = _surroundDownmixEnabled.set(enabled)
    fun setAlacDecoderEnabled(enabled: Boolean) = _alacDecoderEnabled.set(enabled)
    fun setMusicHapticsEnabled(enabled: Boolean) = _musicHapticsEnabled.set(enabled)
    fun setMusicHapticsMode(mode: String) = _musicHapticsMode.set(mode)
    fun setMusicHapticsIntensity(intensity: Float) = _musicHapticsIntensity.set(intensity.coerceIn(0.1f, 1.0f))
    fun setMusicHapticsMuteOnExternal(enabled: Boolean) = _musicHapticsMuteOnExternal.set(enabled)
    fun setMusicHapticsUseSystemGenerator(enabled: Boolean) = _musicHapticsUseSystemGenerator.set(enabled)
    fun setAutoMixBpmSyncEnabled(enabled: Boolean) = _autoMixBpmSyncEnabled.set(enabled)
}

class UiPreferencesStore(prefs: SharedPreferences) : PreferenceStoreBase(prefs) {
    private val _agslFluidBackdropEnabled = BooleanPref("agsl_fluid_backdrop_enabled", true)
    private val _karaokeCanvasEnabled = BooleanPref("karaoke_canvas_enabled", true)
    private val _instrumentalBreakThresholdMs = LongPref("instrumental_break_threshold_ms", 8000L)
    private val _statusBarTickerEnabled = BooleanPref("status_bar_ticker_lyrics_enabled", false)

    val agslFluidBackdropEnabled = _agslFluidBackdropEnabled.state
    val karaokeCanvasEnabled = _karaokeCanvasEnabled.state
    val instrumentalBreakThresholdMs = _instrumentalBreakThresholdMs.state
    val statusBarTickerEnabled = _statusBarTickerEnabled.state

    fun setAgslFluidBackdropEnabled(enabled: Boolean) = _agslFluidBackdropEnabled.set(enabled)
    fun setKaraokeCanvasEnabled(enabled: Boolean) = _karaokeCanvasEnabled.set(enabled)
    fun setInstrumentalBreakThresholdMs(ms: Long) = _instrumentalBreakThresholdMs.set(ms.coerceIn(3000L, 30000L))
    fun setStatusBarTickerEnabled(enabled: Boolean) = _statusBarTickerEnabled.set(enabled)
}

class LibraryPreferencesStore(prefs: SharedPreferences) : PreferenceStoreBase(prefs) {
    private val _stripLeadingArticlesOnSort = BooleanPref("sort_strip_leading_articles", false)
    val stripLeadingArticlesOnSort = _stripLeadingArticlesOnSort.state
    fun setStripLeadingArticlesOnSort(enabled: Boolean) = _stripLeadingArticlesOnSort.set(enabled)
}

class StreamingPreferencesStore(prefs: SharedPreferences) : PreferenceStoreBase(prefs) {
    private val _listenBrainzToken = NullableStringPref("scrobble_listenbrainz_token")
    private val _libreFmSessionKey = NullableStringPref("scrobble_librefm_session_key")
    private val _malojaServerUrl = NullableStringPref("scrobble_maloja_url")
    private val _malojaToken = NullableStringPref("scrobble_maloja_token")

    val listenBrainzToken = _listenBrainzToken.state
    val libreFmSessionKey = _libreFmSessionKey.state
    val malojaServerUrl = _malojaServerUrl.state
    val malojaToken = _malojaToken.state

    fun setListenBrainzToken(token: String?) = _listenBrainzToken.set(token?.trim()?.takeIf { it.isNotEmpty() })
    fun setLibreFmSessionKey(key: String?) = _libreFmSessionKey.set(key?.trim()?.takeIf { it.isNotEmpty() })
    fun setMalojaConfig(url: String?, token: String?) {
        _malojaServerUrl.set(url?.trim()?.takeIf { it.isNotEmpty() })
        _malojaToken.set(token?.trim()?.takeIf { it.isNotEmpty() })
    }
}

class RhythmGuardPreferencesStore(prefs: SharedPreferences) : PreferenceStoreBase(prefs) {
    private val _lastClampedAtMs = LongPref("rhythm_guard_last_clamped_ms", 0L)
    val lastClampedAtMs = _lastClampedAtMs.state
    fun recordClampEvent(timestampMs: Long = System.currentTimeMillis()) = _lastClampedAtMs.set(timestampMs)
}

class DiagnosticsPreferencesStore(prefs: SharedPreferences) : PreferenceStoreBase(prefs) {
    private val _verboseAudioPipelineLogging = BooleanPref("verbose_audio_pipeline_logging", false)
    val verboseAudioPipelineLogging = _verboseAudioPipelineLogging.state
    fun setVerboseAudioPipelineLogging(enabled: Boolean) = _verboseAudioPipelineLogging.set(enabled)
}

class PresetsPreferencesStore(prefs: SharedPreferences) : PreferenceStoreBase(prefs) {
    private val _parametricEqJson = StringPref("peq_20band_json", "[]")
    private val _autoEqProfileId = NullableStringPref("autoeq_active_profile_id")
    private val _convolverIrUri = NullableStringPref("convolver_ir_uri")
    private val _convolverMixPercent = FloatPref("convolver_mix_percent", 0.35f)
    private val _doublePrecisionPeqEnabled = BooleanPref("double_precision_peq_enabled", true)
    private val _deviceAutoEqMapJson = StringPref("device_autoeq_map_json", "{}")
    private val _peqBassGainDb = FloatPref("peq_bass_gain_db", 0f)
    private val _peqTiltDbPerOctave = FloatPref("peq_tilt_db_per_octave", 0f)

    val parametricEqJson = _parametricEqJson.state
    val autoEqProfileId = _autoEqProfileId.state
    val convolverIrUri = _convolverIrUri.state
    val convolverMixPercent = _convolverMixPercent.state
    val doublePrecisionPeqEnabled = _doublePrecisionPeqEnabled.state
    val deviceAutoEqMapJson = _deviceAutoEqMapJson.state
    val peqBassGainDb = _peqBassGainDb.state
    val peqTiltDbPerOctave = _peqTiltDbPerOctave.state

    fun setParametricEqJson(json: String) = _parametricEqJson.set(json)
    fun setAutoEqProfileId(profileId: String?) = _autoEqProfileId.set(profileId)
    fun setConvolverIrUri(uri: String?) = _convolverIrUri.set(uri)
    fun setConvolverMixPercent(mix: Float) = _convolverMixPercent.set(mix.coerceIn(0f, 1f))
    fun setDoublePrecisionPeqEnabled(enabled: Boolean) = _doublePrecisionPeqEnabled.set(enabled)
    fun setDeviceAutoEqMapJson(json: String) = _deviceAutoEqMapJson.set(json)
    fun setPeqBassGainDb(gainDb: Float) = _peqBassGainDb.set(gainDb.coerceIn(-12f, 12f))
    fun setPeqTiltDbPerOctave(tilt: Float) = _peqTiltDbPerOctave.set(tilt.coerceIn(-4f, 4f))
}
