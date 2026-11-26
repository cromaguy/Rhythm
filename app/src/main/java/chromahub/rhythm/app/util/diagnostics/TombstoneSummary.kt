/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util.diagnostics

/**
 * Zero-dependency binary protobuf wire-format decoder for Android `debuggerd`
 * `tombstone.proto` traces returned by `ApplicationExitInfo.getTraceInputStream()`
 * when `reason == ApplicationExitInfo.REASON_CRASH_NATIVE`.
 */
object TombstoneSummary {

    private const val MAX_FRAMES = 24

    // AOSP platform/system/core debuggerd/proto/tombstone.proto field numbers
    private const val TOMBSTONE_TID = 6
    private const val TOMBSTONE_SIGNAL_INFO = 10
    private const val TOMBSTONE_ABORT_MESSAGE = 14
    private const val TOMBSTONE_CAUSES = 15
    private const val TOMBSTONE_THREADS = 16

    private const val SIGNAL_NUMBER = 1
    private const val SIGNAL_NAME = 2
    private const val SIGNAL_CODE_NAME = 3
    private const val SIGNAL_FAULT_ADDRESS = 9

    private const val CAUSE_HUMAN_READABLE = 1

    private const val THREAD_ID = 1
    private const val THREAD_NAME = 2
    private const val THREAD_CURRENT_BACKTRACE = 4

    private const val FRAME_REL_PC = 1
    private const val FRAME_FUNCTION_NAME = 4
    private const val FRAME_FUNCTION_OFFSET = 5
    private const val FRAME_FILE_NAME = 6

    private data class SignalData(
        var number: Int = 0,
        var name: String = "",
        var codeName: String = "",
        var faultAddress: Long? = null
    )

    private data class ThreadData(
        var id: Int = 0,
        var name: String = "",
        val frames: MutableList<String> = mutableListOf()
    )

    fun format(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        return runCatching { parseTombstone(bytes) }.getOrNull()
    }

    private fun parseTombstone(bytes: ByteArray): String? {
        var crashingTid = 0
        var signal: SignalData? = null
        var abortMessage = ""
        val causes = mutableListOf<String>()
        val threads = mutableListOf<ThreadData>()

        WireReader(bytes, 0, bytes.size).forEachField { field, reader ->
            when (field) {
                TOMBSTONE_TID -> crashingTid = reader.varint().toInt()
                TOMBSTONE_SIGNAL_INFO -> signal = parseSignal(reader.message())
                TOMBSTONE_ABORT_MESSAGE -> abortMessage = reader.string()
                TOMBSTONE_CAUSES -> {
                    val cause = parseCause(reader.message())
                    if (cause.isNotBlank()) causes.add(cause)
                }
                TOMBSTONE_THREADS -> {
                    parseThreadMapEntry(reader.message())?.let { threads.add(it) }
                }
                else -> reader.skip()
            }
        }

        if (signal == null && abortMessage.isBlank() && causes.isEmpty() && threads.isEmpty()) {
            return null
        }

        val crashingThread = threads.firstOrNull { it.id == crashingTid } ?: threads.firstOrNull()
        return buildString {
            signal?.let { s ->
                val sigLabel = s.name.ifBlank { "SIG#${s.number}" }
                val codeSuffix = if (s.codeName.isNotBlank()) " (${s.codeName})" else ""
                val addrSuffix = s.faultAddress?.let { " at 0x${it.toULong().toString(16)}" } ?: ""
                appendLine("Signal: $sigLabel$codeSuffix$addrSuffix")
            }
            if (abortMessage.isNotBlank()) {
                appendLine("Abort: $abortMessage")
            }
            causes.forEach { cause ->
                appendLine("Cause: $cause")
            }
            crashingThread?.let { t ->
                val label = t.name.ifBlank { "thread" }
                appendLine("Thread ${t.id} ($label):")
                t.frames.take(MAX_FRAMES).forEachIndexed { idx, frame ->
                    appendLine("  #${idx.toString().padStart(2, '0')} $frame")
                }
            }
        }.trim().takeIf { it.isNotBlank() }
    }

