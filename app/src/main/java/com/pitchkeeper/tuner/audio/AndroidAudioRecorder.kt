package com.pitchkeeper.tuner.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface MicrophoneSource {
    fun start(scope: CoroutineScope, chunks: Channel<FloatArray>)
    suspend fun stop()
}

/** One blocking AudioRecord thread; analysis runs on a separate dispatcher. */
class AndroidAudioRecorder(
    private val sampleRate: Int = 44_100,
) : MicrophoneSource {
    private val lock = Any()
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start(scope: CoroutineScope, chunks: Channel<FloatArray>) {
        synchronized(lock) { check(recordingJob == null) { "Recorder is already running" } }
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBuffer <= 0) throw AudioRecorderException("此设备不支持单声道浮点录音")
        val recorder = AudioRecord.Builder()
            // Avoid voice-call processing that can alter sustained musical tones.
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuffer * 2, CHUNK_SAMPLES * 8))
            .build()
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw AudioRecorderException("麦克风初始化失败，请检查录音权限")
        }
        synchronized(lock) {
            check(recordingJob == null) { "Recorder is already running" }
            audioRecord = recorder
        }
        try {
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "麦克风未能启动，可能正被其他应用占用"
            }
        } catch (error: Exception) {
            synchronized(lock) { audioRecord = null }
            recorder.release()
            throw error
        }
        val job = scope.launch(Dispatchers.IO) {
            try {
                val buffer = FloatArray(CHUNK_SAMPLES)
                while (isActive) {
                    var offset = 0
                    while (offset < buffer.size && isActive) {
                        val read = recorder.read(
                            buffer,
                            offset,
                            buffer.size - offset,
                            AudioRecord.READ_BLOCKING,
                        )
                        if (read < 0) throw AudioRecorderException("麦克风读取失败（错误 $read）")
                        offset += read
                    }
                    if (isActive) chunks.send(buffer.copyOf())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                chunks.close(AudioRecorderException("麦克风连接中断，请重新试唱。", error))
            } finally {
                synchronized(lock) {
                    if (audioRecord === recorder) audioRecord = null
                }
                try {
                    recorder.stop()
                } catch (_: IllegalStateException) {
                    // An audio-route change may stop the recorder before this thread exits.
                }
                recorder.release()
            }
        }
        synchronized(lock) { recordingJob = job }
    }

    override suspend fun stop() {
        val recorder: AudioRecord?
        val job: Job?
        synchronized(lock) {
            recorder = audioRecord
            audioRecord = null
            job = recordingJob
            recordingJob = null
        }
        // AudioRecord.stop() also unblocks READ_BLOCKING on supported API 26+ devices.
        try {
            recorder?.stop()
        } catch (_: IllegalStateException) {
            // Already stopped by the system or the recording thread.
        }
        job?.cancelAndJoin()
    }

    companion object {
        const val CHUNK_SAMPLES = 1024
    }
}

class AudioRecorderException(message: String, cause: Throwable? = null) : Exception(message, cause)
