/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import chromahub.rhythm.app.BuildConfig
import chromahub.rhythm.app.features.streaming.data.provider.UserTrustManager
import chromahub.rhythm.app.features.streaming.di.StreamingMusicModule
import chromahub.rhythm.app.infrastructure.audio.codec.AiffMedia3Extractor
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/**
 * Shared Media3 data/media-source builder used by both [RhythmPlayerEngine] and
 * [PreloadController] so streaming playback and preloading honor user-trusted TLS
 * certificates, resolve `streaming://` URIs consistently, and share [AudioCacheManager].
 */
@OptIn(UnstableApi::class)
object RhythmMediaDataSourceFactory {

    /**
     * Creates a [DataSource.Factory] backed by [UserTrustManager]-aware OkHttp,
     * [AudioCacheManager], and a `streaming://` URL resolver.
     */
    fun createResolvingDataSourceFactory(context: Context): DataSource.Factory {
        val appContext = context.applicationContext
        val okHttpClient = UserTrustManager.buildUserTrustingHttpClientBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val upstreamFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Rhythm/${BuildConfig.VERSION_NAME} (Android)")
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(AudioCacheManager.getCache(appContext))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(appContext, upstreamFactory))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val baseDataSourceFactory = DefaultDataSource.Factory(appContext, cacheDataSourceFactory)

        return ResolvingDataSource.Factory(
            baseDataSourceFactory,
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    if (dataSpec.uri.scheme == "streaming") {
                        val trackId = dataSpec.uri.lastPathSegment
                        if (!trackId.isNullOrBlank()) {
                            val repository = StreamingMusicModule.provideStreamingMusicRepository(appContext)
                            val freshUrl = runBlocking { repository.getStreamingUrl(trackId) }
                            if (!freshUrl.isNullOrBlank()) {
                                return dataSpec.buildUpon()
                                    .setUri(freshUrl.toUri())
                                    .setKey("streaming_$trackId")
                                    .build()
                            }
                        }
                    }
                    return dataSpec
                }
            }
        )
    }

    /**
     * Creates a [DefaultMediaSourceFactory] configured with [createResolvingDataSourceFactory]
     * and extended extractor support (including uncompressed AIFF/AIFC PCM playback).
     */
    fun createMediaSourceFactory(context: Context): DefaultMediaSourceFactory {
        val baseExtractors = DefaultExtractorsFactory()
        val extractorsFactory = object : ExtractorsFactory {
            override fun createExtractors(): Array<Extractor> =
                baseExtractors.createExtractors() +
                    AiffMedia3Extractor() +
                    chromahub.rhythm.app.infrastructure.audio.codec.DsdMedia3Extractor()

            override fun createExtractors(
                uri: android.net.Uri,
                responseHeaders: Map<String, List<String>>
            ): Array<Extractor> =
                baseExtractors.createExtractors(uri, responseHeaders) +
                    AiffMedia3Extractor() +
                    chromahub.rhythm.app.infrastructure.audio.codec.DsdMedia3Extractor()
        }
        return DefaultMediaSourceFactory(context.applicationContext, extractorsFactory)
            .setDataSourceFactory(createResolvingDataSourceFactory(context))
    }
}
