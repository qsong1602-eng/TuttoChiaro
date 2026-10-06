package com.tuttochiaro.offline.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.model.Language

/** Display names follow the selected application locale; nativeName remains useful in target chips. */
@Composable
fun Language.localizedDisplayName(): String = stringResource(
    when (this) {
        Language.CHINESE -> R.string.ui_language_chinese
        Language.ENGLISH -> R.string.ui_language_english
        Language.SPANISH -> R.string.ui_language_spanish
        Language.ITALIAN -> R.string.ui_language_italian
        Language.FRENCH -> R.string.ui_language_french
        Language.GERMAN -> R.string.ui_language_german
        Language.PORTUGUESE -> R.string.ui_language_portuguese
        Language.JAPANESE -> R.string.ui_language_japanese
        Language.KOREAN -> R.string.ui_language_korean
        Language.RUSSIAN -> R.string.ui_language_russian
        Language.THAI -> R.string.ui_language_thai
        Language.VIETNAMESE -> R.string.ui_language_vietnamese
    },
)
