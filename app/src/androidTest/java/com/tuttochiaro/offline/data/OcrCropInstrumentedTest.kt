package com.tuttochiaro.offline.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.ImageFormat
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Proxy

/** Independent sensor/view fixtures: capture JPEG orientation need not match preview orientation. */
class OcrCropInstrumentedTest {
    @Test fun cropsOnlyFramePixelsWithEveryPreviewAndHalRotation() {
        val source = Bitmap.createBitmap(24, 16, Bitmap.Config.ARGB_8888)
        for (y in 0 until 16) for (x in 0 until 24) source.setPixel(x, y, chosenColor(x, y))
        try {
            for (displayRotation in listOf(0, 90, 180, 270)) {
                for (halRotation in listOf(0, 90, 180, 270)) {
                    // Sensor coordinates are twice the raw buffer size; view additionally scales
                    // and translates it, like a FILL_CENTER preview with cropped margins.
                    val sensorToPreview = Matrix().apply {
                        setScale(.5f, .5f)
                        postConcat(rotationMatrix(displayRotation))
                        postScale(3.5f, 3.5f)
                        postTranslate(-17f, -9f)
                    }
                    val sensorToBuffer = Matrix().apply {
                        setScale(.5f, .5f)
                        postConcat(rotationMatrix(halRotation))
                    }
                    val buffer = Bitmap.createBitmap(source, 0, 0, 24, 16, rotationMatrix(halRotation), false)
                    val bufferCrop = rotatedRect(Rect(2, 2, 22, 14), halRotation)
                    val capture = fakeImage(buffer, bufferCrop, (displayRotation - halRotation + 360) % 360, sensorToBuffer)
                    try {
                        // Asymmetric, off-centre rectangles expose incorrect 90/180-degree mapping.
                        for (selected in listOf(Rect(5, 3, 15, 7), Rect(4, 4, 18, 12), Rect(3, 3, 21, 13))) {
                            val uprightRect = rotatedRect(selected, displayRotation)
                            val frame = RectF(uprightRect).apply {
                                inset(-.1f, -.1f)
                                set(left * 3.5f - 17, top * 3.5f - 9, right * 3.5f - 17, bottom * 3.5f - 9)
                            }
                            val result = cropOcrImage(capture, sensorToPreview, frame)
                            try {
                                assertEquals(uprightRect.width(), result.width)
                                assertEquals(uprightRect.height(), result.height)
                                for (y in 0 until result.height) for (x in 0 until result.width) {
                                    val (rawX, rawY) = when (displayRotation) {
                                        0 -> Pair(selected.left + x, selected.top + y)
                                        90 -> Pair(selected.left + y, selected.bottom - 1 - x)
                                        180 -> Pair(selected.right - 1 - x, selected.bottom - 1 - y)
                                        else -> Pair(selected.right - 1 - y, selected.top + x)
                                    }
                                    assertEquals("display=$displayRotation HAL=$halRotation rect=$selected pixel=($x,$y)",
                                        chosenColor(rawX, rawY), result.getPixel(x, y))
                                }
                            } finally { result.recycle() }
                        }
                    } finally {
                        capture.close()
                        if (buffer !== source) buffer.recycle()
                    }
                }
            }
        } finally { source.recycle() }
    }

    @Test fun rejectsFrameOutsideCaptureViewport() {
        val source = Bitmap.createBitmap(24, 16, Bitmap.Config.ARGB_8888)
        try {
            val capture = fakeImage(source, Rect(2, 2, 22, 14), 0, Matrix())
            try {
                cropOcrImage(capture, Matrix(), RectF(0f, 0f, 1f, 1f))
                fail("Frame outside capture viewport was accepted")
            } catch (_: IllegalArgumentException) { /* expected */ }
        } finally { source.recycle() }
    }

    @Test fun rejectsNonInvertiblePreviewTransform() {
        val source = Bitmap.createBitmap(24, 16, Bitmap.Config.ARGB_8888)
        try {
            val capture = fakeImage(source, Rect(0, 0, 24, 16), 0, Matrix())
            try {
                cropOcrImage(capture, Matrix().apply { setScale(0f, 0f) }, RectF(1f, 1f, 5f, 5f))
                fail("Invalid preview transform was accepted")
            } catch (_: IllegalArgumentException) { /* expected */ }
        } finally { source.recycle() }
    }

    private fun rotatedRect(r: Rect, rotation: Int): Rect = when (rotation) {
        0 -> Rect(r)
        90 -> Rect(16 - r.bottom, r.left, 16 - r.top, r.right)
        180 -> Rect(24 - r.right, 16 - r.bottom, 24 - r.left, 16 - r.top)
        else -> Rect(r.top, 24 - r.right, r.bottom, 24 - r.left)
    }

    private fun rotationMatrix(rotation: Int) = Matrix().apply {
        setValues(when (rotation) {
            0 -> floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
            90 -> floatArrayOf(0f, -1f, 16f, 1f, 0f, 0f, 0f, 0f, 1f)
            180 -> floatArrayOf(-1f, 0f, 24f, 0f, -1f, 16f, 0f, 0f, 1f)
            else -> floatArrayOf(0f, 1f, 0f, -1f, 0f, 24f, 0f, 0f, 1f)
        })
    }

    private fun chosenColor(x: Int, y: Int) = Color.rgb(x * 9, y * 13, (x + y) * 6)

    private fun fakeImage(bitmap: Bitmap, crop: Rect, rotation: Int, sensorToBuffer: Matrix): ImageProxy {
        val imageInfo = Proxy.newProxyInstance(ImageInfo::class.java.classLoader,
            arrayOf(ImageInfo::class.java)) { _, method, _ ->
            when (method.name) {
                "getRotationDegrees" -> rotation
                "getTimestamp" -> 0L
                "getSensorToBufferTransformMatrix" -> Matrix(sensorToBuffer)
                else -> throw UnsupportedOperationException("ImageInfo.${method.name}")
            }
        } as ImageInfo
        return Proxy.newProxyInstance(ImageProxy::class.java.classLoader,
            arrayOf(ImageProxy::class.java)) { _, method, _ ->
            when (method.name) {
                "getWidth" -> bitmap.width
                "getHeight" -> bitmap.height
                "getCropRect" -> Rect(crop)
                "getImageInfo" -> imageInfo
                "getFormat" -> ImageFormat.YUV_420_888
                "getPlanes" -> emptyArray<ImageProxy.PlaneProxy>()
                "getImage" -> null
                "toBitmap" -> bitmap.copy(Bitmap.Config.ARGB_8888, true)
                "setCropRect", "close" -> Unit
                else -> throw UnsupportedOperationException("ImageProxy.${method.name}")
            }
        } as ImageProxy
    }
}
