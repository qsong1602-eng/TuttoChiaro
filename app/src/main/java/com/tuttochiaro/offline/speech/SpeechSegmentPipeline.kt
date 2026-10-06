package com.tuttochiaro.offline.speech

import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.i18n.*

import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicReference

/** One inference at a time; two waiting segments and one final overflow segment.
 * The capture callback must stop immediately on false. That final segment is retained,
 * never silently dropped. AudioRecord must not block waiting for inference.
 */
class SpeechSegmentPipeline {
    suspend fun run(
        capture: suspend ((FloatArray) -> Boolean) -> Unit,
        process: suspend (FloatArray) -> Unit,
    ) = coroutineScope {
        val queue = Channel<FloatArray>(2)
        val finalSegment = AtomicReference<FloatArray?>(null)
        val producer = async {
            try {
                capture { samples ->
                    if (queue.trySend(samples).isSuccess) true
                    else {
                        checkUi(finalSegment.compareAndSet(null, samples)) { UiText(R.string.rt_queue_not_stopped) }
                        false
                    }
                }
            } finally { queue.close() }
        }
        try {
            for (samples in queue) {
                currentCoroutineContext().ensureActive()
                process(samples)
            }
            producer.await()
            finalSegment.getAndSet(null)?.let {
                currentCoroutineContext().ensureActive()
                process(it)
            }
        } finally {
            producer.cancel()
            queue.cancel()
            finalSegment.set(null)
        }
    }
}
