/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import chromahub.rhythm.app.shared.data.model.PlaybackLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import android.Manifest
import android.content.pm.PackageManager
import android.bluetooth.BluetoothClass
import android.content.Intent
import android.provider.Settings

/**
 * Utility class to manage audio output devices
 */
class AudioDeviceManager(private val context: Context) {
    private val TAG = "AudioDeviceManager"
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    
    private val bluetoothAdapter = bluetoothManager?.adapter
    
    // Device lists
    private val _availableDevices = MutableStateFlow<List<PlaybackLocation>>(emptyList())
    val availableDevices: StateFlow<List<PlaybackLocation>> = _availableDevices.asStateFlow()
    
    private val _currentDevice = MutableStateFlow<PlaybackLocation?>(null)
    val currentDevice: StateFlow<PlaybackLocation?> = _currentDevice.asStateFlow()
    
    // Device reconnect detection
    private val _deviceReconnected = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
    val deviceReconnected: kotlinx.coroutines.flow.SharedFlow<String> = _deviceReconnected.asSharedFlow()
    private val recentlyDisconnectedDeviceTypes = mutableSetOf<Int>() // AudioDeviceInfo type IDs
    
    // Store the audio focus request for later abandonment
    private var focusRequest: android.media.AudioFocusRequest? = null
    
    // Default device IDs
    companion object {
        const val DEVICE_SPEAKER = "speaker"
        const val DEVICE_WIRED_HEADSET = "wired_headset"
        const val DEVICE_BLUETOOTH_PREFIX = "bt_"
        const val DEVICE_USB_PREFIX = "usb_"
    }
    
    // Add a flag to track if the device was manually selected by the user
    private var isManuallySelected = false
    
