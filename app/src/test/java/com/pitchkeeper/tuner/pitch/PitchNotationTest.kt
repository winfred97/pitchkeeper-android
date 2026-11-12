package com.pitchkeeper.tuner.pitch

import org.junit.Assert.assertEquals
import org.junit.Test

class PitchNotationTest {
    @Test
    fun concertAIsA4() {
        assertEquals(69f, PitchNotation.midiForFrequency(440f), 0.0001f)
        assertEquals(440f, PitchNotation.frequencyForMidi(69f), 0.0001f)
        assertEquals("A4", PitchNotation.nameForMidi(69))
        assertEquals("啦", PitchNotation.solfegeForMidi(69))
        assertEquals(0f, PitchNotation.centsForFrequency(440f), 0.0001f)
    }

    @Test
    fun middleCAndOctaveBoundariesAreCorrect() {
        assertEquals("C4", PitchNotation.nameForMidi(60))
        assertEquals("C2", PitchNotation.nameForMidi(36))
        assertEquals("C6", PitchNotation.nameForMidi(84))
        assertEquals(261.6256f, PitchNotation.frequencyForMidi(60f), 0.001f)
    }

    @Test
    fun sharpAndFlatEnharmonicsUseSharpNames() {
        assertEquals("C♯4", PitchNotation.nameForMidi(61))
        assertEquals("A♯3", PitchNotation.nameForMidi(58))
    }

    @Test
    fun centsAreSignedAroundNearestNote() {
        assertEquals(0f, PitchNotation.centsForFrequency(PitchNotation.frequencyForMidi(70f)), 0.001f)
        assertEquals(-49.99f, PitchNotation.centsForFrequency(PitchNotation.frequencyForMidi(68.5001f)), 0.001f)
        assertEquals(49.99f, PitchNotation.centsForFrequency(PitchNotation.frequencyForMidi(69.4999f)), 0.001f)
    }

    @Test
    fun alternativeConcertAChangesNoteAssignment() {
        assertEquals(442f, PitchNotation.frequencyForMidi(69f, 442f), 0.001f)
        assertEquals(0f, PitchNotation.centsForFrequency(442f, 442f), 0.001f)
    }
}
