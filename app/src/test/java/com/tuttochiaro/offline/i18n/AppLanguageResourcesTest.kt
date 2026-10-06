package com.tuttochiaro.offline.i18n

import android.content.Context
import android.content.res.Configuration
import android.app.LocaleManager
import android.os.LocaleList
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.model.Language
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppLanguageResourcesTest {
    private fun localized(tag: String): Context {
        val app = RuntimeEnvironment.getApplication()
        return app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tag))
        })
    }

    @Test fun allFourLocalesResolveScreenLabelsAndPluralCounts() {
        val expected = mapOf("en" to "Settings", "zh" to "设置", "es" to "Ajustes", "it" to "Impostazioni")
        for ((tag, title) in expected) {
            val context = localized(tag)
            assertEquals(tag, title, context.getString(R.string.ui_settings))
            assertTrue(context.getString(R.string.ui_interface_language).isNotBlank())
            for (count in listOf(1, 2, 12)) {
                assertTrue(context.resources.getQuantityString(R.plurals.ui_results_count, count, count).contains(count.toString()))
                assertTrue(context.resources.getQuantityString(R.plurals.ui_segments_count, count, count).contains(count.toString()))
            }
        }
        assertEquals("1 translation", localized("en").resources.getQuantityString(R.plurals.ui_results_count, 1, 1))
        assertEquals("2 translations", localized("en").resources.getQuantityString(R.plurals.ui_results_count, 2, 2))
    }

    @Test fun retainedStatusAndNestedArgumentsReResolveWithoutChangingUserText() {
        val text = UiText(R.string.rt_segment_phase, 3, UiText(R.string.rt_translation_progress,
            UiText(R.string.rt_generating, "HY"), Language.ITALIAN, 1, 2))
        val values = listOf("en", "zh", "es", "it").map { text.resolve(localized(it)) }
        assertEquals(4, values.toSet().size)
        values.forEach { assertTrue(it.contains("HY")); assertTrue(it.contains("1/2")); assertFalse(it.contains("%1")) }
        val external = UiText.literal("你好 / Grazie / Hola / Thanks")
        for (tag in listOf("en", "zh", "es", "it")) assertEquals(external.rawText, external.resolve(localized(tag)))
        assertEquals("Offline model ready.", UiText(R.string.rt_model_ready).resolve(localized("en")))
        assertEquals("离线模型已就绪", UiText(R.string.rt_model_ready).resolve(localized("zh")))
    }

    @Test fun unsupportedSystemLocaleFallsBackAndRegionalLocalesResolve() {
        assertEquals("Settings", localized("ru").getString(R.string.ui_settings))
        assertEquals("Ajustes", localized("es-MX").getString(R.string.ui_settings))
        assertEquals("设置", localized("zh-CN").getString(R.string.ui_settings))
        assertEquals("Impostazioni", localized("it-CH").getString(R.string.ui_settings))
        val fallback = UiText(R.string.rt_language_voice_missing, Language.ITALIAN).resolve(localized("ru"))
        assertTrue(fallback.contains("Italian"))
    }

    @Test fun applicationLocaleSurvivesActivityReplacementAndSystemChoiceClearsOverride() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(LocaleManager::class.java)
        val previous = manager.applicationLocales
        val controller = Robolectric.buildActivity(LocaleHostActivity::class.java).setup()
        try {
            for (tag in listOf("es", "it", "zh", "en")) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                assertEquals(tag, manager.applicationLocales.toLanguageTags())
                assertEquals(tag, AppCompatDelegate.getApplicationLocales().toLanguageTags())
            }
            controller.pause().stop().destroy()
            val replacement = Robolectric.buildActivity(LocaleHostActivity::class.java).setup()
            try {
                assertEquals("en", AppCompatDelegate.getApplicationLocales().toLanguageTags())
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                assertTrue(manager.applicationLocales.isEmpty)
            } finally { replacement.pause().stop().destroy() }
        } finally { manager.applicationLocales = previous }
    }

    class LocaleHostActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: android.os.Bundle?) {
            setTheme(R.style.Theme_TuttoChiaro)
            super.onCreate(savedInstanceState)
        }
    }
}
