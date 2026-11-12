package com.pitchkeeper.tuner.pitch

/** Reassembles microphone chunks into contiguous, overlapping analysis windows. */
class PitchStreamProcessor(
    private val detector: YinPitchDetector = YinPitchDetector(),
    private val windowSize: Int = 4096,
) {
    private val ring = FloatArray(windowSize)
    private val analysisWindow = FloatArray(windowSize)
    private var writeIndex = 0
    private var received = 0
    var samplesSinceAnalysis = 0
        private set

    fun reset() {
        ring.fill(0f)
        writeIndex = 0
        received = 0
        samplesSinceAnalysis = 0
    }

    fun add(chunk: FloatArray): PitchDetection? {
        for (sample in chunk) {
            ring[writeIndex] = sample
            writeIndex = (writeIndex + 1) % windowSize
        }
        received += chunk.size
        samplesSinceAnalysis += chunk.size
        if (received < windowSize || samplesSinceAnalysis < HOP_SIZE) return null
        for (i in 0 until windowSize) analysisWindow[i] = ring[(writeIndex + i) % windowSize]
        samplesSinceAnalysis = 0
        return detector.detect(analysisWindow)
    }

    companion object {
        const val HOP_SIZE = 1024
    }
}
