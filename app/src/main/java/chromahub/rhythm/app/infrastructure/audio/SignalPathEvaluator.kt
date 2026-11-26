/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio

import kotlin.math.abs

data class SignalPathCheck(
    val stage: String,
    val detail: String,
    val passed: Boolean
)

data class SignalPathInput(
    val sourceCodec: String,
    val sourceSampleRateHz: Int,
    val sourceBitDepth: Int,
    val sourceChannels: Int,
    val isLosslessSource: Boolean,
    val outputSampleRateHz: Int,
    val outputBitDepth: Int,
    val outputDeviceName: String,
    val isUsbDevice: Boolean,
    val bitPerfectMixerActive: Boolean,
    val bitPerfectRequested: Boolean,
    val hasActiveEq: Boolean,
    val hasActiveViper: Boolean,
    val hasActiveOtherDsp: Boolean,
    val playbackSpeed: Float,
    val sampleRateCapHz: Int,
    val surroundDownmixActive: Boolean,
    val floatPipelineActive: Boolean,
    val driftPpm: Double? = null,
    val glitchCount: Long = 0L
)

data class SignalPathReport(
    val verdictTitle: String,
    val verdictSubtitle: String,
    val isBitPerfect: Boolean,
    val checks: List<SignalPathCheck>,
    val dspSummary: String,
    val fidelityScore: Int
)

/**
 * Evaluates end-to-end audio signal integrity across Source -> Resampler/Cap -> DSP/EQ ->
 * Speed/Volume -> Hardware Output / Bit-Perfect USB HAL.
 */
object SignalPathEvaluator {
    fun evaluate(input: SignalPathInput): SignalPathReport {
        val checks = mutableListOf<SignalPathCheck>()
        var penalty = 0

        val srcDetail = buildString {
            append(input.sourceCodec.ifBlank { "PCM" })
            if (input.sourceBitDepth > 0) append(" ${input.sourceBitDepth}-bit")
            if (input.sourceSampleRateHz > 0) append(" / ${formatRate(input.sourceSampleRateHz)}")
            if (input.sourceChannels > 2) append(" (${input.sourceChannels}ch)")
        }
        checks += SignalPathCheck(
            stage = "Source",
            detail = srcDetail,
            passed = input.sourceSampleRateHz > 0
        )

        val outRate = input.outputSampleRateHz.takeIf { it > 0 } ?: input.sourceSampleRateHz
        val resampled = input.sourceSampleRateHz > 0 && outRate != input.sourceSampleRateHz
        val rateDetail = when {
            input.sampleRateCapHz > 0 && input.sourceSampleRateHz > input.sampleRateCapHz ->
                "Capped ${formatRate(input.sourceSampleRateHz)} -> ${formatRate(input.sampleRateCapHz)}"
            !resampled && input.sourceSampleRateHz > 0 ->
                "Native ${formatRate(input.sourceSampleRateHz)}"
            else ->
                "${formatRate(input.sourceSampleRateHz)} -> ${formatRate(outRate)}"
        }
        if (resampled) penalty += 15
        checks += SignalPathCheck(
            stage = "Sample Rate",
            detail = rateDetail,
            passed = !resampled
        )

        val anyDsp = input.hasActiveEq || input.hasActiveViper || input.hasActiveOtherDsp || input.surroundDownmixActive
        val dspSummary = buildList {
            if (input.hasActiveEq) add("Parametric EQ")
            if (input.hasActiveViper) add("ViPER FX")
            if (input.hasActiveOtherDsp) add("Audio Effects")
            if (input.surroundDownmixActive) add("Surround Downmix")
            if (isEmpty()) add(if (input.floatPipelineActive) "32-bit Float" else "Direct Output")
        }.joinToString(" + ")
        if (anyDsp) penalty += 5
        checks += SignalPathCheck(
            stage = "DSP Engine",
            detail = dspSummary,
            passed = !anyDsp
        )

        val tempoChanged = abs(input.playbackSpeed - 1f) > 0.001f
        if (tempoChanged) penalty += 20
        checks += SignalPathCheck(
            stage = "Playback Tempo",
            detail = if (!tempoChanged) "1.00x (Bit-exact timing)" else "${input.playbackSpeed}x time-stretched",
            passed = !tempoChanged
        )

        val bitPerfect = input.isLosslessSource &&
            !resampled &&
            !anyDsp &&
            !tempoChanged &&
            input.bitPerfectMixerActive &&
            input.isUsbDevice

        val outputDetail = when {
            bitPerfect -> "${input.outputDeviceName} • USB Bit-Perfect Direct"
            input.isUsbDevice && input.bitPerfectRequested -> "${input.outputDeviceName} • Mixer Fallback"
            input.floatPipelineActive -> "${input.outputDeviceName} • 32-bit Float Output"
            else -> "${input.outputDeviceName} • ${input.outputBitDepth.coerceAtLeast(16)}-bit Output"
        }
        if (!bitPerfect && input.bitPerfectRequested) penalty += 15
        checks += SignalPathCheck(
            stage = "Output Stage",
            detail = outputDetail,
            passed = bitPerfect || !input.bitPerfectRequested
        )

        input.driftPpm?.let { ppm ->
            val healthy = abs(ppm) < 2500.0 && input.glitchCount == 0L
            checks += SignalPathCheck(
                stage = "Clock Sync",
                detail = String.format(java.util.Locale.ROOT, "%.1f ppm drift • %d glitches", ppm, input.glitchCount),
                passed = healthy
            )
            if (!healthy) penalty += 10
        }

        val verdictTitle = when {
            bitPerfect -> "Bit-Perfect USB"
            !anyDsp && !resampled && input.isLosslessSource -> "Lossless Direct"
            anyDsp && input.floatPipelineActive -> "Hi-Res Enhanced"
            else -> "Processed Output"
        }
        val verdictSubtitle = when {
            bitPerfect -> "Direct USB DAC output with no audio processing"
            anyDsp -> dspSummary
            resampled -> "Resampled to ${formatRate(outRate)}"
            else -> "Standard system audio output"
        }

        return SignalPathReport(
            verdictTitle = verdictTitle,
            verdictSubtitle = verdictSubtitle,
            isBitPerfect = bitPerfect,
            checks = checks,
            dspSummary = dspSummary,
            fidelityScore = (100 - penalty).coerceIn(30, 100)
        )
    }

