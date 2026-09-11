package com.forgebuild.clipforgeandroid.ui

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File

/**
 * Streamed audio/narration preview with a disk cache (200 MB LRU under cacheDir/media).
 * First play streams over the network; every replay is an instant cache hit.
 * Includes a real, working pause control and live progress tracking.
 */
object AudioPreview {
    private var player: ExoPlayer? = null
    private var cache: SimpleCache? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    data class AudioState(
        val url: String? = null,
        val isPlaying: Boolean = false,
        val isPaused: Boolean = false,
        val isBuffering: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L
    )

    private val _state = MutableStateFlow(AudioState())
    val state: StateFlow<AudioState> = _state

    @Synchronized
    fun toggle(context: Context, url: String, pat: String) {
        val current = _state.value
        if (current.url == url) {
            if (current.isPlaying) {
                pause()
            } else {
                resume()
            }
            return
        }
        play(context, url, pat)
    }

    @Synchronized
    fun play(context: Context, url: String, pat: String) {
        val ctx = context.applicationContext
        stopInternal(releasePlayer = false)

        if (cache == null) {
            cache = SimpleCache(
                File(ctx.cacheDir, "media"),
                LeastRecentlyUsedCacheEvictor(200L * 1024 * 1024),
                StandaloneDatabaseProvider(ctx)
            )
        }

        val upstream = OkHttpDataSource.Factory(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Authorization", "Bearer $pat")
                            .header("Accept", "application/vnd.github.raw")
                            .build()
                    )
                }.build()
        )

        val ds = CacheDataSource.Factory()
            .setCache(cache!!)
            .setUpstreamDataSourceFactory(upstream)

        if (player == null) {
            player = ExoPlayer.Builder(ctx)
                .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(ds))
                .build().apply {
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            val buffering = playbackState == Player.STATE_BUFFERING
                            val ended = playbackState == Player.STATE_ENDED
                            if (ended) {
                                _state.value = _state.value.copy(
                                    isPlaying = false,
                                    isPaused = false,
                                    positionMs = 0L
                                )
                                progressJob?.cancel()
                            } else {
                                _state.value = _state.value.copy(
                                    isBuffering = buffering,
                                    durationMs = duration.coerceAtLeast(0L)
                                )
                            }
                        }

                        override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                            val p = player
                            _state.value = _state.value.copy(
                                isPlaying = isPlayingNow,
                                isPaused = !isPlayingNow && (p?.playbackState == Player.STATE_READY),
                                durationMs = p?.duration?.coerceAtLeast(0L) ?: 0L,
                                positionMs = p?.currentPosition?.coerceAtLeast(0L) ?: 0L
                            )
                            if (isPlayingNow) {
                                startProgressTracking()
                            } else {
                                progressJob?.cancel()
                            }
                        }
                    })
                }
        }

        player?.apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            play()
        }

        _state.value = AudioState(
            url = url,
            isPlaying = true,
            isPaused = false,
            isBuffering = true
        )
    }

    @Synchronized
    fun pause() {
        player?.pause()
        _state.value = _state.value.copy(
            isPlaying = false,
            isPaused = true,
            positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: _state.value.positionMs
        )
        progressJob?.cancel()
    }

    @Synchronized
    fun resume() {
        player?.play()
        _state.value = _state.value.copy(
            isPlaying = true,
            isPaused = false
        )
        startProgressTracking()
    }

    @Synchronized
    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    @Synchronized
    fun stop() {
        stopInternal(releasePlayer = true)
    }

    private fun stopInternal(releasePlayer: Boolean) {
        progressJob?.cancel()
        progressJob = null
        if (releasePlayer) {
            player?.release()
            player = null
        } else {
            player?.stop()
        }
        _state.value = AudioState()
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val p = player ?: break
                _state.value = _state.value.copy(
                    positionMs = p.currentPosition.coerceAtLeast(0L),
                    durationMs = p.duration.coerceAtLeast(0L)
                )
                delay(250)
            }
        }
    }
}
