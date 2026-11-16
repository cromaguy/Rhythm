/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-FileCopyrightText: 2026 Rhythm Audio Enhancement Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.viper

/**
 * ViPER4Android DSP audio configurations and parameter states for Rhythm.
 * Covers all ViPER modules, UI complexity levels (Simple, Moderate, Expert),
 * auto-device profiles (Speaker, Bluetooth, Headset, USB), DDC, and engine telemetry.
 */

enum class ViperUiMode {
    SIMPLE,
    MODERATE,
    EXPERT
}

enum class ViperOutputDevice(val displayName: String) {
    HEADSET("Wired Headset"),
    BLUETOOTH("Bluetooth Device"),
    SPEAKER("Phone Speaker"),
    USB("USB DAC / Audio")
}

data class MasterGateConfig(
    val limiterEnabled: Boolean = true,
    val limiterThresholdDb: Float = -0.5f, // -6.0dB to 0.0dB
    val outputVolumeDb: Float = 0.0f,     // -20.0dB to +6.0dB
    val channelPan: Float = 0.0f          // -1.0f (full left) to +1.0f (full right)
)

data class PlaybackGainConfig(
    val enabled: Boolean = false,
    val strength: Int = 2,                // 1 (Subtle), 2 (Moderate), 3 (Aggressive)
    val maxGainDb: Float = 6.0f,          // 0.0dB to 18.0dB
    val outputThresholdDb: Float = -1.0f  // -6.0dB to 0.0dB
)

data class FetCompressorConfig(
    val enabled: Boolean = false,
    val thresholdDb: Float = -18.0f,      // -40.0dB to 0.0dB
    val ratio: Float = 2.5f,              // 1.0 to 10.0
    val attackMs: Float = 15.0f,          // 0.1ms to 60.0ms
    val releaseMs: Float = 60.0f,         // 10.0ms to 500.0ms
    val makeupGainDb: Float = 0.0f,       // 0.0dB to 15.0dB
    val noClip: Boolean = true
)

data class FireEqualizerConfig(
    val enabled: Boolean = false,
    val bandCount: Int = 10,
    val presetName: String = "Flat",
    val bands: List<Float> = List(10) { 0.0f }
) {
    companion object {
        val FREQUENCIES_10 = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val BUILTIN_PRESETS = mapOf(
            "Flat" to List(10) { 0.0f },
            "Acoustic" to listOf(4.5f, 4.5f, 3.5f, 1.2f, 1.0f, 0.5f, 1.4f, 1.7f, 3.5f, 2.5f),
            "Bass Booster" to listOf(6.0f, 4.0f, 2.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            "Bass Reducer" to listOf(-6.0f, -4.0f, -2.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            "Classical" to listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -3.0f, -3.0f, -3.0f, -5.0f),
            "Deep" to listOf(3.0f, 2.0f, 1.0f, 0.5f, 0.5f, 0.0f, -1.0f, -2.0f, -3.0f, -3.5f),
            "R&B" to listOf(3.0f, 6.0f, 4.0f, 1.0f, -1.0f, -0.5f, 1.0f, 1.5f, 2.5f, 3.0f),
            "Rock" to listOf(4.0f, 3.0f, 1.0f, 0.0f, -0.5f, 0.0f, 1.5f, 2.5f, 3.5f, 4.0f),
            "Small Speakers" to listOf(3.0f, 2.0f, 1.5f, 1.0f, 0.5f, -0.5f, -1.5f, -2.0f, -3.0f, -3.5f),
            "Treble Booster" to listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f),
            "Treble Reducer" to listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.0f, -2.0f, -3.0f, -4.0f, -5.0f),
            "Vocal Booster" to listOf(-1.0f, -0.5f, 0.5f, 2.5f, 3.5f, 3.5f, 2.0f, 1.0f, 0.0f, -1.0f)
        )
    }
}

