package com.forgebuild.taskflow.notify

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.exp
import kotlin.math.sin

/**
 * Distinct kitchen-timer / oven "ding" — NOT a generic notification tone.
 * Synthesized in-app (no bundled asset): three struck partials with a fast
 * exponential decay, played three times in quick succession like a real oven.
 */
object TimerSounds {
    private const val SR = 22050

    private fun synthDing(): ShortArray {
        val dur = 0.55
        val n = (SR * dur).toInt()
        val buf = ShortArray(n)
        val partials = listOf(2093.0 to 1.0, 2637.0 to 0.62, 3136.0 to 0.40) // C7 E7 G7-ish chime
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val env = exp(-7.5 * t)
            var s = 0.0
            for ((f, a) in partials) s += a * sin(2.0 * Math.PI * f * t)
            buf[i] = (s / partials.size * env * 28000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buf
    }

    /** Play the triple ding synchronously on a background thread (fire-and-forget). */
    fun playCompletion() {
        Thread {
            runCatching {
                val ding = synthDing()
                fun ring() {
                    val track = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setSampleRate(SR)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(ding.size * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                    track.write(ding, 0, ding.size)
                    track.play()
                    Thread.sleep(640)
                    track.release()
                }
                repeat(3) { k -> if (k > 0) Thread.sleep(90); ring() }
            }
        }.start()
    }
}
