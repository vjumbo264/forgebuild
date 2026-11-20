package com.forgebuild.recall.audio

import com.forgebuild.recall.data.RecallSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RecallSoundSynthTest {

    @Test
    fun allSevenSoundsSynthesizeDistinctNonEmptyBuffers() {
        val lengths = mutableSetOf<Int>()
        for (sound in RecallSound.entries) {
            val pcm = RecallSoundSynth.synthesize(sound, 0.85f)
            assertTrue("Sound $sound should be non-empty", pcm.isNotEmpty())
            val peak = pcm.maxOf { abs(it.toInt()) }
            assertTrue("Sound $sound peak ($peak) should be clearly audible", peak > 5_000)
            lengths.add(pcm.size)
        }
        assertTrue("Expected distinct waveform lengths across sounds", lengths.size >= 4)
    }

    @Test
    fun zeroVolumeProducesEmptyBufferAndIndependentGainScalesProportionally() {
        val muted = RecallSoundSynth.synthesize(RecallSound.CRYSTAL_BELL, 0.0f)
        assertEquals(0, muted.size)

        val quiet = RecallSoundSynth.synthesize(RecallSound.CRYSTAL_BELL, 0.25f)
        val loud = RecallSoundSynth.synthesize(RecallSound.CRYSTAL_BELL, 1.0f)
        val quietPeak = quiet.maxOf { abs(it.toInt()) }
        val loudPeak = loud.maxOf { abs(it.toInt()) }
        assertTrue("Quiet peak ($quietPeak) must be strictly lower than loud peak ($loudPeak)", quietPeak < loudPeak / 2)
    }
}
