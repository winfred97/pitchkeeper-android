package com.pitchkeeper.tuner.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class YinPitchDetectorTest {
    private val detector = YinPitchDetector()

    @Test
    fun detectsSustainedVocalRangeWithinFiveCents() {
        val frequencies = listOf(65.406f, 82.407f, 110f, 130.813f, 164.814f, 196f, 220f, 261.626f, 329.628f, 440f, 523.251f, 659.255f, 880f, 1046.502f)
        for (frequency in frequencies) {
            val result = detector.detect(sine(frequency))
            assertNotNull("Expected $frequency Hz to be voiced", result.frequency)
            val cents = PitchNotation.centsForFrequency(result.frequency!!)
            assertTrue("$frequency Hz detected as ${result.frequency}: $cents cents", abs(cents) <= 5f)
        }
    }

    @Test
    fun detectsHarmonicVoiceWithoutReportingAnUpperHarmonic() {
        for (frequency in listOf(110f, 146.832f, 220f, 329.628f, 440f, 659.255f)) {
            val result = detector.detect(harmonicTone(frequency))
            assertNotNull(result.frequency)
            val errorCents = 1200f * PitchNotation.log2(result.frequency!! / frequency)
            assertTrue("$frequency harmonic tone detected as ${result.frequency}", abs(errorCents) < 5f)
        }
    }

    @Test
    fun silenceDoesNotProduceAPitch() {
        val result = detector.detect(FloatArray(4096))
        assertFalse(result.isVoiced)
        assertNull(result.frequency)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun quietNoiseDoesNotProduceAPitch() {
        val random = java.util.Random(20261109)
        val input = FloatArray(4096) { (random.nextFloat() * 2f - 1f) * 0.002f }
        assertFalse(detector.detect(input).isVoiced)
    }

    @Test
    fun lowConfidenceInputIsRejected() {
        val random = java.util.Random(9)
        val noise = FloatArray(4096) { (random.nextFloat() * 2f - 1f) * 0.2f }
        assertFalse(detector.detect(noise).isVoiced)
    }

    @Test
    fun inputSamplesAreNeverModified() {
        val samples = sine(220f)
        val original = samples.copyOf()
        detector.detect(samples)
        assertTrue(samples.contentEquals(original))
    }

    @Test
    fun shortBufferDoesNotGuess() {
        assertFalse(detector.detect(FloatArray(100)).isVoiced)
    }

    private fun sine(frequency: Float, amplitude: Float = 0.18f): FloatArray = FloatArray(4096) {
        amplitude * sin(2.0 * PI * frequency * it / 44_100.0).toFloat()
    }

    private fun harmonicTone(frequency: Float): FloatArray = FloatArray(4096) { index ->
        val phase = 2.0 * PI * frequency * index / 44_100.0
        // A singer's harmonics may be louder than the fundamental.
        (0.07f * sin(phase) + 0.13f * sin(2 * phase + 0.2) + 0.05f * sin(3 * phase + 0.5)).toFloat()
    }
}
