/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioDeviceClassificationTest {

    @Test
    fun classifyDeviceType_handlesUsbBluetoothWiredAndSpeaker() {
        assertEquals("Speaker", AudioDeviceManager.classifyDeviceType(AudioDeviceManager.DEVICE_SPEAKER))
        assertEquals("Wired Headset", AudioDeviceManager.classifyDeviceType(AudioDeviceManager.DEVICE_WIRED_HEADSET))
        assertEquals("Bluetooth", AudioDeviceManager.classifyDeviceType("${AudioDeviceManager.DEVICE_BLUETOOTH_PREFIX}sony_wh1000xm5"))
        assertEquals("USB Audio", AudioDeviceManager.classifyDeviceType("${AudioDeviceManager.DEVICE_USB_PREFIX}fiio_ka13"))
        assertEquals("Unknown", AudioDeviceManager.classifyDeviceType("other_output"))
    }
}
