package com.tuttochiaro.offline.speech

import com.tuttochiaro.offline.model.Language
import org.junit.Assert.*
import org.junit.Test

class QwenSpeechOutputTest {
    @Test fun separatesLanguageMetadataWithoutLosingChineseCharacters() {
        val result = QwenSpeechOutput.parse("language Chinese<asr_text>这并不是告别。这是新篇章的开始。", null)
        assertEquals(Language.CHINESE, result.language)
        assertEquals("这并不是告别。这是新篇章的开始。", result.text)
    }
    @Test fun preservesPlainTextWhenLanguageWasProvidedInPrompt() {
        val result = QwenSpeechOutput.parse("  Vorrei due biglietti.  ", Language.ITALIAN)
        assertEquals(Language.ITALIAN, result.language)
        assertEquals("Vorrei due biglietti.", result.text)
    }
    @Test fun rejectsEmptyOrControlOnlyOutput() {
        for (text in listOf("", "language Chinese<asr_text>", "<|im_start|>")) {
            try {
                QwenSpeechOutput.parse(text, null)
                fail("Empty/control-only output accepted")
            } catch (_: IllegalStateException) { }
        }
    }
}
