package com.tuttochiaro.offline.speech

/**
 * A bounded, sample-count based PCM16 speech segmenter. It has no Android dependencies.
 * [accept] invokes its callback synchronously, and stops immediately if the callback declines.
 */
internal class SpeechSegmenter {
    private val frameSize = SAMPLE_RATE / 100 // 10 ms
    private val preRoll = ShortArray(SAMPLE_RATE / 5)
    private val segment = FloatArray(SAMPLE_RATE * 8)
    private var frame = ShortArray(frameSize)
    private var frameCount = 0
    private var preCount = 0
    private var preNext = 0
    private var segmentCount = 0
    private var voiceSamples = 0
    private var silenceSamples = 0
    private var continuation = false
    private var idleSilenceSamples = 0

    fun accept(pcm: ShortArray, length: Int, onSegment: (FloatArray) -> Boolean): Boolean {
        require(length in 0..pcm.size)
        for (i in 0 until length) {
            frame[frameCount++] = pcm[i]
            if (frameCount == frameSize && !processFrame(onSegment)) return false
        }
        return true
    }

    /** Submit an eligible last phrase, including a partial 10 ms frame. */
    fun finish(onSegment: (FloatArray) -> Boolean): Boolean {
        if (frameCount > 0 && !processFrame(onSegment)) return false
        return emit(onSegment)
    }

    /** Drop all buffered audio without invoking the callback. */
    fun cancel() {
        frameCount = 0
        preCount = 0
        preNext = 0
        segmentCount = 0
        voiceSamples = 0
        silenceSamples = 0
        continuation = false
        idleSilenceSamples = 0
    }

    private fun processFrame(onSegment: (FloatArray) -> Boolean): Boolean {
        val n = frameCount
        var sumSquares = 0.0
        for (i in 0 until n) {
            val sample = frame[i] / 32768.0
            sumSquares += sample * sample
        }
        val voiced = sumSquares / n >= ENERGY_THRESHOLD_SQUARED
        if (segmentCount == 0) {
            if (voiced) {
                // The ring contains only audio that preceded this frame.
                val oldest = if (preCount == preRoll.size) preNext else 0
                for (i in 0 until preCount) {
                    segment[segmentCount++] = preRoll[(oldest + i) % preRoll.size] / 32768f
                }
                appendFrame(n)
                voiceSamples = n
                silenceSamples = 0
                idleSilenceSamples = 0
                preCount = 0
                preNext = 0
            } else {
                idleSilenceSamples += n
                if (idleSilenceSamples >= SILENCE_SAMPLES) continuation = false
                for (i in 0 until n) {
                    preRoll[preNext] = frame[i]
                    preNext = (preNext + 1) % preRoll.size
                    if (preCount < preRoll.size) preCount++
                }
            }
        } else {
            appendFrame(n)
            if (voiced) {
                voiceSamples += n
                silenceSamples = 0
            } else {
                silenceSamples += n
            }
        }
        frameCount = 0
        if (segmentCount >= segment.size || silenceSamples >= SILENCE_SAMPLES) {
            return emit(onSegment, forcedSplit = segmentCount >= segment.size && silenceSamples < SILENCE_SAMPLES)
        }
        return true
    }

    private fun appendFrame(n: Int) {
        // The 10 ms frame divides the default 8 s cap exactly. A partial final
        // frame cannot overflow because a full frame would already have emitted.
        for (i in 0 until n) segment[segmentCount++] = frame[i] / 32768f
    }

    private fun emit(onSegment: (FloatArray) -> Boolean, forcedSplit: Boolean = false): Boolean {
        if (segmentCount == 0) return true
        val eligible = voiceSamples >= MIN_VOICE_SAMPLES || (continuation && voiceSamples > 0)
        val samples = if (eligible) segment.copyOf(segmentCount) else null
        segmentCount = 0
        voiceSamples = 0
        silenceSamples = 0
        preCount = 0
        preNext = 0
        continuation = forcedSplit && eligible
        idleSilenceSamples = 0
        return samples == null || onSegment(samples)
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val MIN_VOICE_SAMPLES = SAMPLE_RATE / 4 // 250 ms
        private const val SILENCE_SAMPLES = SAMPLE_RATE * 650 / 1000
        private const val ENERGY_THRESHOLD_SQUARED = 0.012 * 0.012
    }
}
