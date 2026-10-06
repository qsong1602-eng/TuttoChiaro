package com.tuttochiaro.offline.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.annotation.StringRes
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraState
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tuttochiaro.offline.TranslatorViewModel
import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.data.CameraLensOption
import com.tuttochiaro.offline.data.CameraLenses
import com.tuttochiaro.offline.data.cropOcrImage
import com.tuttochiaro.offline.i18n.localized
import com.tuttochiaro.offline.i18n.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private enum class OcrFrame(@StringRes val labelId: Int, val widthFraction: Float, val heightFraction: Float) {
    LINE(R.string.ui_frame_narrow, .90f, .22f), SIGN(R.string.ui_frame_standard, .86f, .50f),
    LARGE(R.string.ui_frame_large, .94f, .86f);
    fun rect(size: IntSize): RectF {
        val w = size.width * widthFraction
        val h = size.height * heightFraction
        return RectF((size.width - w) / 2, (size.height - h) / 2, (size.width + w) / 2, (size.height + h) / 2)
    }
}

@SuppressLint("UnsafeOptInUsageError")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OcrCameraScreen(viewModel: TranslatorViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val alive = remember(owner) { AtomicBoolean(true) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var boundLensId by remember { mutableStateOf<String?>(null) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var lenses by remember { mutableStateOf(emptyList<CameraLensOption>()) }
    var lensId by remember { mutableStateOf("auto") }
    var frame by remember { mutableStateOf(OcrFrame.SIGN) }
    var streamReady by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lensNotice by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var zoomPending by remember { mutableStateOf(false) }
    var zoomRevision by remember { mutableIntStateOf(0) }
    var requestId by remember { mutableIntStateOf(0) }
    var processing by remember { mutableStateOf<Job?>(null) }
    var reviewImage by remember { mutableStateOf<Bitmap?>(null) }
    var reviewText by remember { mutableStateOf("") }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                requestId++
                processing?.cancel()
                viewModel.finishOcrPreview()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            alive.set(false)
            requestId++
            processing?.cancel()
            owner.lifecycle.removeObserver(observer)
            provider?.unbindAll()
            // Displayed bitmaps are released by GC after Compose drops its image reference.
        }
    }

    LaunchedEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (alive.get()) {
                try {
                    val loaded = future.get()
                    lenses = CameraLenses.discover(context, loaded)
                    provider = loaded
                } catch (failure: Exception) { error = failure.toUiText(R.string.ui_camera_open_failure).resolve(context) }
            }
        }, mainExecutor)
    }

    DisposableEffect(previewView, owner) {
        val view = previewView
        val layoutListener = android.view.View.OnLayoutChangeListener { target, _, _, _, _, _, _, _, _ ->
            viewSize = IntSize(target.width, target.height)
        }
        viewSize = IntSize(view?.width ?: 0, view?.height ?: 0)
        view?.addOnLayoutChangeListener(layoutListener)
        val observer = Observer<PreviewView.StreamState> { streamReady = it == PreviewView.StreamState.STREAMING }
        view?.previewStreamState?.observe(owner, observer)
        onDispose {
            view?.previewStreamState?.removeObserver(observer)
            view?.removeOnLayoutChangeListener(layoutListener)
        }
    }

    DisposableEffect(camera, owner) {
        val bound = camera
        val observer = Observer<CameraState> { cameraState ->
            if (cameraState.error != null) {
                if (lensId != "auto") {
                    lensNotice = context.getString(R.string.ui_telephoto_fallback)
                    lensId = "auto"
                } else error = context.getString(R.string.ui_camera_unavailable)
            }
        }
        bound?.cameraInfo?.cameraState?.observe(owner, observer)
        onDispose { bound?.cameraInfo?.cameraState?.removeObserver(observer) }
    }

    LaunchedEffect(provider, previewView, previewSize, viewSize, lensId, reviewImage != null, owner) {
        val loaded = provider ?: return@LaunchedEffect
        val view = previewView ?: return@LaunchedEffect
        if (previewSize.width == 0 || previewSize.height == 0 || viewSize != previewSize) return@LaunchedEffect
        requestId++
        processing?.cancel()
        viewModel.finishOcrPreview()
        capture = null
        camera = null
        boundLensId = null
        streamReady = false
        zoomRevision++
        zoomPending = false
        loaded.unbindAll()
        if (reviewImage != null) return@LaunchedEffect
        try {
            val selected = lenses.firstOrNull { it.id == lensId } ?: lenses.first()
            val rotation = view.display?.rotation ?: android.view.Surface.ROTATION_0
            val previewBuilder = Preview.Builder().setTargetRotation(rotation)
            val captureBuilder = ImageCapture.Builder().setTargetRotation(rotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(android.util.Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                ).build())
            selected.physicalId?.let { id ->
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    Camera2Interop.Extender(previewBuilder).setPhysicalCameraId(id)
                    Camera2Interop.Extender(captureBuilder).setPhysicalCameraId(id)
                }
            }
            val preview = previewBuilder.build().also { it.surfaceProvider = view.surfaceProvider }
            val shot = captureBuilder.build()
            val viewport = checkNotNull(view.viewPort) { context.getString(R.string.ui_viewfinder_not_ready) }
            val group = UseCaseGroup.Builder().setViewPort(viewport).addUseCase(preview).addUseCase(shot).build()
            camera = loaded.bindToLifecycle(owner, selected.selector, group)
            boundLensId = selected.id
            capture = shot
            val range = camera?.cameraInfo?.zoomState?.value
            minZoom = range?.minZoomRatio ?: 1f
            maxZoom = range?.maxZoomRatio ?: 1f
            zoom = range?.zoomRatio ?: 1f
            error = null
        } catch (failure: Exception) {
            if (lensId != "auto") {
                lensNotice = context.getString(R.string.ui_telephoto_open_fallback)
                lensId = "auto"
            } else error = failure.toUiText(R.string.ui_camera_open_retry).resolve(context)
        }
    }

    fun setZoom(value: Float) {
        val bound = camera ?: return
        if (viewModel.uiState.value.isOcrBusy) return
        val ratio = value.coerceIn(minZoom, maxZoom)
        val revision = ++zoomRevision
        zoom = ratio
        zoomPending = true
        val future = bound.cameraControl.setZoomRatio(ratio)
        future.addListener({
            if (alive.get() && revision == zoomRevision) {
                zoomPending = false
                try { future.get(); error = null }
                catch (_: Exception) { error = context.getString(R.string.ui_zoom_failed) }
                zoom = bound.cameraInfo.zoomState.value?.zoomRatio ?: ratio
            }
        }, mainExecutor)
    }

    fun takePhoto() {
        val view = previewView ?: return
        val shot = capture ?: return
        val transform = view.sensorToViewTransform ?: run { error = context.getString(R.string.ui_viewfinder_not_ready); return }
        if (zoomPending || !streamReady || boundLensId != lensId || previewSize.width != view.width || previewSize.height != view.height) return
        // Preserve the sensor mapping, including preview rotation, crop and display scaling.
        val frozenTransform = Matrix(transform)
        val frozenFrame = frame.rect(previewSize)
        if (!viewModel.beginOcr()) return
        val ticket = ++requestId
        fun valid() = alive.get() && ticket == requestId && viewModel.uiState.value.showCamera &&
            owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        try {
            shot.takePicture(mainExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    if (!valid()) { image.close(); return }
                    processing = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        var clipped: Bitmap? = null
                        try {
                            try { withContext(Dispatchers.Default) { clipped = cropOcrImage(image, frozenTransform, frozenFrame) } }
                            finally { image.close() }
                            val bitmap = checkNotNull(clipped)
                            val text = viewModel.ocrRepository.recognize(bitmap)
                            ensureActive()
                            if (valid()) {
                                reviewText = text
                                reviewImage = bitmap
                                clipped = null
                                viewModel.finishOcrPreview()
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { if (valid()) viewModel.onOcrError(failure) }
                        finally { clipped?.recycle() }
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    if (valid()) viewModel.onOcrError(exception)
                }
            })
        } catch (failure: Exception) { if (valid()) viewModel.onOcrError(failure) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(TuttoColors.Ink)
        .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
    val controlsHeight = maxHeight * .50f
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::closeCamera) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.ui_back), tint = Color.White) }
            Text(if (reviewImage == null) stringResource(R.string.ui_camera_title) else stringResource(R.string.ui_review_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        val photo = reviewImage
        if (photo != null) {
            Image(photo.asImageBitmap(), stringResource(R.string.ui_cropped_photo_description), Modifier.fillMaxWidth().weight(1f).padding(12.dp), contentScale = ContentScale.Fit)
            Column(Modifier.fillMaxWidth().heightIn(max = controlsHeight).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.ui_review_help), color = Color.White)
                OutlinedTextField(value = reviewText, onValueChange = { reviewText = it },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                    label = { Text(stringResource(R.string.ui_recognized_text_editable)) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedLabelColor = Color.White, unfocusedLabelColor = Color.White))
                if (reviewText.isBlank()) Text(stringResource(R.string.ui_no_text_in_frame), color = TuttoColors.Sun)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { reviewImage = null; reviewText = ""; viewModel.clearMessage() }) { Text(stringResource(R.string.ui_retake), color = Color.White) }
                    Button(onClick = { viewModel.onOcrResult(reviewText) }, enabled = reviewText.isNotBlank()) { Text(stringResource(R.string.ui_translate_frame_text)) }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().weight(1f).onSizeChanged { previewSize = it }) {
                AndroidView(factory = { ctx -> PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewView = this
                } }, modifier = Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    val rect = frame.rect(IntSize(size.width.toInt(), size.height.toInt()))
                    val shade = Color.Black.copy(alpha = .55f)
                    drawRect(shade, size = Size(size.width, rect.top))
                    drawRect(shade, topLeft = Offset(0f, rect.bottom), size = Size(size.width, size.height - rect.bottom))
                    drawRect(shade, topLeft = Offset(0f, rect.top), size = Size(rect.left, rect.height()))
                    drawRect(shade, topLeft = Offset(rect.right, rect.top), size = Size(size.width - rect.right, rect.height()))
                    drawRect(TuttoColors.Sun, Offset(rect.left, rect.top), Size(rect.width(), rect.height()), style = Stroke(2.dp.toPx()))
                }
                if (state.isOcrBusy) CircularProgressIndicator(Modifier.align(Alignment.Center), color = TuttoColors.Sun)
            }
            Column(Modifier.fillMaxWidth().heightIn(max = controlsHeight).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(error ?: state.message?.localized() ?: lensNotice ?: stringResource(R.string.ui_camera_guidance), color = Color.White, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OcrFrame.entries.forEach { option -> FilterChip(selected = frame == option, enabled = !state.isOcrBusy,
                        onClick = { frame = option }, label = { Text(stringResource(option.labelId)) },
                        colors = FilterChipDefaults.filterChipColors(labelColor = Color.White)) }
                }
                if (lenses.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    lenses.forEach { option -> FilterChip(selected = lensId == option.id, enabled = !state.isOcrBusy,
                        onClick = { lensNotice = null; lensId = option.id }, label = { Text(option.label.localized()) },
                        colors = FilterChipDefaults.filterChipColors(labelColor = Color.White)) }
                }
                if (maxZoom > minZoom) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(String.format(Locale.ROOT, "%.1f×", zoom), color = Color.White)
                        Slider(value = zoom.coerceIn(minZoom, maxZoom), onValueChange = ::setZoom,
                            valueRange = minZoom..maxZoom, enabled = !state.isOcrBusy && camera != null, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1f, 2f, 3f, 5f).filter { it in minZoom..maxZoom }.forEach { ratio ->
                            TextButton(onClick = { setZoom(ratio) }, enabled = !state.isOcrBusy) { Text("${ratio.toInt()}×", color = Color.White) }
                        }
                    }
                }
                Text(when {
                    lenses.isEmpty() -> stringResource(R.string.ui_reading_camera_capabilities)
                    lenses.size <= 1 && maxZoom <= minZoom -> stringResource(R.string.ui_no_telephoto_or_zoom)
                    lenses.size <= 1 -> stringResource(R.string.ui_no_independent_telephoto)
                    else -> stringResource(R.string.ui_telephoto_available)
                },
                    color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                Button(onClick = ::takePhoto, enabled = !state.isOcrBusy && capture != null && boundLensId == lensId && streamReady && !zoomPending && error == null,
                    modifier = Modifier.fillMaxWidth()) { Text(if (state.isOcrBusy) stringResource(R.string.ui_cropping_recognizing) else stringResource(R.string.ui_capture_frame)) }
            }
        }
    }
    }
}
