package com.tuttochiaro.offline.speech

import org.junit.Assert.*
import org.junit.Test

class SpeechSegmenterTest {
    private val sampleRate = SpeechSegmenter.SAMPLE_RATE

    @Test fun silenceAndShortNoiseDoNotProduceSegments() {
        val segments = mutableListOf<FloatArray>()
        val segmenter = SpeechSegmenter()
        feed(segmenter, ShortArray(sampleRate * 2), segments)
        feed(segmenter, ShortArray(sampleRate / 10) { 9000 }, segments)
        feed(segmenter, ShortArray(sampleRate), segments)
        assertTrue(segmenter.finish { segments.add(it) }.also { assertTrue(it) })
        assertTrue(segments.isEmpty())
    }

    @Test fun silenceEndsPhraseAndVoiceIsNeverDuplicated() {
        val segments = mutableListOf<FloatArray>()
        val segmenter = SpeechSegmenter()
        feed(segmenter, ShortArray(sampleRate / 5), segments)
        feed(segmenter, ShortArray(sampleRate / 2) { 9000 }, segments)
        feed(segmenter, ShortArray(sampleRate), segments)
        feed(segmenter, ShortArray(sampleRate / 2) { 12000 }, segments)
        feed(segmenter, ShortArray(sampleRate), segments)
        assertEquals(2, segments.size)
        assertEquals(sampleRate / 2, segments.sumOf { chunk -> chunk.count { it > 0.2f && it < 0.3f } })
        assertEquals(sampleRate / 2, segments.sumOf { chunk -> chunk.count { it > 0.3f } })
        assertTrue(segments.all { it.size <= sampleRate * 8 })
    }

    @Test fun longSpeechSplitsAtEightSecondsWithoutLosingSamples() {
        val segments = mutableListOf<FloatArray>()
        val segmenter = SpeechSegmenter()
        feed(segmenter, ShortArray(sampleRate * 17) { 9000 }, segments)
        assertTrue(segmenter.finish { segments.add(it) })
        assertEquals(listOf(sampleRate * 8, sampleRate * 8, sampleRate), segments.map { it.size })
        assertEquals(sampleRate * 17, segments.sumOf { it.size })
    }

    @Test fun shortContinuationAfterForcedSplitIsKeptOnFinish() {
        val segments = mutableListOf<FloatArray>()
        val segmenter = SpeechSegmenter()
        feed(segmenter, ShortArray(sampleRate * 8 + sampleRate / 10) { 9000 }, segments)
        assertTrue(segmenter.finish { segments.add(it) })
        assertEquals(listOf(sampleRate * 8, sampleRate / 10), segments.map { it.size })
    }

    @Test fun realPauseAfterForcedSplitRestoresMinimumVoiceDuration() {
        val segments = mutableListOf<FloatArray>()
        val segmenter = SpeechSegmenter()
        feed(segmenter, ShortArray(sampleRate * 8) { 9000 }, segments)
        feed(segmenter, ShortArray(sampleRate), segments)
        feed(segmenter, ShortArray(sampleRate / 10) { 9000 }, segments)
        feed(segmenter, ShortArray(sampleRate), segments)
        assertTrue(segmenter.finish { segments.add(it) })
        assertEquals(1, segments.size)
    }

    @Test fun finishFlushesEligibleTailAndCancelDiscardsIt() {
        val finished = mutableListOf<FloatArray>()
        val segmenter = SpeechSegmenter()
        feed(segmenter, ShortArray(sampleRate / 2) { 9000 }, finished)
        assertTrue(finished.isEmpty())
        assertTrue(segmenter.finish { finished.add(it) })
        assertEquals(1, finished.size)
        assertEquals(sampleRate / 2, finished.single().size)

        val cancelled = SpeechSegmenter()
        feed(cancelled, ShortArray(sampleRate / 2) { 9000 }, mutableListOf())
        cancelled.cancel()
        assertTrue(cancelled.finish { fail("cancelled audio was emitted"); true })
    }

    @Test fun callbackRejectionStopsDelivery() {
        val segmenter = SpeechSegmenter()
        var calls = 0
        val accepted = segmenter.accept(ShortArray(sampleRate * 9) { 9000 }, sampleRate * 9) {
            calls++
            false
        }
        assertFalse(accepted)
        assertEquals(1, calls)
    }

    private fun feed(segmenter: SpeechSegmenter, samples: ShortArray, segments: MutableList<FloatArray>) {
        var offset = 0
        while (offset < samples.size) {
            val size = minOf(1600, samples.size - offset)
            assertTrue(segmenter.accept(samples.copyOfRange(offset, offset + size), size) { segments.add(it) })
            offset += size
        }
    }
}
