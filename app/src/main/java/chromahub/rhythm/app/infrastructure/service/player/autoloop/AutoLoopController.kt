package chromahub.rhythm.app.infrastructure.service.player.autoloop

import android.content.Context
import androidx.annotation.StringRes
import chromahub.rhythm.app.R
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import chromahub.rhythm.app.infrastructure.service.player.RhythmShuffleOrder

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

/** Keep the resource and arguments unresolved so the UI uses its current locale. */
data class AutoLoopMessage(
    @StringRes val resourceId: Int,
    val formatArgs: List<Any> = emptyList(),
)

/** Session-only setting shared by the playback sheet and the in-process playback service. */
object AutoLoopState {
    private val enabledState = MutableStateFlow(false)
    val enabled = enabledState.asStateFlow()
    internal val windowState = MutableStateFlow<AutoLoopWindow?>(null)
    // Only failed/no-match analysis opens the original source; successful analysis installs
    // a separate loop source. Including the URI in the key prevents releasing another track.
    internal val analysisFallbackKey = MutableStateFlow<String?>(null)
    fun windowFor(item: androidx.media3.common.MediaItem?): AutoLoopWindow? =
        windowState.value?.takeIf {
            item?.mediaId == it.mediaId &&
                item.clippingConfiguration.startPositionMs == 0L &&
                item.clippingConfiguration.endPositionMs == it.endMs
        }
    internal val messageState = MutableStateFlow(AutoLoopMessage(R.string.settings_automatic_loop_desc))
    val message = messageState.asStateFlow()
    fun setEnabled(value: Boolean) {
        if (!value || !enabledState.value) analysisFallbackKey.value = null
        enabledState.value = value
    }
}

