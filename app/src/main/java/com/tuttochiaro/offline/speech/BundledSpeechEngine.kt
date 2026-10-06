package com.tuttochiaro.offline.speech

import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.i18n.*

import com.k2fsa.sherpa.onnx.*
import com.tuttochiaro.offline.llm.BundledModelStore
import com.tuttochiaro.offline.llm.NativeLlm
import java.io.ByteArrayOutputStream
import com.tuttochiaro.offline.model.Language
import com.tuttochiaro.offline.model.SpeechModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SpeechText(val text: String, val language: Language?)

class BundledSpeechEngine(private val models: BundledModelStore) {
    private val gate = Mutex()
    suspend fun recognize(samples: FloatArray, model: SpeechModel, hint: Language?, phase: (UiText) -> Unit): SpeechText = gate.withLock {
        withContext(Dispatchers.Default) {
            requireUi(samples.size in 1600..320000) { UiText(R.string.rt_record_duration) }
            requireUi(samples.any { kotlin.math.abs(it) > 0.015f }) { UiText(R.string.rt_audio_quiet) }
            val dir = models.prepareGroup(model.id)
            fun path(name: String) = dir.resolve(name).absolutePath
            currentCoroutineContext().ensureActive()
            phase(UiText(R.string.rt_model_loading, model.label))
            if (model == SpeechModel.QWEN) {
                var handle = 0L
                try {
                    handle = NativeLlm.load(path("Qwen3-ASR-1.7B-Q8_0.gguf"), 4)
                    currentCoroutineContext().ensureActive()
                    phase(UiText(R.string.rt_qwen_recognizing))
                    NativeLlm.beginAudio(handle, path("mmproj-Qwen3-ASR-1.7B-bf16.gguf"), samples, hint?.englishName.orEmpty())
                    val output = ByteArrayOutputStream()
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val piece = NativeLlm.next(handle) ?: break
                        output.write(piece)
                    }
                    currentCoroutineContext().ensureActive()
                    return@withContext QwenSpeechOutput.parse(output.toString("UTF-8"), hint)
                } finally { if (handle != 0L) NativeLlm.release(handle) }
            }
            val config = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(encoder = path("turbo-encoder.int8.onnx"),
                    decoder = path("turbo-decoder.int8.onnx"), language = hint?.mlKitCode.orEmpty(), task = "transcribe"),
                tokens = path("turbo-tokens.txt"), numThreads = 4, provider = "cpu",
            )
            // Release before translation so both large engines never occupy memory together.
            val recognizer = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = config))
            try {
                currentCoroutineContext().ensureActive()
                phase(UiText(R.string.rt_model_recognizing, model.label))
                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(samples, 16000)
                    recognizer.decode(stream)
                    currentCoroutineContext().ensureActive()
                    val result = recognizer.getResult(stream)
                    val text = result.text.trim()
                    checkUi(text.isNotBlank()) { UiText(R.string.rt_asr_empty) }
                    SpeechText(text, Language.fromTag(result.lang))
                } finally { stream.release() }
            } finally { recognizer.release() }
        }
    }
}
