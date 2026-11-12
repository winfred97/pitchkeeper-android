package com.pitchkeeper.tuner.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PitchStreamProcessorTest {
    @Test
    fun waitsForOneFullWindowBeforeDetecting() {
        val processor = PitchStreamProcessor()
        repeat(3) {
            assertNull(processor.add(FloatArray(1024)))
        }
        val result = processor.add(FloatArray(1024))
        assertNotNull(result)
        assertEquals(0, processor.samplesSinceAnalysis)
    }

    @Test
    fun detectsContiguousAudioAcrossChunkBoundaries() {
        val processor = PitchStreamProcessor()
        var result: PitchDetection? = null
        for (chunkIndex in 0 until 8) {
            val chunk = FloatArray(1024) { index ->
                val sample = chunkIndex * 1024 + index
                (0.12f * sin(2.0 * PI * 220 * sample / 44_100.0)).toFloat()
            }
            processor.add(chunk)?.let { result = it }
        }
        assertNotNull(result)
        assertEquals(220f, result!!.frequency!!, 2f)
    }

    @Test
    fun resetDiscardsOldAudio() {
        val processor = PitchStreamProcessor()
        repeat(3) { processor.add(FloatArray(1024) { 0.2f }) }
        processor.reset()
        repeat(3) { assertNull(processor.add(FloatArray(1024) { 0.2f })) }
    }
}