    private fun formatRate(rateHz: Int): String = when {
        rateHz <= 0 -> "—"
        rateHz % 1000 == 0 -> "${rateHz / 1000} kHz"
        else -> String.format(java.util.Locale.ROOT, "%.1f kHz", rateHz / 1000.0)
    }
}

/**
 * Lightweight playback-clock drift observer comparing ExoPlayer position advance against
 * monotonic wall-clock delta while playing, counting backward stalls/glitches without affecting audio.
 */
class StreamHealthTracker {
    var driftPpm: Double? = null
        private set
    var glitchCount: Long = 0L
        private set

    private var lastPositionMs = -1L
    private var lastWallMs = 0L

    fun reset() {
        driftPpm = null
        glitchCount = 0L
        lastPositionMs = -1L
        lastWallMs = 0L
    }

    fun sample(positionMs: Long, wallMs: Long, playing: Boolean): Double? {
        if (!playing || positionMs < 0) {
            lastPositionMs = -1L
            if (!playing) driftPpm = null
            return driftPpm
        }
        if (lastPositionMs < 0) {
            lastPositionMs = positionMs
            lastWallMs = wallMs
            return driftPpm
        }
        val wallDelta = wallMs - lastWallMs
        val posDelta = positionMs - lastPositionMs
        lastPositionMs = positionMs
        lastWallMs = wallMs
        if (wallDelta < 400L || wallDelta > 3_000L) return driftPpm
        if (abs(posDelta - wallDelta) > 1_500L) {
            if (posDelta < -250L) glitchCount++
            return driftPpm
        }
        if (posDelta <= 0L) {
            if (wallDelta >= 800L) glitchCount++
            return driftPpm
        }
        val instant = (posDelta - wallDelta).toDouble() / wallDelta * 1_000_000.0
        driftPpm = if (driftPpm == null) instant else driftPpm!! * 0.85 + instant * 0.15
        return driftPpm
    }
}
