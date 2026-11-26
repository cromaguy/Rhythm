/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.nativeaudio

import android.annotation.SuppressLint
import android.os.Build
import android.os.Environment

/**
 * Device firmware and security policy guard preventing unsafe dynamic native library loads on
 * incompatible vendor kernels (e.g., Tecno SPARK Go 2024 Android 13) or GrapheneOS hardened
 * DCL-restricted configurations.
 */
object NativeAudioSafetyGuard {

    @SuppressLint("PrivateApi")
    fun canLoadNativeLibrary(): Boolean {
        if (isJvmHost()) return false
        if (isTecnoRestrictedDevice(Build.VERSION.SDK_INT, Build.BRAND, Build.PRODUCT)) {
            return false
        }
        return !isMemoryDclRestricted()
    }

    internal fun isTecnoRestrictedDevice(sdkInt: Int, brand: String?, product: String?): Boolean {
        return sdkInt == 33 &&
            brand.equals("TECNO", ignoreCase = true) &&
            (product?.startsWith("BG6-") == true)
    }

    @SuppressLint("PrivateApi")
    private fun isMemoryDclRestricted(): Boolean {
        return try {
            val method = Environment::class.java.getMethod("isMemoryDclRestricted")
            (method.invoke(null) as? Boolean) == true
        } catch (_: Throwable) {
            false
        }
    }

    private fun isJvmHost(): Boolean {
        val vmName = System.getProperty("java.vm.name").orEmpty()
        val runtime = System.getProperty("java.runtime.name").orEmpty()
        return !vmName.contains("Dalvik", ignoreCase = true) &&
            !runtime.contains("Android", ignoreCase = true)
    }
}
