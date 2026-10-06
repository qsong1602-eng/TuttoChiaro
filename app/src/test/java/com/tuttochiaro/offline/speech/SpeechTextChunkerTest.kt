package com.tuttochiaro.offline.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextChunkerTest {
    @Test fun splitsChineseAtSentenceEndsAndPreservesText() {
        val text = "你好，世界。今天下雨了！明天见。"
        val chunks = SpeechTextChunker.split(text, 9)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.first().endsWith("。"))
        assertTrue(chunks.all { it.length <= 9 })
    }

    @Test fun doesNotTreatDecimalOrAbbreviationAsSentenceEnd() {
        val text = "Dr. Smith paid 3.14 euros. Then left."
        val chunks = SpeechTextChunker.split(text, 26)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.first().endsWith("euros."))
    }

    @Test fun longNonBmpTextNeverSplitsSurrogatePairs() {
        val text = "😀".repeat(100) + "。" + "𠮷".repeat(100)
        val chunks = SpeechTextChunker.split(text, 31)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 31 })
        assertTrue(chunks.all { !Character.isLowSurrogate(it.first()) && !Character.isHighSurrogate(it.last()) })
    }
}
