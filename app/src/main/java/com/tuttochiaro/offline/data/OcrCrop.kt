package com.tuttochiaro.offline.data

import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.i18n.*

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import kotlin.math.ceil
import kotlin.math.floor

data class PixelCrop(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Round inwards: never add pixels outside the selected frame. Invalid crops fail closed. */
fun boundedOcrCrop(left: Float, top: Float, right: Float, bottom: Float,
    width: Int, height: Int): PixelCrop {
    requireUi(listOf(left, top, right, bottom).all { it.isFinite() } && width > 0 && height > 0) { UiText(R.string.rt_crop_invalid) }
    val crop = PixelCrop(ceil(left).toInt().coerceIn(0, width), ceil(top).toInt().coerceIn(0, height),
        floor(right).toInt().coerceIn(0, width), floor(bottom).toInt().coerceIn(0, height))
    requireUi(crop.right - crop.left >= 2 && crop.bottom - crop.top >= 2) { UiText(R.string.rt_crop_invalid) }
    return crop
}

/** Preview and capture MUST be bound in one UseCaseGroup with the same ViewPort. */
fun cropOcrImage(image: ImageProxy, sensorToPreview: Matrix, frame: RectF): Bitmap {
    // JPEG capture may already be rotated by the HAL while preview stays in sensor orientation.
    // CameraX updates sensorToBufferTransformMatrix for that rotation. A normalized crop-rect
    // transform loses this relationship and incorrectly treats the two buffers as aligned.
    val previewToBuffer = Matrix()
    requireUi(sensorToPreview.invert(previewToBuffer)) { UiText(R.string.rt_crop_not_ready) }
    previewToBuffer.postConcat(image.imageInfo.sensorToBufferTransformMatrix)
    val mapped = RectF(frame)
    previewToBuffer.mapRect(mapped)
    requireUi(mapped.intersect(RectF(image.cropRect))) { UiText(R.string.rt_crop_changed) }
    val crop = boundedOcrCrop(mapped.left, mapped.top, mapped.right, mapped.bottom, image.width, image.height)
    val full = image.toBitmap()
    try {
        checkUi(full.width == image.width && full.height == image.height) { UiText(R.string.rt_image_size) }
        val clipped = Bitmap.createBitmap(full, crop.left, crop.top, crop.right - crop.left, crop.bottom - crop.top)
        try {
            val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
            val upright = Bitmap.createBitmap(clipped, 0, 0, clipped.width, clipped.height, matrix, false)
            // Always return independently owned pixels; callers can release the capture immediately.
            return if (upright === full || upright === clipped) upright.copy(Bitmap.Config.ARGB_8888, false) else upright
        } finally { if (clipped !== full) clipped.recycle() }
    } finally { full.recycle() }
}
