package com.forgebuild.clipforge.ui

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Streamed audio/narration preview with a disk cache (200 MB LRU under cacheDir/media):
 * first play streams over the network, every replay is a cache hit — no download-first.
 */
object AudioPreview {
    private var player: ExoPlayer? = null
    private var cache: SimpleCache? = null
    private var currentUrl: String? = null
    var playingUrl: String? = null; private set

    @Synchronized
    fun toggle(context: Context, url: String, pat: String) {
        if (playingUrl == url) { stop(); return }
        stop()
        val ctx = context.applicationContext
        if (cache == null) cache = SimpleCache(
            File(ctx.cacheDir, "media"), LeastRecentlyUsedCacheEvictor(200L * 1024 * 1024),
            StandaloneDatabaseProvider(ctx),
        )
        val upstream = OkHttpDataSource.Factory(
            okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder()
                        .header("Authorization", "Bearer $pat")
                        .header("Accept", "application/vnd.github.raw").build())
                }.build()
        )
        val ds = CacheDataSource.Factory().setCache(cache!!).setUpstreamDataSourceFactory(upstream)
        player = ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(ds))
            .build().apply {
                setMediaItem(MediaItem.fromUri(url)); prepare(); play()
            }
        playingUrl = url; currentUrl = url
    }

    @Synchronized
    fun stop() { player?.release(); player = null; playingUrl = null }
}
