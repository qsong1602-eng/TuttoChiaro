package com.tuttochiaro.offline.llm

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tuttochiaro.offline.data.TranslationRepository
import com.tuttochiaro.offline.model.Language
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledHyTest {
    @Test fun bundledModelRunsAllTwelveDirectionsWithoutInternetPermission() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals(PackageManager.PERMISSION_DENIED,
            context.packageManager.checkPermission(android.Manifest.permission.INTERNET, context.packageName))
        val samples = mapOf(
            Language.ENGLISH to "I need two tickets to the airport tomorrow morning, not return tickets.",
            Language.CHINESE to "我要两张明天上午去机场的车票，不要往返票。",
            Language.SPANISH to "Necesito dos billetes al aeropuerto para mañana por la mañana, no de ida y vuelta.",
            Language.ITALIAN to "Vorrei due biglietti per l'aeroporto per domani mattina, non di andata e ritorno.",
        )
        // On OriginOS, the test host must launch MainActivity after instrumentation starts
        // and keep the phone unlocked. Background-only timings are not representative.
        TranslationRepository(context).use { repository ->
            repository.prepareModel()
            assertTrue(repository.modelState.value.ready)
            for ((source, input) in samples) {
                for (target in Language.defaults.filter { it != source }) {
                    val start = System.currentTimeMillis()
                    val results = repository.translate(input, source, setOf(target))
                    assertEquals(1, results.size)
                    assertEquals(target, results.single().language)
                    val output = results.single().text
                    assertTrue(output.isNotBlank())
                    assertNotEquals(input, output)
                    instrumentation.sendStatus(0, Bundle().apply {
                        putString("stream", "\nHY ${source.tag}->${target.tag} ${System.currentTimeMillis() - start}ms: $output\n")
                    })
                }
            }
        }
    }
}
