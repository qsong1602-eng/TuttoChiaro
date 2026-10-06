package com.tuttochiaro.offline.speech

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechSegmentPipelineTest {
    @Test
    fun processesAcceptedSegmentsInOrderAndRetainsOverflowSegment() = runBlocking {
        val pipeline = SpeechSegmentPipeline()
        val processed = mutableListOf<Int>()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val captureStopped = CompletableDeferred<Unit>()

        val job = launch {
            pipeline.run(
                capture = { offer ->
                    assertTrue(offer(segment(1)))
                    firstStarted.await()

                    assertTrue(offer(segment(2)))
                    assertTrue(offer(segment(3)))
                    assertFalse(offer(segment(4)))
                    assertTrue(captureStopped.complete(Unit))
                    // The pipeline's callback contract requires capture to stop on false.
                },
                process = { samples ->
                    val value = samples.single().toInt()
                    processed += value
                    if (value == 1) {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                    }
                },
            )
        }

        // Capture reached its false callback while the first inference was held. Release
        // it only after the producer has filled both queue slots and supplied overflow.
        captureStopped.await()
        releaseFirst.complete(Unit)
        job.join()
        assertEquals(listOf(1, 2, 3, 4), processed)
    }

    @Test
    fun cancellingRunCancelsCaptureAndPreventsFurtherProcessing() = runBlocking {
        val processed = mutableListOf<Int>()
        val firstProcessed = CompletableDeferred<Unit>()
        val captureCancelled = CompletableDeferred<Unit>()
        val pipeline = SpeechSegmentPipeline()

        val job = launch {
            pipeline.run(
                capture = { offer ->
                    assertTrue(offer(segment(1)))
                    try {
                        awaitCancellation()
                    } finally {
                        captureCancelled.complete(Unit)
                    }
                },
                process = { samples ->
                    processed += samples.single().toInt()
                    firstProcessed.complete(Unit)
                },
            )
        }

        firstProcessed.await()
        job.cancelAndJoin()
        assertTrue(captureCancelled.isCompleted)
        assertEquals(listOf(1), processed)
    }

    @Test
    fun consumerFailureCancelsCaptureProducer() = runBlocking {
        val captureCancelled = CompletableDeferred<Unit>()
        val captureReady = CompletableDeferred<Unit>()
        val pipeline = SpeechSegmentPipeline()
        val failure = IllegalStateException("inference failed")

        try {
            pipeline.run(
                capture = { offer ->
                    assertTrue(offer(segment(1)))
                    captureReady.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        captureCancelled.complete(Unit)
                    }
                },
                process = { throw failure },
            )
            throw AssertionError("Expected consumer failure")
        } catch (actual: IllegalStateException) {
            assertEquals(failure.message, actual.message)
        }

        assertTrue(captureReady.isCompleted)
        assertTrue(captureCancelled.isCompleted)
    }

    private fun segment(value: Int) = floatArrayOf(value.toFloat())
}
