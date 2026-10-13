// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.infrastructure.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.data.metadata.*
import chromahub.rhythm.app.features.local.di.LocalMusicModule
import chromahub.rhythm.app.shared.data.model.*
import chromahub.rhythm.app.core.domain.scan.LibraryFilterPolicy
import kr.co.metadata.crawler.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class LibraryEnrichmentWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val catalog = CatalogEnrichmentService.get(applicationContext)
        val options = catalog.preferences.options.value
        if (!catalog.enabled()) return@withContext Result.failure(workDataOf("message" to applicationContext.getString(R.string.catalog_integrations_off)))
        val repository = LocalMusicModule.provideMusicRepository(applicationContext)
        val settings = AppSettings.getInstance(applicationContext)
        val songs = repository.loadSongs().associateBy(catalog::songKey)
        val existingAlbums = repository.loadAlbums().associateBy { catalog.albumKey(it.title, it.artist) }
        val existingArtists = repository.loadArtists().associateBy { catalog.artistKey(it.name) }
        var plan = CatalogBatchPlan.read(applicationContext) ?: return@withContext Result.failure()
        val start = System.currentTimeMillis()
        val artists = mutableSetOf<String>()
        val albums = mutableSetOf<String>()
        setForeground(notification(plan))
        val lyricsStore = AutomaticLyricsStore.get(applicationContext)
        val writes = CatalogTagWrites(applicationContext)
        while (plan.next < plan.keys.size && !isStopped) {
            // Bounded segments allow WorkManager to resume very large libraries without a huge single run.
            if (System.currentTimeMillis() - start > 8 * 60_000 || plan.next % 25 == 0 && System.currentTimeMillis() - start > 60_000) {
                plan.save(applicationContext)
                // A successful checkpoint is not a failure: retries would grow to five-hour gaps.
                // APPEND also preserves cancellation of the current job and its dependent segments.
                enqueue(applicationContext, continuation = true)
                return@withContext Result.success(workDataOf("done" to plan.next, "total" to plan.keys.size))
            }
            val song = songs[plan.keys[plan.next]]
            val policy = LibraryFilterPolicy(settings.mediaScanMode.value, settings.blacklistedSongs.value,
                settings.blacklistedFolders.value, settings.whitelistedSongs.value, settings.whitelistedFolders.value)
            var changed = false
            var failed = false
            if (song != null && policy.includes(song.id, song.path ?: song.uri.path)) {
                try {
                    if (options.writeTags || options.embedLyrics) PersonalArchive(applicationContext).captureSong(song)
                    var updated = catalog.apply(song)
                    if (options.metadata) {
                        val wanted = buildSet {
                            if (CatalogIdentity.missing(updated.title) || options.replaces(CatalogField.TRACK_TITLE)) add(CatalogField.TRACK_TITLE)
                            if (CatalogIdentity.missing(updated.artist) || options.replaces(CatalogField.TRACK_ARTISTS)) add(CatalogField.TRACK_ARTISTS)
                            if (CatalogIdentity.missing(updated.album) || options.replaces(CatalogField.TRACK_ALBUM_TITLE)) add(CatalogField.TRACK_ALBUM_TITLE)
                            if (CatalogIdentity.missing(updated.genre) || options.replaces(CatalogField.TRACK_GENRES)) add(CatalogField.TRACK_GENRES)
                            if (updated.year <= 0 || options.replaces(CatalogField.TRACK_RELEASE_DATE)) add(CatalogField.TRACK_RELEASE_DATE)
                        }
                        if (wanted.isNotEmpty()) catalog.collect(EntityKind.TRACK, catalog.songKey(updated), updated.title, updated.artist, wanted, updated.duration, updated.album, replace = wanted.any(options::replaces))
                        updated = catalog.apply(updated, fillMissingManual = true)
                    }
                    if (options.artwork && options.replaces(CatalogField.TRACK_IMAGE)) catalog.collect(EntityKind.TRACK, catalog.songKey(updated), updated.title, updated.artist, setOf(CatalogField.TRACK_IMAGE), updated.duration, updated.album, replace = true)
                    if (options.artwork) updated = repository.fetchTrackArtwork(listOf(updated)).single()
                    val albumKey = catalog.albumKey(updated.album, catalog.effectiveAlbumArtist(updated))
                    if (albums.add(albumKey) && !CatalogIdentity.missing(updated.album)) {
                        if (options.artwork) repository.fetchAlbumArtwork(listOf(existingAlbums[albumKey]
                            ?: Album(id = updated.albumId, title = updated.album, artist = catalog.effectiveAlbumArtist(updated), artworkUri = updated.artworkUri)))
                        if (options.profiles) repository.loadAlbumDescription(updated.album, catalog.effectiveAlbumArtist(updated))
                    }
                    if (options.profiles || options.artwork) repository.splitArtistNames(updated.artist).forEach { name ->
                        if (artists.add(catalog.artistKey(name)) && !CatalogIdentity.missing(name)) {
                            if (options.artwork) repository.fetchArtistImages(listOf(existingArtists[catalog.artistKey(name)]
                                ?: Artist(id = catalog.artistKey(name), name = name)))
                            if (options.profiles) repository.loadArtistDescription(name)
                        }
                    }
                    if (options.lyrics) {
                        // Use existing local/embedded information first in bulk; otherwise keep the configured API/source chain.
                        val known = if (options.replaces(CatalogField.TRACK_LYRICS)) null else repository.peekLocalLyrics(updated)
                        if (known != null && lyricsStore.read(updated) == null) lyricsStore.saveFetched(updated, known, embed = false)
                        if (known == null) {
                            val fetched = repository.fetchLyrics(updated.artist, updated.title, updated.id, updated.uri,
                                sourcePreference = settings.lyricsSourcePreference.value, forceRefresh = options.replaces(CatalogField.TRACK_LYRICS), forceOnline = options.replaces(CatalogField.TRACK_LYRICS))
                            if (fetched != null && fetched.hasLyrics() && !fetched.isErrorMessage()) {
                                lyricsStore.saveFetched(updated, fetched); changed = true
                            }
                        }
                    }
                    updated = catalog.apply(updated, fillMissingManual = true)
                    if (updated != song) {
                        changed = true
                        catalog.acceptMissingManualFields(updated)
                        updated = catalog.apply(updated, fillMissingManual = true)
                        val tagsChanged = updated.title != song.title || updated.artist != song.artist || updated.album != song.album ||
                            updated.genre != song.genre || updated.year != song.year ||
                            (updated.artworkUri != song.artworkUri && catalog.usableArtwork(updated.artworkUri))
                        if (options.writeTags && tagsChanged) { writes.add(updated); writes.attempt(updated) }
                    }
                    delay(2_000) // Also space legacy API fallbacks between library items.
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
            }
            plan = plan.copy(next = plan.next + 1, changed = plan.changed + if (changed) 1 else 0,
                skipped = plan.skipped + if (!changed && !failed) 1 else 0, failed = plan.failed + if (failed) 1 else 0)
            plan.save(applicationContext)
            setProgress(workDataOf("done" to plan.next, "total" to plan.keys.size, "changed" to plan.changed,
                "skipped" to plan.skipped, "failed" to plan.failed, "title" to song?.title.orEmpty().take(120)))
            setForeground(notification(plan))
        }
        catalog.notifyChanged()
        PersonalArchive.enqueue(applicationContext)
        CatalogWriteWorker.enqueue(applicationContext)
        Result.success(workDataOf("done" to plan.next, "total" to plan.keys.size, "changed" to plan.changed,
            "skipped" to plan.skipped, "failed" to plan.failed))
    }
    private fun notification(plan: CatalogBatchPlan): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("catalog_loading",
            applicationContext.getString(R.string.catalog_batch_title), NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, "catalog_loading")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(applicationContext.getString(R.string.catalog_batch_title))
            .setContentText("${plan.next} / ${plan.keys.size}").setProgress(plan.keys.size, plan.next, false)
            .setOngoing(true).addAction(0, applicationContext.getString(R.string.catalog_pause),
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(7402, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(7402, notification)
    }
    companion object {
        const val WORK_NAME = "library_catalog_enrichment"
        fun enqueue(context: Context, continuation: Boolean = false) {
            val options = CatalogEnrichmentService.get(context).preferences.options.value
            val constraints = Constraints.Builder().setRequiredNetworkType(if (options.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true).setRequiresBatteryNotLow(true).build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME,
                if (continuation) ExistingWorkPolicy.APPEND else ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<LibraryEnrichmentWorker>().setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        }
    }
}
