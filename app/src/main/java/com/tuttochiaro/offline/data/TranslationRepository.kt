package com.tuttochiaro.offline.data

import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.i18n.*

import android.content.Context
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.tuttochiaro.offline.llm.BundledModelStore
import com.tuttochiaro.offline.llm.HyTranslationEngine
import com.tuttochiaro.offline.llm.HyTranslationPrompt
import com.tuttochiaro.offline.model.TranslationModel
import com.tuttochiaro.offline.model.Language
import com.tuttochiaro.offline.model.TranslationResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.io.Closeable

/** Detection uses bundled ML Kit; translations use the selected bundled LLM. */
class TranslationRepository(context: Context, private val models: BundledModelStore = BundledModelStore(context)) : Closeable {
    private val identifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.35f).build(),
    )
    private val engine = HyTranslationEngine(models)
    val modelState = models.state
    private val mutableProgress = MutableStateFlow<UiText?>(null)
    val progress = mutableProgress.asStateFlow()

    suspend fun prepareModel(model: TranslationModel = TranslationModel.HY) { models.prepareGroup(model.id) }
    suspend fun unload() = engine.unload()

    suspend fun detect(text: String): Language? =
        if (text.isBlank()) null else Language.fromTag(identifier.identifyLanguage(text.trim()).await())

    suspend fun translate(
        text: String,
        source: Language,
        targets: Set<Language>,
        model: TranslationModel = TranslationModel.HY,
        onResult: (TranslationResult) -> Unit = {},
    ): List<TranslationResult> {
        val selected = Language.all.filter { it in targets && it != source }
        val results = mutableListOf<TranslationResult>()
        try {
            // Share one model and context instead of duplicating weights per target.
            for ((index, target) in selected.withIndex()) {
                currentCoroutineContext().ensureActive()
                val translated = engine.translate(HyTranslationPrompt.build(text.trim(), source, target), model) { phase ->
                    mutableProgress.value = UiText(R.string.rt_translation_progress, phase, target, index + 1, selected.size)
                }
                currentCoroutineContext().ensureActive()
                val result = TranslationResult(target, translated)
                results += result
                onResult(result)
            }
            return results
        } finally { mutableProgress.value = null }
    }

    override fun close() {
        engine.close()
        identifier.close()
    }
}
