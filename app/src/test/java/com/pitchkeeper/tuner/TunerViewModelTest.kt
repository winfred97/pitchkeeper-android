package com.pitchkeeper.tuner

import com.pitchkeeper.tuner.audio.MicrophoneSource
import com.pitchkeeper.tuner.pitch.PitchNotation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startAndStopAreSerialized() = runTest {
        val microphone = FakeMicrophone()
        val model = TunerViewModel(microphone)
        model.onPermissionAvailable(true)
        model.toggleListening()
        advanceUntilIdle()
        assertTrue(microphone.started)
        assertTrue(model.uiState.value.isListening)
        model.toggleListening()
        advanceUntilIdle()
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
            while (model.uiState.value.isListening || !microphone.stopped) {
                yield()
                kotlinx.coroutines.delay(10)
            }
        }
        }
        assertFalse("Session remained listening: ${model.uiState.value}", model.uiState.value.isListening)
        assertTrue("Microphone did not stop: ${model.uiState.value}", microphone.stopped)
    }

    @Test
    fun foregroundResumesOnlyIfUserHadRequestedListening() = runTest {
        val microphone = FakeMicrophone()
        val model = TunerViewModel(microphone)
        model.onPermissionAvailable(true)
        advanceUntilIdle()
        assertFalse(microphone.started)
        model.onPermissionResult(true)
        advanceUntilIdle()
        model.onBackground()
        advanceUntilIdle()
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
                while (model.uiState.value.isListening) {
                    yield()
                    kotlinx.coroutines.delay(10)
                }
            }
        }
        model.onPermissionAvailable(true)
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
            while (microphone.startCount < 2) {
                yield()
                kotlinx.coroutines.delay(10)
            }
        }
        }
        advanceUntilIdle()
        assertEquals("Foreground must start a second session: ${model.uiState.value}", 2, microphone.startCount)
        assertTrue(microphone.started)
        assertTrue(model.uiState.value.isListening)
    }

    @Test
    fun manualStopDoesNotRestartOnForeground() = runTest {
        val microphone = FakeMicrophone()
        val model = TunerViewModel(microphone)
        model.onPermissionResult(true)
        advanceUntilIdle()
        model.toggleListening()
        advanceUntilIdle()
        model.onPermissionAvailable(true)
        advanceUntilIdle()
        assertEquals(1, microphone.startCount)
    }

    @Test
    fun microphoneStreamProducesNoteAndFrequency() = runTest {
        val microphone = FakeMicrophone()
        val model = TunerViewModel(microphone)
        model.onPermissionResult(true)
        advanceUntilIdle()
        repeat(8) { index ->
            microphone.chunks?.trySend(FloatArray(1024) { sample ->
                (0.16f * sin(2.0 * PI * 220.0 * (index * 1024 + sample) / 44_100.0)).toFloat()
            })
            advanceUntilIdle()
        }
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
            while (model.uiState.value.frequency == null) {
                yield()
                kotlinx.coroutines.delay(10)
            }
        }
        }
        val state = model.uiState.value
        assertEquals("A3", PitchNotation.nameForMidi(state.displayNote))
        assertEquals(220f, state.frequency!!, 2f)
        model.stopListening()
        advanceUntilIdle()
    }

    @Test
    fun referencePitchIsLimitedToSupportedRange() = runTest {
        val model = TunerViewModel(FakeMicrophone())
        model.setConcertA(500f)
        assertEquals(466f, model.uiState.value.concertA)
        model.setConcertA(300f)
        assertEquals(415f, model.uiState.value.concertA)
    }

    private class FakeMicrophone : MicrophoneSource {
        var started = false
        var stopped = false
        var startCount = 0
        var chunks: Channel<FloatArray>? = null
        override fun start(scope: CoroutineScope, chunks: Channel<FloatArray>) {
            started = true
            stopped = false
            startCount++
            this.chunks = chunks
        }

        override suspend fun stop() {
            stopped = true
            chunks?.close()
        }
    }
}
