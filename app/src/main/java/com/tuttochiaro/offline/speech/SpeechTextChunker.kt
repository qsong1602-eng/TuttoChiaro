package com.tuttochiaro.offline.speech

/** Splits at natural pauses when possible, preserving every input character. */
internal object SpeechTextChunker {
    fun split(text: String, maxLength: Int): List<String> {
        require(maxLength >= 2)
        if (text.isEmpty()) return emptyList()
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var limit = (start + maxLength).coerceAtMost(text.length)
            if (limit == text.length) {
                chunks += text.substring(start)
                break
            }
            // Java's length counts UTF-16 units; never leave half a surrogate in a chunk.
            if (Character.isHighSurrogate(text[limit - 1]) && Character.isLowSurrogate(text[limit])) limit--
            val preferredFrom = start + (limit - start) / 3
            val end = findSentenceBoundary(text, preferredFrom, limit)
                ?: findWordBoundary(text, preferredFrom, limit)
                ?: limit
            chunks += text.substring(start, end)
            start = end
        }
        return chunks
    }

    private fun findSentenceBoundary(text: String, from: Int, limit: Int): Int? {
        for (end in limit downTo from + 1) {
            val mark = text[end - 1]
            if (mark == '\n' || mark == '。' || mark == '！' || mark == '？' || mark == '；' ||
                mark == '!' || mark == '?' || mark == ';'
            ) return end
            if (mark == '.' && isSentencePeriod(text, end - 1)) return end
        }
        return null
    }

    private fun isSentencePeriod(text: String, index: Int): Boolean {
        if (index + 1 < text.length && !text[index + 1].isWhitespace()) return false
        if (index > 0 && index + 1 < text.length &&
            text[index - 1].isDigit() && text[index + 1].isDigit()
        ) return false
        val prefix = text.substring(0, index).takeLastWhile { it.isLetter() }.lowercase()
        return prefix !in setOf("mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc") &&
            prefix.length != 1
    }

    private fun findWordBoundary(text: String, from: Int, limit: Int): Int? {
        for (end in limit downTo from + 1) {
            if (text[end - 1].isWhitespace()) return end
        }
        return null
    }
}
