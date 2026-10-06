package com.tuttochiaro.offline.speech

import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.i18n.*

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.core.content.edit
import com.tuttochiaro.offline.model.Language
import java.io.Closeable
import java.util.UUID

/** A voice that the current Android speech engine can use without a network connection. */
data class OfflineVoiceOption(val id: String, val displayLabel: String, val quality: Int)

class OfflineTextToSpeech(
    private val context: Context,
    private val onReadyChanged: () -> Unit,
) : Closeable {
    private val preferences = context.getSharedPreferences("offline_tts", Context.MODE_PRIVATE)
    private var initialized = false
    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context) { status ->
            initialized = status == TextToSpeech.SUCCESS
            onReadyChanged()
        }
    }

    fun isLanguageReady(language: Language): Boolean = findOfflineVoice(language) != null

    fun readyLanguages(): Set<Language> =
        if (!initialized) emptySet() else Language.all.filterTo(mutableSetOf(), ::isLanguageReady)

    fun offlineVoices(language: Language): List<OfflineVoiceOption> = voicesFor(language).map { voice ->
        OfflineVoiceOption(voice.name, "${voice.locale.toLanguageTag()} · ${voice.name}", voice.quality)
    }

    /** The saved choice, including one whose voice data has since been removed. */
    fun preferredVoiceId(language: Language): String? = preferences.getString("voice_${language.tag}", null)

    /** The voice actually used for playback; falls back when a saved voice is unavailable. */
    fun selectedVoiceId(language: Language): String? = findOfflineVoice(language)?.name

    /** Pass null to restore automatic selection. Only installed offline voices can be selected. */
    fun selectVoice(language: Language, id: String?) {
        requireUi(id == null || voicesFor(language).any { it.name == id }) {
            UiText(R.string.rt_language_voice_unavailable, language)
        }
        preferences.edit {
            if (id == null) remove("voice_${language.tag}") else putString("voice_${language.tag}", id)
        }
        onReadyChanged()
    }

    fun speechRate(): Float = preferences.getFloat("speech_rate", 1.0f).coerceIn(0.75f, 1.2f)

    fun setSpeechRate(rate: Float) {
        requireUi(rate.isFinite()) { UiText(R.string.rt_rate_invalid) }
        preferences.edit { putFloat("speech_rate", rate.coerceIn(0.75f, 1.2f)) }
    }

    fun speak(text: String, language: Language): Result<Unit> = runCatching {
        checkUi(initialized) { UiText(R.string.rt_tts_not_ready) }
        checkUi(text.isNotBlank()) { UiText(R.string.rt_tts_empty) }
        val voice = findOfflineVoice(language)
            ?: throw UiStateException(UiText(R.string.rt_language_voice_missing, language))
        val tts = requireNotNull(engine)
        checkUi(tts.setVoice(voice) == TextToSpeech.SUCCESS) { UiText(R.string.rt_voice_select_failed) }
        checkUi(tts.setSpeechRate(speechRate()) == TextToSpeech.SUCCESS) { UiText(R.string.rt_rate_failed) }
        checkUi(tts.setPitch(1.0f) == TextToSpeech.SUCCESS) { UiText(R.string.rt_pitch_failed) }
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .build(),
        )
        SpeechTextChunker.split(text, TextToSpeech.getMaxSpeechInputLength()).forEachIndexed { index, chunk ->
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (tts.speak(chunk, queueMode, null, UUID.randomUUID().toString()) == TextToSpeech.ERROR) {
                tts.stop()
                throw UiStateException(UiText(R.string.rt_tts_failed))
            }
        }
    }

    fun installVoiceDataIntent(): Intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)

    private fun voicesFor(language: Language): List<Voice> {
        if (!initialized) return emptyList()
        val target = language.locale
        return runCatching { engine?.voices }.getOrNull().orEmpty()
            .asSequence()
            .filter { !it.isNetworkConnectionRequired }
            .filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in it.features.orEmpty() }
            .filter { it.locale.language == target.language }
            .sortedWith(
                compareByDescending<Voice> { it.locale.toLanguageTag().equals(target.toLanguageTag(), true) }
                    .thenByDescending { it.locale.country.equals(target.country, true) }
                    .thenByDescending { it.quality }
                    .thenBy { it.latency }
                    .thenBy { it.name },
            )
            .toList()
    }

    private fun findOfflineVoice(language: Language): Voice? {
        val voices = voicesFor(language)
        val preferred = preferredVoiceId(language)
        return voices.firstOrNull { it.name == preferred } ?: voices.firstOrNull()
    }

    fun stop() { engine?.stop() }

    override fun close() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        initialized = false
    }
}
