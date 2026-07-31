package chromahub.rhythm.app.util

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import android.os.Handler
import android.os.Looper
import java.lang.reflect.Method

/**
 * Monitor audio device changes
 * Detects when audio output devices connect/disconnect
 */
class AudioCapabilitiesMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioCapabilitiesMonitor"

        /** Returns the active A2DP/SCO device name, or null for non-Bluetooth output. */
        fun activeBluetoothOutputName(audioManager: AudioManager): String? {
            return try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    @Suppress("DEPRECATION")
                    return if (audioManager.isBluetoothA2dpOn) "Bluetooth" else null
                }

                val device = audioManager
                    .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .firstOrNull {
                        it.isSink && (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
                    } ?: return null

                // Some devices report the handset model as the sink product name.
                val remoteName = try {
                    device.address
                        .takeIf { it.contains(':') }
                        ?.let {
                            @Suppress("DEPRECATION")
                            BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(it)?.name
                        }
                } catch (e: SecurityException) {
                    null // BLUETOOTH_CONNECT not granted
                } catch (e: IllegalArgumentException) {
                    null // not a valid MAC
                }

                val productName = device.productName?.toString()?.trim()
                    ?.takeIf { it.isNotBlank() && !it.equals(Build.MODEL, ignoreCase = true) }

                remoteName?.trim()?.takeIf { it.isNotBlank() }
                    ?: productName
                    ?: "Bluetooth"
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve active Bluetooth output name", e)
                null
            }
        }
    }
    
    interface Listener {
        fun onAudioDeviceChanged(deviceType: String)
    }
    
    private var listener: Listener? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    when (state) {
                        0 -> {
                            Log.d(TAG, "Headset unplugged")
                            listener?.onAudioDeviceChanged("Headset disconnected")
                        }
                        1 -> {
                            Log.d(TAG, "Headset plugged in")
                            listener?.onAudioDeviceChanged("Headset connected")
                        }
                    }
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    Log.d(TAG, "Audio becoming noisy (headphones disconnected)")
                    listener?.onAudioDeviceChanged("Audio device disconnected")
                }
            }
        }
    }

    private var audioDeviceCallback: Any? = null
    private var registerMethod: Method? = null
    private var unregisterMethod: Method? = null
    
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                // Use reflection to create callback and get methods
                val callbackClass = Class.forName("android.media.AudioManager\$AudioDeviceCallback")
                val audioManagerClass = AudioManager::class.java
                
                registerMethod = audioManagerClass.getMethod("registerAudioDeviceCallback", callbackClass, Handler::class.java)
                unregisterMethod = audioManagerClass.getMethod("unregisterAudioDeviceCallback", callbackClass)
                
                audioDeviceCallback = createAudioDeviceCallbackViaReflection()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize audio device callback via reflection", e)
            }
        }
    }
    
    private fun createAudioDeviceCallbackViaReflection(): Any? {
        return try {
            val callbackClass = Class.forName("android.media.AudioManager\$AudioDeviceCallback")
            
            // Create proxy using reflection
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass)
            ) { _, method, args ->
                when (method.name) {
                    "onAudioDevicesAdded" -> {
                        val devices = args?.get(0) as? Array<*> ?: return@newProxyInstance null
                        devices.forEach { device ->
                            if (device is AudioDeviceInfo) {
                                val deviceName = getDeviceNameViaReflection(device)
                                Log.d(TAG, "Audio device added: $deviceName")
                                listener?.onAudioDeviceChanged("$deviceName connected")
                            }
                        }
                    }
                    "onAudioDevicesRemoved" -> {
                        val devices = args?.get(0) as? Array<*> ?: return@newProxyInstance null
                        devices.forEach { device ->
                            if (device is AudioDeviceInfo) {
                                val deviceName = getDeviceNameViaReflection(device)
                                Log.d(TAG, "Audio device removed: $deviceName")
                                listener?.onAudioDeviceChanged("$deviceName disconnected")
                            }
                        }
                    }
                }
                null
            }
            proxy
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create audio device callback via reflection", e)
            null
        }
    }
    
    private fun getDeviceNameViaReflection(device: AudioDeviceInfo): String {
        return when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
            else -> "Audio Device"
        }
    }
    
    /**
     * Start monitoring audio device changes
     */
    fun startMonitoring(listener: Listener) {
        this.listener = listener
        Log.d(TAG, "Starting audio device monitoring")
        
        // Register broadcast receiver for headset plug events
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        context.registerReceiver(headsetReceiver, filter)
        
        // Register audio device callback for API 23+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            registerAudioDeviceCallbackCompat()
        }
    }
    
    @Suppress("NewApi")
    private fun registerAudioDeviceCallbackCompat() {
        try {
            registerMethod?.invoke(audioManager, audioDeviceCallback, null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register audio device callback", e)
        }
    }
    
    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        Log.d(TAG, "Stopping audio device monitoring")
        
        try {
            context.unregisterReceiver(headsetReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering headset receiver", e)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            unregisterAudioDeviceCallbackCompat()
        }
        
        listener = null
    }
    
    @Suppress("NewApi")
    private fun unregisterAudioDeviceCallbackCompat() {
        try {
            unregisterMethod?.invoke(audioManager, audioDeviceCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister audio device callback", e)
        }
    }
}