    // Receiver for audio becoming noisy events
    private val audioNoisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.d(TAG, "Audio becoming noisy - refreshing devices")
                // When audio routing changes (e.g., headphones unplugged), refresh devices
                refreshDevices()
            }
        }
    }
    
    init {
        refreshDevices()
        
        // Register for audio becoming noisy broadcasts (e.g., headphones unplugged)
        try {
            val filter = android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            context.registerReceiver(audioNoisyReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering audio becoming noisy receiver: ${e.message}", e)
        }
        
        // Register AudioDeviceCallback for reactive device change detection (API 23+)
        audioManager.registerAudioDeviceCallback(object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                addedDevices?.let { devices ->
                    for (device in devices) {
                        if (device.isSink && recentlyDisconnectedDeviceTypes.remove(device.type)) {
                            val deviceName = when (device.type) {
                                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
                                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired"
                                else -> "Audio"
                            }
                            Log.d(TAG, "Device reconnected: $deviceName (type=${device.type})")
                            _deviceReconnected.tryEmit(deviceName)
                        }
                    }
                }
                refreshDevices()
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                removedDevices?.let { devices ->
                    for (device in devices) {
                        if (device.isSink && device.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                            recentlyDisconnectedDeviceTypes.add(device.type)
                            Log.d(TAG, "Device disconnected: type=${device.type}")
                        }
                    }
                }
                // Clear stale entries after 5 minutes
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    recentlyDisconnectedDeviceTypes.clear()
                }, 5 * 60 * 1000L)
                refreshDevices()
            }
        }, android.os.Handler(android.os.Looper.getMainLooper()))
    }
    
    /**
     * Show the native Android media output switcher
     * On Android 11+ this shows the native MediaOutputDialog (same as Quick Settings)
     * On older versions, falls back to Sound Settings
     */
    fun showOutputSwitcherDialog() {
        try {
            // Use the new OutputSwitcherHelper which handles native dialog on Android 11+
            val success = OutputSwitcherHelper.showOutputSwitcher(context)
            if (!success) {
                Log.w(TAG, "OutputSwitcherHelper failed, trying fallback intent approach")
                showOutputSwitcherViaIntent()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing output switcher dialog: ${e.message}", e)
            // Fall back to the intent approach
            showOutputSwitcherViaIntent()
        }
    }
    
    /**
     * Show the output switcher via intent
     * This is an alternative approach that may work on some devices
     */
    private fun showOutputSwitcherViaIntent() {
        try {
            // Try to use the system's floating output switcher dialog
            val intent = Intent("android.settings.MEDIA_OUTPUT")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Log.d(TAG, "Showing output switcher via floating dialog intent")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing floating output switcher: ${e.message}", e)
            
            // Try alternative intents
            val fallbackIntents = listOf(
                Intent("com.android.settings.panel.MediaOutputPanel"),
                Intent("android.settings.panel.MediaOutputPanel"),
                Intent("android.settings.MEDIA_OUTPUT_SETTINGS"),
                Intent("android.settings.SOUND_SETTINGS"),
                Intent(Settings.ACTION_SOUND_SETTINGS)
            )
            
            for (fallbackIntent in fallbackIntents) {
                try {
                    fallbackIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(fallbackIntent)
                    Log.d(TAG, "Showing output switcher via fallback intent: ${fallbackIntent.action}")
                    return
                } catch (e2: Exception) {
                    Log.e(TAG, "Error showing fallback intent ${fallbackIntent.action}: ${e2.message}")
                }
            }
            
            Log.e(TAG, "All output switcher fallback intents failed")
        }
    }
    
    /**
     * Refresh the list of available audio devices
     */
    fun refreshDevices() {
        try {
            val devices = mutableListOf<PlaybackLocation>()
            
            // Always add speaker
            devices.add(
                PlaybackLocation(
                    id = DEVICE_SPEAKER,
                    name = "Phone Speaker",
                    icon = 0 // Use appropriate icon
                )
            )
            
            // Check for wired headset
            try {
                if (isWiredHeadsetConnected()) {
                    devices.add(
                        PlaybackLocation(
                            id = DEVICE_WIRED_HEADSET,
                            name = "Wired Headphones",
                            icon = 0 // Use appropriate icon
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking wired headset status: ${e.message}", e)
            }
            
            // Add Bluetooth devices
            try {
                val bluetoothDevices = getConnectedBluetoothDevices()
                if (bluetoothDevices.isNotEmpty()) {
                    bluetoothDevices.forEach { (name, address) ->
                        devices.add(
                            PlaybackLocation(
                                id = "${DEVICE_BLUETOOTH_PREFIX}$address",
                                name = name,
                                icon = 0 // Use appropriate icon
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting Bluetooth devices: ${e.message}", e)
            }

            try {
                val usbDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
                    (it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET) && it.isSink
                }
                for (usb in usbDevices) {
                    val name = usb.productName.toString().takeIf { it.isNotBlank() } ?: "USB DAC / Audio"
                    devices.add(
                        PlaybackLocation(
                            id = "${DEVICE_USB_PREFIX}${usb.id}",
                            name = name,
                            icon = 0
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking USB audio devices: ${e.message}", e)
            }
            
            // Update the available devices - only if changed to avoid unnecessary UI updates
            val currentDevices = _availableDevices.value
            val devicesChanged = devices.size != currentDevices.size || 
                !devices.map { it.id }.containsAll(currentDevices.map { it.id }) ||
                !currentDevices.map { it.id }.containsAll(devices.map { it.id })
                
            if (devicesChanged) {
                _availableDevices.value = devices
                
                // Handle current device selection
                updateCurrentDeviceAfterRefresh(devices)
            } else {
                // Even if the device list hasn't changed, we should check if the active device has changed
                if (!isManuallySelected) {
                    detectActiveDevice(devices)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing audio devices: ${e.message}", e)
        }
    }
    
    /**
     * Update current device selection after refresh
     */
    private fun updateCurrentDeviceAfterRefresh(devices: List<PlaybackLocation>) {
        try {
            // Current device selection logic
            val currentDeviceId = _currentDevice.value?.id
            
            // Check if current device is still available
            val deviceStillAvailable = currentDeviceId != null && 
                                      devices.any { it.id == currentDeviceId }
            
            if (!deviceStillAvailable) {
                // Select a new device based on priority: Bluetooth > Wired > Speaker
                val bluetoothDevice = devices.find { it.id.startsWith(DEVICE_BLUETOOTH_PREFIX) }
                val wiredDevice = devices.find { it.id == DEVICE_WIRED_HEADSET }
                val speakerDevice = devices.find { it.id == DEVICE_SPEAKER }
                
                val newDevice = bluetoothDevice ?: wiredDevice ?: speakerDevice
                
                if (newDevice != null) {
                    _currentDevice.value = newDevice
                    Log.d(TAG, "Previous device disconnected, switching to: ${newDevice.name}")
                } else {
                    _currentDevice.value = null
                    Log.w(TAG, "No audio devices available")
                }
            } else if (deviceStillAvailable) {
                // If the current device is still available, detect which one is actually active
                detectActiveDevice(devices)
            } else if (_currentDevice.value == null && devices.isNotEmpty()) {
                // If no device is selected but devices are available, select one
                val bluetoothDevice = devices.find { it.id.startsWith(DEVICE_BLUETOOTH_PREFIX) }
                val wiredDevice = devices.find { it.id == DEVICE_WIRED_HEADSET }
                val speakerDevice = devices.find { it.id == DEVICE_SPEAKER }
                
                val newDevice = bluetoothDevice ?: wiredDevice ?: speakerDevice
                
                if (newDevice != null) {
                    _currentDevice.value = newDevice
                    Log.d(TAG, "No device was selected, selecting: ${newDevice.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating current device: ${e.message}", e)
            // Fallback to speaker if available
            val speakerDevice = devices.find { it.id == DEVICE_SPEAKER }
            if (speakerDevice != null) {
                _currentDevice.value = speakerDevice
                Log.d(TAG, "Fallback to speaker after error")
            }
        }
    }
    
    /**
     * Check if a wired headset is connected
     */
    private fun isWiredHeadsetConnected(): Boolean {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        }
    }
    
    /**
     * Detect which audio device is actually active
     */
    private fun detectActiveDevice(devices: List<PlaybackLocation>) {
        try {
            // If the device was manually selected by the user, respect that choice
            // but reset the flag after 10 seconds to allow auto-detection again
            if (isManuallySelected) {
                Log.d(TAG, "Skipping auto-detection because device was manually selected")
                
                // Reset the manual selection flag after 10 seconds
                // In a real implementation, you would use a Handler or coroutine with delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "Resetting manual selection flag")
                    isManuallySelected = false
                }, 10000) // 10 seconds
                
                return
            }
            
            val isUsbActive = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                (it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET) && it.isSink
            }

            // First check if Bluetooth is active
            val isBluetoothActive = isBluetoothActive()
            
            // Then check if wired headset is connected
            val isWiredHeadsetActive = isWiredHeadsetConnected()
            
            // Determine the active device based on the checks
            val activeDevice = when {
                isUsbActive -> {
                    devices.find { it.id.startsWith(DEVICE_USB_PREFIX) }
                }
                isBluetoothActive -> {
                    // Try to find the actually connected Bluetooth device
                    findActiveBluetoothDevice(devices) ?: 
                    // Fallback to any Bluetooth device in our list
                    devices.find { it.id.startsWith(DEVICE_BLUETOOTH_PREFIX) }
                }
                isWiredHeadsetActive -> {
                    // Find the wired headset device
                    devices.find { it.id == DEVICE_WIRED_HEADSET }
                }
                else -> {
                    // Default to speaker
                    devices.find { it.id == DEVICE_SPEAKER }
                }
            }
            
            // Update current device if it's different from what we detected
            activeDevice?.let { device ->
                if (_currentDevice.value?.id != device.id) {
                    Log.d(TAG, "Detected active device change: ${_currentDevice.value?.name ?: "None"} -> ${device.name}")
                    _currentDevice.value = device
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting active device: ${e.message}", e)
        }
    }
    
    /**
     * Set the current audio output device
     */
    fun setCurrentDevice(device: PlaybackLocation) {
        Log.d(TAG, "Setting current device to: ${device.name}")
        
        // Update the current device in the UI
        _currentDevice.value = device
        
        // Mark as manually selected by the user
        isManuallySelected = true
        
        // Show the system output switcher dialog instead of manually handling audio routing
        showOutputSwitcherDialog()
        
        val deviceType = when {
            device.id == DEVICE_SPEAKER -> "Speaker"
            device.id == DEVICE_WIRED_HEADSET -> "Wired Headset"
            device.id.startsWith(DEVICE_BLUETOOTH_PREFIX) -> "Bluetooth"
            else -> "Unknown"
        }
        
        Log.d(TAG, "Audio output selection initiated for $deviceType: ${device.name}")
    }
    
    /**
     * Force audio routing to the selected device
     */
    private fun forceAudioRouting(device: PlaybackLocation) {
        try {
            Log.d(TAG, "Forcing audio routing to: ${device.name}")
            
            // First, stop any existing Bluetooth SCO connection
            stopBluetoothSco()
            
            // Set audio mode to normal initially
            audioManager.mode = AudioManager.MODE_NORMAL
            
            // For some devices, we need to temporarily disable all audio outputs
            // to force the system to re-evaluate audio routing
            try {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
                Thread.sleep(50)
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting audio outputs: ${e.message}", e)
            }
            
            when {
                device.id.startsWith(DEVICE_BLUETOOTH_PREFIX) -> {
                    // For Bluetooth devices, we need to route audio to Bluetooth
                    
                    // First ensure Bluetooth is the preferred device
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        audioManager.clearCommunicationDevice()
                    }
                    
                    // Try to force audio mode changes which can help with routing
                    try {
                        // Some devices need this sequence to properly switch to Bluetooth
                        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                        Thread.sleep(100)
                        audioManager.mode = AudioManager.MODE_NORMAL
                        Thread.sleep(100)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error toggling audio mode: ${e.message}", e)
                    }
                    
                    // Turn off speakerphone explicitly
                    setSpeakerphoneOn(false)
                    
                    // Route audio to the specific Bluetooth device
                    val success = routeAudioToBluetooth(device.id)
                    
                    // If routing failed, try alternative approach with more aggressive mode changes
                    if (!success) {
                        Log.d(TAG, "Initial Bluetooth routing failed, trying alternative approaches")
                        
                        // Try different audio modes in sequence
                        val modes = arrayOf(
                            AudioManager.MODE_NORMAL,
                            AudioManager.MODE_IN_COMMUNICATION,
                            AudioManager.MODE_IN_CALL
                        )
                        
                        for (mode in modes) {
                            try {
                                audioManager.mode = mode
                                Thread.sleep(150)
                                
                                // Try routing again with this mode
                                if (routeAudioToBluetooth(device.id)) {
                                    Log.d(TAG, "Successfully routed to Bluetooth using mode: $mode")
                                    break
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error using audio mode $mode: ${e.message}", e)
                            }
                        }
                        
                        // As a last resort for older devices, try to restart the audio system
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            try {
                                // Temporarily mute and unmute audio
                                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                                Thread.sleep(100)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)
                                
                                // Try toggling speaker to force audio routing change
                                setSpeakerphoneOn(true)
                                Thread.sleep(100)
                                setSpeakerphoneOn(false)
                                Thread.sleep(100)
                                
                                // Try routing one more time
                                routeAudioToBluetooth(device.id)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in last resort Bluetooth routing: ${e.message}", e)
                            }
                        }
                    }
                    
                    // Request audio focus to ensure our app controls audio routing
                    requestAudioFocus()
                    
                    Log.d(TAG, "Bluetooth route requested without changing user volume")
                }
                device.id == DEVICE_WIRED_HEADSET -> {
                    // For wired headset, we need to ensure it's the active device
                    
                    // Make sure speakerphone is off
                    setSpeakerphoneOn(false)
                    
                    // Try to force audio mode changes which can help with routing
                    try {
                        // Some devices need this sequence to properly switch to wired headset
                        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                        Thread.sleep(100)
                        audioManager.mode = AudioManager.MODE_NORMAL
                        Thread.sleep(100)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error toggling audio mode: ${e.message}", e)
                    }
                    
                    // On Android 12+, explicitly set the wired headset as the communication device
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try {
                            val devices = audioManager.availableCommunicationDevices
                            val wiredDevice = devices.find { 
                                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                            }
                            
                            if (wiredDevice != null) {
                                // Try multiple times with different modes if needed
                                var success = false
                                val modes = arrayOf(
                                    AudioManager.MODE_NORMAL,
                                    AudioManager.MODE_IN_COMMUNICATION
                                )
                                
                                for (mode in modes) {
                                    try {
                                        audioManager.mode = mode
                                        Thread.sleep(100)
                                        
                                        val result = audioManager.setCommunicationDevice(wiredDevice)
                                        Log.d(TAG, "Set wired headset as communication device with mode $mode: $result")
                                        
                                        if (result) {
                                            success = true
                                            break
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error setting wired headset with mode $mode: ${e.message}", e)
                                    }
                                }
                                
                                if (!success) {
                                    Log.d(TAG, "Failed to set wired headset as communication device, trying alternative approach")
                                    
                                    // Try to force audio routing by toggling speaker
                                    try {
                                        setSpeakerphoneOn(true)
                                        Thread.sleep(100)
                                        setSpeakerphoneOn(false)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error toggling speaker: ${e.message}", e)
                                    }
                                }
                            } else {
                                // If no specific wired device is found, clear the communication device
                                // to let the system route audio to the wired headset automatically
                                audioManager.clearCommunicationDevice()
                                Log.d(TAG, "Cleared communication device to default to wired headset")
                                
                                // Try to force audio routing by toggling audio mode
                                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                                Thread.sleep(100)
                                audioManager.mode = AudioManager.MODE_NORMAL
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error setting wired headset as communication device: ${e.message}", e)
                        }
                    } else {
                        // For older Android versions, try toggling audio mode to force re-routing
                        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                        setSpeakerphoneOn(false)
                        
                        // Small delay to allow system to process the change
                        Thread.sleep(100)
                        
                        audioManager.mode = AudioManager.MODE_NORMAL
                        
                        // For some older devices, we need to temporarily enable speaker and then disable it
                        try {
                            @Suppress("DEPRECATION")
                            audioManager.isSpeakerphoneOn = true
                            Thread.sleep(100)
                            @Suppress("DEPRECATION")
                            audioManager.isSpeakerphoneOn = false
                        } catch (e: Exception) {
                            Log.e(TAG, "Error toggling speaker: ${e.message}", e)
                        }
                    }
                    
                    // Request audio focus
                    requestAudioFocus()
                    
                    Log.d(TAG, "Wired headset route requested without changing user volume")
                }
                device.id == DEVICE_SPEAKER -> {
                    // For speaker, ensure speakerphone is on
                    
                    // Try different audio modes to ensure speaker works
                    val modes = arrayOf(
                        AudioManager.MODE_NORMAL,
                        AudioManager.MODE_IN_COMMUNICATION
                    )
                    
                    var speakerActivated = false
                    
                    for (mode in modes) {
                        if (speakerActivated) break
                        
                        try {
                            audioManager.mode = mode
                            Thread.sleep(100)
                            
                            // For Android 12+, use communication device APIs
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                try {
                                    // Find speaker device
                                    val devices = audioManager.availableCommunicationDevices
                                    val speakerDevice = devices.find { 
                                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                                    }
                                    
                                    if (speakerDevice != null) {
                                        val result = audioManager.setCommunicationDevice(speakerDevice)
                                        Log.d(TAG, "Set communication device to speaker with mode $mode: $result")
                                        
                                        if (result) {
                                            speakerActivated = true
                                        }
                                    } else {
                                        // If no specific speaker device is found, use the deprecated method
                                        setSpeakerphoneOn(true)
                                        speakerActivated = true
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error setting speaker as communication device: ${e.message}", e)
                                    // Fall back to deprecated method
                                    setSpeakerphoneOn(true)
                                    speakerActivated = true
                                }
                            } else {
                                // For older versions, use the deprecated method
                                setSpeakerphoneOn(true)
                                speakerActivated = true
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error activating speaker with mode $mode: ${e.message}", e)
                        }
                    }
                    
                    // If all else failed, try one more time with the deprecated method
                    if (!speakerActivated) {
                        try {
                            @Suppress("DEPRECATION")
                            audioManager.isSpeakerphoneOn = true
                            Log.d(TAG, "Forced speaker on with deprecated method")
                        } catch (e: Exception) {
                            Log.e(TAG, "Final speaker activation attempt failed: ${e.message}", e)
                        }
                    }
                    
                    // Request audio focus
                    requestAudioFocus()
                    
                    Log.d(TAG, "Speaker route requested without changing user volume")
                }
            }
            
            // Broadcast an intent to notify other apps about the audio route change
            try {
                val intent = android.content.Intent(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error broadcasting audio route change: ${e.message}", e)
            }
            
            // Log the final state
            Log.d(TAG, "Audio routing complete. Selected device: ${device.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error routing audio to device: ${e.message}", e)
        }
    }
    
    /**
     * Request audio focus to ensure our app has control of audio routing
     * @return true if audio focus was granted
     */
    private fun requestAudioFocus(): Boolean {
        try {
            var focusGranted = false
            
            // For Android 8.0+, use the newer audio focus API
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            
            // Create a focus change listener to handle focus changes
            val focusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                        Log.d(TAG, "Audio focus gained")
                        // We have full focus now
                    }
                    android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                        Log.d(TAG, "Audio focus lost")
                        // We've lost focus, but we'll handle this elsewhere
                    }
                    android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        Log.d(TAG, "Audio focus lost temporarily")
                        // We've lost focus temporarily
                    }
                }
            }
            
            val focusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            
            val result = audioManager.requestAudioFocus(focusRequest)
            Log.d(TAG, "Audio focus request result: $result")
            
            focusGranted = result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            
            // Store the focus request for later abandonment
            if (focusGranted) {
                this.focusRequest = focusRequest
            }
            
            return focusGranted
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Stop Bluetooth SCO audio connection
     */
    private fun stopBluetoothSco() {
        try {
            Log.d(TAG, "Attempting to stop Bluetooth SCO")
            
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothScoOn) {
                Log.d(TAG, "Stopping active Bluetooth SCO connection")
                
                try {
                    @Suppress("DEPRECATION")
                    audioManager.stopBluetoothSco()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping Bluetooth SCO: ${e.message}", e)
                }
                
                try {
                    @Suppress("DEPRECATION")
                    audioManager.isBluetoothScoOn = false
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting BluetoothScoOn to false: ${e.message}", e)
                }
                
                // Wait a moment to ensure SCO is fully stopped
                Thread.sleep(50)
                
                Log.d(TAG, "Bluetooth SCO stopped")
            }
            
            // Removed communicationDevice/clearCommunicationDevice usage for compatibility
        } catch (e: Exception) {
            Log.e(TAG, "Error in stopBluetoothSco: ${e.message}", e)
        }
    }
    
    /**
     * Try to route audio to a specific Bluetooth device
     * @return true if routing was successful, false otherwise
     */
    private fun routeAudioToBluetooth(deviceId: String): Boolean {
        try {
            // Set audio mode to normal
            audioManager.mode = AudioManager.MODE_NORMAL
            
            // Turn off speakerphone
            setSpeakerphoneOn(false)
            
            // Track if we successfully routed audio
            var routingSuccess = false
            
            // Try to force a Bluetooth A2DP route using the public (deprecated) API
            try {
                @Suppress("DEPRECATION")
                audioManager.setBluetoothA2dpOn(false)
                Thread.sleep(100)
                @Suppress("DEPRECATION")
                audioManager.setBluetoothA2dpOn(true)
                
                Log.d(TAG, "Successfully used setBluetoothA2dpOn")
                routingSuccess = true
            } catch (e: Exception) {
                // This is expected to fail on most devices due to security restrictions
                Log.d(TAG, "Could not set Bluetooth A2DP: ${e.message}")
            }
            
            // On Android 12+, we can use the communication device APIs
            if (!routingSuccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    // Try to set communication device to the specific Bluetooth device
                    val devices = audioManager.availableCommunicationDevices
                    
                    // Extract the device name from the ID to help with matching
                    val deviceNameFromId = deviceId.substringAfter("bt_")
                        .replace("_", " ")
                        .replace("active", "")
                        .replace("audio", "")
                        .trim()
                    
                    // Try to find the specific Bluetooth device
                    val bluetoothDevice = devices.find { device -> 
                        (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                         device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) &&
                        device.isSink &&
                        (device.productName?.toString()?.lowercase()?.contains(deviceNameFromId) == true ||
                         deviceNameFromId.contains(device.productName?.toString()?.lowercase() ?: ""))
                    } ?: devices.find { 
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    }
                    
                    if (bluetoothDevice != null) {
                        // Try multiple times with different audio modes if needed
                        var attempts = 0
                        var result = false
                        
                        while (!result && attempts < 3) {
                            when (attempts) {
                                0 -> audioManager.mode = AudioManager.MODE_NORMAL
                                1 -> audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                                2 -> audioManager.mode = AudioManager.MODE_IN_CALL
                            }
                            
                            // Small delay to allow mode change to take effect
                            Thread.sleep(50)
                            
                            // Try to set the communication device
                            result = audioManager.setCommunicationDevice(bluetoothDevice)
                            Log.d(TAG, "Set communication device to Bluetooth (attempt ${attempts+1}): $result (${bluetoothDevice.productName})")
                            
                            if (result) {
                                routingSuccess = true
                                break
                            }
                            
                            attempts++
                        }
                        
                        // If we couldn't set the device, try to force it by setting audio attributes
                        if (!routingSuccess) {
                            try {
                                // Create a media player to force routing
                                val mediaPlayer = android.media.MediaPlayer()
                                
                                // Set audio attributes with Bluetooth routing preference
                                val audioAttributes = android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                                
                                mediaPlayer.setAudioAttributes(audioAttributes)
                                
                                // Try to start and immediately stop the player
                                // This sometimes helps establish the Bluetooth route
                                try {
                                    mediaPlayer.prepare()
                                    mediaPlayer.start()
                                    Thread.sleep(50)
                                    mediaPlayer.stop()
                                } catch (e: Exception) {
                                    // This is expected as we don't have a data source
                                    Log.d(TAG, "Expected MediaPlayer error: ${e.message}")
                                } finally {
                                    mediaPlayer.release()
                                }
                                
                                // Try one more time to set the communication device
                                result = audioManager.setCommunicationDevice(bluetoothDevice)
                                if (result) {
                                    Log.d(TAG, "Set communication device after MediaPlayer attempt")
                                    routingSuccess = true
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error with MediaPlayer routing attempt: ${e.message}", e)
                            }
                        }
                    } else {
                        Log.d(TAG, "No matching Bluetooth communication device found")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting communication device: ${e.message}", e)
                }
            }
            
            // If we couldn't use the communication device API or it failed, try SCO
            if (!routingSuccess) {
                try {
                    // Start Bluetooth SCO
                    startBluetoothSco()
                    
                    // Wait a moment for SCO to connect
                    Thread.sleep(300)  // Longer wait time for better chance of connection
                    
                    // Check if SCO is active
                    @Suppress("DEPRECATION")
                    if (audioManager.isBluetoothScoOn) {
                        Log.d(TAG, "Successfully started Bluetooth SCO")
                        routingSuccess = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting Bluetooth SCO: ${e.message}", e)
                }
            }
            
            // For older Android versions, try to connect to the specific device using reflection
            if (!routingSuccess && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                try {
                    // Extract the device address from the ID
                    val deviceAddress = deviceId.substringAfter("bt_")
                    
                    // Get the BluetoothAdapter
                    val adapter = bluetoothAdapter ?: return false
                    
                    // Try to get the BluetoothDevice using reflection
                    val getDeviceMethod = adapter.javaClass.getMethod("getRemoteDevice", String::class.java)
                    val device = getDeviceMethod.invoke(adapter, deviceAddress)
                    
                    if (device != null) {
                        // Try to connect to the device using reflection
                        val connectMethod = device.javaClass.getMethod("connect")
                        connectMethod.invoke(device)
                        Log.d(TAG, "Connected to Bluetooth device using reflection: $deviceAddress")
                        routingSuccess = true
                        
                        // Give it a moment to connect
                        Thread.sleep(300)
                    }
                } catch (e: Exception) {
                    // This is expected to fail on many devices due to security restrictions
                    Log.d(TAG, "Could not connect to specific Bluetooth device using reflection: ${e.message}")
                }
            }
            
            // As a last resort, try setting the audio mode to MODE_IN_CALL which often forces Bluetooth routing
            if (!routingSuccess) {
                try {
                    // Try a sequence of mode changes that sometimes helps
                    val modes = arrayOf(
                        AudioManager.MODE_IN_CALL,
                        AudioManager.MODE_IN_COMMUNICATION,
                        AudioManager.MODE_NORMAL
                    )
                    
                    for (mode in modes) {
                        audioManager.mode = mode
                        Thread.sleep(150)
                        
                        // Check if Bluetooth is now active
                        if (isBluetoothActive()) {
                            Log.d(TAG, "Forced Bluetooth routing by setting audio mode to $mode")
                            routingSuccess = true
                            break
                        }
                    }
                    
                    // If still not successful, try one more trick: toggle airplane mode via Settings
                    if (!routingSuccess && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        try {
                            // This requires WRITE_SECURE_SETTINGS permission which most apps don't have
                            // But it sometimes works in development environments
                            val contentResolver = context.contentResolver
                            
                            // Get current airplane mode state
                            val isAirplaneModeOn = android.provider.Settings.Global.getInt(
                                contentResolver,
                                android.provider.Settings.Global.AIRPLANE_MODE_ON, 0
                            ) != 0
                            
                            // Toggle airplane mode
                            android.provider.Settings.Global.putInt(
                                contentResolver,
                                android.provider.Settings.Global.AIRPLANE_MODE_ON,
                                if (isAirplaneModeOn) 0 else 1
                            )
                            
                            // Broadcast intent to notify about airplane mode change
                            val intent = android.content.Intent(android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED)
                            intent.putExtra("state", !isAirplaneModeOn)
                            context.sendBroadcast(intent)
                            
                            // Wait a moment
                            Thread.sleep(1000)
                            
                            // Toggle back
                            android.provider.Settings.Global.putInt(
                                contentResolver,
                                android.provider.Settings.Global.AIRPLANE_MODE_ON,
                                if (isAirplaneModeOn) 1 else 0
                            )
                            
                            // Broadcast intent again
                            val intent2 = android.content.Intent(android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED)
                            intent2.putExtra("state", isAirplaneModeOn)
                            context.sendBroadcast(intent2)
                            
                            // Wait for Bluetooth to reconnect
                            Thread.sleep(2000)
                            
                            // Check if Bluetooth is now active
                            if (isBluetoothActive()) {
                                Log.d(TAG, "Forced Bluetooth routing by toggling airplane mode")
                                routingSuccess = true
                            }
                        } catch (e: Exception) {
                            // This is expected to fail due to permission issues
                            Log.d(TAG, "Could not toggle airplane mode: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error forcing Bluetooth routing: ${e.message}", e)
                }
            }
            
            return routingSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error routing audio to Bluetooth: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Set speakerphone on/off using the appropriate method for the Android version
     */
    private fun setSpeakerphoneOn(on: Boolean) {
        try {
            Log.d(TAG, "Setting speakerphone: $on")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // For Android 12+, use communication device APIs if available
                try {
                    if (on) {
                        // Find speaker device
                        val devices = audioManager.availableCommunicationDevices
                        val speakerDevice = devices.find { 
                            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                        }
                        
                        if (speakerDevice != null) {
                            val result = audioManager.setCommunicationDevice(speakerDevice)
                            Log.d(TAG, "Set communication device to speaker: $result")
                            
                            if (!result) {
                                // Fall back to deprecated method if setting communication device fails
                                @Suppress("DEPRECATION")
                                audioManager.isSpeakerphoneOn = on
                                Log.d(TAG, "Fallback to deprecated method for speaker: $on")
                            }
                        } else {
                            // Fall back to deprecated method
                            @Suppress("DEPRECATION")
                            audioManager.isSpeakerphoneOn = on
                            Log.d(TAG, "No speaker device found, using deprecated method: $on")
                        }
                    } else {
                        // When turning speaker off, we need to choose another device or clear
                        
                        // First check if wired headset is connected
                        if (isWiredHeadsetConnected()) {
                            val devices = audioManager.availableCommunicationDevices
                            val wiredDevice = devices.find { 
                                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                            }
                            
                            if (wiredDevice != null) {
                                val result = audioManager.setCommunicationDevice(wiredDevice)
                                Log.d(TAG, "Set communication device to wired headset: $result")
                            } else {
                                audioManager.clearCommunicationDevice()
                                Log.d(TAG, "Cleared communication device when turning speaker off")
                            }
                        } else if (isBluetoothActive()) {
                            // Try to find a Bluetooth device
                            val devices = audioManager.availableCommunicationDevices
                            val btDevice = devices.find { 
                                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                            }
                            
                            if (btDevice != null) {
                                val result = audioManager.setCommunicationDevice(btDevice)
                                Log.d(TAG, "Set communication device to Bluetooth: $result")
                            } else {
                                audioManager.clearCommunicationDevice()
                                Log.d(TAG, "Cleared communication device when turning speaker off")
                            }
                        } else {
                            // Just clear the communication device
                            audioManager.clearCommunicationDevice()
                            
                            // Also ensure speakerphone is off using deprecated method as backup
                            @Suppress("DEPRECATION")
                            audioManager.isSpeakerphoneOn = false
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting communication device: ${e.message}", e)
                    // Fall back to deprecated method
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = on
                    Log.d(TAG, "Exception fallback to deprecated method: $on")
                }
            } else {
                // For older versions, use the deprecated method
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = on
                Log.d(TAG, "Using deprecated speakerphone method: $on")
                
                // For some devices, we need to set the audio mode appropriately
                if (on) {
                    // Some devices require normal mode for speaker
                    audioManager.mode = AudioManager.MODE_NORMAL
                } else if (isBluetoothActive()) {
                    // For Bluetooth, sometimes MODE_IN_COMMUNICATION works better
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting speakerphone: ${e.message}", e)
            try {
                // Last resort fallback
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = on
            } catch (e2: Exception) {
                Log.e(TAG, "Error in fallback speakerphone setting: ${e2.message}", e2)
            }
        }
    }
    
    /**
     * Start Bluetooth SCO audio connection with appropriate handling for deprecation
     * @return true if SCO was started successfully
     */
    @Suppress("DEPRECATION")
    private fun startBluetoothSco(): Boolean {
        try {
            // First check if Bluetooth is enabled and connected
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                Log.d(TAG, "Bluetooth not enabled, can't start SCO")
                return false
            }
            
            // Check for required permissions
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // For Android 12+, we need BLUETOOTH_CONNECT permission
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                // For older Android versions, check BLUETOOTH permission
                context.checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
            }
            
            if (!hasPermission) {
                Log.w(TAG, "Missing required Bluetooth permissions")
                return false
            }
            
            // Check if any Bluetooth A2DP device is connected
            val isBluetoothConnected = try {
                if (hasPermission) {
                    // Use BluetoothAdapter constants instead of BluetoothProfile constants
                    bluetoothAdapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED ||
                    bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking Bluetooth profile state: ${e.message}", e)
                false
            }
            
            if (!isBluetoothConnected) {
                Log.d(TAG, "No Bluetooth audio device connected, can't start SCO")
                return false
            }
            
            // Register a receiver to detect when SCO connection state changes
            val scoReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                    if (intent?.action == android.media.AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
                        val state = intent.getIntExtra(
                            android.media.AudioManager.EXTRA_SCO_AUDIO_STATE,
                            android.media.AudioManager.SCO_AUDIO_STATE_ERROR
                        )
                        
                        when (state) {
                            android.media.AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                                Log.d(TAG, "Bluetooth SCO connected")
                                try {
                                    @Suppress("DEPRECATION")
                                    audioManager.isBluetoothScoOn = true
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error setting BluetoothScoOn: ${e.message}", e)
                                }
                            }
                            android.media.AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                                Log.d(TAG, "Bluetooth SCO disconnected")
                            }
                            android.media.AudioManager.SCO_AUDIO_STATE_ERROR -> {
                                Log.e(TAG, "Bluetooth SCO error")
                            }
                        }
                    }
                }
            }
            
            // Register the receiver
            try {
                val filter = android.content.IntentFilter(android.media.AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
                context.registerReceiver(scoReceiver, filter)
                
                // Unregister after 5 seconds to avoid leaks
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        context.unregisterReceiver(scoReceiver)
                        Log.d(TAG, "Unregistered SCO state receiver")
                    } catch (e: Exception) {
                        // Receiver may already be unregistered
                    }
                }, 5000)
            } catch (e: Exception) {
                Log.e(TAG, "Error registering SCO state receiver: ${e.message}", e)
            }
            
            // Start Bluetooth SCO audio connection
            @Suppress("DEPRECATION")
            if (!audioManager.isBluetoothScoOn) {
                try {
                    // Set audio mode to support SCO
                    audioManager.mode = AudioManager.MODE_NORMAL
                    
                    // Start Bluetooth SCO
                    @Suppress("DEPRECATION")
                    audioManager.startBluetoothSco()
                    
                    // Wait a bit to see if SCO connects
                    var attempts = 0
                    while (attempts < 5) {
                        Thread.sleep(100)
                        @Suppress("DEPRECATION")
                        if (audioManager.isBluetoothScoOn) {
                            Log.d(TAG, "Bluetooth SCO started successfully")
                            return true
                        }
                        attempts++
                    }
                    
                    // Try with different audio mode if still not connected
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    
                    @Suppress("DEPRECATION")
                    audioManager.startBluetoothSco()
                    @Suppress("DEPRECATION")
                    audioManager.isBluetoothScoOn = true
                    
                    Log.d(TAG, "Started Bluetooth SCO with MODE_IN_COMMUNICATION")
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting Bluetooth SCO: ${e.message}", e)
                    return false
                }
            } else {
                Log.d(TAG, "Bluetooth SCO already on")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Bluetooth SCO: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Get a list of connected Bluetooth audio devices
     * @return List of device name and address pairs
     */
    private fun getConnectedBluetoothDevices(): List<Pair<String, String>> {
        val devices = mutableListOf<Pair<String, String>>()
        
        try {
            // Check if Bluetooth is enabled
            if (bluetoothAdapter == null) {
                Log.d(TAG, "Bluetooth adapter is null")
                return emptyList()
            }
            
            if (!bluetoothAdapter.isEnabled) {
                Log.d(TAG, "Bluetooth is not enabled")
                return emptyList()
            }
            
            // Check for required permissions
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // For Android 12+, we need BLUETOOTH_CONNECT permission
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                // For older Android versions, check BLUETOOTH permission
                context.checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
            }
            
            if (!hasPermission) {
                Log.w(TAG, "Missing required Bluetooth permissions")
                return emptyList()
            }
            
            // Set to track device names to avoid duplicates
            val deviceNameSet = mutableSetOf<String>()
            
            // Get active Bluetooth devices from AudioManager first (more reliable for currently active devices)
            try {
                val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val activeBluetoothDevices = audioDevices.filter { 
                    (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                     it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) && 
                    it.isSink
                }
                
                for (device in activeBluetoothDevices) {
                    val deviceName = device.productName?.toString() ?: "Bluetooth Audio"
                    
                    // Skip if we already have a similar device name
                    if (isDuplicateDeviceName(deviceName, deviceNameSet)) {
                        Log.d(TAG, "Skipping duplicate active Bluetooth device: $deviceName")
                        continue
                    }
                    
                    // Add to our tracking set
                    deviceNameSet.add(deviceName.lowercase())
                    
                    // Create a unique ID that includes "active" to prioritize this device
                    val deviceId = "${DEVICE_BLUETOOTH_PREFIX}active_${deviceName.replace(" ", "_").lowercase()}"
                    
                    devices.add(Pair(deviceName, deviceId))
                    Log.d(TAG, "Found active Bluetooth device via AudioManager: $deviceName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting active audio devices from AudioManager: ${e.message}", e)
            }
            
            // Get bonded devices safely
            val bondedDevices = try {
                bluetoothAdapter.bondedDevices ?: emptySet()
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception accessing bonded devices", e)
                emptySet()
            } catch (e: Exception) {
                Log.e(TAG, "Error accessing bonded devices: ${e.message}", e)
                emptySet()
            }
            
            // Process bonded devices
            for (device in bondedDevices) {
                try {
                    // Extract device information safely
                    val deviceName = try {
                        device.name ?: "Unknown Device"
                    } catch (e: SecurityException) {
                        "Unknown Device"
                    }
                    
                    val deviceAddress = try {
                        device.address
                    } catch (e: SecurityException) {
                        // Generate a unique ID if we can't get the address
                        "unknown_${deviceName.hashCode()}"
                    }
                    
                    // Check if this device is already in our list (from AudioManager)
                    if (isDuplicateDeviceName(deviceName, deviceNameSet)) {
                        Log.d(TAG, "Skipping duplicate Bluetooth device: $deviceName")
                        continue
                    }
                    
                    // Add to our tracking set
                    deviceNameSet.add(deviceName.lowercase())
                    
                    // Check if it's an audio device (A2DP)
                    val deviceClass = try {
                        device.bluetoothClass?.majorDeviceClass
                    } catch (e: SecurityException) {
                        null
                    }
                    
                    if (deviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO) {
                        devices.add(Pair(deviceName, "${DEVICE_BLUETOOTH_PREFIX}${deviceAddress}"))
                        Log.d(TAG, "Found Bluetooth audio device: $deviceName")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing Bluetooth device: ${e.message}", e)
                }
            }
            
            // Alternative approach using AudioManager (Android 6.0+) if we haven't found any devices yet
            if (devices.isEmpty()) {
                try {
                    val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    for (device in audioDevices) {
                        if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                            // For Bluetooth devices from AudioManager, we don't have the address
                            // So we'll use a unique identifier based on the product name
                            val deviceName = device.productName?.toString() ?: "Bluetooth Audio"
                            
                            // Skip if we already have a similar device name
                            if (isDuplicateDeviceName(deviceName, deviceNameSet)) {
                                continue
                            }
                            
                            // Add to our tracking set
                            deviceNameSet.add(deviceName.lowercase())
                            
                            val deviceId = "${DEVICE_BLUETOOTH_PREFIX}audio_${deviceName.replace(" ", "_").lowercase()}"
                            devices.add(Pair(deviceName, deviceId))
                            Log.d(TAG, "Found Bluetooth audio device via AudioManager: $deviceName")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting audio devices from AudioManager: ${e.message}", e)
                }
            }
            
            if (devices.isEmpty()) {
                Log.d(TAG, "No Bluetooth audio devices found")
            } else {
                Log.d(TAG, "Found ${devices.size} Bluetooth devices: ${devices.map { it.first }}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Bluetooth devices: ${e.message}", e)
        }
        
        return devices
    }
    
    /**
     * Check if a device name is a duplicate of an existing device
     */
    private fun isDuplicateDeviceName(deviceName: String, existingNames: Set<String>): Boolean {
        val normalizedName = deviceName.lowercase()
        
        // Check for exact match
        if (existingNames.contains(normalizedName)) {
            return true
        }
        
        // Check for similar names (e.g., "My Headphones" vs "My Headphones (LE)")
        for (existingName in existingNames) {
            // If one name contains the other, consider it a duplicate
            if (normalizedName.contains(existingName) || existingName.contains(normalizedName)) {
                return true
            }
            
            // Check for high similarity using Levenshtein distance
            if (existingName.length > 4 && normalizedName.length > 4) {
                val similarity = calculateSimilarity(normalizedName, existingName)
                if (similarity > 0.7) { // 70% similarity threshold
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * Calculate similarity between two strings (0-1 where 1 is identical)
     */
    private fun calculateSimilarity(s1: String, s2: String): Double {
        val longerLength = maxOf(s1.length, s2.length)
        if (longerLength == 0) {
            return 1.0 // Both strings are empty
        }
        
        // Calculate Levenshtein distance
        val distance = levenshteinDistance(s1, s2)
        
        // Convert to similarity score
        return (longerLength - distance) / longerLength.toDouble()
    }
    
    /**
     * Calculate Levenshtein distance between two strings
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        
        // Create a matrix of size (m+1) x (n+1)
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        // Initialize the first row and column
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        
        // Fill the matrix
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return dp[m][n]
    }
    
    /**
     * Check if audio is currently playing through a Bluetooth device
     */
    fun isBluetoothActive(): Boolean {
        try {
            // For Android 6.0+ we can check the active audio devices
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            
            // Check if any Bluetooth device is active
            val isBluetoothActive = devices.any { 
                (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                 it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) && 
                it.isSink
            }
            
            Log.d(TAG, "Bluetooth active check (API ${Build.VERSION.SDK_INT}): $isBluetoothActive")
            return isBluetoothActive
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth active status: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Find the specific Bluetooth device that is currently active
     */
    private fun findActiveBluetoothDevice(devices: List<PlaybackLocation>): PlaybackLocation? {
        // Only proceed if we have Bluetooth devices in our list
        val bluetoothDevices = devices.filter { it.id.startsWith(DEVICE_BLUETOOTH_PREFIX) }
        if (bluetoothDevices.isEmpty()) {
            return null
        }
        
        try {
            // For Android 6.0+ we can check the active audio devices
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            
            // Find active Bluetooth devices
            val activeBluetoothDevices = audioDevices.filter { 
                (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                 it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) && 
                it.isSink
            }
            
            if (activeBluetoothDevices.isNotEmpty()) {
                // Get the name of the active Bluetooth device
                val activeDeviceName = activeBluetoothDevices.first().productName?.toString()
                
                if (activeDeviceName != null) {
                    Log.d(TAG, "Active Bluetooth device from AudioManager: $activeDeviceName")
                    
                    // Try to find a matching device in our list by name
                    val matchingDevice = bluetoothDevices.find { 
                        it.name.equals(activeDeviceName, ignoreCase = true) ||
                        it.name.contains(activeDeviceName, ignoreCase = true) ||
                        activeDeviceName.contains(it.name, ignoreCase = true)
                    }
                    
                    if (matchingDevice != null) {
                        Log.d(TAG, "Found matching Bluetooth device: ${matchingDevice.name}")
                        return matchingDevice
                    }
                }
            }
            
            // If we couldn't find a specific device or are on an older Android version
            // Try to get the connected device from the Bluetooth adapter
            bluetoothAdapter?.let { adapter ->
                if (adapter.isEnabled) {
                    // Check for required permissions
                    val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                    } else {
                        context.checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
                    }
                    
                    if (!hasPermission) {
                        Log.w(TAG, "Missing required Bluetooth permission in findActiveBluetoothDevice")
                        return bluetoothDevices.firstOrNull() // Return first as fallback
                    }
                    
                    try {
                        // Get the currently connected A2DP device
                        val a2dpConnected = adapter.getProfileConnectionState(BluetoothProfile.A2DP) == 
                                           BluetoothAdapter.STATE_CONNECTED
                        
                        if (a2dpConnected) {
                            // Get the most recently connected device as a best guess
                            val bondedDevices = adapter.bondedDevices?.toList() ?: emptyList()
                            
                            // Find devices that match our list of Bluetooth devices
                            for (device in bondedDevices) {
                                val deviceName = try { device.name ?: "" } catch (e: Exception) { "" }
                                val deviceAddress = try { device.address ?: "" } catch (e: Exception) { "" }
                                
                                // Try to match by address or name
                                val matchingDevice = bluetoothDevices.find { 
                                    it.id.endsWith(deviceAddress) || 
                                    it.name.equals(deviceName, ignoreCase = true)
                                }
                                
                                if (matchingDevice != null) {
                                    Log.d(TAG, "Found likely active Bluetooth device: ${matchingDevice.name}")
                                    return matchingDevice
                                }
                            }
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Security exception accessing Bluetooth devices", e)
                        return bluetoothDevices.firstOrNull() // Return first as fallback
                    } catch (e: Exception) {
                        Log.e(TAG, "Error finding active Bluetooth device from adapter", e)
                    }
                }
            }
            
            // If we still couldn't find a specific device, return the first one as a fallback
            return bluetoothDevices.firstOrNull()?.also {
                Log.d(TAG, "Using first available Bluetooth device as fallback: ${it.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding active Bluetooth device", e)
            return null
        }
    }
    
    // Clean up when no longer needed
    fun cleanup() {
        try {
            // Unregister broadcast receiver
            try {
                context.unregisterReceiver(audioNoisyReceiver)
                Log.d(TAG, "Unregistered audio becoming noisy receiver")
            } catch (e: Exception) {
                // Receiver may already be unregistered
                Log.e(TAG, "Error unregistering audio becoming noisy receiver: ${e.message}", e)
            }
            
            // Release audio focus
            try {
                // Use the stored focus request if available
                if (focusRequest != null) {
                    audioManager.abandonAudioFocusRequest(focusRequest!!)
                    Log.d(TAG, "Abandoned stored audio focus request")
                } else {
                    // Create a new request to abandon if we don't have one stored
                    val audioAttributes = android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                    
                    val tempFocusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(audioAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener { }
                        .build()
                    
                    audioManager.abandonAudioFocusRequest(tempFocusRequest)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error abandoning audio focus request: ${e.message}", e)
            }
            
            // Stop Bluetooth SCO if active
            stopBluetoothSco()
            
            // Reset audio mode to normal
            try {
                audioManager.mode = AudioManager.MODE_NORMAL
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting audio mode: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up AudioDeviceManager: ${e.message}", e)
        }
    }
} 
