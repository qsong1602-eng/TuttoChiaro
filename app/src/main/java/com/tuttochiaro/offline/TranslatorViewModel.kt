package com.tuttochiaro.offline

import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.i18n.*

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuttochiaro.offline.data.OcrRepository
import com.tuttochiaro.offline.data.TranslationRepository
import com.tuttochiaro.offline.model.InputOrigin
import com.tuttochiaro.offline.model.Language
import com.tuttochiaro.offline.model.ResourceState
import com.tuttochiaro.offline.model.TranslatorUiState
import com.tuttochiaro.offline.speech.BundledSpeechEngine
import com.tuttochiaro.offline.speech.MicrophoneRecorder
import com.tuttochiaro.offline.speech.SpeechSegmentPipeline
import com.tuttochiaro.offline.model.SpeechTranslationSegment
import com.tuttochiaro.offline.llm.BundledModelStore
import com.tuttochiaro.offline.model.TranslationModel
import com.tuttochiaro.offline.model.SpeechModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.tuttochiaro.offline.speech.OfflineTextToSpeech
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class TranslatorViewModel(application: Application) : AndroidViewModel(application) {
    private val models = BundledModelStore(application)
    private val translationRepository = TranslationRepository(application, models)
    private val preferences = application.getSharedPreferences("offline-models", 0)
    private val inferenceGate = Mutex()
    private val captureGate = Mutex()
    private val speechEngine = BundledSpeechEngine(models)
    private var microphone: MicrophoneRecorder? = null
    private var speechJob: Job? = null
    private var speechRevision = 0
    val ocrRepository = OcrRepository()

    private val _uiState = MutableStateFlow(TranslatorUiState(
        translationModel = TranslationModel.entries.firstOrNull { it.id == preferences.getString("translation", "hy") } ?: TranslationModel.HY,
        speechModel = SpeechModel.entries.firstOrNull { it.id == preferences.getString("speech", "qwen_asr") } ?: SpeechModel.QWEN,
        onDeviceSpeechAvailable = true,
        continuousSpeech = preferences.getBoolean("continuousSpeech", false),
    ).withCommonLanguages(Language.commonFromTags(preferences.getStringSet("commonLanguages", null))))
    val uiState: StateFlow<TranslatorUiState> = _uiState.asStateFlow()

    private var translationJob: Job? = null
    private var resourceRefreshJob: Job? = null
    private var modelPreparationJob: Job? = null

    val textToSpeech = OfflineTextToSpeech(application) { refreshResources() }

    init {
        viewModelScope.launch {
            models.states.collect { states ->
                _uiState.update { state ->
                    val model = states[state.translationModel.id] ?: com.tuttochiaro.offline.llm.ModelPreparation()
                    val speech = states[state.speechModel.id]
                    state.copy(modelStates = states, modelReady = model.ready, modelProgress = model.progress,
                        speechMessage = if (state.isRecognizing && speech != null && !speech.ready) speech.message else state.speechMessage,
                        modelMessage = model.message, resourceStates = state.resourceStates.map {
                            it.copy(translationReady = model.ready, speechReady = states[state.speechModel.id]?.ready == true)
                        })
                }
            }
        }
        viewModelScope.launch {
            translationRepository.progress.collect { progress ->
                _uiState.update { it.copy(translationProgress = progress) }
            }
        }
        prepareBundledModel()
        refreshResources()
    }

    fun setPermissionState(microphone: Boolean? = null, camera: Boolean? = null) {
        _uiState.update {
            it.copy(
                microphonePermissionGranted = microphone ?: it.microphonePermissionGranted,
                cameraPermissionGranted = camera ?: it.cameraPermissionGranted,
            )
        }
    }

    fun updateInput(text: String) {
        stopListening()
        _uiState.update { it.withInput(text, InputOrigin.TEXT) }
        translationJob?.cancel()
        if (text.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), isTranslating = false) }
            return
        }
        translationJob = viewModelScope.launch {
            delay(550)
            translateInternal(null)
        }
    }

    fun selectSource(language: Language?) {
        if (language != null && language !in _uiState.value.commonLanguages) return
        stopListening()
        translationJob?.cancel()
        _uiState.update {
            it.withInput(it.input, it.origin).copy(sourceLanguage = language)
        }
        if (!_uiState.value.continuousSpeech && _uiState.value.input.isNotBlank()) translateNow()
    }

    fun toggleTarget(language: Language) {
        if (language !in _uiState.value.commonLanguages) return
        stopListening()
        translationJob?.cancel()
        _uiState.update { state ->
            val next = if (language in state.selectedTargets) {
                if (state.selectedTargets.size == 1) state.selectedTargets
                else state.selectedTargets - language
            } else {
                state.selectedTargets + language
            }
            state.copy(selectedTargets = next, results = emptyList(), isTranslating = false,
                translationProgress = null)
        }
        if (!_uiState.value.continuousSpeech && _uiState.value.input.isNotBlank()) translateNow()
    }

    fun toggleCommonLanguage(language: Language) {
        val current = _uiState.value.commonLanguages
        if (language in current && current.size <= 2) {
            showMessage(UiText(R.string.rt_min_languages))
            return
        }
        stopListening()
        translationJob?.cancel()
        textToSpeech.stop()
        val next = if (language in current) current - language else current + language
        preferences.edit { putStringSet("commonLanguages", next.map { it.tag }.toSet()) }
        _uiState.update { it.withCommonLanguages(next) }
    }

    fun translateNow(sourceHint: Language? = null) {
        if (_uiState.value.isListening || _uiState.value.isRecognizing || _uiState.value.continuousSessionActive) return
        translationJob?.cancel()
        translationJob = viewModelScope.launch { translateInternal(sourceHint) }
    }

    private suspend fun translateInternal(sourceHint: Language?) {
        val snapshot = _uiState.value
        val text = snapshot.input.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), isTranslating = false) }
            return
        }
        _uiState.update { it.copy(isTranslating = true, results = emptyList(), message = null) }
        try {
            val source = snapshot.resolvedSource(sourceHint) ?: translationRepository.detect(text)
            if (source == null) {
                _uiState.update {
                    it.copy(isTranslating = false, detectedLanguage = null,
                        message = UiText(R.string.rt_unknown_language))
                }
                return
            }
            _uiState.update { it.copy(detectedLanguage = source) }
            val targets = snapshot.selectedTargets - source
            val results = if (targets.isEmpty()) emptyList()
            else inferenceGate.withLock {
                translationRepository.translate(text, source, targets, snapshot.translationModel) { result ->
                    _uiState.update { it.copy(results = it.results + result) }
                }
            }
            _uiState.update {
                it.copy(
                    detectedLanguage = source,
                    results = results,
                    isTranslating = false,
                    message = if (targets.isEmpty()) UiText(R.string.rt_same_target) else null,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _uiState.update {
                it.copy(isTranslating = false, message = error.toUiText(R.string.rt_translation_failed))
            }
        }
    }

    fun startListening() {
        if (!_uiState.value.microphonePermissionGranted) { showMessage(UiText(R.string.rt_mic_permission)); return }
        stopListening()
        translationJob?.cancel()
        textToSpeech.stop()
        val recorder = MicrophoneRecorder()
        microphone = recorder
        val revision = ++speechRevision
        val snapshot = _uiState.value
        if (snapshot.continuousSpeech) {
            startContinuousSession(recorder, revision, snapshot)
            return
        }
        _uiState.update { it.withInput("", InputOrigin.SPEECH).copy(isListening = true, speechMessage = UiText(R.string.rt_recording)) }
        speechJob = viewModelScope.launch {
            try {
                val samples = captureGate.withLock { recorder.record() }
                _uiState.update { it.copy(isListening = false, isRecognizing = true, speechMessage = UiText(R.string.rt_preparing_asr)) }
                val result = inferenceGate.withLock {
                    translationRepository.unload()
                    speechEngine.recognize(samples, snapshot.speechModel, snapshot.sourceLanguage) { phase ->
                        if (revision == speechRevision) _uiState.update { it.copy(speechMessage = phase) }
                    }
                }
                currentCoroutineContext().ensureActive()
                if (revision == speechRevision) {
                    _uiState.update { it.withInput(result.text, InputOrigin.SPEECH).copy(
                        detectedLanguage = result.language, isRecognizing = false, speechMessage = null) }
                    translateNow(result.language)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (revision == speechRevision) showMessage(error.toUiText(R.string.rt_asr_failed))
            } finally {
                if (revision == speechRevision) _uiState.update { it.copy(isListening = false, isRecognizing = false, speechMessage = null) }
            }
        }
    }

    fun selectContinuousSpeech(enabled: Boolean) {
        if (enabled == _uiState.value.continuousSpeech) return
        stopListening()
        translationJob?.cancel()
        textToSpeech.stop()
        preferences.edit { putBoolean("continuousSpeech", enabled) }
        _uiState.update { it.copy(continuousSpeech = enabled, isTranslating = false,
            translationProgress = null, message = null) }
    }

    private fun startContinuousSession(recorder: MicrophoneRecorder, revision: Int, snapshot: TranslatorUiState) {
        _uiState.update { it.copy(speechSegments = emptyList(),
            continuousSessionActive = true, isListening = false, isRecognizing = true,
            speechMessage = UiText(R.string.rt_preparing_live)) }
        speechJob = viewModelScope.launch {
            var segmentNumber = 0
            try {
                // Avoid filling the audio queue while large assets are first extracted.
                models.prepareGroup(snapshot.speechModel.id)
                translationRepository.prepareModel(snapshot.translationModel)
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(isListening = true, isRecognizing = false,
                    speechMessage = UiText(R.string.rt_recording_live)) }
                SpeechSegmentPipeline().run(
                    capture = { emit ->
                        val end = captureGate.withLock { recorder.recordSegments(emit) }
                        if (revision == speechRevision) _uiState.update { it.copy(isListening = false,
                            message = when (end) {
                                MicrophoneRecorder.CaptureEnd.QUEUE_FULL -> UiText(R.string.rt_queue_full)
                                MicrophoneRecorder.CaptureEnd.LIMIT -> UiText(R.string.rt_recording_limit)
                                else -> it.message
                            }) }
                    },
                    process = { samples ->
                        currentCoroutineContext().ensureActive()
                        val id = ++segmentNumber
                        try {
                            val recognized = inferenceGate.withLock {
                                translationRepository.unload()
                                // A forced split can leave a voiced tail shorter than the
                                // recognizer's 100 ms minimum. Pad silence instead of losing it.
                                val audio = if (samples.size < 1600) samples.copyOf(1600) else samples
                                speechEngine.recognize(audio, snapshot.speechModel, snapshot.sourceLanguage) { phase ->
                                    if (revision == speechRevision) _uiState.update {
                                        it.copy(isRecognizing = true, speechMessage = UiText(R.string.rt_segment_phase, id, phase))
                                    }
                                }
                            }
                            currentCoroutineContext().ensureActive()
                            val source = snapshot.sourceLanguage ?: recognized.language ?: translationRepository.detect(recognized.text)
                            _uiState.update { it.copy(
                                isRecognizing = false, isTranslating = true, speechMessage = UiText(R.string.rt_segment_translating, id),
                                speechSegments = it.speechSegments + SpeechTranslationSegment(id, recognized.text, source)) }
                            checkUi(source != null) { UiText(R.string.rt_segment_unknown) }
                            val targets = snapshot.selectedTargets - source
                            checkUi(targets.isNotEmpty()) { UiText(R.string.rt_segment_same) }
                            inferenceGate.withLock {
                                translationRepository.translate(recognized.text, source, targets, snapshot.translationModel) { result ->
                                    if (revision == speechRevision) _uiState.update { state -> state.copy(
                                        speechSegments = state.speechSegments.map { row ->
                                            if (row.id == id) row.copy(translations = row.translations + result) else row
                                        }) }
                                }
                            }
                            currentCoroutineContext().ensureActive()
                            _uiState.update { state -> state.copy(isTranslating = false,
                                speechSegments = state.speechSegments.map { if (it.id == id) it.copy(complete = true) else it }) }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            if (revision == speechRevision) _uiState.update { state ->
                                val message = error.toUiText(R.string.rt_segment_failed)
                                val rows = if (state.speechSegments.any { it.id == id }) state.speechSegments.map {
                                    if (it.id == id) it.copy(error = message, complete = true) else it
                                } else state.speechSegments + SpeechTranslationSegment(id, "", null, error = message, complete = true)
                                state.copy(speechSegments = rows, isRecognizing = false, isTranslating = false)
                            }
                        }
                        if (revision == speechRevision) _uiState.update { it.copy(
                            speechMessage = if (it.isListening) UiText(R.string.rt_keep_listening) else UiText(R.string.rt_remaining_speech)) }
                    },
                )
                if (segmentNumber == 0 && revision == speechRevision) showMessage(UiText(R.string.rt_no_speech))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (revision == speechRevision) showMessage(error.toUiText(R.string.rt_live_failed))
            } finally {
                recorder.cancel()
                if (revision == speechRevision) _uiState.update { it.copy(continuousSessionActive = false,
                    isListening = false, isRecognizing = false, isTranslating = false, speechMessage = null) }
            }
        }
    }

    fun finishListening() { microphone?.finish() }
    fun stopListening() {
        ++speechRevision
        microphone?.cancel()
        speechJob?.cancel()
        _uiState.update { state -> state.copy(isListening = false, isRecognizing = false,
            isTranslating = if (state.continuousSessionActive) false else state.isTranslating,
            continuousSessionActive = false, speechMessage = null,
            speechSegments = state.speechSegments.map { if (it.complete) it else it.copy(complete = true, error = UiText(R.string.rt_segment_cancelled)) }) }
    }

    fun selectTranslationModel(model: TranslationModel) {
        if (model == _uiState.value.translationModel) return
        stopListening()
        translationJob?.cancel()
        textToSpeech.stop()
        preferences.edit { putString("translation", model.id) }
        _uiState.update { state ->
            val ready = state.modelStates[model.id]
            state.withInput(state.input, state.origin).copy(translationModel = model,
                modelReady = ready?.ready == true, modelMessage = ready?.message ?: UiText(R.string.rt_waiting_local))
        }
        viewModelScope.launch { inferenceGate.withLock { translationRepository.unload() } }
        prepareBundledModel()
        refreshResources()
        // Model choice belongs to Settings; translating starts when the user returns.
    }

    fun selectSpeechModel(model: SpeechModel) {
        stopListening()
        preferences.edit { putString("speech", model.id) }
        _uiState.update { it.copy(speechModel = model) }
        refreshResources()
    }

    fun showCamera() {
        if (_uiState.value.cameraPermissionGranted) {
            stopListening()
            _uiState.update { it.copy(showCamera = true, message = null) }
        } else {
            showMessage(UiText(R.string.rt_camera_permission))
        }
    }

    fun closeCamera() = _uiState.update { it.copy(showCamera = false, isOcrBusy = false) }

    fun beginOcr(): Boolean {
        val gate = AtomicBoolean(false)
        _uiState.update { state ->
            if (state.isOcrBusy || !state.showCamera) state else {
                gate.set(true)
                state.copy(isOcrBusy = true, message = null)
            }
        }
        return gate.get()
    }

    fun onOcrResult(text: String) {
        if (!_uiState.value.showCamera) return
        if (text.isBlank()) {
            _uiState.update { it.copy(isOcrBusy = false, message = UiText(R.string.rt_ocr_empty)) }
            return
        }
        _uiState.update {
            it.copy(
                showCamera = false,
                isOcrBusy = false,
                input = text,
                origin = InputOrigin.OCR,
                detectedLanguage = null,
                sourceLanguage = null,
                results = emptyList(),
                selectedTargets = it.selectedTargets,
            )
        }
        translateNow()
    }

    fun onOcrError(error: Throwable) {
        _uiState.update {
            it.copy(isOcrBusy = false, message = error.toUiText(R.string.rt_ocr_failed))
        }
    }

    fun speak(text: String, language: Language) {
        if (_uiState.value.isListening || _uiState.value.continuousSessionActive || _uiState.value.isRecognizing) {
            showMessage(UiText(R.string.rt_finish_before_speaking))
            return
        }
        textToSpeech.speak(text, language).onFailure { showMessage(it.toUiText(R.string.rt_tts_failed)) }
    }

    fun finishOcrPreview() = _uiState.update { it.copy(isOcrBusy = false) }

    fun setTtsRate(rate: Float) { textToSpeech.setSpeechRate(rate); refreshResources() }
    fun selectTtsVoice(language: Language, id: String?) {
        runCatching { textToSpeech.selectVoice(language, id) }.onFailure { showMessage(it.toUiText(R.string.rt_voice_unavailable)) }
        refreshResources()
    }

    fun previewTts(language: Language) = speak(language.previewText, language)

    fun showResources(show: Boolean) {
        if (show) {
            stopListening()
            translationJob?.cancel()
            textToSpeech.stop()
        }
        _uiState.update { it.copy(showResources = show, message = null, isTranslating = false, translationProgress = null) }
        if (show) refreshResources()
    }

    fun refreshResources() {
        resourceRefreshJob?.cancel()
        resourceRefreshJob = viewModelScope.launch {
            val voices = textToSpeech.readyLanguages()
            _uiState.update { state ->
                state.copy(onDeviceSpeechAvailable = true, ttsRate = textToSpeech.speechRate(),
                    ttsVoices = Language.all.associateWith { textToSpeech.offlineVoices(it) },
                    ttsSelectedVoices = Language.all.associateWith { textToSpeech.selectedVoiceId(it) },
                    resourceStates = Language.all.map { language ->
                    ResourceState(language, state.modelReady, state.modelStates[state.speechModel.id]?.ready == true, language in voices)
                })
            }
        }
    }

    fun prepareBundledModel() {
        modelPreparationJob?.cancel()
        val selected = _uiState.value.translationModel
        modelPreparationJob = viewModelScope.launch {
            _uiState.update { it.copy(isPreparingModel = true) }
            try {
                translationRepository.prepareModel(selected)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showMessage(error.toUiText(R.string.rt_model_prepare_failed))
            } finally {
                _uiState.update { it.copy(isPreparingModel = false) }
            }
        }
    }

    fun prepareSpeechModel() {
        val selected = _uiState.value.speechModel
        viewModelScope.launch {
            try { models.prepareGroup(selected.id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showMessage(error.toUiText(R.string.rt_speech_prepare_failed)) }
        }
    }

    fun showMessage(message: UiText) = _uiState.update { it.copy(message = message) }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    override fun onCleared() {
        stopListening()
        textToSpeech.close()
        translationRepository.close()
        ocrRepository.close()
        super.onCleared()
    }
}
