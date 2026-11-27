/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.peq

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.DeviceAutoEqMap
import chromahub.rhythm.app.util.AutoEQManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Automatically switches Rhythm's Double-precision Parametric EQ / AutoEQ target curve whenever
 * the active audio output route changes (Bluetooth headset, USB DAC, wired headset, or speaker),
 * consulting [DeviceAutoEqMap] and [AppSettings].
 */
object AutoEqController {

    private const val TAG = "AutoEqController"
    private val applyMutex = Mutex()

    fun resolveDeviceKey(devices: Array<out AudioDeviceInfo>): String {
        val btDevice = devices.firstOrNull {
            it.isSink && (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
                it.type == AudioDeviceInfo.TYPE_HEARING_AID)
        }
        if (btDevice != null) {
            val label = btDevice.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "Bluetooth"
            return "bt:$label"
        }

        val usbDevice = devices.firstOrNull {
            it.isSink && (it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY)
        }
        if (usbDevice != null) {
            val label = usbDevice.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "DAC"
            return "usb:$label"
        }

        val wiredDevice = devices.firstOrNull {
            it.isSink && (it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && it.type == AudioDeviceInfo.TYPE_USB_HEADSET))
        }
        if (wiredDevice != null) return "wired"

        return "speaker"
    }

    fun resolveActiveDeviceKey(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "speaker"
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "speaker"
        val outputs = runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }
            .getOrNull() ?: emptyArray()
        return resolveDeviceKey(outputs)
    }

    suspend fun syncForActiveOutputDevice(
        context: Context,
        appSettings: AppSettings
    ): String? = applyMutex.withLock {
        val deviceKey = resolveActiveDeviceKey(context)
        val mapJson = appSettings.presetsPrefs.deviceAutoEqMapJson.value
        val boundProfileName = DeviceAutoEqMap.profileForDevice(mapJson, deviceKey)
            ?: return@withLock null

        val activeId = appSettings.presetsPrefs.autoEqProfileId.value
        if (boundProfileName.equals(activeId, ignoreCase = true)) {
            return@withLock boundProfileName
        }

        val manager = AutoEQManager(context.applicationContext)
        val loaded = withContext(Dispatchers.IO) {
            manager.loadProfiles()
            manager.findProfileByName(boundProfileName)
                ?: manager.searchProfiles(boundProfileName).firstOrNull()
        } ?: return@withLock null

        val bassDb = appSettings.presetsPrefs.peqBassGainDb.value.toDouble()
        val tiltDb = appSettings.presetsPrefs.peqTiltDbPerOctave.value.toDouble()
        val peq = withContext(Dispatchers.Default) {
            manager.fitParametricEq(
                profile = loaded,
                bassGainDb = bassDb,
                tiltDbPerOctave = tiltDb
            )
        }
        appSettings.setAutoEQProfile(loaded.name)
        appSettings.presetsPrefs.setAutoEqProfileId(loaded.name)
        appSettings.presetsPrefs.setParametricEqJson(PeqSettings.toJson(peq))
        Log.d(TAG, "Auto-switched AutoEQ profile for '$deviceKey' -> '${loaded.name}'")
        return@withLock loaded.name
    }
}
