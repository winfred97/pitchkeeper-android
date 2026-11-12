package com.pitchkeeper.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitchkeeper.tuner.audio.AndroidAudioRecorder
import com.pitchkeeper.tuner.audio.AudioRecorderException
import com.pitchkeeper.tuner.audio.MicrophoneSource
import com.pitchkeeper.tuner.pitch.PitchNotation
import com.pitchkeeper.tuner.pitch.PitchSmoother
import com.pitchkeeper.tuner.pitch.PitchStreamProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

enum class ListeningStatus { IDLE, STARTING, LISTENING }

data class TunerUiState(
    val status: ListeningStatus = ListeningStatus.IDLE,
    val hasPermission: Boolean = false,
    val permissionDenied: Boolean = false,
    val frequency: Float? = null,
    val rawFrequency: Float? = null,
    val confidence: Float = 0f,
    val rms: Float = 0f,
    val displayNote: Int = 69,
    val displayCents: Float = 0f,
    val history: List<Float?> = emptyList(),
    val concertA: Float = 440f,
    val errorMessage: String? = null,
) {
    val isListening: Boolean get() = status == ListeningStatus.STARTING || status == ListeningStatus.LISTENING
}

class TunerViewModel(
    private val microphone: MicrophoneSource = AndroidAudioRecorder(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = _uiState.asStateFlow()
    private var chunks: Channel<FloatArray>? = null
    private var analysisJob: Job? = null
    private var userRequestedListening = false
    @Volatile private var screenVisible = true
    private val controlScope = MainScope()

    fun onPermissionAvailable(granted: Boolean) {
        screenVisible = true
        _uiState.update { it.copy(hasPermission = granted) }
        if (granted && userRequestedListening) {
            requestListening(true)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                hasPermission = granted,
                permissionDenied = !granted,
                errorMessage = if (granted) null else "允许使用麦克风后，才能听到你的声音。",
            )
        }
        requestListening(granted)
    }

    fun toggleListening() {
        requestListening(!userRequestedListening)
    }

    private fun requestListening(active: Boolean) {
        userRequestedListening = active
        controlScope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (userRequestedListening && screenVisible) startSession()
            else stopSession()
        }
    }

    /** Session controls are called on Main; analysis runs on a background dispatcher. */
    private fun startSession() {
        if (analysisJob != null) return
        if (!_uiState.value.hasPermission) {
            _uiState.update { it.copy(permissionDenied = true) }
            return
        }
        _uiState.update {
            it.copy(
                status = ListeningStatus.STARTING,
                errorMessage = null,
                frequency = null,
                rawFrequency = null,
                confidence = 0f,
                rms = 0f,
                history = emptyList(),
            )
        }
        val channel = Channel<FloatArray>(capacity = 3)
        val job = viewModelScope.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            val processor = PitchStreamProcessor()
            val smoother = PitchSmoother()
            var publishedRms = 0f
            try {
                _uiState.update { it.copy(status = ListeningStatus.LISTENING) }
                for (chunk in channel) {
                    val detection = processor.add(chunk) ?: continue
                    val frequency = smoother.update(detection, _uiState.value.concertA)
                    publishedRms = publishedRms * 0.75f + detection.rms * 0.25f
                    updateMeasurement(frequency, smoother, publishedRms)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showRecorderError(error)
                requestListening(false)
            }
        }
        try {
            chunks = channel
            analysisJob = job
            microphone.start(viewModelScope, channel)
        } catch (error: Exception) {
            job.cancel()
            channel.close()
            showRecorderError(error)
            requestListening(false)
        }
    }

    private fun updateMeasurement(frequency: Float?, smoother: PitchSmoother, rms: Float) {
        _uiState.update { current ->
            val midi = frequency?.let { PitchNotation.midiForFrequency(it, current.concertA) }
            val note = midi?.let { Math.round(it) } ?: current.displayNote
            val cents = midi?.let { (it - note) * 100f }?.coerceIn(-50f, 50f) ?: 0f
            current.copy(
                status = if (current.status == ListeningStatus.STARTING) ListeningStatus.LISTENING else current.status,
                frequency = frequency,
                rawFrequency = smoother.rawFrequency,
                confidence = smoother.confidence,
                rms = rms,
                displayNote = note,
                displayCents = cents,
                history = (current.history + if (frequency != null) cents else null).takeLast(HISTORY_SIZE),
            )
        }
    }

    private fun showRecorderError(error: Exception) {
        val message = when (error) {
            is SecurityException -> "麦克风权限已关闭，请在系统设置中允许录音。"
            is AudioRecorderException -> error.cause?.let { "麦克风连接中断，请重新试唱。" } ?: error.message
            is IllegalStateException -> error.message
            else -> "暂时无法使用麦克风，请关闭其他录音应用后重试。"
        } ?: "麦克风暂不可用，请重试。"
        _uiState.update { it.copy(errorMessage = message) }
    }

    private suspend fun stopSession() {
        val job = analysisJob ?: run {
            _uiState.update { it.copy(status = ListeningStatus.IDLE) }
            return
        }
        analysisJob = null
        chunks?.close()
        chunks = null
        job.cancel()
        microphone.stop()
        job.cancelAndJoin()
        _uiState.update {
            it.copy(
                status = ListeningStatus.IDLE,
                frequency = null,
                rawFrequency = null,
                confidence = 0f,
                rms = 0f,
            )
        }
    }

    fun stopListening() {
        requestListening(false)
        _uiState.update {
            it.copy(
                status = ListeningStatus.IDLE,
                frequency = null,
                rawFrequency = null,
                confidence = 0f,
                rms = 0f,
            )
        }
    }

    fun onBackground() {
        screenVisible = false
        controlScope.launch(start = CoroutineStart.UNDISPATCHED) { stopSession() }
        _uiState.update {
            it.copy(
                status = ListeningStatus.IDLE,
                frequency = null,
                rawFrequency = null,
                confidence = 0f,
                rms = 0f,
            )
        }
    }

    /** The Activity's destruction is bounded; cancellation also wakes blocking reads. */
    fun finishListening() {
        screenVisible = false
        userRequestedListening = false
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(2_000) { stopSession() }
        }
        _uiState.update { it.copy(status = ListeningStatus.IDLE) }
    }

    fun clearHistory() {
        _uiState.update { it.copy(history = emptyList()) }
    }

    fun setConcertA(value: Float) {
        _uiState.update { it.copy(concertA = value.coerceIn(415f, 466f)) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        // MainActivity.onDestroy performs final cleanup before this scope is cancelled.
    }

    companion object {
        const val HISTORY_SIZE = 100
    }
}
