/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-FileCopyrightText: 2026 Rhythm Audio Enhancement Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.screens.equalizer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.infrastructure.audio.viper.*
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import java.util.Locale

/**
 * ViPER4Android Master Gate & Limiter UI
 */
@Composable
fun ViperMasterGateCard(
    config: MasterGateConfig,
    onConfigChange: (MasterGateConfig) -> Unit,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    ViperSectionCard(
        title = "Master Control & Limiter",
        subtitle = "Output Gain, Balance & Peak Protection",
        icon = MaterialSymbolIcon("tune", filled = true),
        enabled = config.limiterEnabled,
        onEnabledChange = { onConfigChange(config.copy(limiterEnabled = it)) },
        activeColor = activeColor
    ) {
        ViperSliderRow(
            label = "Output Volume",
            value = config.outputVolumeDb,
            valueRange = -20.0f..6.0f,
            valueFormatted = String.format(Locale.US, "%+.1f dB", config.outputVolumeDb),
            onValueChange = { onConfigChange(config.copy(outputVolumeDb = it)) },
            activeColor = activeColor
        )

        ViperSliderRow(
            label = "Channel Pan (L / R)",
            value = config.channelPan,
            valueRange = -1.0f..1.0f,
            valueFormatted = when {
                config.channelPan < -0.05f -> String.format(Locale.US, "L %d%%", (-config.channelPan * 100).toInt())
                config.channelPan > 0.05f -> String.format(Locale.US, "R %d%%", (config.channelPan * 100).toInt())
                else -> "Center"
            },
            onValueChange = { onConfigChange(config.copy(channelPan = it)) },
            activeColor = activeColor
        )

        ViperSliderRow(
            label = "Limiter Threshold",
            value = config.limiterThresholdDb,
            valueRange = -6.0f..0.0f,
            valueFormatted = String.format(Locale.US, "%.1f dB", config.limiterThresholdDb),
            onValueChange = { onConfigChange(config.copy(limiterThresholdDb = it)) },
            activeColor = activeColor
        )
    }
}

/**
 * ViPER4Android FireEqualizer UI
 */
