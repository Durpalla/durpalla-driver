package com.durpalla.driver.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.durpalla.driver.R
import com.durpalla.driver.data.AvatarCrop
import com.durpalla.driver.data.FacePresence
import com.durpalla.driver.data.ServiceLocator
import com.durpalla.driver.ui.components.DTopBar
import com.durpalla.driver.ui.theme.DP
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Front-camera selfie for profile photo. Gallery is not offered.
 * Capture stays disabled until ML Kit sees one face (live) in front of the camera.
 */
@Composable
fun AvatarCameraScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var hasCamPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamPerm = it }
    LaunchedEffect(Unit) { if (!hasCamPerm) permLauncher.launch(Manifest.permission.CAMERA) }

    var faceOk by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf(ctx.getString(R.string.avatar_face_looking)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val analyzing = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val liveDetector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.2f)
                .build(),
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            liveDetector.close()
        }
    }

    Scaffold(
        topBar = { DTopBar(stringResource(R.string.avatar_camera_title), onBack) },
        containerColor = Color.Black,
    ) { pad ->
        if (!hasCamPerm) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.avatar_camera_permission),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(pad)) {
            AndroidView(
                factory = { c ->
                    PreviewView(c).also { previewView ->
                        val providerFuture = ProcessCameraProvider.getInstance(c)
                        providerFuture.addListener({
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setResolutionSelector(
                                    ResolutionSelector.Builder()
                                        .setResolutionStrategy(
                                            ResolutionStrategy(
                                                Size(480, 640),
                                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                            ),
                                        )
                                        .build(),
                                )
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(executor) { proxy ->
                                if (!analyzing.compareAndSet(false, true)) {
                                    proxy.close()
                                    return@setAnalyzer
                                }
                                try {
                                    val media = proxy.image
                                    if (media == null) {
                                        proxy.close()
                                        analyzing.set(false)
                                        return@setAnalyzer
                                    }
                                    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                                    liveDetector.process(image)
                                        .addOnSuccessListener { faces ->
                                            faceOk = faces.size == 1
                                            hint = when {
                                                faces.isEmpty() -> c.getString(R.string.avatar_face_looking)
                                                faces.size > 1 -> c.getString(R.string.avatar_face_one)
                                                else -> c.getString(R.string.avatar_face_ready)
                                            }
                                        }
                                        .addOnCompleteListener {
                                            proxy.close()
                                            analyzing.set(false)
                                        }
                                } catch (_: Throwable) {
                                    proxy.close()
                                    analyzing.set(false)
                                }
                            }
                            try {
                                // Match JPEG EXIF / buffer rotation to the display so selfies are upright.
                                val rotation = previewView.display?.rotation
                                    ?: android.view.Surface.ROTATION_0
                                preview.targetRotation = rotation
                                imageCapture.targetRotation = rotation
                                analysis.targetRotation = rotation
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycle,
                                    CameraSelector.DEFAULT_FRONT_CAMERA,
                                    preview,
                                    imageCapture,
                                    analysis,
                                )
                            } catch (_: Throwable) {
                                error = c.getString(R.string.avatar_camera_failed)
                            }
                        }, ContextCompat.getMainExecutor(c))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension * 0.36f
                val center = Offset(size.width / 2f, size.height * 0.42f)
                val dim = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
                    addOval(
                        androidx.compose.ui.geometry.Rect(
                            center.x - r,
                            center.y - r,
                            center.x + r,
                            center.y + r,
                        ),
                    )
                }
                drawPath(dim, Color.Black.copy(alpha = 0.55f))
                drawCircle(
                    color = if (faceOk) Color(0xFF22C55E) else Color.White.copy(alpha = 0.9f),
                    radius = r,
                    center = center,
                    style = Stroke(width = 4.dp.toPx()),
                )
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(hint, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.avatar_camera_sub),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                error?.let { Text(it, color = DP.Danger, fontSize = 13.sp, textAlign = TextAlign.Center) }
                Spacer(Modifier.height(16.dp))
                if (busy) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    FloatingActionButton(
                        onClick = {
                            if (!faceOk || busy) return@FloatingActionButton
                            busy = true
                            error = null
                            val dir = File(ctx.cacheDir, "avatar").also { it.mkdirs() }
                            val file = File(dir, "selfie_${System.currentTimeMillis()}.jpg")
                            val opts = ImageCapture.OutputFileOptions.Builder(file).build()
                            imageCapture.takePicture(
                                opts,
                                ContextCompat.getMainExecutor(ctx),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onError(exc: ImageCaptureException) {
                                        busy = false
                                        error = ctx.getString(R.string.avatar_camera_failed)
                                    }

                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        scope.launch {
                                            val uri = android.net.Uri.fromFile(file)
                                            when (FacePresence.checkUri(ctx, uri)) {
                                                FacePresence.Result.Ok -> {
                                                    val bytes = withContext(Dispatchers.IO) { AvatarCrop.fromUri(ctx, uri) }
                                                    if (bytes == null) {
                                                        error = ctx.getString(R.string.avatar_failed)
                                                        busy = false
                                                    } else {
                                                        ServiceLocator.profile.uploadAvatarJpeg(bytes)
                                                            .onSuccess {
                                                                busy = false
                                                                onDone()
                                                            }
                                                            .onFailure {
                                                                error = ctx.getString(R.string.avatar_failed)
                                                                busy = false
                                                            }
                                                    }
                                                }
                                                FacePresence.Result.NoFace -> {
                                                    error = ctx.getString(R.string.avatar_face_looking); busy = false
                                                }
                                                FacePresence.Result.MultipleFaces -> {
                                                    error = ctx.getString(R.string.avatar_face_one); busy = false
                                                }
                                                FacePresence.Result.FaceTooSmall -> {
                                                    error = ctx.getString(R.string.avatar_face_closer); busy = false
                                                }
                                                FacePresence.Result.EyesClosed -> {
                                                    error = ctx.getString(R.string.avatar_eyes_open); busy = false
                                                }
                                            }
                                            runCatching { file.delete() }
                                        }
                                    }
                                },
                            )
                        },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        containerColor = if (faceOk) DP.Accent else Color.Gray,
                    ) {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = stringResource(R.string.avatar_capture),
                            tint = Color.White,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
