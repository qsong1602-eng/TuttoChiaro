package com.tuttochiaro.offline.data

import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.core.content.ContextCompat
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.platform.app.InstrumentationRegistry
import com.tuttochiaro.offline.MainActivity
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Live hardware geometry check. No photos, OCR text or audio are persisted. */
@android.annotation.SuppressLint("UnsafeOptInUsageError")
class OcrCameraGeometryTest {
    @Test fun liveCaptureMatchesPreviewFrameWithHalRotation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val executor = ContextCompat.getMainExecutor(context)
        // OriginOS can stall ActivityScenario startup. Bring MainActivity to the foreground
        // from adb after instrumentation starts; do not block on ActivityScenario's idle sync.
        var foregroundActivity: MainActivity? = null
        await {
            foregroundActivity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
            foregroundActivity != null
        }
        val activity = checkNotNull(foregroundActivity)
        val provider = ProcessCameraProvider.getInstance(context).get(20, TimeUnit.SECONDS)
        lateinit var view: PreviewView
        lateinit var shot: ImageCapture
        lateinit var camera: Camera
        try {
            instrumentation.runOnMainSync {
                view = PreviewView(activity).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                activity.setContentView(view)
            }
            await { view.width > 0 && view.height > 0 && view.viewPort != null }
            instrumentation.runOnMainSync {
                provider.unbindAll()
                val preview = Preview.Builder().setTargetRotation(view.display.rotation).build()
                preview.surfaceProvider = view.surfaceProvider
                shot = ImageCapture.Builder().setTargetRotation(view.display.rotation)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build()).build()
                camera = provider.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA,
                    UseCaseGroup.Builder().setViewPort(checkNotNull(view.viewPort)).addUseCase(preview).addUseCase(shot).build())
            }
            await { view.previewStreamState.value == PreviewView.StreamState.STREAMING && view.sensorToViewTransform != null }
            val maxZoom = camera.cameraInfo.zoomState.value!!.maxZoomRatio
            for (zoom in listOf(1f, 3f).filter { it <= maxZoom }) {
                val zoomReady = CompletableFuture<Unit>()
                instrumentation.runOnMainSync {
                    val future = camera.cameraControl.setZoomRatio(zoom)
                    future.addListener({ try { future.get(); zoomReady.complete(Unit) } catch (e: Exception) { zoomReady.completeExceptionally(e) } }, executor)
                }
                zoomReady.get(10, TimeUnit.SECONDS)
                for ((wf, hf) in listOf(.90f to .22f, .86f to .50f, .94f to .86f)) {
                    val completed = CompletableFuture<Unit>()
                    instrumentation.runOnMainSync {
                        val sensorToView = Matrix(checkNotNull(view.sensorToViewTransform))
                        val oldPreviewTransform = checkNotNull(view.outputTransform)
                        val frame = RectF(view.width * (1 - wf) / 2, view.height * (1 - hf) / 2,
                            view.width * (1 + wf) / 2, view.height * (1 + hf) / 2)
                        val viewWidth = view.width
                        val viewHeight = view.height
                        shot.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    val map = Matrix()
                                    check(sensorToView.invert(map))
                                    map.postConcat(image.imageInfo.sensorToBufferTransformMatrix)
                                    val actual = RectF(frame).also { map.mapRect(it) }
                                    val old = RectF(frame).also {
                                        CoordinateTransform(oldPreviewTransform, ImageProxyTransformFactory().getOutputTransform(image)).mapRect(it)
                                    }
                                    // Independently expect the frame's fractions inside the upright
                                    // shared ViewPort; account for remaining metadata rotation only.
                                    val rotate = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
                                    val uprightViewport = RectF(image.cropRect).also { rotate.mapRect(it) }
                                    val uprightActual = RectF(actual).also { rotate.mapRect(it) }
                                    val expected = RectF(
                                        uprightViewport.left + uprightViewport.width() * frame.left / viewWidth,
                                        uprightViewport.top + uprightViewport.height() * frame.top / viewHeight,
                                        uprightViewport.left + uprightViewport.width() * frame.right / viewWidth,
                                        uprightViewport.top + uprightViewport.height() * frame.bottom / viewHeight)
                                    val error = listOf(abs(expected.left - uprightActual.left), abs(expected.top - uprightActual.top),
                                        abs(expected.right - uprightActual.right), abs(expected.bottom - uprightActual.bottom)).max()
                                    val result = cropOcrImage(image, sensorToView, frame)
                                    try {
                                        instrumentation.sendStatus(0, Bundle().apply { putString("stream",
                                            "\nCAMERA_GEOMETRY zoom=$zoom frame=$wf,$hf buffer=${image.width}x${image.height} rotation=${image.imageInfo.rotationDegrees} crop=${image.cropRect} old=$old fixed=$actual errorPx=$error output=${result.width}x${result.height}\n") })
                                        assertTrue("Preview/capture frame discrepancy: $error pixels", error <= 2f)
                                        // Inward rounding removes <1 pixel at each actual mapped
                                        // edge. ViewPort integer metadata can add a separate subpixel
                                        // discrepancy, already checked against expected above.
                                        assertTrue(abs(uprightActual.width() - result.width) <= 2f)
                                        assertTrue(abs(uprightActual.height() - result.height) <= 2f)
                                    } finally { result.recycle() }
                                    completed.complete(Unit)
                                } catch (failure: Throwable) { completed.completeExceptionally(failure) }
                                finally { image.close() }
                            }
                            override fun onError(exception: ImageCaptureException) { completed.completeExceptionally(exception) }
                        })
                    }
                    completed.get(20, TimeUnit.SECONDS)
                }
            }
        } finally {
            instrumentation.runOnMainSync { provider.unbindAll() }
            instrumentation.runOnMainSync { activity.recreate() }
        }
    }

    private fun await(condition: () -> Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            Thread.sleep(100)
        }
        fail("Camera preview did not become ready")
    }
}
