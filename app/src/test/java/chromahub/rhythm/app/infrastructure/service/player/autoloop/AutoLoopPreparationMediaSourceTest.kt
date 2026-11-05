package chromahub.rhythm.app.infrastructure.service.player.autoloop

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultAllocator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoLoopPreparationMediaSourceTest {
    @Before fun enable() {
        AutoLoopState.setEnabled(false)
        AutoLoopState.windowState.value = null
        AutoLoopState.setEnabled(true)
    }

    @After fun reset() {
        AutoLoopState.setEnabled(false)
        AutoLoopState.windowState.value = null
    }

    private class Fake(val item: MediaItem = MediaItem.Builder().setMediaId("first").build()) {
        var created = 0
        var released = 0
        val prepared = CompletableDeferred<Unit>()
        private val period = Proxy.newProxyInstance(MediaPeriod::class.java.classLoader,
            arrayOf(MediaPeriod::class.java)) { _, method, args ->
            when (method.name) {
                "prepare" -> {
                    (args!![0] as MediaPeriod.Callback).onPrepared(periodReference!!)
                    null
                }
                "setUsesStreamPrerollFlags", "maybeThrowPrepareError" -> null
                else -> error("Unexpected period call: ${method.name}")
            }
        } as MediaPeriod
        private var periodReference: MediaPeriod? = period
        val source = Proxy.newProxyInstance(MediaSource::class.java.classLoader,
            arrayOf(MediaSource::class.java)) { _, method, _ ->
            when (method.name) {
                "getMediaItem" -> item
                "createPeriod" -> { created++; period }
                "releasePeriod" -> { released++; null }
                "maybeThrowSourceInfoRefreshError" -> null
                else -> error("Unexpected source call: ${method.name}")
            }
        } as MediaSource

        fun prepare(source: MediaSource): MediaPeriod =
            source.createPeriod(MediaSource.MediaPeriodId("period"), DefaultAllocator(true, 1024), 0L).also {
                it.prepare(object : MediaPeriod.Callback {
                    override fun onPrepared(mediaPeriod: MediaPeriod) { prepared.complete(Unit) }
                    override fun onContinueLoadingRequested(source: MediaPeriod) = Unit
                }, 0L)
            }
    }

    @Test fun newTrackDoesNotPrepareUntilAnalysisAllowsFallbackPlayback() = runBlocking {
        val fake = Fake()
        val source = AutoLoopPreparationMediaSource(fake.source, Dispatchers.Unconfined)
        val pending = fake.prepare(source)
        try {
            assertEquals(0, fake.created)
            assertFalse(fake.prepared.isCompleted)
            AutoLoopState.analysisFallbackKey.value = AutoLoopPreparation.key(fake.item)
            withTimeout(1000) { fake.prepared.await() }
            assertEquals(1, fake.created)
        } finally {
            source.releasePeriod(pending)
        }
        assertEquals(1, fake.released)
    }

    @Test fun disablingAutomaticLoopReleasesTheWaitingTrack() = runBlocking {
        val fake = Fake()
        val source = AutoLoopPreparationMediaSource(fake.source, Dispatchers.Unconfined)
        val pending = fake.prepare(source)
        try {
            assertEquals(0, fake.created)
            AutoLoopState.setEnabled(false)
            withTimeout(1000) { fake.prepared.await() }
            assertEquals(1, fake.created)
        } finally {
            source.releasePeriod(pending)
        }
    }

    @Test fun skippingAwayCancelsPreparationEvenIfOldAnalysisFinishesLater() {
        val fake = Fake()
        val source = AutoLoopPreparationMediaSource(fake.source, Dispatchers.Unconfined)
        val pending = fake.prepare(source)
        source.releasePeriod(pending)
        AutoLoopState.analysisFallbackKey.value = AutoLoopPreparation.key(fake.item)
        AutoLoopState.setEnabled(false)
        assertEquals(0, fake.created)
        assertEquals(0, fake.released)
        assertFalse(fake.prepared.isCompleted)
    }

    @Test fun analysisForAnotherTrackCannotReleaseThisTrack() {
        val fake = Fake()
        val source = AutoLoopPreparationMediaSource(fake.source, Dispatchers.Unconfined)
        val pending = fake.prepare(source)
        try {
            AutoLoopState.analysisFallbackKey.value = AutoLoopPreparation.key(
                MediaItem.Builder().setMediaId("second").build())
            assertEquals(0, fake.created)
            assertFalse(fake.prepared.isCompleted)
        } finally {
            source.releasePeriod(pending)
        }
    }

    @Test fun configuredLoopBypassesTheHoldButOriginalTrackStillWaits() {
        val item = MediaItem.Builder().setMediaId("first").build()
        val clip = item.buildUpon().setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setEndPositionMs(8000L).build()).build()
        AutoLoopState.windowState.value = AutoLoopWindow("first", 2000L, 8000L, 12000L)
        assertFalse(AutoLoopPreparation.shouldWait(clip))
        assertTrue(AutoLoopPreparation.shouldWait(item))
    }

    @Test fun ordinaryPlaybackPreparesImmediatelyWhenAutomaticLoopIsOff() {
        AutoLoopState.setEnabled(false)
        val fake = Fake()
        val source = AutoLoopPreparationMediaSource(fake.source, Dispatchers.Unconfined)
        val period = fake.prepare(source)
        assertEquals(1, fake.created)
        assertTrue(fake.prepared.isCompleted)
        source.releasePeriod(period)
        assertEquals(1, fake.released)
    }
}
