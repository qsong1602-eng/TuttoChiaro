package com.tuttochiaro.offline.llm

import com.tuttochiaro.offline.model.Language
import org.junit.Assert.*
import org.junit.Test

class HyTranslationPromptTest {
    @Test fun allCatalogDirectionsNameTheirSourceAndTarget() {
        val names = mapOf(Language.CHINESE to "Simplified Chinese", Language.ENGLISH to "English",
            Language.SPANISH to "Spanish", Language.ITALIAN to "Italian",
            Language.FRENCH to "French", Language.GERMAN to "German",
            Language.PORTUGUESE to "Portuguese", Language.JAPANESE to "Japanese",
            Language.KOREAN to "Korean", Language.RUSSIAN to "Russian",
            Language.THAI to "Thai", Language.VIETNAMESE to "Vietnamese")
        for (source in Language.all) for (target in Language.all.filter { it != source }) {
            val text = "Two tickets, not three. €12.50\nRoom 208"
            val prompt = HyTranslationPrompt.build(text, source, target)
            assertTrue(prompt.contains("from ${names[source]} into ${names[target]}"))
            assertTrue(prompt.endsWith(text))
        }
    }
    @Test fun rejectsBlankSameLanguageAndOversizedInput() {
        assertThrows(IllegalArgumentException::class.java) { HyTranslationPrompt.build(" ", Language.ENGLISH, Language.CHINESE) }
        assertThrows(IllegalArgumentException::class.java) { HyTranslationPrompt.build("hi", Language.ENGLISH, Language.ENGLISH) }
        assertThrows(IllegalArgumentException::class.java) { HyTranslationPrompt.build("x".repeat(1201), Language.ENGLISH, Language.CHINESE) }
    }
}
