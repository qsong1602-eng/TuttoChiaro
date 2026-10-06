package com.tuttochiaro.offline.data

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.Closeable

class OcrRepository : Closeable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // ML Kit owns a reference until its Task completes. Do not let coroutine cancellation
    // recycle the bitmap early; the UI separately rejects results from abandoned captures.
    suspend fun recognize(croppedImage: Bitmap): String = withContext(NonCancellable) {
        recognizer.process(InputImage.fromBitmap(croppedImage, 0)).await().text.trim()
    }

    override fun close() = recognizer.close()
}
