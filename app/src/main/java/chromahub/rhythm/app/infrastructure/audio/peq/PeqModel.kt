/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.peq

import chromahub.rhythm.app.util.GsonUtils
import com.google.gson.reflect.TypeToken
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Filter shapes understood by Rhythm's Double-precision parametric EQ and EqualizerAPO files. */
enum class PeqFilterType(val code: String, val hasGain: Boolean) {
    PEAKING("PK", true),
    LOW_SHELF("LSC", true),
    HIGH_SHELF("HSC", true),
    LOW_PASS("LPQ", false),
    HIGH_PASS("HPQ", false);

    companion object {
        fun fromCode(code: String): PeqFilterType? = when (code.trim().uppercase(Locale.ROOT)) {
            "PK", "PEQ", "PEAK", "PEAKING" -> PEAKING
            "LSC", "LS", "LSQ", "LOWSHELF" -> LOW_SHELF
            "HSC", "HS", "HSQ", "HIGHSHELF" -> HIGH_SHELF
            "LP", "LPQ", "LOWPASS" -> LOW_PASS
            "HP", "HPQ", "HIGHPASS" -> HIGH_PASS
            else -> null
        }
    }
}

data class PeqFilter(
    val type: PeqFilterType = PeqFilterType.PEAKING,
    val frequencyHz: Double = 1000.0,
    val gainDb: Double = 0.0,
    val q: Double = 0.7071,
    val enabled: Boolean = true
)

