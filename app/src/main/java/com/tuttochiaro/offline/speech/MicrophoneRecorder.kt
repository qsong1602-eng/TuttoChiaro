package com.tuttochiaro.offline.speech

import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.i18n.*

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Bounded RAM-only capture. Cancelling discards audio; finishing submits it. */
class MicrophoneRecorder {
    enum class CaptureEnd { FINISHED, LIMIT, QUEUE_FULL, CANCELLED }

    @Volatile private var finishRequested = false
    @Volatile private var cancelled = false
    fun finish() { finishRequested = true }
    fun cancel() { cancelled = true; finishRequested = true }

    @SuppressLint("MissingPermission")
    suspend fun record(): FloatArray = withContext(Dispatchers.IO) {
        val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        checkUi(minimum > 0) { UiText(R.string.rt_audio_format) }
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 6400))
        try {
            checkUi(recorder.state == AudioRecord.STATE_INITIALIZED) { UiText(R.string.rt_mic_open) }
            recorder.startRecording()
            checkUi(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { UiText(R.string.rt_mic_busy) }
            val output = FloatArray(320000)
            val buffer = ShortArray(1600)
            var count = 0
            while (!finishRequested && count < output.size) {
                currentCoroutineContext().ensureActive()
                val n = recorder.read(buffer, 0, minOf(buffer.size, output.size - count))
                checkUi(n > 0) { UiText(R.string.rt_record_interrupted) }
                for (i in 0 until n) output[count++] = buffer[i] / 32768f
            }
            currentCoroutineContext().ensureActive()
            checkUi(!cancelled) { UiText(R.string.rt_record_cancelled) }
            output.copyOf(count)
        } finally {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            recorder.release()
        }
    }

    /**
     * Continuously captures at 16 kHz for at most 120 seconds. Each completed
     * phrase is delivered on the IO dispatcher. Returning false from [onSegment]
     * stops capture, allowing a bounded consumer queue to reject excess work.
     * Cancellation of the coroutine throws and discards the pending phrase.
     */
    @SuppressLint("MissingPermission")
    suspend fun recordSegments(onSegment: (FloatArray) -> Boolean): CaptureEnd = withContext(Dispatchers.IO) {
        val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        checkUi(minimum > 0) { UiText(R.string.rt_audio_format) }
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 6400))
        val segmenter = SpeechSegmenter()
        try {
            checkUi(recorder.state == AudioRecord.STATE_INITIALIZED) { UiText(R.string.rt_mic_open) }
            recorder.startRecording()
            checkUi(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { UiText(R.string.rt_mic_busy) }
            val buffer = ShortArray(1600)
            val maxSamples = SpeechSegmenter.SAMPLE_RATE * 120
            var count = 0
            while (!finishRequested && !cancelled && count < maxSamples) {
                currentCoroutineContext().ensureActive()
                val n = recorder.read(buffer, 0, minOf(buffer.size, maxSamples - count))
                currentCoroutineContext().ensureActive()
                checkUi(n > 0) { UiText(R.string.rt_record_interrupted) }
                count += n
                if (!segmenter.accept(buffer, n, onSegment)) {
                    segmenter.cancel()
                    return@withContext CaptureEnd.QUEUE_FULL
                }
            }
            currentCoroutineContext().ensureActive()
            if (cancelled) {
                segmenter.cancel()
                CaptureEnd.CANCELLED
            } else if (!segmenter.finish(onSegment)) {
                CaptureEnd.QUEUE_FULL
            } else if (count >= maxSamples) {
                CaptureEnd.LIMIT
            } else {
                CaptureEnd.FINISHED
            }
        } finally {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            recorder.release()
        }
    }
}
