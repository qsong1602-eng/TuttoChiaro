package com.tuttochiaro.offline.speech

import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.i18n.*

import com.tuttochiaro.offline.model.Language

object QwenSpeechOutput {
    fun parse(raw: String, hint: Language?): SpeechText {
        val marker = "<asr_text>"
        val index = raw.indexOf(marker)
        val prefix = if (index >= 0) raw.substring(0, index).trim() else ""
        val language = if (prefix.startsWith("language ")) Language.fromTag(prefix.removePrefix("language ").trim()) else hint
        val text = (if (index >= 0) raw.substring(index + marker.length) else raw).trim()
        checkUi(text.isNotBlank() && !text.contains("<|")) { UiText(R.string.rt_asr_unclear) }
        return SpeechText(text, language)
    }
}