/** Analyzes locally, then temporarily clips the current queue item for native repeat playback. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoLoopController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val player: () -> Player,
    private val enginePlayer: () -> ExoPlayer,
    private val installLoopQueue: ((ExoPlayer, List<androidx.media3.common.MediaItem>, Int, Long) -> Unit)? = null
) {
    private var nativeSession: NativeAutoLoopSession? = null

    fun restartFromPreviousCommand(): Boolean = nativeSession?.restartFromPreviousCommand() ?: false

    /** Called synchronously before the service releases the player. */
    fun stop() {
        nativeSession?.close()
        nativeSession = null
        AutoLoopState.setEnabled(false)
    }

    fun start() = scope.launch {
        var key: String? = null
        var analysis: Job? = null
        var points: Pair<Long, Long>? = null
        fun clearBoundary() {
            nativeSession?.close()
            nativeSession = null
        }
        try {
            while (isActive) {
                val p = player()
                val item = p.currentMediaItem
                val uri = item?.localConfiguration?.uri
                val nextKey = if (AutoLoopState.enabled.value) AutoLoopPreparation.key(item) else null
                if (key != nextKey) {
                    clearBoundary()
                    analysis?.cancelAndJoin()
                    points = null
                    key = nextKey
                    if (nextKey != null) {
                        AutoLoopState.analysisFallbackKey.value = null
                        // Recreate any already-prepared sources too (e.g. enabling the toggle
                        // during playback). The preparation gate keeps the position stationary
                        // until the loop source is installed, without changing playWhenReady.
                        val raw = enginePlayer()
                        val items = (0 until raw.mediaItemCount).map(raw::getMediaItemAt)
                        val index = raw.currentMediaItemIndex
                        val position = raw.currentPosition
                        val shuffle = if (raw.shuffleModeEnabled) {
                            val timeline = raw.currentTimeline
                            val order = mutableListOf<Int>()
                            var i = timeline.getFirstWindowIndex(true)
                            while (i != androidx.media3.common.C.INDEX_UNSET && i !in order && order.size < items.size) {
                                order += i
                                i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, true)
                            }
                            order.takeIf { it.size == items.size }?.toIntArray()
                        } else null
                        if (installLoopQueue != null) installLoopQueue.invoke(raw, items, index, position)
                        else raw.setMediaItems(items, index, position)
                        if (shuffle != null) raw.setShuffleOrder(RhythmShuffleOrder(shuffle))
                        if (raw.playbackState == Player.STATE_IDLE || raw.playbackState == Player.STATE_ENDED)
                            raw.prepare()
                        AutoLoopState.messageState.value = AutoLoopMessage(R.string.automatic_loop_analyzing)
                        analysis = launch {
                            try {
                                require(uri != null && uri.scheme in listOf("content", "file")) {
                                    "Automatic looping supports local tracks only"
                                }
                                val result = withContext(Dispatchers.Default) { decodeAndFind(uri) }
                                points = result
                                if (result == null) AutoLoopState.analysisFallbackKey.value = nextKey
                                AutoLoopState.messageState.value = if (result == null)
                                    AutoLoopMessage(R.string.automatic_loop_not_found)
                                else AutoLoopMessage(
                                    R.string.automatic_loop_points,
                                    listOf(result.first / 1000.0, result.second / 1000.0),
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.w("AutoLoopController", "Loop analysis failed", e)
                                AutoLoopState.analysisFallbackKey.value = nextKey
                                AutoLoopState.messageState.value = AutoLoopMessage(R.string.automatic_loop_analysis_failed)
                            }
                        }
                    }
                }
                points?.let { bounds ->
                    val raw = enginePlayer()
                    val session = nativeSession
                    if (session != null && raw.repeatMode != Player.REPEAT_MODE_ONE) {
                        // Explicit repeat-mode changes take precedence over automatic looping.
                        AutoLoopState.setEnabled(false)
                        points = null
                        clearBoundary()
                        return@let
                    }
                    if (session != null && (session.player !== raw || !session.isCurrent())) {
                        // A queue reload or source replacement invalidates this session.
                        // Do not repeatedly reinstall an old clip into the middle of playback.
                        clearBoundary()
                        points = null
                        AutoLoopState.setEnabled(false)
                        return@let
                    }
                    if (nativeSession == null) {
                        // Prepare intro + first pass together as soon as analysis completes.
                        // Crossing the loop start must never trigger a source rebuild.
                        nativeSession = NativeAutoLoopSession(raw, bounds,
                            installLoopQueue?.let { installer -> { items, index, position -> installer(raw, items, index, position) } })
                    }
                }
                // Only tracks selection/enabled changes; never times a playback boundary.
                delay(250L)
            }
        } finally {
            clearBoundary()
            analysis?.cancel()
            AutoLoopState.setEnabled(false)
        }
    }

    internal suspend fun decodeAndFind(uri: android.net.Uri): Pair<Long, Long>? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var refineAac = false
        val result = try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            val format = extractor.getTrackFormat(track)
            refineAac = format.getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm"
            // Bound CPU and memory use; do not silently analyze only part of a long track.
            require(format.getLong(MediaFormat.KEY_DURATION) <= 600_000_000L)
            extractor.selectTrack(track)
            val codec = try {
                MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
            } catch (e: Exception) {
                // M4A is a container: ALAC tracks may need the bundled FFmpeg decoder even
                // though Android's extractor can read their packets and metadata.
                android.util.Log.i("AutoLoopController", "Using software decoder for ${format.getString(MediaFormat.KEY_MIME)}")
                return AutoLoopSoftwareDecoder.find(context, uri)
            }
            decoder = codec
            codec.configure(format, null, null, 0)
            codec.start()
            val samples = FloatArray(600 * 4000 + 4000)
            var count = 0
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var phase = 0
            var monoSum = 0.0
            var monoFrames = 0
            var inputDone = false
            val info = MediaCodec.BufferInfo()
            while (true) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val size = extractor.readSampleData(requireNotNull(codec.getInputBuffer(index)), 0)
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = codec.dequeueOutputBuffer(info, 10_000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val output = codec.outputFormat
                    rate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    encoding = if (output.containsKey(MediaFormat.KEY_PCM_ENCODING))
                        output.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT)
                } else if (index >= 0) {
                    val buffer = requireNotNull(codec.getOutputBuffer(index)).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val bytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                    while (buffer.remaining() >= bytes * channels) {
                        var mono = 0f
                        repeat(channels) { mono += if (bytes == 4) buffer.float else buffer.short / 32768f }
                        // Average the input interval before downsampling. Picking isolated
                        // samples folded high-frequency instruments into incorrect pitch classes.
                        monoSum += mono / channels
                        monoFrames++
                        phase += 4000
                        if (phase >= rate) {
                            phase -= rate
                            require(count < samples.size)
                            samples[count++] = (monoSum / monoFrames).toFloat()
                            monoSum = 0.0
                            monoFrames = 0
                        }
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            LoopPointDetector.find(samples, count)
        } finally {
            decoder?.release()
            extractor.release()
        }
        return if (refineAac && result != null) {
            try {
                AutoLoopAacSpliceRefiner.refine(context, uri, result)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                result
            }
        } else result
    }
}

