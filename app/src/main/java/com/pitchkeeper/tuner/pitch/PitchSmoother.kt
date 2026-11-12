package com.pitchkeeper.tuner.pitch

import kotlin.math.abs

/**
 * Suppresses isolated detection jumps and smooths continuous vibrato.
 * A repeated new pitch is always accepted, including a deliberate octave leap.
 */
class PitchSmoother(
    private val missingFramesToReset: Int = 7,
    private val maximumJumpCents: Float = 650f,
) {
    var rawFrequency: Float? = null
        private set
    var frequency: Float? = null
        private set
    var confidence: Float = 0f
        private set
    var rms: Float = 0f
        private set

    private var missingFrames = missingFramesToReset
    private var candidateFrequency: Float? = null
    private var candidateFrames = 0

    fun reset() {
        rawFrequency = null
        frequency = null
        confidence = 0f
        rms = 0f
        missingFrames = missingFramesToReset
        candidateFrequency = null
        candidateFrames = 0
    }

    fun update(detection: PitchDetection, concertA: Float = 440f): Float? {
        rawFrequency = detection.frequency
        rms = detection.rms
        confidence = detection.confidence
        val detected = detection.frequency
        if (detected == null) {
            missingFrames++
            candidateFrequency = null
            candidateFrames = 0
            if (missingFrames >= missingFramesToReset) frequency = null
            return frequency
        }

        val current = frequency
        if (current == null) {
            missingFrames = 0
            frequency = detected
            return frequency
        }

        val jumpCents = centsBetween(current, detected)
        if (jumpCents > maximumJumpCents) {
            missingFrames++
            val candidate = candidateFrequency
            if (candidate != null && centsBetween(candidate, detected) < 60f) {
                candidateFrames++
            } else {
                candidateFrequency = detected
                candidateFrames = 1
            }
            // Three consistent readings confirm a real note change; one isolated
            // octave/subharmonic detection is discarded without a hard octave lock.
            if (candidateFrames < 3) return current
            candidateFrequency = null
            candidateFrames = 0
        } else {
            candidateFrequency = null
            candidateFrames = 0
            missingFrames = 0
        }

        val previousMidi = PitchNotation.midiForFrequency(current, concertA)
        val measuredMidi = PitchNotation.midiForFrequency(detected, concertA)
        val smoothing = if (abs(measuredMidi - previousMidi) > 1f) 0.65f else 0.20f
        frequency = PitchNotation.frequencyForMidi(
            previousMidi + (measuredMidi - previousMidi) * smoothing,
            concertA,
        )
        return frequency
    }

    private fun centsBetween(first: Float, second: Float): Float =
        abs(PitchNotation.midiForFrequency(second) - PitchNotation.midiForFrequency(first)) * 100f
}
