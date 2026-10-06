package com.tuttochiaro.offline.i18n

import org.junit.Assert.*
import org.junit.Test

class AppLanguageTest {
    @Test fun savedAndSystemLocaleTagsMapToChoices() {
        for (choice in AppLanguage.entries) assertEquals(choice, AppLanguage.fromTag(choice.tag))
        assertEquals(AppLanguage.CHINESE, AppLanguage.fromTag("zh-Hans-CN"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTag("ES_mx"))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.fromTag("it-CH"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-GB"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("invalid"))
    }
}