data class PeqSettings(
    val preampDb: Double = 0.0,
    val filters: List<PeqFilter> = emptyList(),
    val sourceName: String? = null,
    val fitRmsErrorDb: Double? = null
) {
    val isFlat: Boolean
        get() = abs(preampDb) < 0.001 && filters.none {
            it.enabled && (!it.type.hasGain || abs(it.gainDb) >= 0.001)
        }

    fun sanitized(): PeqSettings = copy(
        preampDb = preampDb.takeIf { it.isFinite() }?.coerceIn(-24.0, 24.0) ?: 0.0,
        filters = filters
            .filter { it.frequencyHz.isFinite() && it.q.isFinite() && it.gainDb.isFinite() }
            .take(MAX_FILTERS)
            .map {
                it.copy(
                    frequencyHz = it.frequencyHz.coerceIn(10.0, 24_000.0),
                    gainDb = it.gainDb.coerceIn(-24.0, 24.0),
                    q = it.q.coerceIn(0.05, 16.0)
                )
            }
    )

    companion object {
        const val MAX_FILTERS = 20

        fun toJson(settings: PeqSettings): String = GsonUtils.gson.toJson(settings)

        fun fromJson(json: String?): PeqSettings? {
            if (json.isNullOrBlank() || json.trim() == "[]") return null
            return try {
                val trimmed = json.trim()
                if (trimmed.startsWith("[")) {
                    val listType = object : TypeToken<List<PeqFilter>>() {}.type
                    val parsed: List<PeqFilter> = GsonUtils.gson.fromJson(trimmed, listType) ?: return null
                    if (parsed.isEmpty()) null else PeqSettings(
                        preampDb = PeqMath.autoPreampDb(parsed),
                        filters = parsed
                    ).sanitized()
                } else {
                    val parsed = GsonUtils.gson.fromJson(trimmed, PeqSettings::class.java)
                    if (parsed == null || parsed.filters.isEmpty()) null else parsed.sanitized()
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** RBJ biquad coefficient and frequency-response math in 64-bit IEEE double precision. */
object PeqMath {
    const val DEFAULT_SAMPLE_RATE = 48_000.0

    /** 1/12-octave logarithmically spaced grid (20 Hz .. 20.48 kHz, 121 points). */
    val LOG_GRID: DoubleArray = DoubleArray(121) { 20.0 * 2.0.pow(it / 12.0) }

    /** Normalized [b0/a0, b1/a0, b2/a0, a1/a0, a2/a0] RBJ coefficients. */
    fun coefficients(filter: PeqFilter, sampleRate: Double = DEFAULT_SAMPLE_RATE): DoubleArray {
        val safeFs = sampleRate.coerceAtLeast(8_000.0)
        val f = filter.frequencyHz.coerceIn(10.0, safeFs * 0.49)
        val w = 2.0 * PI * f / safeFs
        val cs = cos(w)
        val alpha = sin(w) / (2.0 * max(filter.q, 0.05))
        val a = 10.0.pow(filter.gainDb.coerceIn(-30.0, 30.0) / 40.0)
        val b0: Double
        val b1: Double
        val b2: Double
        val a0: Double
        val a1: Double
        val a2: Double
        when (filter.type) {
            PeqFilterType.LOW_SHELF -> {
                val k = 2.0 * sqrt(a) * alpha
                b0 = a * ((a + 1.0) - (a - 1.0) * cs + k)
                b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cs)
                b2 = a * ((a + 1.0) - (a - 1.0) * cs - k)
                a0 = (a + 1.0) + (a - 1.0) * cs + k
                a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cs)
                a2 = (a + 1.0) + (a - 1.0) * cs - k
            }
            PeqFilterType.HIGH_SHELF -> {
                val k = 2.0 * sqrt(a) * alpha
                b0 = a * ((a + 1.0) + (a - 1.0) * cs + k)
                b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cs)
                b2 = a * ((a + 1.0) + (a - 1.0) * cs - k)
                a0 = (a + 1.0) - (a - 1.0) * cs + k
                a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cs)
                a2 = (a + 1.0) - (a - 1.0) * cs - k
            }
            PeqFilterType.LOW_PASS -> {
                b0 = (1.0 - cs) / 2.0
                b1 = 1.0 - cs
                b2 = (1.0 - cs) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cs
                a2 = 1.0 - alpha
            }
            PeqFilterType.HIGH_PASS -> {
                b0 = (1.0 + cs) / 2.0
                b1 = -(1.0 + cs)
                b2 = (1.0 + cs) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cs
                a2 = 1.0 - alpha
            }
            PeqFilterType.PEAKING -> {
                b0 = 1.0 + alpha * a
                b1 = -2.0 * cs
                b2 = 1.0 - alpha * a
                a0 = 1.0 + alpha / a
                a1 = -2.0 * cs
                a2 = 1.0 - alpha / a
            }
        }
        return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    fun filterDb(filter: PeqFilter, frequency: Double, sampleRate: Double = DEFAULT_SAMPLE_RATE): Double {
        if (!filter.enabled) return 0.0
        if (filter.type.hasGain && abs(filter.gainDb) < 0.001) return 0.0
        if (filter.frequencyHz >= sampleRate * 0.49) return 0.0
        val c = coefficients(filter, sampleRate)
        val w = 2.0 * PI * frequency.coerceIn(1.0, sampleRate * 0.49) / sampleRate
        val cos1 = cos(w)
        val sin1 = sin(w)
        val cos2 = cos(2.0 * w)
        val sin2 = sin(2.0 * w)
        val nr = c[0] + c[1] * cos1 + c[2] * cos2
        val ni = -c[1] * sin1 - c[2] * sin2
        val dr = 1.0 + c[3] * cos1 + c[4] * cos2
        val di = -c[3] * sin1 - c[4] * sin2
        val mag = sqrt((nr * nr + ni * ni) / max(dr * dr + di * di, 1e-24))
        return 20.0 * log10(max(mag, 1e-12))
    }

    fun filtersDb(filters: List<PeqFilter>, frequency: Double, sampleRate: Double = DEFAULT_SAMPLE_RATE): Double =
        filters.sumOf { filterDb(it, frequency, sampleRate) }

    fun responseDb(settings: PeqSettings, frequency: Double, sampleRate: Double = DEFAULT_SAMPLE_RATE): Double =
        settings.preampDb + filtersDb(settings.filters, frequency, sampleRate)

    /** AutoEQ negative preamp equal to the maximum positive boost, rounded down to 0.1 dB. */
    fun autoPreampDb(filters: List<PeqFilter>, grid: DoubleArray = LOG_GRID): Double {
        if (filters.isEmpty()) return 0.0
        val maxBoost = grid.maxOf { filtersDb(filters, it) }
        if (maxBoost <= 0.0) return 0.0
        return -(kotlin.math.ceil(maxBoost * 10.0) / 10.0)
    }

    fun bassShelfDb(gainDb: Double, frequency: Double): Double =
        filterDb(PeqFilter(PeqFilterType.LOW_SHELF, 105.0, gainDb, 0.7), frequency)

    fun tiltDb(dbPerOctave: Double, frequency: Double): Double =
        dbPerOctave * ln(frequency.coerceAtLeast(10.0) / 1000.0) / ln(2.0)

    fun roundTo(value: Double, step: Double): Double = floor(value / step + 0.5) * step
}

/** Parses and formats Equalizer APO / AutoEQ `ParametricEQ.txt` filter specifications. */
object ParametricEqText {
    private val PREAMP = Regex("""^\s*Preamp:\s*([-+]?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE)
    private val FILTER = Regex(
        """^\s*Filter\s*\d*\s*:\s*(ON|OFF)\s+([A-Z]+)\s+Fc\s+([-+]?\d+(?:\.\d+)?)\s*Hz""" +
            """(?:\s+Gain\s+([-+]?\d+(?:\.\d+)?)\s*dB)?(?:\s+Q\s+([-+]?\d+(?:\.\d+)?))?""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String, sourceName: String? = null): PeqSettings? {
        var preamp: Double? = null
        val filters = mutableListOf<PeqFilter>()
        for (line in text.lineSequence()) {
            PREAMP.find(line)?.let { preamp = it.groupValues[1].toDoubleOrNull() }
            val match = FILTER.find(line) ?: continue
            val type = PeqFilterType.fromCode(match.groupValues[2]) ?: continue
            val fc = match.groupValues[3].toDoubleOrNull() ?: continue
            val gain = match.groupValues[4].toDoubleOrNull() ?: 0.0
            val q = match.groupValues[5].toDoubleOrNull() ?: 0.7071
            filters += PeqFilter(
                type = type,
                frequencyHz = fc,
                gainDb = gain,
                q = q,
                enabled = match.groupValues[1].equals("ON", ignoreCase = true)
            )
            if (filters.size >= PeqSettings.MAX_FILTERS) break
        }
        if (filters.isEmpty()) return null
        val resolvedPreamp = preamp ?: PeqMath.autoPreampDb(filters)
        return PeqSettings(preampDb = resolvedPreamp, filters = filters, sourceName = sourceName).sanitized()
    }

    fun format(settings: PeqSettings): String = buildString {
        appendLine(String.format(Locale.ROOT, "Preamp: %.1f dB", settings.preampDb))
        settings.filters.forEachIndexed { index, f ->
            append("Filter ${index + 1}: ${if (f.enabled) "ON" else "OFF"} ${f.type.code} ")
            append(String.format(Locale.ROOT, "Fc %.0f Hz", f.frequencyHz))
            if (f.type.hasGain) {
                append(String.format(Locale.ROOT, " Gain %.1f dB", f.gainDb))
            }
            appendLine(String.format(Locale.ROOT, " Q %.2f", f.q))
        }
    }
}