data class ViperDdcConfig(
    val enabled: Boolean = false,
    val deviceName: String = "None"
) {
    companion object {
        val BUILTIN_DEVICES = listOf(
            "None",
            "Apple AirPods Pro",
            "Apple AirPods Max",
            "Sony WH-1000XM4",
            "Sony WH-1000XM5",
            "Sennheiser HD 650",
            "Audio-Technica ATH-M50x",
            "Beyerdynamic DT 770 Pro",
            "Beyerdynamic DT 990 Pro",
            "Samsung Galaxy Buds Pro",
            "Moondrop Aria",
            "KZ ZSN Pro X"
        )
    }
}

data class SpectrumExtensionConfig(
    val enabled: Boolean = false,
    val strength: Float = 0.5f,           // 0.0 to 1.0
    val exciter: Float = 0.3f             // 0.0 to 1.0
)

data class ConvolverConfig(
    val enabled: Boolean = false,
    val kernelName: String = "Studio Master",
    val crossChannel: Float = 0.0f
)

data class FieldSurroundConfig(
    val enabled: Boolean = false,
    val widening: Float = 0.4f,           // 0.0 to 1.0
    val midImage: Float = 1.0f,           // 0.5 to 2.0
    val depthMs: Float = 15.0f            // 0.0 to 40.0 ms
)

data class DiffSurroundConfig(
    val enabled: Boolean = false,
    val delayMs: Float = 6.0f,            // 1.0ms to 25.0ms
    val reversePhase: Boolean = false,
    val wetDryMix: Float = 0.5f,          // 0.0 to 1.0
    val lpCutoffHz: Int = 6000            // 1000Hz to 12000Hz
)

data class HeadphoneSurroundConfig(
    val enabled: Boolean = false,
    val quality: Int = 1                  // 0: Level 1, 1: Level 2 (VHS+)
)

data class ReverberationConfig(
    val enabled: Boolean = false,
    val roomSize: Float = 0.35f,          // 0.0 to 1.0
    val width: Float = 0.5f,              // 0.0 to 1.0
    val damp: Float = 0.4f,               // 0.0 to 1.0
    val wet: Float = 0.15f,               // 0.0 to 1.0
    val dry: Float = 0.9f                 // 0.0 to 1.0
)

data class DynamicSystemConfig(
    val enabled: Boolean = false,
    val device: Int = 1,                  // 0: Common Earphone, 1: High-End Headphone, 2: Apple EarPods, 3: Extreme Bass, 4: Speaker
    val strength: Float = 0.4f
)

data class TubeSimulatorConfig(
    val enabled: Boolean = false,
    val model: Int = 0,                   // 0: 6N1J Vacuum Tube (ViPER Original), 1: 12AX7 High Gain, 2: Warm FET
    val drive: Float = 0.4f,              // 0.0 to 1.0
    val mix: Float = 0.3f                 // 0.0 to 1.0
)

data class AnalogXConfig(
    val enabled: Boolean = false,
    val mode: Int = 1                     // 0: Light, 1: Moderate, 2: Warm / Vintage
)

data class ViperBassConfig(
    val enabled: Boolean = false,
    val mode: Int = 0,                    // 0: Natural Bass, 1: Pure Bass, 2: Subwoofer
    val frequencyHz: Int = 60,            // 30Hz to 200Hz
    val gainDb: Float = 4.0f,             // 0.0dB to 18.0dB
    val antiPop: Boolean = true
)

data class ViperClarityConfig(
    val enabled: Boolean = false,
    val mode: Int = 1,                    // 0: Natural, 1: Ozone+, 2: X-HiFi
    val gainDb: Float = 3.5f              // 0.0dB to 12.0dB
)

data class BassMonoConfig(
    val enabled: Boolean = false,
    val frequencyHz: Int = 80             // 40Hz to 200Hz
)

data class PsychoacousticBassConfig(
    val enabled: Boolean = false,
    val cutoffHz: Int = 80,
    val intensity: Float = 0.5f,
    val harmonicOrder: Int = 3,
    val originalLevel: Float = 0.8f
)

data class StereoImagerConfig(
    val enabled: Boolean = false,
    val lowWidth: Float = 0.7f,
    val midWidth: Float = 1.2f,
    val highWidth: Float = 1.4f,
    val lowCrossoverHz: Int = 250,
    val highCrossoverHz: Int = 4000
)

