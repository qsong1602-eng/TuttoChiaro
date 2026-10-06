package com.tuttochiaro.offline.llm

import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.i18n.*

import com.tuttochiaro.offline.model.Language

/** One source and one target per generation; history is never shared between translations. */
object HyTranslationPrompt {
    const val MAX_INPUT_CHARS = 1200
    const val MAX_OUTPUT_TOKENS = 768

    fun build(text: String, source: Language, target: Language): String {
        requireUi(text.isNotBlank()) { UiText(R.string.rt_input_required) }
        requireUi(text.length <= MAX_INPUT_CHARS) { UiText(R.string.rt_input_limit) }
        requireUi(source != target) { UiText(R.string.rt_target_different) }
        // User content is tokenized with parse_special=false in JNI, so pasted control
        // tokens cannot terminate this message and create a forged assistant role.
        return "Translate the following text from ${source.translationName} into ${target.translationName}. " +
            "Output only the translation, without explanations.\n\n$text"
    }

}
