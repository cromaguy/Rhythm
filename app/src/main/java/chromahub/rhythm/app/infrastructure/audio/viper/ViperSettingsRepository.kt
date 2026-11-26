/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.viper

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.core.content.edit
import chromahub.rhythm.app.util.AudioDeviceManager
import chromahub.rhythm.app.util.GsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Repository for managing and persisting ViPER4Android DSP settings in Rhythm.
 * Supports auto-switching profiles per output device (Speaker, Headset, Bluetooth, USB),
 * UI Complexity modes (Simple, Moderate, Expert), and profile Import/Export.
 */
class ViperSettingsRepository private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "rhythm_viper_audio_settings"
        private const val KEY_GLOBAL_MASTER_ENABLE = "viper_master_enabled"

        @Volatile
        private var instance: ViperSettingsRepository? = null

        fun getInstance(context: Context): ViperSettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: ViperSettingsRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _configFlow = MutableStateFlow(loadConfigFromPrefs())
    val configFlow: StateFlow<ViperMasterConfig> = _configFlow.asStateFlow()

    init {
        // Monitor audio device changes to auto-switch profile if enabled
        val audioDeviceManager = AudioDeviceManager(context)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        scope.launch {
            audioDeviceManager.availableDevices.collect { _ ->
                if (_configFlow.value.autoDeviceProfileEnabled) {
                    val isBt = audioDeviceManager.isBluetoothActive()
                    val isHeadset = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any {
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    } == true
                    val isUsb = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any {
                        it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                    } == true

                    val detectedDevice = when {
                        isBt -> ViperOutputDevice.BLUETOOTH
                        isUsb -> ViperOutputDevice.USB
                        isHeadset -> ViperOutputDevice.HEADSET
                        else -> ViperOutputDevice.SPEAKER
                    }
                    if (detectedDevice != _configFlow.value.activeOutputDevice) {
                        switchDeviceProfile(detectedDevice)
                    }
                }
            }
        }
    }

    fun updateConfig(updater: (ViperMasterConfig) -> ViperMasterConfig) {
        val newConfig = updater(_configFlow.value)
        _configFlow.value = newConfig
        saveConfigToPrefs(newConfig)
        saveProfileForDevice(newConfig.activeOutputDevice, newConfig)
    }

    fun setMasterEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_GLOBAL_MASTER_ENABLE, enabled) }
        updateConfig { it.copy(masterEnabled = enabled) }
    }

    fun setUiMode(mode: ViperUiMode) {
        updateConfig { it.copy(uiMode = mode) }
    }

    fun switchDeviceProfile(device: ViperOutputDevice) {
        // Save current device profile first
        saveProfileForDevice(_configFlow.value.activeOutputDevice, _configFlow.value)
        // Load target device profile while preserving user global master enable & UI mode
        val globalMaster = prefs.getBoolean(KEY_GLOBAL_MASTER_ENABLE, _configFlow.value.masterEnabled)
        val targetConfig = loadProfileForDevice(device).copy(
            masterEnabled = globalMaster,
            activeOutputDevice = device,
            uiMode = _configFlow.value.uiMode,
            autoDeviceProfileEnabled = _configFlow.value.autoDeviceProfileEnabled
        )
        _configFlow.value = targetConfig
        saveConfigToPrefs(targetConfig)
        saveProfileForDevice(device, targetConfig)
    }

    fun resetConfig() {
        val currentDevice = _configFlow.value.activeOutputDevice
        val currentMode = _configFlow.value.uiMode
        val globalMaster = prefs.getBoolean(KEY_GLOBAL_MASTER_ENABLE, _configFlow.value.masterEnabled)
        val defaultConfig = ViperMasterConfig(
            masterEnabled = globalMaster,
            uiMode = currentMode,
            activeOutputDevice = currentDevice,
            autoDeviceProfileEnabled = _configFlow.value.autoDeviceProfileEnabled
        )
        _configFlow.value = defaultConfig
        saveConfigToPrefs(defaultConfig)
        saveProfileForDevice(currentDevice, defaultConfig)
    }

    private fun getDevicePrefix(device: ViperOutputDevice): String = "dev_${device.name.lowercase()}_"

    private fun saveProfileForDevice(device: ViperOutputDevice, config: ViperMasterConfig) {
        val p = getDevicePrefix(device)
        prefs.edit {
            val json = GsonUtils.gson.toJson(config)
            putString("${p}profile_json", json)
        }
    }

    private fun loadProfileForDevice(device: ViperOutputDevice): ViperMasterConfig {
        val p = getDevicePrefix(device)
        val json = prefs.getString("${p}profile_json", null)
        if (json != null) {
            try {
                val loaded = GsonUtils.gson.fromJson(json, ViperMasterConfig::class.java)
                if (loaded != null) {
                    return sanitizeConfig(loaded).copy(activeOutputDevice = device)
                }
            } catch (_: Exception) {}
        }
        return ViperMasterConfig(activeOutputDevice = device)
    }

    /**
     * Export full active ViPER profile to a JSON string bundle
     */
    fun exportProfileJson(): String {
        return GsonUtils.gson.toJson(_configFlow.value)
    }

    /**
     * Import full ViPER profile from a JSON string bundle
     */
    fun importProfileJson(json: String): Boolean {
        return try {
            val imported = GsonUtils.gson.fromJson(json, ViperMasterConfig::class.java) ?: return false
            val sanitized = sanitizeConfig(imported)
            _configFlow.value = sanitized
            saveConfigToPrefs(sanitized)
            saveProfileForDevice(sanitized.activeOutputDevice, sanitized)
            true
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("SENSELESS_COMPARISON")
    private fun sanitizeConfig(c: ViperMasterConfig?): ViperMasterConfig {
        if (c == null) return ViperMasterConfig()
        val default = ViperMasterConfig()
        val feq = if (c.fireEqualizer != null) c.fireEqualizer else default.fireEqualizer
        val bands = feq.bands
        val sanitizedBands = if (bands != null && bands.size == 10) bands else List(10) { 0.0f }
        val sanitizedFeq = feq.copy(bands = sanitizedBands)
        return c.copy(
            masterGate = if (c.masterGate != null) c.masterGate else default.masterGate,
            playbackGain = if (c.playbackGain != null) c.playbackGain else default.playbackGain,
            fetCompressor = if (c.fetCompressor != null) c.fetCompressor else default.fetCompressor,
            fireEqualizer = sanitizedFeq,
            viperDdc = if (c.viperDdc != null) c.viperDdc else default.viperDdc,
            spectrumExtension = if (c.spectrumExtension != null) c.spectrumExtension else default.spectrumExtension,
            convolver = if (c.convolver != null) c.convolver else default.convolver,
            fieldSurround = if (c.fieldSurround != null) c.fieldSurround else default.fieldSurround,
            diffSurround = if (c.diffSurround != null) c.diffSurround else default.diffSurround,
            headphoneSurround = if (c.headphoneSurround != null) c.headphoneSurround else default.headphoneSurround,
            reverberation = if (c.reverberation != null) c.reverberation else default.reverberation,
            dynamicSystem = if (c.dynamicSystem != null) c.dynamicSystem else default.dynamicSystem,
            tubeSimulator = if (c.tubeSimulator != null) c.tubeSimulator else default.tubeSimulator,
            analogX = if (c.analogX != null) c.analogX else default.analogX,
            viperBass = if (c.viperBass != null) c.viperBass else default.viperBass,
            viperClarity = if (c.viperClarity != null) c.viperClarity else default.viperClarity,
            bassMono = if (c.bassMono != null) c.bassMono else default.bassMono,
            psychoacousticBass = if (c.psychoacousticBass != null) c.psychoacousticBass else default.psychoacousticBass,
            stereoImager = if (c.stereoImager != null) c.stereoImager else default.stereoImager,
            cureCrossfeed = if (c.cureCrossfeed != null) c.cureCrossfeed else default.cureCrossfeed,
            dynamicEq = if (c.dynamicEq != null) c.dynamicEq else default.dynamicEq,
            speakerOpt = if (c.speakerOpt != null) c.speakerOpt else default.speakerOpt,
            lufs = if (c.lufs != null) c.lufs else default.lufs,
            multibandCompressor = if (c.multibandCompressor != null) c.multibandCompressor else default.multibandCompressor
        )
    }

    private fun loadConfigFromPrefs(): ViperMasterConfig {
        val rawUiMode = prefs.getString("viper_ui_mode", ViperUiMode.MODERATE.name)
        val uiMode = try { ViperUiMode.valueOf(rawUiMode ?: "MODERATE") } catch (_: Exception) { ViperUiMode.MODERATE }
        val rawDevice = prefs.getString("viper_active_device", ViperOutputDevice.SPEAKER.name)
        val device = try { ViperOutputDevice.valueOf(rawDevice ?: "SPEAKER") } catch (_: Exception) { ViperOutputDevice.SPEAKER }
        val masterEnabled = prefs.getBoolean(KEY_GLOBAL_MASTER_ENABLE, prefs.getBoolean("master_enabled", false))

        // Check if device profile JSON exists
        val p = getDevicePrefix(device)
        val deviceJson = prefs.getString("${p}profile_json", null)
        if (deviceJson != null) {
            try {
                val profile = GsonUtils.gson.fromJson(deviceJson, ViperMasterConfig::class.java)
                if (profile != null) {
                    return sanitizeConfig(profile).copy(
                        masterEnabled = masterEnabled,
                        uiMode = uiMode,
                        activeOutputDevice = device,
                        autoDeviceProfileEnabled = prefs.getBoolean("auto_device_profile", true),
                        bindToSystemEffect = prefs.getBoolean("bind_system_effect", false)
                    )
                }
            } catch (_: Exception) {}
        }

        val bandsString = prefs.getString("feq_bands", null)
        val bandsList = bandsString?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: List(10) { 0.0f }

        return ViperMasterConfig(
            masterEnabled = masterEnabled,
            uiMode = uiMode,
            activeOutputDevice = device,
            autoDeviceProfileEnabled = prefs.getBoolean("auto_device_profile", true),
            bindToSystemEffect = prefs.getBoolean("bind_system_effect", false),
            masterGate = MasterGateConfig(
                limiterEnabled = prefs.getBoolean("mg_limiter_enabled", true),
                limiterThresholdDb = prefs.getFloat("mg_limiter_thresh", -0.5f),
                outputVolumeDb = prefs.getFloat("mg_out_vol", 0.0f),
                channelPan = prefs.getFloat("mg_pan", 0.0f)
            ),
            playbackGain = PlaybackGainConfig(
                enabled = prefs.getBoolean("pgc_enabled", false),
                strength = prefs.getInt("pgc_strength", 2),
                maxGainDb = prefs.getFloat("pgc_max_gain", 6.0f),
                outputThresholdDb = prefs.getFloat("pgc_thresh", -1.0f)
            ),
            fetCompressor = FetCompressorConfig(
                enabled = prefs.getBoolean("fet_enabled", false),
                thresholdDb = prefs.getFloat("fet_thresh", -18.0f),
                ratio = prefs.getFloat("fet_ratio", 2.5f),
                attackMs = prefs.getFloat("fet_attack", 15.0f),
                releaseMs = prefs.getFloat("fet_release", 60.0f),
                makeupGainDb = prefs.getFloat("fet_makeup", 0.0f),
                noClip = prefs.getBoolean("fet_noclip", true)
            ),
            fireEqualizer = FireEqualizerConfig(
                enabled = prefs.getBoolean("feq_enabled", false),
                presetName = prefs.getString("feq_preset", "Flat") ?: "Flat",
                bands = bandsList
            ),
            viperDdc = ViperDdcConfig(
                enabled = prefs.getBoolean("vddc_enabled", false),
                deviceName = prefs.getString("vddc_device", "None") ?: "None"
            ),
            viperBass = ViperBassConfig(
                enabled = prefs.getBoolean("vb_enabled", false),
                mode = prefs.getInt("vb_mode", 0),
                frequencyHz = prefs.getInt("vb_freq", 60),
                gainDb = prefs.getFloat("vb_gain", 4.0f),
                antiPop = prefs.getBoolean("vb_antipop", true)
            ),
            viperClarity = ViperClarityConfig(
                enabled = prefs.getBoolean("vc_enabled", false),
                mode = prefs.getInt("vc_mode", 1),
                gainDb = prefs.getFloat("vc_gain", 3.5f)
            ),
            tubeSimulator = TubeSimulatorConfig(
                enabled = prefs.getBoolean("tube_enabled", false),
                model = prefs.getInt("tube_model", 0),
                drive = prefs.getFloat("tube_drive", 0.4f),
                mix = prefs.getFloat("tube_mix", 0.3f)
            ),
            convolver = ConvolverConfig(
                enabled = prefs.getBoolean("conv_enabled", false),
                kernelName = prefs.getString("conv_kernel", "Studio Master") ?: "Studio Master",
                crossChannel = prefs.getFloat("conv_cross", 0.0f)
            ),
            fieldSurround = FieldSurroundConfig(
                enabled = prefs.getBoolean("fs_enabled", false),
                widening = prefs.getFloat("fs_widen", 0.4f),
                midImage = prefs.getFloat("fs_mid", 1.0f),
                depthMs = prefs.getFloat("fs_depth", 15.0f)
            ),
            diffSurround = DiffSurroundConfig(
                enabled = prefs.getBoolean("diff_enabled", false),
                delayMs = prefs.getFloat("diff_delay", 6.0f),
                reversePhase = prefs.getBoolean("diff_reverse", false),
                wetDryMix = prefs.getFloat("diff_mix", 0.5f),
                lpCutoffHz = prefs.getInt("diff_lp", 6000)
            ),
            headphoneSurround = HeadphoneSurroundConfig(
                enabled = prefs.getBoolean("hs_enabled", false),
                quality = prefs.getInt("hs_qual", 1)
            ),
            reverberation = ReverberationConfig(
                enabled = prefs.getBoolean("rev_enabled", false),
                roomSize = prefs.getFloat("rev_room", 0.35f),
                width = prefs.getFloat("rev_width", 0.5f),
                damp = prefs.getFloat("rev_damp", 0.4f),
                wet = prefs.getFloat("rev_wet", 0.15f),
                dry = prefs.getFloat("rev_dry", 0.9f)
            ),
            dynamicSystem = DynamicSystemConfig(
                enabled = prefs.getBoolean("ds_enabled", false),
                device = prefs.getInt("ds_device", 1),
                strength = prefs.getFloat("ds_strength", 0.4f)
            ),
            analogX = AnalogXConfig(
                enabled = prefs.getBoolean("ax_enabled", false),
                mode = prefs.getInt("ax_mode", 1)
            ),
            spectrumExtension = SpectrumExtensionConfig(
                enabled = prefs.getBoolean("spe_enabled", false),
                strength = prefs.getFloat("spe_strength", 0.5f),
                exciter = prefs.getFloat("spe_exciter", 0.3f)
            ),
            bassMono = BassMonoConfig(
                enabled = prefs.getBoolean("bm_enabled", false),
                frequencyHz = prefs.getInt("bm_freq", 80)
            ),
            psychoacousticBass = PsychoacousticBassConfig(
                enabled = prefs.getBoolean("pab_enabled", false),
                cutoffHz = prefs.getInt("pab_cutoff", 80),
                intensity = prefs.getFloat("pab_intensity", 0.5f),
                harmonicOrder = prefs.getInt("pab_order", 3),
                originalLevel = prefs.getFloat("pab_orig", 0.8f)
            ),
            stereoImager = StereoImagerConfig(
                enabled = prefs.getBoolean("si_enabled", false),
                lowWidth = prefs.getFloat("si_low_w", 0.7f),
                midWidth = prefs.getFloat("si_mid_w", 1.2f),
                highWidth = prefs.getFloat("si_high_w", 1.4f)
            ),
            cureCrossfeed = CureCrossfeedConfig(
                enabled = prefs.getBoolean("cure_enabled", false),
                preset = prefs.getInt("cure_preset", 1)
            ),
            dynamicEq = DynamicEqConfig(
                enabled = prefs.getBoolean("deq_enabled", false)
            ),
            speakerOpt = SpeakerOptConfig(
                enabled = prefs.getBoolean("so_enabled", false)
            )
        )
    }

    private fun saveConfigToPrefs(c: ViperMasterConfig) {
        prefs.edit {
            putBoolean(KEY_GLOBAL_MASTER_ENABLE, c.masterEnabled)
            putBoolean("master_enabled", c.masterEnabled)
            putString("viper_ui_mode", c.uiMode.name)
            putString("viper_active_device", c.activeOutputDevice.name)
            putBoolean("auto_device_profile", c.autoDeviceProfileEnabled)
            putBoolean("bind_system_effect", c.bindToSystemEffect)

            // Master Gate
            putBoolean("mg_limiter_enabled", c.masterGate.limiterEnabled)
            putFloat("mg_limiter_thresh", c.masterGate.limiterThresholdDb)
            putFloat("mg_out_vol", c.masterGate.outputVolumeDb)
            putFloat("mg_pan", c.masterGate.channelPan)

            // FireEqualizer
            putBoolean("feq_enabled", c.fireEqualizer.enabled)
            putString("feq_preset", c.fireEqualizer.presetName)
            putString("feq_bands", c.fireEqualizer.bands.joinToString(","))

            // ViperDDC
            putBoolean("vddc_enabled", c.viperDdc.enabled)
            putString("vddc_device", c.viperDdc.deviceName)

            // Playback Gain
            putBoolean("pgc_enabled", c.playbackGain.enabled)
            putInt("pgc_strength", c.playbackGain.strength)
            putFloat("pgc_max_gain", c.playbackGain.maxGainDb)
            putFloat("pgc_thresh", c.playbackGain.outputThresholdDb)

            // FET Compressor
            putBoolean("fet_enabled", c.fetCompressor.enabled)
            putFloat("fet_thresh", c.fetCompressor.thresholdDb)
            putFloat("fet_ratio", c.fetCompressor.ratio)
            putFloat("fet_attack", c.fetCompressor.attackMs)
            putFloat("fet_release", c.fetCompressor.releaseMs)
            putFloat("fet_makeup", c.fetCompressor.makeupGainDb)
            putBoolean("fet_noclip", c.fetCompressor.noClip)

            // ViPER Bass
            putBoolean("vb_enabled", c.viperBass.enabled)
            putInt("vb_mode", c.viperBass.mode)
            putInt("vb_freq", c.viperBass.frequencyHz)
            putFloat("vb_gain", c.viperBass.gainDb)
            putBoolean("vb_antipop", c.viperBass.antiPop)

            // ViPER Clarity
            putBoolean("vc_enabled", c.viperClarity.enabled)
            putInt("vc_mode", c.viperClarity.mode)
            putFloat("vc_gain", c.viperClarity.gainDb)

            // Tube Simulator (6N1J)
            putBoolean("tube_enabled", c.tubeSimulator.enabled)
            putInt("tube_model", c.tubeSimulator.model)
            putFloat("tube_drive", c.tubeSimulator.drive)
            putFloat("tube_mix", c.tubeSimulator.mix)

            // Convolver
            putBoolean("conv_enabled", c.convolver.enabled)
            putString("conv_kernel", c.convolver.kernelName)
            putFloat("conv_cross", c.convolver.crossChannel)

            // Field Surround
            putBoolean("fs_enabled", c.fieldSurround.enabled)
            putFloat("fs_widen", c.fieldSurround.widening)
            putFloat("fs_mid", c.fieldSurround.midImage)
            putFloat("fs_depth", c.fieldSurround.depthMs)

            // Differential Surround
            putBoolean("diff_enabled", c.diffSurround.enabled)
            putFloat("diff_delay", c.diffSurround.delayMs)
            putBoolean("diff_reverse", c.diffSurround.reversePhase)
            putFloat("diff_mix", c.diffSurround.wetDryMix)
            putInt("diff_lp", c.diffSurround.lpCutoffHz)

            // Headphone Surround VHS+
            putBoolean("hs_enabled", c.headphoneSurround.enabled)
            putInt("hs_qual", c.headphoneSurround.quality)

            // Reverberation
            putBoolean("rev_enabled", c.reverberation.enabled)
            putFloat("rev_room", c.reverberation.roomSize)
            putFloat("rev_width", c.reverberation.width)
            putFloat("rev_damp", c.reverberation.damp)
            putFloat("rev_wet", c.reverberation.wet)
            putFloat("rev_dry", c.reverberation.dry)

            // Dynamic System
            putBoolean("ds_enabled", c.dynamicSystem.enabled)
            putInt("ds_device", c.dynamicSystem.device)
            putFloat("ds_strength", c.dynamicSystem.strength)

            // AnalogX
            putBoolean("ax_enabled", c.analogX.enabled)
            putInt("ax_mode", c.analogX.mode)

            // Spectrum Extension
            putBoolean("spe_enabled", c.spectrumExtension.enabled)
            putFloat("spe_strength", c.spectrumExtension.strength)
            putFloat("spe_exciter", c.spectrumExtension.exciter)

            // Bass Mono
            putBoolean("bm_enabled", c.bassMono.enabled)
            putInt("bm_freq", c.bassMono.frequencyHz)

            // Psychoacoustic Bass
            putBoolean("pab_enabled", c.psychoacousticBass.enabled)
            putInt("pab_cutoff", c.psychoacousticBass.cutoffHz)
            putFloat("pab_intensity", c.psychoacousticBass.intensity)
            putInt("pab_order", c.psychoacousticBass.harmonicOrder)
            putFloat("pab_orig", c.psychoacousticBass.originalLevel)

            // Stereo Imager
            putBoolean("si_enabled", c.stereoImager.enabled)
            putFloat("si_low_w", c.stereoImager.lowWidth)
            putFloat("si_mid_w", c.stereoImager.midWidth)
            putFloat("si_high_w", c.stereoImager.highWidth)

            // Cure Crossfeed
            putBoolean("cure_enabled", c.cureCrossfeed.enabled)
            putInt("cure_preset", c.cureCrossfeed.preset)

            // Dynamic EQ
            putBoolean("deq_enabled", c.dynamicEq.enabled)

            // Speaker Opt
            putBoolean("so_enabled", c.speakerOpt.enabled)
        }
    }
}