    private fun parseSignal(reader: WireReader): SignalData {
        val out = SignalData()
        reader.forEachField { field, r ->
            when (field) {
                SIGNAL_NUMBER -> out.number = r.varint().toInt()
                SIGNAL_NAME -> out.name = r.string()
                SIGNAL_CODE_NAME -> out.codeName = r.string()
                SIGNAL_FAULT_ADDRESS -> out.faultAddress = r.varint()
                else -> r.skip()
            }
        }
        return out
    }

    private fun parseCause(reader: WireReader): String {
        var text = ""
        reader.forEachField { field, r ->
            if (field == CAUSE_HUMAN_READABLE) {
                text = r.string()
            } else {
                r.skip()
            }
        }
        return text
    }

    private fun parseThreadMapEntry(reader: WireReader): ThreadData? {
        var keyTid = 0
        var thread: ThreadData? = null
        reader.forEachField { field, r ->
            when (field) {
                1 -> keyTid = r.varint().toInt()
                2 -> thread = parseThread(r.message())
                else -> r.skip()
            }
        }
        return thread?.also { if (it.id == 0) it.id = keyTid }
    }

    private fun parseThread(reader: WireReader): ThreadData {
        val out = ThreadData()
        reader.forEachField { field, r ->
            when (field) {
                THREAD_ID -> out.id = r.varint().toInt()
                THREAD_NAME -> out.name = r.string()
                THREAD_CURRENT_BACKTRACE -> {
                    if (out.frames.size < MAX_FRAMES) {
                        out.frames.add(parseFrame(r.message()))
                    } else {
                        r.skip()
                    }
                }
                else -> r.skip()
            }
        }
        return out
    }

    private fun parseFrame(reader: WireReader): String {
        var relPc = 0L
        var functionName = ""
        var functionOffset = 0L
        var fileName = ""
        reader.forEachField { field, r ->
            when (field) {
                FRAME_REL_PC -> relPc = r.varint()
                FRAME_FUNCTION_NAME -> functionName = r.string()
                FRAME_FUNCTION_OFFSET -> functionOffset = r.varint()
                FRAME_FILE_NAME -> fileName = r.string()
                else -> r.skip()
            }
        }
        val pcHex = "0x${relPc.toULong().toString(16)}"
        val loc = fileName.ifBlank { "<unknown>" }
        val fn = if (functionName.isNotBlank()) {
            if (functionOffset > 0L) "$functionName+$functionOffset" else functionName
        } else {
            "<unknown>"
        }
        return "pc $pcHex $loc ($fn)"
    }

    private class WireReader(
        private val bytes: ByteArray,
        private var pos: Int,
        private val end: Int
    ) {
        private var wireType: Int = 0

        inline fun forEachField(block: (field: Int, reader: WireReader) -> Unit) {
            while (pos < end) {
                val key = varint()
                wireType = (key and 7L).toInt()
                block((key ushr 3).toInt(), this)
            }
        }

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(pos < end && shift < 64) { "truncated varint" }
                val b = bytes[pos++].toInt()
                result = result or ((b and 0x7FL.toInt()).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }

        private fun lengthDelimited(): IntRange {
            val length = varint().toInt()
            require(length >= 0 && pos + length <= end) { "bad length" }
            val range = pos until (pos + length)
            pos += length
            return range
        }

        fun string(): String = lengthDelimited().let { String(bytes, it.first, it.count(), Charsets.UTF_8) }

        fun message(): WireReader = lengthDelimited().let { WireReader(bytes, it.first, it.last + 1) }

        fun skip() {
            when (wireType) {
                0 -> varint()
                1 -> pos += 8
                2 -> lengthDelimited()
                5 -> pos += 4
                else -> error("unsupported wire type $wireType")
            }
            require(pos <= end) { "truncated field" }
        }
    }
}
