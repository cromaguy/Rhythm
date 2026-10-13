// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.UUID

/** One recorder owned by the playback service; UI lifecycles never determine whether a play is logged. */
class ListeningRecorder(private val context: Context, private val songOf: (MediaItem) -> Song?) : Player.Listener {
    private var player: Player? = null
    private var song: Song? = null
    private var playId = UUID.randomUUID().toString()
    private var segmentId: String? = null
    private var startWall = 0L
    private var startElapsed = 0L
    private var startPosition = 0L
    private var speed = 1f
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val records = Channel<ListeningRecord>(Channel.UNLIMITED)
    private val timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    init {
        ioScope.launch {
            val ledger = ListeningLedger.get(context)
            var recovered = false
            while (!recovered) try { ledger.recover(); recovered = true } catch (_: Exception) { ledger.storageIssue("write_failed"); delay(2_000) }
            for (record in records) {
                // Preserve failed writes in memory and retry; do not silently drop a record.
                var saved = false
                while (!saved) try { ledger.record(record); ledger.storageIssue(null); saved = true }
                catch (_: Exception) { ledger.storageIssue("write_failed"); delay(2_000) }
            }
        }
        timerScope.launch { while (isActive) { delay(10_000); checkpoint("checkpoint") } }
    }
    fun attach(next: Player) {
        finish("player_swap")
        player?.removeListener(this)
        player = next; next.addListener(this)
        val nextSong = next.currentMediaItem?.let(songOf)
        if (song?.id != nextSong?.id) { song = nextSong; playId = UUID.randomUUID().toString() }
        if (next.isPlaying) start()
    }
    private fun start() {
        val p = player ?: return
        if (segmentId != null) return
        if (song == null) song = p.currentMediaItem?.let(songOf)
        if (song == null) return
        segmentId = UUID.randomUUID().toString(); startWall = System.currentTimeMillis()
        startElapsed = SystemClock.elapsedRealtime(); startPosition = p.currentPosition.coerceAtLeast(0)
        speed = p.playbackParameters.speed
    }
    private fun checkpoint(reason: String, completed: Boolean = false, endPosition: Long? = null) {
        val p = player ?: return; val s = song ?: return; val id = segmentId ?: return
        val elapsed = (SystemClock.elapsedRealtime() - startElapsed).coerceAtLeast(0)
        if (elapsed == 0L) return
        val settings = AppSettings.getInstance(context)
        val outputs = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.distinct()
        val key = if (s.uri.scheme in listOf("http", "https")) s.uri.buildUpon().clearQuery().fragment(null).build().toString() else s.uri.toString()
        records.trySend(ListeningRecord(id, playId, "segment", s.id, key, s.title, s.artist, s.album, s.genre,
            startWall, startWall + elapsed, elapsed, s.duration, startPosition, (endPosition ?: p.currentPosition).coerceAtLeast(0), speed,
            reason, completed, s.dateAdded, outputTypes = outputs, repeatMode = p.repeatMode,
            shuffled = p.shuffleModeEnabled, volume = p.volume, favorite = settings.favoriteSongs.value?.let { json -> runCatching { com.google.gson.JsonParser.parseString(json).asJsonArray.any { it.asString == s.id } }.getOrDefault(false) } ?: false,
            albumArtist = s.albumArtist, libraryModifiedAt = s.dateModified, codec = s.codec, sampleRate = s.sampleRate, channels = s.channels, bitrate = s.bitrate, year = s.year, trackNumber = s.trackNumber, discNumber = s.discNumber))
    }
    private fun finish(reason: String, completed: Boolean = false, endPosition: Long? = null) { checkpoint(reason, completed, endPosition); segmentId = null }
    private fun action(reason: String, position: Long = player?.currentPosition ?: 0) {
        val s = song ?: return; val p = player ?: return; val now = System.currentTimeMillis()
        records.trySend(ListeningRecord(UUID.randomUUID().toString(), playId, "action", s.id, (if (s.uri.scheme in listOf("http", "https")) s.uri.buildUpon().clearQuery().fragment(null).build().toString() else s.uri.toString()), s.title, s.artist, s.album, s.genre,
            now, now, 0, s.duration, position.coerceAtLeast(0), p.currentPosition.coerceAtLeast(0), p.playbackParameters.speed, reason,
            libraryAddedAt = s.dateAdded, repeatMode = p.repeatMode, shuffled = p.shuffleModeEnabled, volume = p.volume))
    }
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) { start(); action("resume") } else { val reason = if (player?.playbackState == Player.STATE_BUFFERING && player?.playWhenReady == true) "buffer" else "pause"; finish(reason); action(reason) }
    }
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val next = mediaItem?.let(songOf)
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && next?.id == song?.id) { song = next; return }
        val completed = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        finish(if (completed) "completed" else "skip_or_queue_change", completed, if (completed) song?.duration else null)
        action(if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) "repeat" else if (completed) "next_auto" else "track_change")
        song = next; playId = UUID.randomUUID().toString()
        if (player?.isPlaying == true) start()
    }
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK && oldPosition.mediaItem?.mediaId == newPosition.mediaItem?.mediaId) {
            finish("seek", endPosition = oldPosition.positionMs); action("seek", oldPosition.positionMs)
            if (player?.isPlaying == true) start()
        }
    }
    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) { finish("completed", true); action("ended") }
    }
    override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
        finish("speed_change"); action("speed_change"); if (player?.isPlaying == true) start()
    }
    override fun onRepeatModeChanged(repeatMode: Int) { action("repeat_mode_change") }
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { action("shuffle_change") }
    override fun onVolumeChanged(volume: Float) { action("volume_change") }
    fun close() { finish("service_stop"); player?.removeListener(this); timerScope.cancel(); records.close() }
}
