package chromahub.rhythm.app.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import chromahub.rhythm.app.shared.data.model.Song

/**
 * StatusBroadcaster handles broadcasting playback status to third-party apps
 * like Tasker, KWGT (Kustom Widget), Zooper, and other automation tools.
 * 
 * This implements the standard Android music player broadcast protocol that
 * many automation and widget apps rely on to detect what's playing.
 * 
 * Broadcasts the com.android.music.metachanged and com.android.music.playstatechanged
 * intents which are the de-facto standard for music player integration on Android.
 * 
 * Compatible with:
 * - Tasker (automation)
 * - KWGT/KLWP (Kustom widgets)
 * - Zooper Widget
 * - AutoApps
 * - MacroDroid
 * - Any app listening for standard music player intents
 */
class StatusBroadcaster(private val context: Context) {
    
    companion object {
        private const val TAG = "StatusBroadcaster"
        
        // Standard Android music player broadcast actions
        // These are widely supported by automation and widget apps
        private const val ACTION_META_CHANGED = "com.android.music.metachanged"
        private const val ACTION_PLAYSTATE_CHANGED = "com.android.music.playstatechanged"
        
        // Standard extras used by automation apps
        private const val EXTRA_ID = "id"
        private const val EXTRA_ARTIST = "artist"
        private const val EXTRA_ALBUM = "album"
        private const val EXTRA_TRACK = "track"
        private const val EXTRA_DURATION = "duration"
        private const val EXTRA_POSITION = "position"
        private const val EXTRA_PLAYING = "playing"
        
        // Additional extras for enhanced compatibility
        private const val EXTRA_LIST_SIZE = "ListSize"
        private const val EXTRA_LIST_POSITION = "ListPosition"

        
        private const val PACKAGE_NAME = "chromahub.rhythm.app"
    }
    
    /**
     * Broadcast track metadata change
     * This should be called when the track changes
     */
    fun broadcastMetadataChanged(
        song: Song,
        position: Long = 0L,
        queueSize: Int = 0,
        queuePosition: Int = 0,
        bluetoothLyricsMode: Boolean = false,
        currentLyricLine: String? = null
    ) {
        try {
            val mergedTitleArtist = listOf(song.title, song.artist)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" - ")
                .ifBlank { song.title }
            val artistMetadata = if (bluetoothLyricsMode) mergedTitleArtist else song.artist
            val trackMetadata = if (bluetoothLyricsMode) {
                currentLyricLine?.takeIf { it.isNotBlank() } ?: song.title
            } else {
                song.title
            }

            val intent = Intent(ACTION_META_CHANGED).apply {
                // Standard metadata
                putExtra(EXTRA_ID, song.id.hashCode().toLong())
                putExtra(EXTRA_ARTIST, artistMetadata)
                putExtra(EXTRA_ALBUM, song.album)
                putExtra(EXTRA_TRACK, trackMetadata)
                putExtra(EXTRA_DURATION, song.duration)
                putExtra(EXTRA_POSITION, position)
                
                // Queue information for apps that support it
                if (queueSize > 0) {
                    putExtra(EXTRA_LIST_SIZE, queueSize)
                    putExtra(EXTRA_LIST_POSITION, queuePosition)
                }
                
                // Set package to prevent permission issues on newer Android versions
                setPackage(null) // Broadcast to all apps
            }
            
            context.sendBroadcast(intent)
            Log.d(
                TAG,
                "Broadcast metadata: artist='$artistMetadata', track='$trackMetadata', mode=${if (bluetoothLyricsMode) "bluetooth_lyrics" else "standard"}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting metadata change", e)
        }
    }
    
    /**
     * Broadcast playback state change
     * This should be called when play/pause state changes
     */
    fun broadcastPlaystateChanged(isPlaying: Boolean, position: Long = 0L) {
        try {
            val intent = Intent(ACTION_PLAYSTATE_CHANGED).apply {
                putExtra(EXTRA_PLAYING, isPlaying)
                putExtra(EXTRA_POSITION, position)
                setPackage(null) // Broadcast to all apps
            }
            
            context.sendBroadcast(intent)
            Log.d(TAG, "Broadcast playstate: playing=$isPlaying, position=$position")
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting playstate change", e)
        }
    }
    
    /**
     * Broadcast complete status update (metadata + playstate)
     * Use this when starting a new track
     */
    fun broadcastNowPlaying(
        song: Song,
        isPlaying: Boolean,
        position: Long = 0L,
        queueSize: Int = 0,
        queuePosition: Int = 0,
        bluetoothLyricsMode: Boolean = false,
        currentLyricLine: String? = null
    ) {
        broadcastMetadataChanged(
            song = song,
            position = position,
            queueSize = queueSize,
            queuePosition = queuePosition,
            bluetoothLyricsMode = bluetoothLyricsMode,
            currentLyricLine = currentLyricLine
        )
        broadcastPlaystateChanged(isPlaying, position)
    }
}
