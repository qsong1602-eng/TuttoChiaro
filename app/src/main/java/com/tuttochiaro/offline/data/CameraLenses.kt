package com.tuttochiaro.offline.data

import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.i18n.*

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider

data class CameraLensOption(
    val id: String,
    val label: UiText,
    val selector: CameraSelector,
    /** A physical ID belonging to the logical camera selected by [selector], when needed. */
    val physicalId: String? = null,
)

/** Cameras that can be requested explicitly for OCR. A missing telephoto is not inferred from zoom. */
@android.annotation.SuppressLint("UnsafeOptInUsageError")
object CameraLenses {
    private const val MIN_TELEPHOTO_RATIO = 1.5

    fun discover(context: Context, provider: ProcessCameraProvider): List<CameraLensOption> {
        val automatic = CameraLensOption("auto", UiText(R.string.rt_auto_lens), CameraSelector.DEFAULT_BACK_CAMERA)
        val infos = runCatching { provider.availableCameraInfos }.getOrNull() ?: return listOf(automatic)
        val defaultInfo = runCatching {
            CameraSelector.DEFAULT_BACK_CAMERA.filter(infos).firstOrNull()
        }.getOrNull() ?: return listOf(automatic)
        val defaultId = cameraId(defaultInfo) ?: return listOf(automatic)
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return listOf(automatic)
        val defaultCharacteristics = characteristics(manager, defaultId) ?: return listOf(automatic)
        val baseline = fieldOfViewScale(defaultCharacteristics) ?: return listOf(automatic)

        val candidates = mutableListOf<LensCandidate>()
        val standaloneIds = mutableSetOf<String>()
        for (info in infos) {
            val id = cameraId(info) ?: continue
            if (id == defaultId || !standaloneIds.add(id)) continue
            val details = characteristics(manager, id) ?: continue
            if (!isRearCamera(details)) continue
            val scale = fieldOfViewScale(details) ?: continue
            val ratio = scale / baseline
            if (ratio.isFinite() && ratio >= MIN_TELEPHOTO_RATIO) {
                candidates += LensCandidate("camera:$id", id, null, ratio)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val physicalIds = runCatching { defaultCharacteristics.physicalCameraIds }.getOrDefault(emptySet())
            for (physicalId in physicalIds) {
                // A separately exposed camera is simpler to bind directly than as a physical stream.
                if (physicalId == defaultId || candidates.any { it.logicalId == physicalId }) continue
                val details = characteristics(manager, physicalId) ?: continue
                if (!isRearCamera(details)) continue
                val scale = fieldOfViewScale(details) ?: continue
                val ratio = scale / baseline
                if (ratio.isFinite() && ratio >= MIN_TELEPHOTO_RATIO) {
                    candidates += LensCandidate("physical:$defaultId:$physicalId", defaultId, physicalId, ratio)
                }
            }
        }

        return listOf(automatic) + candidates.sortedBy { it.ratio }.map { candidate ->
            CameraLensOption(
                id = candidate.id,
                label = UiText(R.string.rt_telephoto, candidate.ratio),
                selector = selectorForId(candidate.logicalId),
                physicalId = candidate.physicalId,
            )
        }
    }

    private fun cameraId(info: CameraInfo): String? =
        runCatching { Camera2CameraInfo.from(info).cameraId }.getOrNull()

    private fun characteristics(manager: CameraManager, id: String): CameraCharacteristics? =
        runCatching { manager.getCameraCharacteristics(id) }.getOrNull()

    private fun isRearCamera(details: CameraCharacteristics): Boolean =
        runCatching {
            details.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                details.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) == true
        }
            .getOrDefault(false)

    // tan(horizontal FOV / 2) = sensor width / (2 * focal length). Thus focal / width
    // is proportional to optical magnification and can be compared across sensor sizes.
    private fun fieldOfViewScale(details: CameraCharacteristics): Double? = runCatching {
        val width = details.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width?.toDouble()
            ?: return@runCatching null
        if (!width.isFinite() || width <= 0.0) return@runCatching null
        val focalLength = details.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.asSequence()?.map { it.toDouble() }?.filter { it.isFinite() && it > 0.0 }?.minOrNull()
            ?: return@runCatching null
        focalLength / width
    }.getOrNull()

    private fun selectorForId(id: String): CameraSelector = CameraSelector.Builder()
        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
        .addCameraFilter { infos -> infos.filter { cameraId(it) == id } }
        .build()

    private data class LensCandidate(
        val id: String,
        val logicalId: String,
        val physicalId: String?,
        val ratio: Double,
    )
}
