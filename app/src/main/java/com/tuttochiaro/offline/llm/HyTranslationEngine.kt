package com.tuttochiaro.offline.llm

import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.i18n.*

import com.tuttochiaro.offline.model.TranslationModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.Closeable

class HyTranslationEngine(private val models: BundledModelStore) : Closeable {
    private val gate = Mutex()
    private var handle = 0L
    private var activeModel: TranslationModel? = null
    @Volatile private var closed = false

    suspend fun translate(prompt: String, selected: TranslationModel = TranslationModel.HY, onPhase: (UiText) -> Unit): String = gate.withLock {
        withContext(Dispatchers.Default) {
            checkUi(!closed) { UiText(R.string.rt_engine_closed) }
            currentCoroutineContext().ensureActive()
            val model = models.prepareGroup(selected.id).resolve(selected.file)
            if (activeModel != selected && handle != 0L) { NativeLlm.release(handle); handle = 0L }
            try {
                if (handle == 0L) {
                    onPhase(UiText(R.string.rt_model_loading_first, selected.label))
                    handle = NativeLlm.load(model.absolutePath, minOf(4, Runtime.getRuntime().availableProcessors()))
                    activeModel = selected
                    checkUi(handle != 0L) { UiText(R.string.rt_model_load_failed, selected.label) }
                }
                currentCoroutineContext().ensureActive()
                onPhase(UiText(R.string.rt_generating, selected.label))
                NativeLlm.begin(handle, prompt.toByteArray(Charsets.UTF_8), HyTranslationPrompt.MAX_OUTPUT_TOKENS, selected.ordinal)
                val output = ByteArrayOutputStream()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    checkUi(!closed) { UiText(R.string.rt_engine_closed) }
                    val piece = NativeLlm.next(handle) ?: break
                    output.write(piece)
                }
                currentCoroutineContext().ensureActive()
                output.toString("UTF-8").trim().also { checkUi(it.isNotBlank()) { UiText(R.string.rt_model_empty) } }
            } catch (error: LinkageError) {
                throw UiStateException(UiText(R.string.rt_engine_unavailable), error)
            }
        }
    }

    suspend fun unload() = withContext(Dispatchers.Default) {
        gate.withLock {
            if (handle != 0L) NativeLlm.release(handle)
            handle = 0L
            activeModel = null
        }
    }

    override fun close() {
        closed = true
        // Never free a native model while a cancelled prefill/decode still owns it.
        val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        cleanup.launch {
            try {
                gate.withLock {
                    if (handle != 0L) NativeLlm.release(handle)
                    handle = 0L
                }
            } finally { cleanup.cancel() }
        }
    }
}
