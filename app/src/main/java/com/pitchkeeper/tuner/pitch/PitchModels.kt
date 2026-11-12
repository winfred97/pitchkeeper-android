package com.pitchkeeper.tuner.pitch

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** C2–C6: the shared singing range used by this first, monophonic version. */
data class PitchDetectorConfig(
    val sampleRate: Int = 44_100,
    val windowSize: Int = 4_096,
    val minimumFrequency: Float = 65f,
    val maximumFrequency: Float = 1_050f,
    val yinThreshold: Float = 0.15f,
    val minimumConfidence: Float = 0.75f,
    val minimumRms: Float = 0.0025f,
) {
    init {
        require(sampleRate > 0)
        require(windowSize >= 256 && windowSize % 2 == 0)
        require(minimumFrequency > 0f && minimumFrequency < maximumFrequency)
        require(maximumFrequency < sampleRate / 2f)
        require(yinThreshold in 0f..1f)
        require(minimumConfidence in 0f..1f)
        require(minimumRms >= 0f)
    }
}

data class PitchDetection(
    val frequency: Float? = null,
    val confidence: Float = 0f,
    val rms: Float = 0f,
) {
    val isVoiced: Boolean get() = frequency != null

    companion object {
        val Silence = PitchDetection()
    }
}

/** Musical conversion is separate from the signal-processing algorithm. */
object PitchNotation {
    private val noteNames = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    private val solfege = arrayOf("哆", "升哆", "来", "升来", "咪", "发", "升发", "索", "升索", "啦", "升啦", "西")

    fun midiForFrequency(frequency: Float, concertA: Float = 440f): Float {
        require(frequency > 0f && concertA > 0f)
        return 69f + 12f * log2(frequency / concertA)
    }

    fun frequencyForMidi(midi: Float, concertA: Float = 440f): Float {
        require(concertA > 0f)
        return concertA * 2.0.pow((midi - 69.0) / 12.0).toFloat()
    }

    fun nearestMidi(frequency: Float, concertA: Float = 440f): Int =
        midiForFrequency(frequency, concertA).roundToInt()

    fun centsForFrequency(frequency: Float, concertA: Float = 440f): Float {
        val midi = midiForFrequency(frequency, concertA)
        return (midi - midi.roundToInt()) * 100f
    }

    fun nameForMidi(midi: Int): String = "${noteNames[Math.floorMod(midi, 12)]}${midi / 12 - 1}"

    fun solfegeForMidi(midi: Int): String = solfege[Math.floorMod(midi, 12)]

    fun log2(value: Float): Float = (kotlin.math.ln(value.toDouble()) / kotlin.math.ln(2.0)).toFloat()
}

/**
 * YIN (de Cheveigné & Kawahara, 2002), steps 1–4 with parabolic interpolation.
 * Operates on PCM only; no Android dependencies so this runs in local JVM tests.
 */
class YinPitchDetector(private val config: PitchDetectorConfig = PitchDetectorConfig()) {
    private val halfWindow = config.windowSize / 2
    private val minTau = (config.sampleRate / config.maximumFrequency).toInt().coerceAtLeast(2)
    private val maxTau = (config.sampleRate / config.minimumFrequency).toInt()
        .coerceAtMost(halfWindow - 1)
    private val cumulative = FloatArray(halfWindow + 1)
    private val window = FloatArray(config.windowSize)

    init {
        require(minTau <= maxTau) { "Window is too short for the requested frequency range" }
        // Taper is applied after the difference, preserving the fixed support
        // required by YIN's cumulative normalization across different lags.
        val taperSize = maxTau / 8
        for (i in 0 until taperSize) {
            window[i] = 0.5f * (1f - cos(2.0 * PI * i / (2 * taperSize))).toFloat()
            window[config.windowSize - maxTau - 1 - i] = window[i]
        }
        window.fill(1f, taperSize, config.windowSize - maxTau - taperSize)
    }

    /** The first [config.windowSize] samples are consumed; extra samples are ignored. */
    fun detect(samples: FloatArray): PitchDetection {
        if (samples.size < config.windowSize) return PitchDetection.Silence
        val signal = DoubleArray(config.windowSize)
        var sumSquares = 0.0
        for (i in signal.indices) {
            val sample = samples[i].toDouble()
            signal[i] = sample
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / config.windowSize)
        if (rms < config.minimumRms) return PitchDetection(rms = rms.toFloat())

        var cumulativeSum = 0.0
        cumulative[0] = 1f
        for (tau in 1..maxTau) {
            var difference = 0.0
            val limit = config.windowSize - maxTau
            for (i in 0 until limit) {
                val delta = signal[i] - signal[i + tau]
                difference += delta * delta
            }
            cumulativeSum += difference
            cumulative[tau] = if (cumulativeSum == 0.0) 1f else (difference * tau / cumulativeSum).toFloat()
        }

        var tauEstimate = -1
        for (tau in minTau..maxTau) {
            if (cumulative[tau] < config.yinThreshold) {
                var localMinimum = tau
                while (localMinimum + 1 <= maxTau &&
                    cumulative[localMinimum + 1] < cumulative[localMinimum]
                ) {
                    localMinimum++
                }
                tauEstimate = localMinimum
                break
            }
        }
        if (tauEstimate == -1) tauEstimate = minByValue(cumulative, minTau, maxTau)
        return refine(tauEstimate, cumulative, rms.toFloat())
    }

    private fun refine(tau: Int, difference: FloatArray, rms: Float): PitchDetection {
        val refinedTau = if (tau >= minTau && tau < maxTau) {
            val left = difference[tau - 1]
            val center = difference[tau]
            val right = difference[tau + 1]
            val denominator = 2f * (2f * center - right - left)
            val adjustment = if (abs(denominator) > 0.000001f) (right - left) / denominator else 0f
            tau + adjustment.coerceIn(-0.5f, 0.5f)
        } else tau.toFloat()
        val confidence = (1f - difference[tau]).coerceIn(0f, 1f)
        val frequency = config.sampleRate / refinedTau
        if (confidence < config.minimumConfidence ||
            frequency !in config.minimumFrequency..config.maximumFrequency
        ) return PitchDetection(confidence = confidence, rms = rms)
        return PitchDetection(frequency, confidence, rms)
    }

    private fun minByValue(values: FloatArray, from: Int, to: Int): Int {
        var best = from
        for (i in from + 1..to) if (values[i] < values[best]) best = i
        return best
    }
}
