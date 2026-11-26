/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util.diagnostics

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class TombstoneSummaryTest {

    private fun ByteArrayOutputStream.writeVarint(value: Long) {
        var v = value
        while (true) {
            if ((v and 0x7FL.inv()) == 0L) {
                write(v.toInt())
                return
            }
            write(((v and 0x7FL) or 0x80L).toInt())
            v = v ushr 7
        }
    }

    private fun ByteArrayOutputStream.writeTag(fieldNumber: Int, wireType: Int) {
        writeVarint(((fieldNumber.toLong()) shl 3) or wireType.toLong())
    }

    private fun ByteArrayOutputStream.writeBytesField(fieldNumber: Int, payload: ByteArray) {
        writeTag(fieldNumber, 2)
        writeVarint(payload.size.toLong())
        write(payload)
    }

    private fun protoMessage(build: ByteArrayOutputStream.() -> Unit): ByteArray {
        return ByteArrayOutputStream().apply(build).toByteArray()
    }

    @Test
    fun format_extractsSignalAbortCauseAndCrashingThreadBacktrace() {
        val signalBytes = protoMessage {
            writeTag(1, 0); writeVarint(11L) // number = SIGSEGV
            writeBytesField(2, "SIGSEGV".toByteArray())
            writeBytesField(3, "SEGV_MAPERR".toByteArray())
            writeTag(9, 0); writeVarint(0xdeadbeefL) // fault_address
        }

        val frameBytes = protoMessage {
            writeTag(1, 0); writeVarint(0x1a20L) // rel_pc
            writeBytesField(4, "decodeAudioFrame".toByteArray())
            writeTag(5, 0); writeVarint(44L)
            writeBytesField(6, "/data/app/libffmpegJNI.so".toByteArray())
        }

        val crashingThread = protoMessage {
            writeTag(1, 0); writeVarint(4242L)
            writeBytesField(2, "AudioTrackOut".toByteArray())
            writeBytesField(4, frameBytes)
        }

        val threadMapEntry = protoMessage {
            writeTag(1, 0); writeVarint(4242L)
            writeBytesField(2, crashingThread)
        }

        val causeBytes = protoMessage {
            writeBytesField(1, "Null pointer dereference".toByteArray())
        }

        val tombstone = protoMessage {
            writeTag(6, 0); writeVarint(4242L) // tid
            writeBytesField(10, signalBytes)
            writeBytesField(14, "Check failed in DSP node".toByteArray())
            writeBytesField(15, causeBytes)
            writeBytesField(16, threadMapEntry)
        }

        val summary = TombstoneSummary.format(tombstone)
        assertNotNull(summary)
        assertTrue(summary!!.contains("Signal: SIGSEGV (SEGV_MAPERR) at 0xdeadbeef"))
        assertTrue(summary.contains("Abort: Check failed in DSP node"))
        assertTrue(summary.contains("Cause: Null pointer dereference"))
        assertTrue(summary.contains("Thread 4242 (AudioTrackOut)"))
        assertTrue(summary.contains("libffmpegJNI.so (decodeAudioFrame+44)"))
    }

    @Test
    fun format_returnsNullOnMalformedOrEmptyPayloadWithoutThrowing() {
        assertNull(TombstoneSummary.format(byteArrayOf()))
        assertNull(TombstoneSummary.format(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())))
    }
}
