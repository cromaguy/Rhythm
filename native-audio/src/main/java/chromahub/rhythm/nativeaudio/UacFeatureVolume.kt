/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.nativeaudio

import android.hardware.usb.UsbDeviceConnection
import android.util.Log
import kotlin.math.roundToInt

/**
 * UAC1/UAC2 Feature Unit hardware volume controller. Verifies actual DAC attenuation by reading
 * back `GET_CUR` after `SET_CUR` so non-responsive dongles fail closed to software attenuation.
 */
class UacFeatureVolume(
    private val connection: UsbDeviceConnection,
    private val controlInterfaceId: Int
) {
    companion object {
        private const val TAG = "UacFeatureVolume"
        private const val REQUEST_SET_CUR = 0x01
        private const val REQUEST_GET_CUR = 0x81
        private const val REQUEST_GET_MIN = 0x82
        private const val REQUEST_GET_MAX = 0x83
        private const val REQUEST_GET_RES = 0x84
        private const val CS_MUTE = 0x01
        private const val CS_VOLUME = 0x02
        private const val USB_TYPE_CLASS_IFACE_OUT = 0x21
        private const val USB_TYPE_CLASS_IFACE_IN = 0xA1
        private const val CONTROL_TIMEOUT_MS = 100
        private val FALLBACK_UNIT_IDS = intArrayOf(1, 2, 3, 5, 6, 9, 10)
    }

    var available: Boolean = false
        private set

    var minRaw: Int = -16384
        private set
    var maxRaw: Int = 0
        private set
    var resRaw: Int = 256
        private set

    private var featureUnitId: Int = 0
    private var channels: IntArray = intArrayOf(0)

    fun attach(): Boolean {
        available = false
        for (unitId in FALLBACK_UNIT_IDS) {
            val found = discoverWritableChannels(unitId)
            if (found.isEmpty()) continue
            queryRange(unitId, found.first())
            val writable = found.filter { ch -> verifyWrite(unitId, ch) }.toIntArray()
            if (writable.isEmpty()) continue
            featureUnitId = unitId
            channels = writable
            available = true
            setMuted(false)
            Log.i(TAG, "Verified UAC Feature Unit 0x${unitId.toString(16)} ch=${writable.joinToString()}")
            return true
        }
        return false
    }

    fun setVolumeFraction(fraction: Float): Boolean {
        if (!available) return false
        val raw = fractionToRaw(fraction, minRaw, maxRaw, resRaw)
        var anyWritten = false
        for (ch in channels) {
            if (writeVolume(featureUnitId, ch, raw)) anyWritten = true
        }
        return anyWritten
    }

    fun setMuted(muted: Boolean): Boolean {
        val unit = if (featureUnitId > 0) featureUnitId else return false
        val buf = byteArrayOf(if (muted) 1 else 0)
        val res = connection.controlTransfer(
            USB_TYPE_CLASS_IFACE_OUT,
            REQUEST_SET_CUR,
            (CS_MUTE shl 8) or 0,
            (unit shl 8) or (controlInterfaceId and 0xFF),
            buf,
            1,
            CONTROL_TIMEOUT_MS
        )
        return res == 1
    }

    private fun discoverWritableChannels(unitId: Int): List<Int> {
        return intArrayOf(0, 1, 2).filter { ch -> readVolume(unitId, ch) != null }
    }

    private fun queryRange(unitId: Int, ch: Int) {
        minRaw = readControlWord(REQUEST_GET_MIN, unitId, ch) ?: -16384
        maxRaw = readControlWord(REQUEST_GET_MAX, unitId, ch) ?: 0
        resRaw = (readControlWord(REQUEST_GET_RES, unitId, ch) ?: 256).coerceAtLeast(1)
        if (maxRaw <= minRaw) {
            minRaw = -16384
            maxRaw = 0
        }
    }

    private fun verifyWrite(unitId: Int, ch: Int): Boolean {
        val initial = readVolume(unitId, ch) ?: return false
        val mid = ((minRaw + maxRaw) / 2)
        if (!writeVolume(unitId, ch, mid)) return false
        val after = readVolume(unitId, ch)
        writeVolume(unitId, ch, initial)
        return after != null && (after != initial || initial == mid)
    }

    private fun readVolume(unitId: Int, ch: Int): Int? =
        readControlWord(REQUEST_GET_CUR, unitId, ch)

    private fun readControlWord(request: Int, unitId: Int, ch: Int): Int? {
        val buf = ByteArray(2)
        val res = connection.controlTransfer(
            USB_TYPE_CLASS_IFACE_IN,
            request,
            (CS_VOLUME shl 8) or (ch and 0xFF),
            (unitId shl 8) or (controlInterfaceId and 0xFF),
            buf,
            2,
            CONTROL_TIMEOUT_MS
        )
        if (res != 2) return null
        return ((buf[1].toInt() shl 8) or (buf[0].toInt() and 0xFF))
    }

    private fun writeVolume(unitId: Int, ch: Int, raw: Int): Boolean {
        val buf = byteArrayOf((raw and 0xFF).toByte(), ((raw ushr 8) and 0xFF).toByte())
        val res = connection.controlTransfer(
            USB_TYPE_CLASS_IFACE_OUT,
            REQUEST_SET_CUR,
            (CS_VOLUME shl 8) or (ch and 0xFF),
            (unitId shl 8) or (controlInterfaceId and 0xFF),
            buf,
            2,
            CONTROL_TIMEOUT_MS
        )
        return res == 2
    }

    internal fun fractionToRaw(fraction: Float, min: Int, max: Int, step: Int): Int {
        val f = fraction.coerceIn(0f, 1f)
        val rawFloat = min.toFloat() + (max - min).toFloat() * f
        val quantizedSteps = ((rawFloat - min) / step.coerceAtLeast(1)).roundToInt()
        return (min + quantizedSteps * step.coerceAtLeast(1)).coerceIn(min, max)
    }
}
