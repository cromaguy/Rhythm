/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.nativeaudio

import android.util.Log
import java.io.Closeable

/**
 * Kotlin lifecycle handle for Rhythm's native Linux `usbdevfs` UAC2 isochronous ring stream.
 * Bypasses Android AudioFlinger mixer/resampler for pre-Android 14 USB DACs when device
 * descriptors and permissions are granted by the user.
 */
class UsbAudioStream(
    fd: Int,
    interfaceId: Int,
    endpointOut: Int,
    endpointFeedback: Int,
    sampleRateHz: Int,
    channelCount: Int,
    bitDepth: Int,
    maxPacketSize: Int,
    dataInterval: Int = 1,
    feedbackPacketSize: Int = 4,
    feedbackInterval: Int = 1,
    continuousFeedback: Boolean = false
) : Closeable {

    companion object {
        private const val TAG = "UsbAudioStream"
    }

    @Volatile
    var nativeHandle: Long = 0L
        private set

    init {
        if (NativeAudioBridge.ensureLoaded()) {
            nativeHandle = runCatching {
                nativeUsbAudioCreate(
                    fd,
                    interfaceId,
                    endpointOut,
                    endpointFeedback,
                    sampleRateHz,
                    channelCount,
                    bitDepth,
                    maxPacketSize,
                    dataInterval,
                    feedbackPacketSize,
                    feedbackInterval,
                    continuousFeedback
                )
            }.getOrDefault(0L)
            if (nativeHandle == 0L) {
                Log.w(TAG, "nativeUsbAudioCreate returned 0; USB direct output unavailable")
            }
        }
    }

    val isReady: Boolean get() = nativeHandle != 0L
    val isRunning: Boolean get() = nativeHandle != 0L && nativeUsbAudioIsRunning(nativeHandle)
    val framesWritten: Long get() = if (nativeHandle != 0L) nativeUsbAudioFramesWritten(nativeHandle) else 0L

    fun setAltSetting(altSetting: Int): Boolean {
        val h = nativeHandle
        if (h == 0L) return false
        return nativeUsbAudioSetAltSetting(h, altSetting)
    }

    fun setSampleRate(sampleRateHz: Int): Boolean {
        val h = nativeHandle
        if (h == 0L) return false
        return nativeUsbAudioSetSampleRate(h, sampleRateHz)
    }

    fun start(): Boolean {
        val h = nativeHandle
        if (h == 0L) return false
        return nativeUsbAudioStart(h)
    }

    fun writeFloat(samples: FloatArray) {
        val h = nativeHandle
        if (h != 0L) nativeUsbAudioWrite(h, samples)
    }

    fun writeRaw(pcm: ByteArray, inputBitDepth: Int) {
        val h = nativeHandle
        if (h != 0L) nativeUsbAudioWriteRaw(h, pcm, inputBitDepth)
    }

    fun stop() {
        val h = nativeHandle
        if (h != 0L) nativeUsbAudioStop(h)
    }

    fun drainUrbs(): Int {
        val h = nativeHandle
        return if (h != 0L) nativeUsbAudioDrainUrbs(h) else 0
    }

    override fun close() {
        val h = nativeHandle
        if (h != 0L) {
            nativeHandle = 0L
            nativeUsbAudioDestroy(h)
        }
    }

    private external fun nativeUsbAudioCreate(
        fd: Int,
        ifId: Int,
        epOut: Int,
        epFb: Int,
        rate: Int,
        ch: Int,
        bits: Int,
        maxPkt: Int,
        dataInterval: Int,
        feedbackPacketSize: Int,
        feedbackInterval: Int,
        continuousFeedback: Boolean
    ): Long

    private external fun nativeUsbAudioSetAltSetting(handle: Long, altSetting: Int): Boolean
    private external fun nativeUsbAudioSetSampleRate(handle: Long, sampleRateHz: Int): Boolean
    private external fun nativeUsbAudioStart(handle: Long): Boolean
    private external fun nativeUsbAudioWrite(handle: Long, pcm: FloatArray)
    private external fun nativeUsbAudioWriteRaw(handle: Long, pcm: ByteArray, inputBitDepth: Int)
    private external fun nativeUsbAudioStop(handle: Long)
    private external fun nativeUsbAudioDrainUrbs(handle: Long): Int
    private external fun nativeUsbAudioDestroy(handle: Long)
    private external fun nativeUsbAudioIsRunning(handle: Long): Boolean
    private external fun nativeUsbAudioFramesWritten(handle: Long): Long
}