/** Refine a coarse spectral match without assuming a fixed playback delay. */
internal object LoopSpliceRefiner {
    fun similarity(samples: FloatArray, size: Int, start: Int, end: Int): Double {
        val before = min(2000, start)
        val after = min(2000, size - end)
        var dot = 0.0
        var xx = 0.0
        var yy = 0.0
        for (k in -before until after) {
            val x = samples[start + k].toDouble()
            val y = samples[end + k].toDouble()
            dot += x * y; xx += x * x; yy += y * y
        }
        return dot / sqrt(xx * yy + 1e-20)
    }

    suspend fun refine(samples: FloatArray, size: Int, start: Int, end: Int): Pair<Int, Int> {
        require(size in 0..samples.size && start in 0 until end && end < size)
        // +/-250 ms at 4 kHz; compare a full second around both splice points.
        // Bounded to 501 coarse offsets and seven fine offsets.
        val radius = 1000
        val before = min(2000, min(start, end - radius))
        val after = min(2000, min(size - start, size - end - radius))
        if (before < 0 || after < 0 || before + after < 1000) return start to end
        fun correlation(offset: Int, smooth: Boolean = false): Double {
            var dot = 0.0
            var xx = 0.0
            var yy = 0.0
            for (k in -before until after step 4) {
                var x = samples[start + k].toDouble()
                var y = samples[end + offset + k].toDouble()
                if (smooth && k + 3 < after) {
                    for (j in 1..3) {
                        x += samples[start + k + j]
                        y += samples[end + offset + k + j]
                    }
                }
                dot += x * y
                xx += x * x
                yy += y * y
            }
            if (xx < 1e-9 || yy < 1e-9) return -1.0
            return dot / sqrt(xx * yy)
        }
        var bestOffset = 0
        var best = correlation(0, smooth = true)
        for (offset in -radius..radius step 4) {
            currentCoroutineContext().ensureActive()
            val score = correlation(offset, smooth = true)
            if (score > best + 1e-7 || (abs(score - best) <= 1e-7 && abs(offset) < abs(bestOffset))) {
                best = score
                bestOffset = offset
            }
        }
        val coarseOffset = bestOffset
        best = correlation(bestOffset)
        for (offset in max(-radius, coarseOffset - 3)..min(radius, coarseOffset + 3)) {
            val score = correlation(offset)
            if (score > best + 1e-7) { best = score; bestOffset = offset }
        }
        // Spectrally similar performances need not have identical waveforms. Retain the
        // musical match when correlation cannot confidently resolve its sample offset.
        return start to if (best >= 0.85) end + bestOffset else end
    }
}

/**
 * Musical similarity search inspired by PyMusicLooper: chroma/loudness candidates,
 * weighted look-ahead/look-behind scoring, then nearby rising zero crossings.
 * Uses a dense time grid instead of librosa beat tracking; no waveform identity required.
 */
object LoopPointDetector {
    private const val RATE = 4000
    private const val HOP = 400 // 100 ms candidate grid.
    private const val FFT = 256 // Shorter window locates note attacks more accurately.
    private data class Frame(val notes: DoubleArray, val db: Double, val tonal: Double)
    private data class Candidate(val a: Int, val b: Int, val distance: Double)