@Composable
fun ViperFireEqualizerCard(
    config: FireEqualizerConfig,
    onConfigChange: (FireEqualizerConfig) -> Unit,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    val presets = FireEqualizerConfig.BUILTIN_PRESETS.keys.toList()

    ViperSectionCard(
        title = "FireEqualizer",
        subtitle = "10-Band Graphic Tone Equalizer",
        icon = MaterialSymbolIcon("equalizer", filled = true),
        enabled = config.enabled,
        onEnabledChange = { onConfigChange(config.copy(enabled = it)) },
        activeColor = activeColor
    ) {
        // Preset selector
        ViperSegmentedRow(
            label = "Equalizer Preset",
            options = listOf("Flat", "Rock", "Acoustic", "Bass", "R&B"),
            selectedIndex = listOf("Flat", "Rock", "Acoustic", "Bass Booster", "R&B").indexOf(config.presetName).coerceAtLeast(0),
            onSelectIndex = { idx ->
                val pName = listOf("Flat", "Rock", "Acoustic", "Bass Booster", "R&B")[idx]
                val bands = FireEqualizerConfig.BUILTIN_PRESETS[pName] ?: List(10) { 0.0f }
                onConfigChange(config.copy(presetName = pName, bands = bands))
            },
            activeColor = activeColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Band sliders
        val freqs = FireEqualizerConfig.FREQUENCIES_10
        for (i in 0 until minOf(10, config.bands.size)) {
            val freqLabel = if (freqs[i] >= 1000) "${freqs[i] / 1000} kHz" else "${freqs[i]} Hz"
            ViperSliderRow(
                label = freqLabel,
                value = config.bands[i],
                valueRange = -12.0f..12.0f,
                valueFormatted = String.format(Locale.US, "%+.1f dB", config.bands[i]),
                onValueChange = { newLevel ->
                    val updatedBands = config.bands.toMutableList()
                    updatedBands[i] = newLevel
                    onConfigChange(config.copy(bands = updatedBands, presetName = "Custom"))
                },
                activeColor = activeColor
            )
        }
    }
}

/**
 * ViPER4Android ViperDDC (Digital Device Correction) UI
 */
@Composable
fun ViperDdcCard(
    config: ViperDdcConfig,
    onConfigChange: (ViperDdcConfig) -> Unit,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    val devices = ViperDdcConfig.BUILTIN_DEVICES

    ViperSectionCard(
        title = "ViperDDC",
        subtitle = "Digital Device Headphone Correction",
        icon = MaterialSymbolIcon("headphones", filled = true),
        enabled = config.enabled,
        onEnabledChange = { onConfigChange(config.copy(enabled = it)) },
        activeColor = activeColor
    ) {
        ViperSegmentedRow(
            label = "Headphone Model",
            options = listOf("None", "AirPods", "Sony XM4", "HD 650", "M50x"),
            selectedIndex = when (config.deviceName) {
                "Apple AirPods Pro" -> 1
                "Sony WH-1000XM4" -> 2
                "Sennheiser HD 650" -> 3
                "Audio-Technica ATH-M50x" -> 4
                else -> 0
            },
            onSelectIndex = { idx ->
                val chosen = when (idx) {
                    1 -> "Apple AirPods Pro"
                    2 -> "Sony WH-1000XM4"
                    3 -> "Sennheiser HD 650"
                    4 -> "Audio-Technica ATH-M50x"
                    else -> "None"
                }
                onConfigChange(config.copy(deviceName = chosen))
            },
            activeColor = activeColor
        )

        Text(
            text = "Currently applied DDC curve: ${config.deviceName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/**
 * ViPER4Android Clarity UI Card
 */
@Composable
fun ViperClarityCard(
    config: ViperClarityConfig,
    onConfigChange: (ViperClarityConfig) -> Unit,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    ViperSectionCard(
        title = "ViPER Clarity",
        subtitle = "High-Frequency Detail & Presence",
        icon = MaterialSymbolIcon("waves", filled = true),
        enabled = config.enabled,
        onEnabledChange = { onConfigChange(config.copy(enabled = it)) },
        activeColor = activeColor
    ) {
        ViperSegmentedRow(
            label = "Clarity Mode",
            options = listOf("Natural", "Ozone+", "X-HiFi"),
            selectedIndex = config.mode.coerceIn(0, 2),
            onSelectIndex = { idx -> onConfigChange(config.copy(mode = idx)) },
            activeColor = activeColor
        )

        ViperSliderRow(
            label = "Clarity Gain",
            value = config.gainDb,
            valueRange = 0.0f..12.0f,
            valueFormatted = String.format(Locale.US, "+%.1f dB", config.gainDb),
            onValueChange = { v -> onConfigChange(config.copy(gainDb = v)) },
            activeColor = activeColor
        )
    }
}

/**
 * ViPER4Android Convolver (IRS) UI Card
 */
@Composable
fun ViperConvolverCard(
    config: ConvolverConfig,
    onConfigChange: (ConvolverConfig) -> Unit,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    val presets = listOf("Studio Master", "Warm Tube", "Analog Tape", "Acoustic Room")

    ViperSectionCard(
        title = "Convolver (IRS)",
        subtitle = "Hardware & Room Impulse Response",
        icon = MaterialSymbolIcon("settings_input_component", filled = true),
        enabled = config.enabled,
        onEnabledChange = { onConfigChange(config.copy(enabled = it)) },
        activeColor = activeColor
    ) {
        ViperSegmentedRow(
            label = "Impulse Preset",
            options = presets,
            selectedIndex = presets.indexOf(config.kernelName).coerceAtLeast(0),
            onSelectIndex = { idx -> onConfigChange(config.copy(kernelName = presets[idx])) },
            activeColor = activeColor
        )

        ViperSliderRow(
            label = "Cross Channel Level",
            value = config.crossChannel * 100f,
            valueRange = 0.0f..100.0f,
            valueFormatted = String.format(Locale.US, "%d%%", (config.crossChannel * 100).toInt()),
            onValueChange = { v -> onConfigChange(config.copy(crossChannel = v / 100f)) },
            activeColor = activeColor
        )
    }
}

/**
 * ViPER4Android Dynamics & Loudness Section
 * (AGC, FET Compressor, Multiband Compressor)
 */
@Composable
fun ViperDynamicsGroup(
    masterConfig: ViperMasterConfig,
    onUpdate: ((ViperMasterConfig) -> ViperMasterConfig) -> Unit
) {
    val secColor = MaterialTheme.colorScheme.secondary

    // 1. Playback Gain Control (AGC)
    ViperSectionCard(
        title = "AGC (Playback Gain)",
        subtitle = "Dynamic Auto Gain Leveler",
        icon = MaterialSymbolIcon("volume_up", filled = true),
        enabled = masterConfig.playbackGain.enabled,
        onEnabledChange = { en -> onUpdate { it.copy(playbackGain = it.playbackGain.copy(enabled = en)) } },
        activeColor = secColor
    ) {
        ViperSegmentedRow(
            label = "Strength",
            options = listOf("Subtle", "Moderate", "Aggressive"),
            selectedIndex = (masterConfig.playbackGain.strength - 1).coerceIn(0, 2),
            onSelectIndex = { idx -> onUpdate { it.copy(playbackGain = it.playbackGain.copy(strength = idx + 1)) } },
            activeColor = secColor
        )

        ViperSliderRow(
            label = "Max Gain",
            value = masterConfig.playbackGain.maxGainDb,
            valueRange = 0.0f..18.0f,
            valueFormatted = String.format(Locale.US, "+%.1f dB", masterConfig.playbackGain.maxGainDb),
            onValueChange = { v -> onUpdate { it.copy(playbackGain = it.playbackGain.copy(maxGainDb = v)) } },
            activeColor = secColor
        )

        ViperSliderRow(
            label = "Output Threshold",
            value = masterConfig.playbackGain.outputThresholdDb,
            valueRange = -6.0f..0.0f,
            valueFormatted = String.format(Locale.US, "%.1f dB", masterConfig.playbackGain.outputThresholdDb),
            onValueChange = { v -> onUpdate { it.copy(playbackGain = it.playbackGain.copy(outputThresholdDb = v)) } },
            activeColor = secColor
        )
    }

    // 2. FET Compressor
    if (masterConfig.uiMode == ViperUiMode.EXPERT) {
        ViperSectionCard(
            title = "Fet Compressor",
            subtitle = "Vintage Fast Analog Dynamics & Punch",
            icon = MaterialSymbolIcon("compress", filled = true),
            enabled = masterConfig.fetCompressor.enabled,
            onEnabledChange = { en -> onUpdate { it.copy(fetCompressor = it.fetCompressor.copy(enabled = en)) } },
            activeColor = secColor
        ) {
            ViperSliderRow(
                label = "Threshold",
                value = masterConfig.fetCompressor.thresholdDb,
                valueRange = -40.0f..0.0f,
                valueFormatted = String.format(Locale.US, "%.1f dB", masterConfig.fetCompressor.thresholdDb),
                onValueChange = { v -> onUpdate { it.copy(fetCompressor = it.fetCompressor.copy(thresholdDb = v)) } },
                activeColor = secColor
            )

            ViperSliderRow(
                label = "Ratio",
                value = masterConfig.fetCompressor.ratio,
                valueRange = 1.0f..10.0f,
                valueFormatted = String.format(Locale.US, "%.1f:1", masterConfig.fetCompressor.ratio),
                onValueChange = { v -> onUpdate { it.copy(fetCompressor = it.fetCompressor.copy(ratio = v)) } },
                activeColor = secColor
            )

            ViperSliderRow(
                label = "Attack Time",
                value = masterConfig.fetCompressor.attackMs,
                valueRange = 0.5f..50.0f,
                valueFormatted = String.format(Locale.US, "%.1f ms", masterConfig.fetCompressor.attackMs),
                onValueChange = { v -> onUpdate { it.copy(fetCompressor = it.fetCompressor.copy(attackMs = v)) } },
                activeColor = secColor
            )

            ViperSliderRow(
                label = "Release Time",
                value = masterConfig.fetCompressor.releaseMs,
                valueRange = 10.0f..400.0f,
                valueFormatted = String.format(Locale.US, "%.0f ms", masterConfig.fetCompressor.releaseMs),
                onValueChange = { v -> onUpdate { it.copy(fetCompressor = it.fetCompressor.copy(releaseMs = v)) } },
                activeColor = secColor
            )

            ViperSliderRow(
                label = "Makeup Gain",
                value = masterConfig.fetCompressor.makeupGainDb,
                valueRange = 0.0f..12.0f,
                valueFormatted = String.format(Locale.US, "+%.1f dB", masterConfig.fetCompressor.makeupGainDb),
                onValueChange = { v -> onUpdate { it.copy(fetCompressor = it.fetCompressor.copy(makeupGainDb = v)) } },
                activeColor = secColor
            )
        }
    }
}

/**
 * ViPER4Android Bass Section
 * (ViPER Bass, Bass Mono, Psychoacoustic Bass, Dynamic System)
 */
@Composable
fun ViperBassGroup(
    masterConfig: ViperMasterConfig,
    onUpdate: ((ViperMasterConfig) -> ViperMasterConfig) -> Unit
) {
    val bassColor = MaterialTheme.colorScheme.tertiary

    // 1. ViPER Bass
    ViperSectionCard(
        title = "Bass (ViPER Bass)",
        subtitle = "Natural, Pure & Subwoofer Low-End",
        icon = RhythmIcons.SpeakerFilled,
        enabled = masterConfig.viperBass.enabled,
        onEnabledChange = { en -> onUpdate { it.copy(viperBass = it.viperBass.copy(enabled = en)) } },
        activeColor = bassColor
    ) {
        ViperSegmentedRow(
            label = "Bass Mode",
            options = listOf("Natural", "Pure Bass", "Subwoofer"),
            selectedIndex = masterConfig.viperBass.mode.coerceIn(0, 2),
            onSelectIndex = { idx -> onUpdate { it.copy(viperBass = it.viperBass.copy(mode = idx)) } },
            activeColor = bassColor
        )

        ViperSliderRow(
            label = "Bass Frequency",
            value = masterConfig.viperBass.frequencyHz.toFloat(),
            valueRange = 40.0f..160.0f,
            valueFormatted = String.format(Locale.US, "%d Hz", masterConfig.viperBass.frequencyHz),
            onValueChange = { v -> onUpdate { it.copy(viperBass = it.viperBass.copy(frequencyHz = v.toInt())) } },
            activeColor = bassColor
        )

        ViperSliderRow(
            label = "Bass Gain",
            value = masterConfig.viperBass.gainDb,
            valueRange = 0.0f..18.0f,
            valueFormatted = String.format(Locale.US, "+%.1f dB", masterConfig.viperBass.gainDb),
            onValueChange = { v -> onUpdate { it.copy(viperBass = it.viperBass.copy(gainDb = v)) } },
            activeColor = bassColor
        )
    }

    if (masterConfig.uiMode != ViperUiMode.SIMPLE) {
        // 2. Dynamic System (Driver Emulation)
        ViperSectionCard(
            title = "Dynamic System",
            subtitle = "Acoustic Transducer & Driver Emulation",
            icon = MaterialSymbolIcon("graphic_eq", filled = true),
            enabled = masterConfig.dynamicSystem.enabled,
            onEnabledChange = { en -> onUpdate { it.copy(dynamicSystem = it.dynamicSystem.copy(enabled = en)) } },
            activeColor = bassColor
        ) {
            ViperSegmentedRow(
                label = "Transducer Preset",
                options = listOf("Earphone", "Hi-End", "EarPods", "Extreme", "Speaker"),
                selectedIndex = masterConfig.dynamicSystem.device.coerceIn(0, 4),
                onSelectIndex = { idx -> onUpdate { it.copy(dynamicSystem = it.dynamicSystem.copy(device = idx)) } },
                activeColor = bassColor
            )

            ViperSliderRow(
                label = "Effect Strength",
                value = masterConfig.dynamicSystem.strength * 100f,
                valueRange = 0.0f..100.0f,
                valueFormatted = String.format(Locale.US, "%d%%", (masterConfig.dynamicSystem.strength * 100).toInt()),
                onValueChange = { v -> onUpdate { it.copy(dynamicSystem = it.dynamicSystem.copy(strength = v / 100f)) } },
                activeColor = bassColor
            )
        }
    }

    if (masterConfig.uiMode == ViperUiMode.EXPERT) {
        // 3. Bass Mono
        ViperSectionCard(
            title = "Bass Mono",
            subtitle = "Center Low-End Frequencies for Punch",
            icon = MaterialSymbolIcon("music_note", filled = true),
            enabled = masterConfig.bassMono.enabled,
            onEnabledChange = { en -> onUpdate { it.copy(bassMono = it.bassMono.copy(enabled = en)) } },
            activeColor = bassColor
        ) {
            ViperSliderRow(
                label = "Cutoff Frequency",
                value = masterConfig.bassMono.frequencyHz.toFloat(),
                valueRange = 40.0f..200.0f,
                valueFormatted = String.format(Locale.US, "%d Hz", masterConfig.bassMono.frequencyHz),
                onValueChange = { v -> onUpdate { it.copy(bassMono = it.bassMono.copy(frequencyHz = v.toInt())) } },
                activeColor = bassColor
            )
        }

        // 4. Psychoacoustic Bass
        ViperSectionCard(
            title = "Psychoacoustic Bass",
            subtitle = "Missing Fundamental Harmonic Generator",
            icon = MaterialSymbolIcon("psychology", filled = true),
            enabled = masterConfig.psychoacousticBass.enabled,
            onEnabledChange = { en -> onUpdate { it.copy(psychoacousticBass = it.psychoacousticBass.copy(enabled = en)) } },
            activeColor = bassColor
        ) {
            ViperSliderRow(
                label = "Bass Cutoff",
                value = masterConfig.psychoacousticBass.cutoffHz.toFloat(),
                valueRange = 40.0f..140.0f,
                valueFormatted = String.format(Locale.US, "%d Hz", masterConfig.psychoacousticBass.cutoffHz),
                onValueChange = { v -> onUpdate { it.copy(psychoacousticBass = it.psychoacousticBass.copy(cutoffHz = v.toInt())) } },
                activeColor = bassColor
            )

            ViperSliderRow(
                label = "Harmonic Intensity",
                value = masterConfig.psychoacousticBass.intensity * 100f,
                valueRange = 0.0f..100.0f,
                valueFormatted = String.format(Locale.US, "%d%%", (masterConfig.psychoacousticBass.intensity * 100).toInt()),
                onValueChange = { v -> onUpdate { it.copy(psychoacousticBass = it.psychoacousticBass.copy(intensity = v / 100f)) } },
                activeColor = bassColor
            )
        }
    }
}

/**
 * ViPER4Android Clarity & Harmonics Section
 * (Clarity, Tube Simulator 6N1J, Spectrum Extension, AnalogX)
 */
@Composable
fun ViperClarityGroup(
    masterConfig: ViperMasterConfig,
    onUpdate: ((ViperMasterConfig) -> ViperMasterConfig) -> Unit
) {
    val clarColor = MaterialTheme.colorScheme.primary

    // 1. ViPER Clarity
    ViperSectionCard(
        title = "Clarity",
        subtitle = "High-Frequency Detail & Presence",
        icon = MaterialSymbolIcon("waves", filled = true),
        enabled = masterConfig.viperClarity.enabled,
        onEnabledChange = { en -> onUpdate { it.copy(viperClarity = it.viperClarity.copy(enabled = en)) } },
        activeColor = clarColor
    ) {
        ViperSegmentedRow(
            label = "Clarity Mode",
            options = listOf("Natural", "Ozone+", "X-HiFi"),
            selectedIndex = masterConfig.viperClarity.mode.coerceIn(0, 2),
            onSelectIndex = { idx -> onUpdate { it.copy(viperClarity = it.viperClarity.copy(mode = idx)) } },
            activeColor = clarColor
        )

        ViperSliderRow(
            label = "Clarity Gain",
            value = masterConfig.viperClarity.gainDb,
            valueRange = 0.0f..12.0f,
            valueFormatted = String.format(Locale.US, "+%.1f dB", masterConfig.viperClarity.gainDb),
            onValueChange = { v -> onUpdate { it.copy(viperClarity = it.viperClarity.copy(gainDb = v)) } },
            activeColor = clarColor
        )
    }

    if (masterConfig.uiMode != ViperUiMode.SIMPLE) {
        // 2. Tube Simulator (6N1J)
        ViperSectionCard(
            title = "Tube Simulator (6N1J)",
            subtitle = "Warm Vacuum Tube Triode Saturation",
            icon = MaterialSymbolIcon("candlestick_chart", filled = true),
            enabled = masterConfig.tubeSimulator.enabled,
            onEnabledChange = { en -> onUpdate { it.copy(tubeSimulator = it.tubeSimulator.copy(enabled = en)) } },
            activeColor = clarColor
        ) {
            ViperSegmentedRow(
                label = "Tube Model",
                options = listOf("6N1J Triode", "12AX7 Warm", "FET Stage"),
                selectedIndex = masterConfig.tubeSimulator.model.coerceIn(0, 2),
                onSelectIndex = { idx -> onUpdate { it.copy(tubeSimulator = it.tubeSimulator.copy(model = idx)) } },
                activeColor = clarColor
            )

            ViperSliderRow(
                label = "Drive Amount",
                value = masterConfig.tubeSimulator.drive * 100f,
                valueRange = 0.0f..100.0f,
                valueFormatted = String.format(Locale.US, "%d%%", (masterConfig.tubeSimulator.drive * 100).toInt()),
                onValueChange = { v -> onUpdate { it.copy(tubeSimulator = it.tubeSimulator.copy(drive = v / 100f)) } },
                activeColor = clarColor
            )

            ViperSliderRow(
                label = "Wet / Dry Mix",
                value = masterConfig.tubeSimulator.mix * 100f,
                valueRange = 0.0f..100.0f,
                valueFormatted = String.format(Locale.US, "%d%%", (masterConfig.tubeSimulator.mix * 100).toInt()),
                onValueChange = { v -> onUpdate { it.copy(tubeSimulator = it.tubeSimulator.copy(mix = v / 100f)) } },
                activeColor = clarColor
            )
        }

        // 3. Spectrum Extension
        ViperSectionCard(
            title = "Spectrum Extension",
            subtitle = "High-Frequency Harmonic Reconstructor",
            icon = MaterialSymbolIcon("insights", filled = true),
            enabled = masterConfig.spectrumExtension.enabled,
            onEnabledChange = { en -> onUpdate { it.copy(spectrumExtension = it.spectrumExtension.copy(enabled = en)) } },
            activeColor = clarColor
        ) {
            ViperSliderRow(
                label = "Extension Strength",
                value = masterConfig.spectrumExtension.strength * 100f,
                valueRange = 0.0f..100.0f,
                valueFormatted = String.format(Locale.US, "%d%%", (masterConfig.spectrumExtension.strength * 100).toInt()),
                onValueChange = { v -> onUpdate { it.copy(spectrumExtension = it.spectrumExtension.copy(strength = v / 100f)) } },
                activeColor = clarColor
            )

            ViperSliderRow(
                label = "Exciter Amount",
                value = masterConfig.spectrumExtension.exciter * 100f,
                valueRange = 0.0f..100.0f,
                valueFormatted = String.format(Locale.US, "%d%%", (masterConfig.spectrumExtension.exciter * 100).toInt()),
                onValueChange = { v -> onUpdate { it.copy(spectrumExtension = it.spectrumExtension.copy(exciter = v / 100f)) } },
                activeColor = clarColor
            )
        }
    }

    if (masterConfig.uiMode == ViperUiMode.EXPERT) {
        // 4. AnalogX
        ViperSectionCard(
            title = "AnalogX",
            subtitle = "Class-A Discrete Analog Amplifier Emulation",
            icon = MaterialSymbolIcon("memory", filled = true),
            enabled = masterConfig.analogX.enabled,
            onEnabledChange = { en -> onUpdate { it.copy(analogX = it.analogX.copy(enabled = en)) } },
            activeColor = clarColor
        ) {
            ViperSegmentedRow(
                label = "Warmth Level",
                options = listOf("Subtle", "Moderate", "Warm Vintage"),
                selectedIndex = masterConfig.analogX.mode.coerceIn(0, 2),
                onSelectIndex = { idx -> onUpdate { it.copy(analogX = it.analogX.copy(mode = idx)) } },
                activeColor = clarColor
            )
        }
    }
}

/**
 * ViPER4Android Soundstage & Spatial Section
 * (Field Surround, Differential Surround, Headphone Surround+, Reverberation, Convolver)
 */
@Composable
fun ViperSpatialGroup(
    masterConfig: ViperMasterConfig,
    onUpdate: ((ViperMasterConfig) -> ViperMasterConfig) -> Unit
) {
    val spatColor = MaterialTheme.colorScheme.secondary

    // 1. Field Surround
    ViperSectionCard(
        title = "Field Surround",
        subtitle = "Multi-Dimensional Soundstage Widening",
        icon = MaterialSymbolIcon("surround_sound", filled = true),
        enabled = masterConfig.fieldSurround.enabled,
        onEnabledChange = { en -> onUpdate { it.copy(fieldSurround = it.fieldSurround.copy(enabled = en)) } },
        activeColor = spatColor
    ) {
        ViperSliderRow(
            label = "Soundstage Widening",
            value = masterConfig.fieldSurround.widening * 100f,
            valueRange = 0.0f..100.0f,
            valueFormatted = String.format(Locale.US, "%d%%", (masterConfig.fieldSurround.widening * 100).toInt()),
            onValueChange = { v -> onUpdate { it.copy(fieldSurround = it.fieldSurround.copy(widening = v / 100f)) } },
            activeColor = spatColor
        )

        ViperSliderRow(
            label = "Mid-Image Focus",
            value = masterConfig.fieldSurround.midImage,
            valueRange = 0.5f..2.0f,
            valueFormatted = String.format(Locale.US, "%.1fx", masterConfig.fieldSurround.midImage),
            onValueChange = { v -> onUpdate { it.copy(fieldSurround = it.fieldSurround.copy(midImage = v)) } },
            activeColor = spatColor
        )
    }

    // 2. Convolver (IRS)
    ViperSectionCard(
        title = "Convolver",
        subtitle = "Hardware & Room Impulse Response (IRS)",
        icon = MaterialSymbolIcon("settings_input_component", filled = true),
        enabled = masterConfig.convolver.enabled,
        onEnabledChange = { en -> onUpdate { it.copy(convolver = it.convolver.copy(enabled = en)) } },
        activeColor = spatColor
    ) {
        val presets = listOf("Studio Master", "Warm Tube", "Analog Tape", "Acoustic Room")
        ViperSegmentedRow(
            label = "Impulse Preset",
            options = presets,
            selectedIndex = presets.indexOf(masterConfig.convolver.kernelName).coerceAtLeast(0),
            onSelectIndex = { idx -> onUpdate { it.copy(convolver = it.convolver.copy(kernelName = presets[idx])) } },
            activeColor = spatColor
        )

        ViperSliderRow(
            label = "Cross Channel Level",
            value = masterConfig.convolver.crossChannel * 100f,
            valueRange = 0.0f..100.0f,
            valueFormatted = String.format(Locale.US, "%d%%", (masterConfig.convolver.crossChannel * 100).toInt()),
            onValueChange = { v -> onUpdate { it.copy(convolver = it.convolver.copy(crossChannel = v / 100f)) } },
            activeColor = spatColor
        )
    }

    // 3. Reverberation
    ViperSectionCard(
        title = "Reverberation",
        subtitle = "Algorithmic Acoustic Reverb",
        icon = MaterialSymbolIcon("blur_on", filled = true),
        enabled = masterConfig.reverberation.enabled,
        onEnabledChange = { en -> onUpdate { it.copy(reverberation = it.reverberation.copy(enabled = en)) } },
        activeColor = spatColor
    ) {
        ViperSliderRow(
            label = "Room Size",
            value = masterConfig.reverberation.roomSize * 100f,
            valueRange = 0.0f..100.0f,
            valueFormatted = String.format(Locale.US, "%d%%", (masterConfig.reverberation.roomSize * 100).toInt()),
            onValueChange = { v -> onUpdate { it.copy(reverberation = it.reverberation.copy(roomSize = v / 100f)) } },
            activeColor = spatColor
        )

        ViperSliderRow(
            label = "Wet Signal",
            value = masterConfig.reverberation.wet * 100f,
            valueRange = 0.0f..50.0f,
            valueFormatted = String.format(Locale.US, "%d%%", (masterConfig.reverberation.wet * 100).toInt()),
            onValueChange = { v -> onUpdate { it.copy(reverberation = it.reverberation.copy(wet = v / 100f)) } },
            activeColor = spatColor
        )
    }

    // 4. Headphone Surround+ (VHS+)
    ViperSectionCard(
        title = "Headphone Surround+",
        subtitle = "Binaural Headphone Spatial Virtualizer",
        icon = MaterialSymbolIcon("headphones", filled = true),
        enabled = masterConfig.headphoneSurround.enabled,
        onEnabledChange = { en -> onUpdate { it.copy(headphoneSurround = it.headphoneSurround.copy(enabled = en)) } },
        activeColor = spatColor
    ) {
        ViperSegmentedRow(
            label = "Surround Quality",
            options = listOf("Level 1 (Standard)", "Level 2 (VHS+)"),
            selectedIndex = masterConfig.headphoneSurround.quality.coerceIn(0, 1),
            onSelectIndex = { idx -> onUpdate { it.copy(headphoneSurround = it.headphoneSurround.copy(quality = idx)) } },
            activeColor = spatColor
        )
    }

    if (masterConfig.uiMode == ViperUiMode.EXPERT) {
        // 5. Differential Surround
        ViperSectionCard(
            title = "Diffrential Surround",
            subtitle = "Haas-Effect Phase Delay",
            icon = MaterialSymbolIcon("spatial_audio", filled = true),
            enabled = masterConfig.diffSurround.enabled,
            onEnabledChange = { en -> onUpdate { it.copy(diffSurround = it.diffSurround.copy(enabled = en)) } },
            activeColor = spatColor
        ) {
            ViperSliderRow(
                label = "Surround Delay",
                value = masterConfig.diffSurround.delayMs,
                valueRange = 1.0f..20.0f,
                valueFormatted = String.format(Locale.US, "%.1f ms", masterConfig.diffSurround.delayMs),
                onValueChange = { v -> onUpdate { it.copy(diffSurround = it.diffSurround.copy(delayMs = v)) } },
                activeColor = spatColor
            )

            ViperSliderRow(
                label = "Wet / Dry Mix",
                value = masterConfig.diffSurround.wetDryMix * 100f,
                valueRange = 0.0f..100.0f,
                valueFormatted = String.format(Locale.US, "%d%%", (masterConfig.diffSurround.wetDryMix * 100).toInt()),
                onValueChange = { v -> onUpdate { it.copy(diffSurround = it.diffSurround.copy(wetDryMix = v / 100f)) } },
                activeColor = spatColor
            )
        }
    }
}
