package com.tuttochiaro.offline.model

import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.i18n.*

enum class InputOrigin { TEXT, SPEECH, OCR }

data class SpeechTranslationSegment(
    val id: Int,
    val original: String,
    val language: Language?,
    val translations: List<TranslationResult> = emptyList(),
    val error: UiText? = null,
    val complete: Boolean = false,
)

data class TranslationResult(
    val language: Language,
    val text: String,
)

data class ResourceState(
    val language: Language,
    val translationReady: Boolean = false,
    val speechReady: Boolean = false,
    val ttsReady: Boolean = false,
)

data class TranslatorUiState(
    val commonLanguages: List<Language> = Language.defaults,
    val continuousSpeech: Boolean = false,
    val continuousSessionActive: Boolean = false,
    val speechSegments: List<SpeechTranslationSegment> = emptyList(),
    val ttsRate: Float = 1f,
    val ttsVoices: Map<Language, List<com.tuttochiaro.offline.speech.OfflineVoiceOption>> = emptyMap(),
    val ttsSelectedVoices: Map<Language, String?> = emptyMap(),
    val translationModel: TranslationModel = TranslationModel.HY,
    val speechModel: SpeechModel = SpeechModel.QWEN,
    val isRecognizing: Boolean = false,
    val speechMessage: UiText? = null,
    val modelStates: Map<String, com.tuttochiaro.offline.llm.ModelPreparation> = emptyMap(),
    val input: String = "",
    val sourceLanguage: Language? = null,
    val detectedLanguage: Language? = null,
    val selectedTargets: Set<Language> = setOf(Language.CHINESE, Language.ENGLISH, Language.ITALIAN),
    val results: List<TranslationResult> = emptyList(),
    val origin: InputOrigin = InputOrigin.TEXT,
    val isTranslating: Boolean = false,
    val isListening: Boolean = false,
    val isOcrBusy: Boolean = false,
    val showCamera: Boolean = false,
    val showResources: Boolean = false,
    val message: UiText? = null,
    val microphonePermissionGranted: Boolean = false,
    val cameraPermissionGranted: Boolean = false,
    val onDeviceSpeechAvailable: Boolean = false,
    val resourceStates: List<ResourceState> = Language.all.map { ResourceState(it) },
    val modelReady: Boolean = false,
    val isPreparingModel: Boolean = false,
    val modelProgress: Int = 0,
    val modelMessage: UiText = UiText(R.string.rt_hy_bundled),
    val translationProgress: UiText? = null,
) {
    // A result belongs to an exact input, never to the next utterance or edit.
    fun withInput(text: String, inputOrigin: InputOrigin) = copy(
        input = text,
        origin = inputOrigin,
        detectedLanguage = null,
        results = emptyList(),
        isTranslating = false,
        translationProgress = null,
        message = null,
    )

    fun withCommonLanguages(languages: List<Language>): TranslatorUiState {
        val ordered = Language.all.filter { it in languages }
        requireUi(ordered.size >= 2) { UiText(R.string.rt_min_languages) }
        val source = sourceLanguage?.takeIf { it in ordered }
        val targets = selectedTargets.filterTo(linkedSetOf()) { it in ordered }
            .ifEmpty { linkedSetOf(ordered.first { it != source }) }
        return copy(commonLanguages = ordered, sourceLanguage = source,
            selectedTargets = targets, results = emptyList(), detectedLanguage = null,
            isTranslating = false, translationProgress = null)
    }

    fun resolvedSource(hint: Language?): Language? = sourceLanguage ?: hint
}
