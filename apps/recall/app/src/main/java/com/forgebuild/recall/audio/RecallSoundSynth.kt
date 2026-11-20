package com.forgebuild.recall.audio

import com.forgebuild.recall.data.RecallSound
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Pure-Kotlin 44.1 kHz PCM16 synthesizer for RECALL's 7 built-in chimes.
 * Zero external audio assets; perceptual application-managed gain scaling is
 * baked into the synthesized buffer so system Alarm stream volume is never altered.
 */
object RecallSoundSynth {
    const val SAMPLE_RATE = 44_100

    fun perceptualGain(volume: Float): Float =
        volume.coerceIn(0f, 1f).toDouble().pow(1.6).toFloat()

    fun synthesize(sound: RecallSound, volume: Float): ShortArray {
        val gain = perceptualGain(volume).toDouble()
        if (gain <= 0.001) return ShortArray(0)

        return when (sound) {
            RecallSound.CRYSTAL_BELL -> renderBell(
                durationSec = 0.65,
                partials = listOf(1046.5 to 1.0, 2093.0 to 0.60, 3136.0 to 0.35, 4186.0 to 0.16),
                decayRate = 6.8,
                gain = gain,
            )

            RecallSound.DOUBLE_STRIKE -> concatWithGap(
                gapSec = 0.09,
                parts = listOf(
                    renderBell(0.30, listOf(784.0 to 1.0, 1568.0 to 0.55, 2352.0 to 0.25), 9.5, gain),
                    renderBell(0.45, listOf(987.77 to 1.0, 1975.5 to 0.55, 2963.3 to 0.25), 7.5, gain),
                ),
            )

            RecallSound.CHIME_TRIAD -> mixNotes(
                totalDurationSec = 1.25,
                notes = listOf(
                    NoteSpec(0.00, 0.70, listOf(523.25 to 1.0, 1046.5 to 0.45, 1567.98 to 0.20), 5.5),
                    NoteSpec(0.16, 0.75, listOf(659.25 to 1.0, 1318.5 to 0.45, 1975.53 to 0.20), 5.0),
                    NoteSpec(0.32, 0.90, listOf(783.99 to 1.0, 1567.98 to 0.48, 2351.97 to 0.22), 4.2),
                ),
                gain = gain,
            )

            RecallSound.SONAR_PING -> renderBell(
                durationSec = 0.72,
                partials = listOf(660.0 to 1.0, 1320.0 to 0.32, 1980.0 to 0.10),
                decayRate = 5.4,
                gain = gain,
            )

            RecallSound.METRONOME_CLICK -> concatWithGap(
                gapSec = 0.11,
                parts = listOf(
                    renderBell(0.085, listOf(1480.0 to 1.0, 2960.0 to 0.45), 34.0, gain),
                    renderBell(0.095, listOf(1864.66 to 1.0, 3729.31 to 0.40), 30.0, gain),
                ),
            )

            RecallSound.SOFT_PULSE -> concatWithGap(
                gapSec = 0.10,
                parts = listOf(
                    renderBell(0.28, listOf(440.0 to 1.0, 880.0 to 0.30), 8.8, gain, attackSec = 0.012),
                    renderBell(0.34, listOf(554.37 to 1.0, 1108.73 to 0.30), 7.6, gain, attackSec = 0.012),
                ),
            )

            RecallSound.WESTMINSTER_CUE -> mixNotes(
                totalDurationSec = 1.45,
                notes = listOf(
                    NoteSpec(0.00, 0.45, listOf(659.25 to 1.0, 1318.5 to 0.42), 6.2),
                    NoteSpec(0.26, 0.45, listOf(587.33 to 1.0, 1174.66 to 0.42), 6.2),
                    NoteSpec(0.52, 0.45, listOf(523.25 to 1.0, 1046.5 to 0.42), 6.2),
                    NoteSpec(0.78, 0.65, listOf(392.00 to 1.0, 783.99 to 0.48, 1174.66 to 0.18), 4.5),
                ),
                gain = gain,
            )
        }
    }

    private data class NoteSpec(
        val startSec: Double,
        val durationSec: Double,
        val partials: List<Pair<Double, Double>>,
        val decayRate: Double,
    )

    private fun renderBell(
        durationSec: Double,
        partials: List<Pair<Double, Double>>,
        decayRate: Double,
        gain: Double,
        attackSec: Double = 0.004,
    ): ShortArray {
        val count = (SAMPLE_RATE * durationSec).toInt()
        val out = ShortArray(count)
        val norm = partials.sumOf { it.second }.coerceAtLeast(1.0)
        val fadeOutStart = (durationSec - 0.025).coerceAtLeast(0.0)

        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = if (t < attackSec) (t / attackSec) else 1.0
            val tailFade = if (t > fadeOutStart) ((durationSec - t) / 0.025).coerceIn(0.0, 1.0) else 1.0
            val env = attack * tailFade * exp(-decayRate * t)
            var sample = 0.0
            for ((freq, amp) in partials) {
                sample += amp * sin(2.0 * PI * freq * t)
            }
            val scaled = (sample / norm) * env * gain * 29000.0
            out[i] = scaled.toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun concatWithGap(gapSec: Double, parts: List<ShortArray>): ShortArray {
        val gapSamples = (SAMPLE_RATE * gapSec).toInt()
        val total = parts.sumOf { it.size } + gapSamples * (parts.size - 1).coerceAtLeast(0)
        val out = ShortArray(total)
        var offset = 0
        parts.forEachIndexed { idx, part ->
            part.copyInto(out, offset)
            offset += part.size
            if (idx < parts.lastIndex) offset += gapSamples
        }
        return out
    }

    private fun mixNotes(
        totalDurationSec: Double,
        notes: List<NoteSpec>,
        gain: Double,
    ): ShortArray {
        val total = (SAMPLE_RATE * totalDurationSec).toInt()
        val acc = DoubleArray(total)
        for (note in notes) {
            val startIdx = (note.startSec * SAMPLE_RATE).toInt()
            val rendered = renderBell(note.durationSec, note.partials, note.decayRate, gain = 1.0)
            for (j in rendered.indices) {
                val dst = startIdx + j
                if (dst in acc.indices) {
                    acc[dst] += rendered[j].toDouble() * 0.72
                }
            }
        }
        val out = ShortArray(total)
        for (i in 0 until total) {
            val v = (acc[i] * gain).toInt().coerceIn(-32768, 32767)
            out[i] = v.toShort()
        }
        return out
    }
}
