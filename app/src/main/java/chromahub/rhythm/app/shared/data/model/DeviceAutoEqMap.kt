/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.data.model

import chromahub.rhythm.app.util.GsonUtils
import com.google.gson.reflect.TypeToken

/**
 * Serializes per-output-device AutoEQ profile bindings (keyed by stable AudioDeviceInfo descriptor
 * or transport bucket such as "bt:WH-1000XM5", "usb:DAC", "wired", "speaker") into JSON.
 */
object DeviceAutoEqMap {
    fun decode(json: String?): Map<String, String> {
        if (json.isNullOrBlank() || json.trim() == "{}") return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            GsonUtils.gson.fromJson<Map<String, String>>(json, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun encode(map: Map<String, String>): String = GsonUtils.gson.toJson(map)

    fun withBinding(json: String?, deviceKey: String, profileName: String?): String {
        val next = decode(json).toMutableMap()
        val normalizedKey = deviceKey.trim()
        if (normalizedKey.isEmpty()) return encode(next)
        if (profileName.isNullOrBlank() || profileName.equals("None", ignoreCase = true)) {
            next.remove(normalizedKey)
        } else {
            next[normalizedKey] = profileName.trim()
        }
        return encode(next)
    }

    fun profileForDevice(json: String?, deviceKey: String): String? {
        if (deviceKey.isBlank()) return null
        return decode(json)[deviceKey.trim()]
    }
}
