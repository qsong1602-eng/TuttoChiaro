package com.tuttochiaro.offline.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageTest {
    @Test
    fun mapsSupportedBcp47Tags() {
        assertEquals(Language.CHINESE, Language.fromTag("zh-Hans-CN"))
        assertEquals(Language.CHINESE, Language.fromTag("cmn"))
        assertEquals(Language.ENGLISH, Language.fromTag("en-GB"))
        assertEquals(Language.SPANISH, Language.fromTag("es-MX"))
        assertEquals(Language.ITALIAN, Language.fromTag("it-IT"))
        assertEquals(Language.CHINESE, Language.fromTag("cmn-Hans-CN"))
        assertEquals(Language.ENGLISH, Language.fromTag(" EN_us "))
    }

    @Test
    fun rejectsLanguagesOutsideTravelSet() {
        assertNull(Language.fromTag("unknown-XX"))
        assertNull(Language.fromTag(null))
        assertNull(Language.fromTag("klingon"))
        assertNull(Language.fromTag("ita"))
        assertNull(Language.fromTag(""))
    }

    @Test fun mapsEmbeddedRecognizerLanguageNames() {
        assertEquals(Language.ENGLISH, Language.fromTag("English"))
        assertEquals(Language.CHINESE, Language.fromTag("Chinese"))
        assertEquals(Language.SPANISH, Language.fromTag("Spanish"))
        assertEquals(Language.ITALIAN, Language.fromTag("Italian"))
    }

    @Test fun newLanguagesRoundTripThroughBothRecognizerFormats() {
        Language.all.forEach { language ->
            assertEquals(language, Language.fromTag(language.tag))
            assertEquals(language, Language.fromTag(language.englishName))
            assertEquals(language, Language.fromTag(language.mlKitCode))
        }
        assertEquals(Language.PORTUGUESE, Language.fromTag("pt-BR"))
        assertEquals(Language.FRENCH, Language.fromTag("fr-CA"))
    }

    @Test fun commonLanguagePreferencesSurviveCatalogChangesAndCorruptValues() {
        assertEquals(Language.defaults, Language.commonFromTags(null))
        assertEquals(Language.defaults, Language.commonFromTags(setOf("obsolete", "ja")))
        assertEquals(listOf(Language.FRENCH, Language.JAPANESE),
            Language.commonFromTags(setOf("ja-JP", "fr-FR", "unknown")))
    }
}