    suspend fun find(samples: FloatArray, size: Int): Pair<Long, Long>? {
        require(size in 0..samples.size)
        require(size <= RATE * 600 + RATE)
        if (size < RATE * 10) return null
        val frames = ArrayList<Frame>(6004)
        val window = DoubleArray(FFT) { 0.5 - 0.5 * cos(2 * PI * it / (FFT - 1)) }
        val bins = IntArray(FFT / 2) { bin ->
            if (bin < 1) -1 else {
                val midi = 69 + 12 * ln(bin * RATE.toDouble() / FFT / 440) / ln(2.0)
                if (midi < 36 || midi > 95) -1 else ((midi.roundToInt() % 12) + 12) % 12
            }
        }
        val re = DoubleArray(FFT)
        val im = DoubleArray(FFT)
        // At most 6,010 256-point FFTs (less FFT work than the old 3,004 x 1024).
        // Nine refinements per candidate cell entry bound context scoring to <27M note products.
        fun feature(start: Int): Frame {
            for (i in re.indices) re[i] = samples[start + i] * window[i]
            im.fill(0.0)
            transform(re, im)
            val notes = DoubleArray(12)
            var power = 0.0
            var peak = 0.0
            for (bin in 1 until FFT / 2) {
                val p = re[bin] * re[bin] + im[bin] * im[bin]
                power += p
                peak = max(peak, p)
                if (bins[bin] >= 0) notes[bins[bin]] += p
            }
            // Subtract the spectral floor so broadband noise doesn't look like every chord.
            val floor = notes.minOrNull() ?: 0.0
            for (i in notes.indices) notes[i] = sqrt(max(0.0, notes[i] - floor))
            val norm = sqrt(notes.sumOf { it * it })
            if (norm > 0) for (i in notes.indices) notes[i] /= norm
            return Frame(notes, 10 * log10(power / (FFT * FFT) + 1e-12), peak / (power + 1e-12))
        }
        for (start in 0..size - FFT step HOP) {
            currentCoroutineContext().ensureActive()
            frames += feature(start)
        }
        val gap = max(5 * RATE / HOP, (frames.size * 0.35).toInt())
        // Preserve coverage across both start and end time. A single endpoint heap
        // discarded musically stronger long loops before context scoring could see them.
        // 32 x 32 cells, at most four candidates per cell; bounded independently of length.
        val candidateCells = Array(32 * 32) {
            java.util.PriorityQueue<Candidate>(compareByDescending { it.distance })
        }
        val peakDb = frames.maxOf { it.db }
        fun audible(i: Int) = frames[i].db > max(-75.0, peakDb - 40) && frames[i].tonal > 0.08
        fun similarity(a: Int, b: Int): Double =
            frames[a].notes.indices.sumOf { frames[a].notes[it] * frames[b].notes[it] }
        for (a in 0 until frames.size - gap step 2) {
            currentCoroutineContext().ensureActive()
            if (!audible(a)) continue
            for (b in a + gap until frames.size step 2) {
                if (!audible(b)) continue
                val loudness = abs(frames[a].db - frames[b].db)
                if (loudness > 3.0) continue
                val distance = 1 - similarity(a, b)
                if (distance > 0.12) continue
                val candidate = Candidate(a, b, distance + loudness * 0.003)
                val cell = candidateCells[(a * 32 / frames.size) * 32 + b * 32 / frames.size]
                if (cell.size < 4) cell.add(candidate)
                else if (candidate.distance < requireNotNull(cell.peek()).distance) {
                    cell.poll()
                    cell.add(candidate)
                }
            }
        }
        fun sequence(a: Int, b: Int, direction: Int): Double {
            var score = 0.0
            var weights = 0.0
            // Six seconds on either side, with the greatest weight near the splice.
            val length = min(6 * RATE / HOP, frames.size / 4)
            for (k in 0 until length) {
                val x = a + k * direction
                val y = b + k * direction
                val weight = exp(-ln(max(2.0, length / 12.0)) * k / max(1, length - 1))
                weights += weight
                if (x !in frames.indices || y !in frames.indices) continue
                if (!audible(x) || !audible(y)) continue
                // Like PyMusicLooper, use loudness for endpoint filtering, not as
                // a penalty on the surrounding chroma sequence.
                score += weight * similarity(x, y)
            }
            return score / weights
        }
        var best = 0.0
        var chosen: Candidate? = null
        val ranked = java.util.PriorityQueue<Pair<Candidate, Double>>(compareBy { it.second })
        for (candidate in candidateCells.flatMap { it.toList() }) {
            currentCoroutineContext().ensureActive()
            for (da in -1..1) for (db in -1..1) {
                val a = candidate.a + da
                val b = candidate.b + db
                if (a !in frames.indices || b !in frames.indices || b - a < gap) continue
                if (!audible(a) || !audible(b) || abs(frames[a].db - frames[b].db) > 0.5) continue
                if (1 - similarity(a, b) > 0.02) continue
                val score = max(sequence(a, b, 1), sequence(a, b, -1))
                ranked.add(Candidate(a, b, 0.0) to score)
                if (ranked.size > 16) ranked.poll()
                val previous = chosen
                if (score > best + 0.0001 || (abs(score - best) <= 0.0001 &&
                        previous != null && b - a > previous.b - previous.a)) {
                    best = score
                    chosen = Candidate(a, b, 0.0)
                }
            }
        }
        val match = chosen ?: return null
        if (best < 0.80) return null
        var (start, end) = LoopSpliceRefiner.refine(
            samples, size, match.a * HOP + FFT / 2, match.b * HOP + FFT / 2
        )
        // Validate only the best sixteen musical candidates at sample precision.
        // This resolves a shared repeat period without requiring waveform identity for
        // all tracks. Low-correlation performances retain the chroma-only result.
        var waveBest = 0.95
        var periodMatch: Pair<Int, Int>? = null
        for ((candidate, context) in ranked) {
            currentCoroutineContext().ensureActive()
            if (context < 0.80) continue
            val aligned = LoopSpliceRefiner.refine(samples, size,
                candidate.a * HOP + FFT / 2, candidate.b * HOP + FFT / 2)
            val wave = LoopSpliceRefiner.similarity(samples, size, aligned.first, aligned.second)
            if (wave > waveBest) {
                waveBest = wave
                periodMatch = aligned
            }
        }
        periodMatch?.let { aligned ->
            val period = aligned.second - aligned.first
            // Bounded local search: at most 51 phases, 120 short FFTs per phase.
            // Evaluate actual aligned windows, not rounded grid indices.
            var alignedBest = 0.0
            val low = max(FFT / 2, aligned.first - 4 * RATE)
            val high = min(size - period - FFT / 2 - 1, aligned.first + RATE)
            for (phase in low..high step HOP) {
                currentCoroutineContext().ensureActive()
                var phaseScore = 0.0
                for (direction in listOf(1, -1)) {
                    var score = 0.0
                    var weights = 0.0
                    val length = min(6 * RATE / HOP, frames.size / 4)
                    for (k in 0 until length) {
                        val weight = exp(-ln(max(2.0, length / 12.0)) * k / max(1, length - 1))
                        weights += weight
                        val a = phase + direction * k * HOP - FFT / 2
                        val b = a + period
                        if (a < 0 || b + FFT > size) continue
                        val fa = feature(a)
                        val fb = feature(b)
                        if (fa.db < peakDb - 40 || fb.db < peakDb - 40) continue
                        score += weight * fa.notes.indices.sumOf { fa.notes[it] * fb.notes[it] }
                    }
                    phaseScore = max(phaseScore, score / weights)
                }
                if (phaseScore > alignedBest) {
                    alignedBest = phaseScore
                    start = phase
                    end = phase + period
                }
            }
        }
        // Shift both boundaries together: independent zero-crossing adjustments can
        // reintroduce a timing mismatch after waveform alignment.
        var shift = 0
        var cost = Double.POSITIVE_INFINITY
        for (offset in -20..20) {
            val a = start + offset
            val b = end + offset
            if (a < 1 || b < 1 || a >= size || b >= size) continue
            if (samples[a - 1] <= 0 && samples[a] > 0) {
                val value = abs(samples[a]).toDouble() + abs(samples[b]) + abs(offset) * 0.001
                if (value < cost) { cost = value; shift = offset }
            }
        }
        return (start + shift) * 1000L / RATE to (end + shift) * 1000L / RATE
    }

    /** In-place radix-2 FFT. */
    private fun transform(re: DoubleArray, im: DoubleArray) {
        var j = 0
        for (i in 1 until re.size) {
            var bit = re.size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val t = re[i]; re[i] = re[j]; re[j] = t }
        }
        var length = 2
        while (length <= re.size) {
            val angle = -2 * PI / length
            val wr = cos(angle)
            val wi = sin(angle)
            for (start in re.indices step length) {
                var ur = 1.0
                var ui = 0.0
                for (k in 0 until length / 2) {
                    val a = start + k
                    val b = a + length / 2
                    val tr = re[b] * ur - im[b] * ui
                    val ti = re[b] * ui + im[b] * ur
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                    val next = ur * wr - ui * wi
                    ui = ur * wi + ui * wr
                    ur = next
                }
            }
            length *= 2
        }
    }
}

