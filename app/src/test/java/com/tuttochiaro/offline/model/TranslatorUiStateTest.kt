package com.tuttochiaro.offline.model

import org.junit.Assert.*
import org.junit.Test

class TranslatorUiStateTest {
    @Test fun removingCommonLanguagesDropsHiddenSelectionsAndKeepsAValidTarget() {
        val previous = TranslatorUiState(sourceLanguage = Language.ENGLISH,
            selectedTargets = setOf(Language.CHINESE),
            results = listOf(TranslationResult(Language.CHINESE, "旧译文")))
        val next = previous.withCommonLanguages(listOf(Language.FRENCH, Language.JAPANESE))
        assertNull(next.sourceLanguage)
        assertEquals(setOf(Language.FRENCH), next.selectedTargets)
        assertTrue(next.results.isEmpty())
        val manual = previous.copy(sourceLanguage = Language.FRENCH)
            .withCommonLanguages(listOf(Language.FRENCH, Language.JAPANESE))
        assertEquals(setOf(Language.JAPANESE), manual.selectedTargets)
    }

    @Test fun addingCommonLanguageDoesNotSelectItAsATranslationTarget() {
        val previous = TranslatorUiState()
        val next = previous.withCommonLanguages(Language.defaults + Language.FRENCH)
        assertEquals(previous.selectedTargets, next.selectedTargets)
    }

    @Test fun editingOrdinaryInputPreservesContinuousHistory() {
        val previous = TranslatorUiState(speechSegments = listOf(
            SpeechTranslationSegment(1, "Bonjour", Language.FRENCH, complete = true)))
        assertEquals(previous.speechSegments, previous.withInput("Hola", InputOrigin.TEXT).speechSegments)
    }

    @Test(expected = IllegalArgumentException::class)
    fun commonLanguagesRequireAtLeastTwoDistinctLanguages() {
        TranslatorUiState().withCommonLanguages(listOf(Language.ENGLISH, Language.ENGLISH))
    }

    @Test
    fun newTextOrUtteranceCannotDisplayOrSpeakPreviousTranslation() {
        val previous = TranslatorUiState(
            input = "Hola",
            detectedLanguage = Language.SPANISH,
            results = listOf(TranslationResult(Language.CHINESE, "你好")),
            isTranslating = true,
            message = com.tuttochiaro.offline.i18n.UiText.literal("old error"),
            sourceLanguage = Language.ITALIAN,
            selectedTargets = setOf(Language.CHINESE, Language.ENGLISH),
        )
        for (origin in InputOrigin.entries) {
            val next = previous.withInput("Grazie", origin)
            assertEquals("Grazie", next.input)
            assertTrue(next.results.isEmpty())
            assertNull(next.detectedLanguage)
            assertNull(next.message)
            assertFalse(next.isTranslating)
            assertEquals(origin, next.origin)
            assertEquals(previous.sourceLanguage, next.sourceLanguage)
            assertEquals(previous.selectedTargets, next.selectedTargets)
        }
    }

    @Test
    fun clearingInputAlsoClearsResults() {
        val previous = TranslatorUiState(results = listOf(TranslationResult(Language.ENGLISH, "old")))
        assertTrue(previous.withInput("", InputOrigin.TEXT).results.isEmpty())
    }

    @Test
    fun manualSourceWinsOverRecognizerButAutoUsesRecognizerHint() {
        val manual = TranslatorUiState(sourceLanguage = Language.SPANISH)
        assertEquals(Language.SPANISH, manual.resolvedSource(Language.ITALIAN))
        assertEquals(Language.SPANISH, manual.resolvedSource(null))
        val auto = manual.copy(sourceLanguage = null)
        assertEquals(Language.ITALIAN, auto.resolvedSource(Language.ITALIAN))
        assertNull(auto.resolvedSource(null))
    }
}
