package com.pitchkeeper.tuner.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PitchSmootherTest {
    @Test
    fun firstStablePitchIsDisplayedWithoutDelay() {
        val smoother = PitchSmoother()
        assertEquals(440f, smoother.update(PitchDetection(440f, 0.98f, 0.05f))!!)
    }

    @Test
    fun tinyFluctuationsAreSmoothedInMusicalSpace() {
        val smoother = PitchSmoother()
        smoother.update(PitchDetection(440f, 0.98f, 0.05f))
        val smoothed = smoother.update(PitchDetection(445f, 0.98f, 0.05f))!!
        assert(smoothed > 440f && smoothed < 441f)
    }

    @Test
    fun isolatedOctaveJumpDoesNotReplaceDisplayedPitch() {
        val smoother = PitchSmoother()
        smoother.update(PitchDetection(440f, 0.98f, 0.05f))
        val result = smoother.update(PitchDetection(880f, 0.98f, 0.05f))
        assertEquals(440f, result!!, 0.001f)
    }

    @Test
    fun silenceClearsStaleReadingAfterTimeout() {
        val smoother = PitchSmoother()
        smoother.update(PitchDetection(440f, 0.98f, 0.05f))
        repeat(6) { smoother.update(PitchDetection.Silence) }
        assertEquals(440f, smoother.frequency!!)
        smoother.update(PitchDetection.Silence)
        assertNull(smoother.frequency)
    }

    @Test
    fun deliberateOctaveLeapIsNotLockedToTheOldNote() {
        val smoother = PitchSmoother()
        smoother.update(PitchDetection(440f, 0.98f, 0.05f))
        repeat(20) { smoother.update(PitchDetection(880f, 0.98f, 0.05f)) }
        assertEquals(880f, smoother.frequency!!, 5f)
    }

    @Test
    fun realSustainedNoteChangeIsEventuallyAccepted() {
        val smoother = PitchSmoother()
        smoother.update(PitchDetection(440f, 0.98f, 0.05f))
        repeat(12) { smoother.update(PitchDetection(523.25f, 0.98f, 0.05f)) }
        assertEquals(523.25f, smoother.frequency!!, 2f)
    }
}
