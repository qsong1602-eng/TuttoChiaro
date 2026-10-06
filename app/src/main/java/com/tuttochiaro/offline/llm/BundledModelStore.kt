package com.tuttochiaro.offline.llm

import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.i18n.*

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class ModelPreparation(val ready: Boolean = false, val progress: Int = 0, val message: UiText = UiText(R.string.rt_waiting_offline))

/** Reconstructs verified files from assets in signed split APKs. */
class BundledModelStore(context: Context) {
    private val app = context.applicationContext
    private val gate = Mutex()
    private val verified = mutableSetOf<String>()
    private val mutableStates = MutableStateFlow<Map<String, ModelPreparation>>(emptyMap())
    val states = mutableStates.asStateFlow()
    private val mutableState = MutableStateFlow(ModelPreparation())
    val state = mutableState.asStateFlow()
    private fun publish(id: String, value: ModelPreparation) {
        mutableStates.value = mutableStates.value + (id to value)
        if (id == "hy") mutableState.value = value
    }
    suspend fun prepare(): File = prepareGroup("hy").resolve(NAME)

    suspend fun prepareGroup(id: String): File = gate.withLock {
        withContext(Dispatchers.IO) {
            val directory = File(app.noBackupFilesDir, if (id == "hy") "llm" else "models/$id")
            checkUi(directory.isDirectory || directory.mkdirs()) { UiText(R.string.rt_create_directory) }
            if (id in verified) return@withContext directory
            try {
                val manifest = app.assets.open("model-manifest.json").bufferedReader().use { JSONObject(it.readText()) }
                val files = manifest.getJSONObject("groups").getJSONArray(id)
                val total = (0 until files.length()).sumOf { files.getJSONObject(it).getLong("size") }
                var completed = 0L
                for (index in 0 until files.length()) {
                    currentCoroutineContext().ensureActive()
                    val spec = files.getJSONObject(index)
                    val file = File(directory, spec.getString("path"))
                    checkUi(file.canonicalPath.startsWith(directory.canonicalPath + File.separator)) { UiText(R.string.rt_model_path) }
                    file.parentFile!!.mkdirs()
                    val size = spec.getLong("size")
                    val expected = spec.getString("sha256")
                    publish(id, ModelPreparation(progress = (completed * 100 / total).toInt(), message = UiText(R.string.rt_verifying_model)))
                    if (file.isFile && file.length() == size && hash(file) == expected) {
                        completed += size
                        continue
                    }
                    val partial = File(file.path + ".partial")
                    partial.delete()
                    checkUi(StatFs(directory.path).availableBytes > size + 128L * 1024 * 1024) { UiText(R.string.rt_low_storage, size / 1_000_000 + 150) }
                    try {
                        val digest = MessageDigest.getInstance("SHA-256")
                        var copied = 0L
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(1024 * 1024)
                            val shards = spec.getJSONArray("assets")
                            for (shard in 0 until shards.length()) {
                                app.assets.open(shards.getString(shard)).use { input ->
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        copied += count
                                        checkUi(copied <= size) { UiText(R.string.rt_model_size) }
                                        output.write(buffer, 0, count)
                                        digest.update(buffer, 0, count)
                                        val p = ((completed + copied) * 100 / total).toInt().coerceAtMost(99)
                                        publish(id, ModelPreparation(progress = p, message = UiText(R.string.rt_model_progress, p)))
                                    }
                                }
                            }
                            output.fd.sync()
                        }
                        checkUi(copied == size && digest.hex() == expected) { UiText(R.string.rt_model_corrupt) }
                        currentCoroutineContext().ensureActive()
                        checkUi(!file.exists() || file.delete()) { UiText(R.string.rt_model_replace) }
                        checkUi(partial.renameTo(file)) { UiText(R.string.rt_model_save) }
                        completed += size
                    } finally { partial.delete() }
                }
                verified += id
                publish(id, ModelPreparation(true, 100, UiText(R.string.rt_model_ready)))
                directory
            } catch (cancelled: CancellationException) {
                publish(id, ModelPreparation(message = UiText(R.string.rt_model_paused)))
                throw cancelled
            } catch (missing: java.io.FileNotFoundException) {
                val error = UiStateException(UiText(R.string.rt_model_missing), missing)
                publish(id, ModelPreparation(message = error.uiText))
                throw error
            } catch (error: Exception) {
                publish(id, ModelPreparation(message = error.toUiText(R.string.rt_install_suite)))
                throw error
            }
        }
    }
    private suspend fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.hex()
    }
    private fun MessageDigest.hex() = digest().joinToString("") { "%02x".format(it) }
    companion object {
        const val NAME = "Hy-MT2-1.8B-Q4_K_M.gguf"
        const val SIZE = 1_133_080_448L
        const val SHA256 = "dc5f44fcf1fa496ee7ad725982c0c8c553a4de00259b53af84c4b89fb0c06699"
    }
}
