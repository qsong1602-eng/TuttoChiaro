package com.tuttochiaro.offline.llm

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class BundledModelStoreTest {
    private fun isolatedContext(): Context {
        val test = InstrumentationRegistry.getInstrumentation().context
        // Instrumentation runs in the target app process, so its UID must own the temp path.
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "model-store-${UUID.randomUUID()}")
        return object : ContextWrapper(test) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = directory
        }
    }

    @Test fun reconstructsShardsAndRepairsCorruptCacheOnNextProcess() = runBlocking {
        val context = isolatedContext()
        try {
            val first = BundledModelStore(context)
            val file = first.prepareGroup("fixture").resolve("test.bin")
            assertEquals("abcdef", file.readText())
            assertTrue(first.states.value.getValue("fixture").ready)
            file.writeText("broken") // Same length; size alone must never validate cached weights.
            val nextProcess = BundledModelStore(context)
            assertEquals("abcdef", nextProcess.prepareGroup("fixture").resolve("test.bin").readText())
        } finally { context.noBackupFilesDir.deleteRecursively() }
    }

    @Test fun rejectsCorruptAndMissingAssetsWithoutPublishingReadyFile() = runBlocking {
        val context = isolatedContext()
        try {
            for (group in listOf("corrupt", "missing")) {
                val store = BundledModelStore(context)
                try {
                    store.prepareGroup(group)
                    fail("Invalid resource was accepted")
                } catch (_: IllegalStateException) {
                    assertFalse(store.states.value.getValue(group).ready)
                    assertFalse(File(context.noBackupFilesDir, "models/$group/test.bin").exists())
                    assertFalse(File(context.noBackupFilesDir, "models/$group/test.bin.partial").exists())
                }
            }
        } finally { context.noBackupFilesDir.deleteRecursively() }
    }
}
