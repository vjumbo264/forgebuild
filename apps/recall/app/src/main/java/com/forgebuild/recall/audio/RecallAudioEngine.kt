package com.forgebuild.recall.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.forgebuild.recall.data.RecallInterval
import com.forgebuild.recall.data.RecallSound
import com.forgebuild.recall.util.RecallDiagnostics
import java.util.concurrent.atomic.AtomicLong

/**
 * Alarm-pipeline audio playback engine for RECALL.
 *
 * Design guarantees:
 * 1. Uses [AudioAttributes.USAGE_ALARM] + [AudioAttributes.CONTENT_TYPE_SONIFICATION]
 *    so chimes remain audible when Ring/Notification streams are muted or screen is off.
 * 2. NEVER modifies the device's system ALARM stream volume; per-interval volume is
 *    applied strictly via application-managed perceptual gain in [RecallSoundSynth].
 * 3. Requests transient ducking [AudioFocusRequest] before playing and releases it
 *    in a finally block.
 * 4. Reports currently active audio output routes via [describeOutputRoutes] without
 *    assuming simultaneous speaker + headphone playback.
 */
object RecallAudioEngine {

    private val playbackLock = Any()
    private val generation = AtomicLong(0L)
    @Volatile
    private var activeTrack: AudioTrack? = null

    private val alarmAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun stopCurrent() {
        generation.incrementAndGet()
        synchronized(playbackLock) {
            runCatching {
                activeTrack?.let { track ->
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                    track.release()
                }
            }
            activeTrack = null
        }
    }

    /**
     * Plays a single chime synchronously (blocking until playback finishes or is
     * superseded). Designed to be invoked from [RecallAlarmReceiver] under WakeLock.
     */
    fun playChimeBlocking(context: Context, sound: RecallSound, volume: Float) {
        val pcm = RecallSoundSynth.synthesize(sound, volume)
        if (pcm.isEmpty()) return

        val myGen = generation.incrementAndGet()
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(alarmAttributes)
            .setOnAudioFocusChangeListener { /* transient alarm ducking */ }
            .build()

        var focusResult = AudioManager.AUDIOFOCUS_REQUEST_FAILED
        try {
            focusResult = am.requestAudioFocus(focusRequest)
            val byteSize = pcm.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(alarmAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RecallSoundSynth.SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(byteSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            synchronized(playbackLock) {
                runCatching { activeTrack?.release() }
                activeTrack = track
            }

            track.write(pcm, 0, pcm.size)
            track.play()

            val durationMs = (pcm.size * 1000L) / RecallSoundSynth.SAMPLE_RATE + 80L
            val deadline = System.currentTimeMillis() + durationMs
            while (System.currentTimeMillis() < deadline && generation.get() == myGen) {
                Thread.sleep(25L)
            }
        } catch (t: Throwable) {
            RecallDiagnostics.logError(context, "RecallAudioEngine", "Playback error for $sound: ${t.message}", t)
        } finally {
            synchronized(playbackLock) {
                activeTrack?.let { track ->
                    if (generation.get() != myGen || track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        runCatching { track.stop() }
                    }
                    runCatching { track.release() }
                    if (activeTrack === track) activeTrack = null
                }
            }
            if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                runCatching { am.abandonAudioFocusRequest(focusRequest) }
            }
        }
    }

    /** Non-blocking preview of a specific sound at a given app-level volume. */
    fun previewAsync(context: Context, sound: RecallSound, volume: Float) {
        val appCtx = context.applicationContext
        Thread({
            playChimeBlocking(appCtx, sound, volume)
        }, "RecallAudioPreview").start()
    }

    /** Global test: plays the highest-priority enabled interval chime (or default Crystal Bell). */
    fun playGlobalTestAsync(context: Context, intervals: List<RecallInterval>) {
        val target = intervals.filter { it.enabled }.maxByOrNull { it.priority }
            ?: intervals.firstOrNull()
        val sound = target?.sound ?: RecallSound.CRYSTAL_BELL
        val volume = target?.volume ?: 0.85f
        previewAsync(context, sound, volume)
    }

    /**
     * Inspects current Android audio output devices so users can verify whether
     * wired headphones or Bluetooth audio are attached alongside the built-in speaker.
     */
    fun describeOutputRoutes(context: Context): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val labels = LinkedHashSet<String>()
        for (d in devices) {
            when (d.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> labels.add("Wired Headphones")
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> labels.add("Bluetooth Audio")
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> labels.add("Bluetooth LE Audio")
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE -> labels.add("USB Audio")
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> labels.add("Phone Speaker")
            }
        }
        return if (labels.isEmpty()) "Phone Speaker" else labels.joinToString(" · ")
    }
}