data class CureCrossfeedConfig(
    val enabled: Boolean = false,
    val preset: Int = 1                   // 0: Slight, 1: Moderate, 2: Strong
)

data class DynamicEqConfig(
    val enabled: Boolean = false,
    val band1Freq: Int = 80,
    val band1GainDb: Float = 0.0f,
    val band2Freq: Int = 1000,
    val band2GainDb: Float = 0.0f,
    val band3Freq: Int = 8000,
    val band3GainDb: Float = 0.0f
)

data class SpeakerOptConfig(
    val enabled: Boolean = false
)

data class LufsConfig(
    val enabled: Boolean = false,
    val targetLufs: Float = -14.0f,
    val maxGainDb: Float = 6.0f,
    val speed: Int = 1
)

data class MultibandCompressorConfig(
    val enabled: Boolean = false,
    val crossover1: Int = 120,
    val crossover2: Int = 500,
    val crossover3: Int = 4000,
    val crossover4: Int = 8000,
    val bandEnables: List<Boolean> = listOf(true, true, true, true, true),
    val thresholdsDb: List<Float> = listOf(-18f, -18f, -16f, -14f, -12f),
    val ratios: List<Float> = listOf(2.5f, 2.0f, 1.8f, 1.8f, 1.5f),
    val gainsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f)
)

data class ViperMasterConfig(
    val masterEnabled: Boolean = false,
    val uiMode: ViperUiMode = ViperUiMode.MODERATE,
    val activeOutputDevice: ViperOutputDevice = ViperOutputDevice.SPEAKER,
    val autoDeviceProfileEnabled: Boolean = true,
    val bindToSystemEffect: Boolean = false,
    val masterGate: MasterGateConfig = MasterGateConfig(),
    val playbackGain: PlaybackGainConfig = PlaybackGainConfig(),
    val fetCompressor: FetCompressorConfig = FetCompressorConfig(),
    val fireEqualizer: FireEqualizerConfig = FireEqualizerConfig(),
    val viperDdc: ViperDdcConfig = ViperDdcConfig(),
    val spectrumExtension: SpectrumExtensionConfig = SpectrumExtensionConfig(),
    val convolver: ConvolverConfig = ConvolverConfig(),
    val fieldSurround: FieldSurroundConfig = FieldSurroundConfig(),
    val diffSurround: DiffSurroundConfig = DiffSurroundConfig(),
    val headphoneSurround: HeadphoneSurroundConfig = HeadphoneSurroundConfig(),
    val reverberation: ReverberationConfig = ReverberationConfig(),
    val dynamicSystem: DynamicSystemConfig = DynamicSystemConfig(),
    val tubeSimulator: TubeSimulatorConfig = TubeSimulatorConfig(),
    val analogX: AnalogXConfig = AnalogXConfig(),
    val viperBass: ViperBassConfig = ViperBassConfig(),
    val viperClarity: ViperClarityConfig = ViperClarityConfig(),
    val bassMono: BassMonoConfig = BassMonoConfig(),
    val psychoacousticBass: PsychoacousticBassConfig = PsychoacousticBassConfig(),
    val stereoImager: StereoImagerConfig = StereoImagerConfig(),
    val cureCrossfeed: CureCrossfeedConfig = CureCrossfeedConfig(),
    val dynamicEq: DynamicEqConfig = DynamicEqConfig(),
    val speakerOpt: SpeakerOptConfig = SpeakerOptConfig(),
    val lufs: LufsConfig = LufsConfig(),
    val multibandCompressor: MultibandCompressorConfig = MultibandCompressorConfig()
)

data class ViperEngineTelemetry(
    val isProcessing: Boolean = false,
    val sampleRate: Int = 44100,
    val channelCount: Int = 2,
    val bufferFrames: Int = 1024,
    val activeEffectsCount: Int = 0,
    val currentDevice: ViperOutputDevice = ViperOutputDevice.SPEAKER,
    val peakOutputL: Float = 0.0f,
    val peakOutputR: Float = 0.0f
)
