package com.tuttochiaro.offline.llm

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tuttochiaro.offline.data.TranslationRepository
import com.tuttochiaro.offline.model.*
import com.tuttochiaro.offline.speech.BundledSpeechEngine
import com.tuttochiaro.offline.speech.MicrophoneRecorder
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class OfflineSuiteTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun report(text: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$text\n") })

    @Test fun microphoneOpensAndStopsWithoutSystemSpeechService() = runBlocking {
        org.junit.Assume.assumeTrue(instrumentation.targetContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        val microphone = MicrophoneRecorder()
        try {
            val capture = async { microphone.record() }
            delay(400)
            microphone.finish()
            val samples = capture.await()
            assertTrue(samples.isNotEmpty())
            // Do not transcribe, log, or save live microphone audio in this hardware check.
            report("MIC_CAPTURE_OK ${samples.size} samples; audio discarded")
        } finally { microphone.cancel() }
    }

    @Test fun bothSpeechModelsTranscribeFourLanguages() = runBlocking {
        val models = BundledModelStore(instrumentation.targetContext)
        val speech = BundledSpeechEngine(models)
        val fixtures = instrumentation.context.assets.open("speech/references.json").bufferedReader().use { JSONArray(it.readText()) }
        val errors = mutableListOf<String>()
        val filter = InstrumentationRegistry.getArguments().getString("speechModel")
        for (model in SpeechModel.entries.filter { filter == null || it.id == filter }) {
            for (index in 0 until fixtures.length()) {
                val fixture = fixtures.getJSONObject(index)
                val language = fixture.getString("language")
                val bytes = instrumentation.context.assets.open("speech/$language.pcm").use { it.readBytes() }
                val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val samples = FloatArray(shorts.remaining()) { shorts.get() / 32768f }
                val start = System.currentTimeMillis()
                report("ASR_START ${model.id} $language")
                try {
                    val result = speech.recognize(samples, model, null) { report(it.resolve(instrumentation.targetContext)) }
                    val error = errorRate(fixture.getString("reference"), result.text, language == "zh")
                    report("ASR_RESULT ${model.id} $language ${System.currentTimeMillis() - start}ms error=$error text=${result.text}")
                    // A broad smoke-test threshold catches gibberish, not small punctuation differences.
                    if (error > 0.65) errors += "${model.id}/$language error=$error"
                    if (language == "zh" && (!result.text.contains("并不是") || !result.text.contains("篇章"))) {
                        errors += "${model.id}/zh lost Chinese regression words"
                    }
                } catch (failure: Exception) {
                    errors += "${model.id}/$language: ${failure.message}"
                    report("ASR_ERROR ${errors.last()}")
                }
            }
        }
        assertTrue(errors.joinToString("\n"), errors.isEmpty())
    }

    @Test fun translationModelSwitchingRunsAllDirections() = runBlocking {
        val samples = mapOf(
            Language.CHINESE to "我要两张明天上午去机场的车票，不要往返票。",
            Language.ENGLISH to "I need two tickets to the airport tomorrow morning, not return tickets.",
            Language.SPANISH to "Necesito dos billetes al aeropuerto para mañana por la mañana, no de ida y vuelta.",
            Language.ITALIAN to "Vorrei due biglietti per l'aeroporto per domani mattina, non di andata e ritorno.",
        )
        TranslationRepository(instrumentation.targetContext).use { repository ->
            for ((pass, model) in listOf(TranslationModel.QWEN, TranslationModel.HY, TranslationModel.QWEN).withIndex()) {
                repository.prepareModel(model)
                val cases = if (pass == 2) samples.filterKeys { it == Language.ENGLISH } else samples
                for ((source, input) in cases) {
                    val start = System.currentTimeMillis()
                    val targets = if (pass == 2) setOf(Language.CHINESE) else Language.defaults.toSet() - source
                    val results = repository.translate(input, source, targets, model)
                    assertEquals(targets.size, results.size)
                    results.forEach {
                        assertTrue(it.text.isNotBlank())
                        assertNotEquals(input, it.text)
                        assertFalse(it.text.contains("<think>"))
                        report("TRANSLATION ${model.id} ${source.tag}->${it.language.tag}: ${it.text}")
                    }
                    report("TRANSLATION_TIME ${model.id} ${source.tag} ${System.currentTimeMillis() - start}ms")
                }
                repository.unload()
            }
        }
    }

    private fun errorRate(reference: String, hypothesis: String, characters: Boolean): Double {
        fun tokens(text: String): List<String> {
            val clean = text.lowercase().replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()
            return if (characters) clean.filterNot { it.isWhitespace() }.map { it.toString() }
            else clean.split(Regex("\\s+")).filter { it.isNotEmpty() }
        }
        val a = tokens(reference)
        val b = tokens(hypothesis)
        var row = IntArray(b.size + 1) { it }
        for (i in a.indices) {
            val next = IntArray(b.size + 1)
            next[0] = i + 1
            for (j in b.indices) next[j + 1] = minOf(next[j] + 1, row[j + 1] + 1, row[j] + if (a[i] == b[j]) 0 else 1)
            row = next
        }
        return row.last().toDouble() / a.size.coerceAtLeast(1)
    }
}
