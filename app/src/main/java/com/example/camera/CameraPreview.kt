package com.glorycam.app.camera

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.glorycam.app.ui.theme.GloryGold
import java.io.File
import java.util.concurrent.Executor

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.graphics.Brush
import com.example.camera.ui.theme.MutedSlate

@Composable
fun CameraPreview(
    lensFacing: Int,
    flashMode: FlashModeState,
    gridType: GridType,
    aspectRatioPreset: AspectRatioPreset,
    selectedFilter: FilterPreset,
    exposureIndex: Int,
    onImageCaptureCreated: (ImageCapture) -> Unit,
    modifier: Modifier = Modifier,
    selectedMode: CameraMode = CameraMode.STANDARD,
    isoValue: Int = 400
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var cameraInstance by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    // Tap-to-focus visual states
    var focusRingOffset by remember { mutableStateOf<Offset?>(null) }
    var focusRingVisible by remember { mutableStateOf(false) }
    var fadeOutJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Zoom visual states
    var zoomLevelText by remember { mutableStateOf<String?>(null) }
    var currentZoomValue by remember { mutableStateOf(1.0f) }
    var zoomTextJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setFlashMode(flashMode.flashModeValue)
            .build()
    }

    // Keep the image capture's flash mode updated when state changes
    LaunchedEffect(flashMode) {
        imageCapture.flashMode = flashMode.flashModeValue
    }

    // Bind real-time Exposure compensation Index directly on the active hardware lens
    LaunchedEffect(cameraInstance, exposureIndex) {
        val camera = cameraInstance ?: return@LaunchedEffect
        try {
            camera.cameraControl.setExposureCompensationIndex(exposureIndex)
        } catch (t: Throwable) {
            Log.e("CameraPreview", "Failed to set exposure compensation to $exposureIndex", t)
        }
    }

    // Notify parent of the capture object
    LaunchedEffect(imageCapture) {
        onImageCaptureCreated(imageCapture)
    }

    // Bind and unbind camera based on lens facing and lifecycle
    var isResumed by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isResumed = true
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                isResumed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isPermissionGranted = remember(isResumed) {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(lensFacing, isResumed, isPermissionGranted) {
        if (!isResumed) {
            Log.d("CameraPreview", "Not in RESUMED state. Postponing camera binding...")
            return@DisposableEffect onDispose {}
        }
        // High-security check before connecting to Camera Provider: avoids background system exception or app crashes
        if (!isPermissionGranted) {
            Log.w("CameraPreview", "Camera permission not yet granted. Postponing process camera binding...")
            return@DisposableEffect onDispose {}
        }

        var isDisposed = false
        val cameraProviderFuture = try {
            ProcessCameraProvider.getInstance(context)
        } catch (t: Throwable) {
            Log.e("CameraPreview", "Failed to obtain ProcessCameraProvider instance", t)
            return@DisposableEffect onDispose {}
        }

        cameraProviderFuture.addListener({
            if (isDisposed) return@addListener
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()

                // Robust fallback check: ensures virtual/emulator environments which lack a specific lens do not crash or raise IllegalArgumentException
                val selectorBuilder = CameraSelector.Builder()
                val finalSelector = try {
                    val hasBack = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                    val hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                    
                    if (lensFacing == CameraSelector.LENS_FACING_BACK && hasBack) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else if (lensFacing == CameraSelector.LENS_FACING_FRONT && hasFront) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else if (hasBack) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else if (hasFront) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        selectorBuilder.requireLensFacing(lensFacing).build()
                    }
                } catch (ex: Exception) {
                    Log.w("CameraPreview", "Failure querying camera details. Defaulting to chosen lensFacing.", ex)
                    selectorBuilder.requireLensFacing(lensFacing).build()
                }

                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }

                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    finalSelector,
                    preview,
                    imageCapture
                )
                cameraInstance = camera
                currentZoomValue = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1.0f
            } catch (e: Throwable) {
                Log.e("CameraPreview", "Camera binding failed spectacularly", e)
            }
        }, mainExecutor)

        onDispose {
            isDisposed = true
            try {
                if (cameraProviderFuture.isDone) {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                }
            } catch (t: Throwable) {
                Log.w("CameraPreview", "Failed to unbind camera provider on dispose", t)
            }
        }
    }

    fun triggerFocus(x: Float, y: Float) {
        val camera = cameraInstance ?: return
        try {
            val factory = previewView.meteringPointFactory
            val point = factory.createPoint(x, y)
            val action = androidx.camera.core.FocusMeteringAction.Builder(point, androidx.camera.core.FocusMeteringAction.FLAG_AF or androidx.camera.core.FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            camera.cameraControl.startFocusAndMetering(action)
        } catch (t: Throwable) {
            Log.e("CameraPreview", "Focus trigger failed", t)
        }
    }

    Box(modifier = modifier) {
        // Viewport aspect ratio container based on settings
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val viewWidth = maxWidth
            val viewHeight = maxHeight

            // Math to fit the chosen aspect ratio elegantly inside constraints
            val requestedRatio = aspectRatioPreset.value
            val currentBoxRatio = viewWidth.value / viewHeight.value

            val containerModifier = if (requestedRatio > currentBoxRatio) {
                // Constrained by width
                val targetHeight = viewWidth / requestedRatio
                Modifier
                    .width(viewWidth)
                    .height(targetHeight)
            } else {
                // Constrained by height
                val targetWidth = viewHeight * requestedRatio
                Modifier
                    .width(targetWidth)
                    .height(viewHeight)
            }

            Box(
                modifier = containerModifier
                    .align(androidx.compose.ui.Alignment.Center)
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        try {
                            detectTapGestures(
                                onTap = { offset ->
                                    try {
                                        triggerFocus(offset.x, offset.y)
                                        // Trigger focal ring animation
                                        try {
                                            fadeOutJob?.cancel()
                                            focusRingOffset = offset
                                            focusRingVisible = true
                                            fadeOutJob = scope.launch {
                                                kotlinx.coroutines.delay(1500)
                                                focusRingVisible = false
                                            }
                                        } catch (t: Throwable) {
                                            Log.e("CameraPreview", "Focus ring animation exception", t)
                                        }
                                    } catch (t: Throwable) {
                                        Log.e("CameraPreview", "Tap focus inner exception", t)
                                    }
                                }
                            )
                        } catch (t: Throwable) {
                            Log.e("CameraPreview", "Tap gestures pointerInput exception", t)
                        }
                    }
                    .pointerInput(Unit) {
                        try {
                            detectTransformGestures { _, _, zoom, _ ->
                                if (zoom != 1f) {
                                    val camera = cameraInstance ?: return@detectTransformGestures
                                    try {
                                        val zoomState = camera.cameraInfo.zoomState.value
                                        val currentZoom = zoomState?.zoomRatio ?: 1f
                                        val minZoom = zoomState?.minZoomRatio ?: 1f
                                        val maxZoom = zoomState?.maxZoomRatio ?: 8f
                                        val targetZoom = (currentZoom * zoom).coerceIn(minZoom, maxZoom)
                                        try {
                                            camera.cameraControl.setZoomRatio(targetZoom)
                                            currentZoomValue = targetZoom
                                            
                                            val formatted = String.format(java.util.Locale.US, "%.1fx", targetZoom)
                                            zoomLevelText = formatted
                                            
                                            zoomTextJob?.cancel()
                                            zoomTextJob = scope.launch {
                                                kotlinx.coroutines.delay(1200)
                                                zoomLevelText = null
                                            }
                                        } catch (t: Throwable) {
                                            Log.e("CameraPreview", "Camera setZoomRatio exception", t)
                                        }
                                    } catch (t: Throwable) {
                                        Log.e("CameraPreview", "Zoom state exception", t)
                                    }
                                }
                            }
                        } catch (t: Throwable) {
                            Log.e("CameraPreview", "Transform gestures pointerInput exception", t)
                        }
                    }
            ) {
                // 1. Real Camera View or Elegant Live Viewfinder Simulator
                if (cameraInstance == null) {
                    GlorySimulatorViewport(
                        selectedFilter = selectedFilter,
                        cameraMode = selectedMode,
                        aspectRatio = aspectRatioPreset,
                        exposureIndex = exposureIndex,
                        isoValue = isoValue,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 2. Real-time Live Filter tint effect overlay
                FilterViewfinderOverlay(selectedFilter = selectedFilter)

                // 3. Cinematic Photography grid lines overlay (Rule of Thirds / Golden Triangle)
                GridOverlayLines(gridType = gridType)

                // 4. Focus Ring Overlay Indicator
                if (focusRingVisible && focusRingOffset != null) {
                    val ringOffset = focusRingOffset!!
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = GloryGold,
                            radius = 36.dp.toPx(),
                            center = ringOffset,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                        )
                        drawCircle(
                            color = GloryGold.copy(alpha = 0.45f),
                            radius = 6.dp.toPx(),
                            center = ringOffset
                        )
                    }
                }

                // 5. On-screen floating Zoom Badge Indicator
                if (zoomLevelText != null) {
                    Box(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                            .border(1.2.dp, GloryGold, RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "ZOOM: ${zoomLevelText}",
                            color = GloryGold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // 6. Quick Zoom Multipliers Pills at the bottom parameter of viewport
                Row(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .border(1.dp, GloryGold.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    val targetZooms = listOf(1.0f, 2.0f, 4.0f, 8.0f)
                    targetZooms.forEach { zMultiplier ->
                        val isCurrent = Math.abs(currentZoomValue - zMultiplier) < 0.15f
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isCurrent) GloryGold else Color(0x22FFFFFF))
                                .clickable {
                                    try {
                                        val camera = cameraInstance
                                        if (camera != null) {
                                            camera.cameraControl.setZoomRatio(zMultiplier)
                                            currentZoomValue = zMultiplier
                                            zoomLevelText = "${zMultiplier.toInt()}x"
                                            zoomTextJob?.cancel()
                                            zoomTextJob = scope.launch {
                                                kotlinx.coroutines.delay(1200)
                                                zoomLevelText = null
                                            }
                                        }
                                    } catch (t: Throwable) {
                                        Log.e("CameraPreview", "Quick zoom tap failed", t)
                                    }
                                },
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text(
                                text = "${zMultiplier.toInt()}x",
                                color = if (isCurrent) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterViewfinderOverlay(selectedFilter: FilterPreset) {
    // We add beautiful live view color shifts that perfectly preview the cinematic output color tones
    val tintColor = when (selectedFilter) {
        FilterPreset.ORIGINAL -> Color.Transparent
        FilterPreset.NEO_NOIR -> Color(0x333F444D) // Cold gray overlay to desaturate visually
        FilterPreset.GOLDEN_HOUR -> Color(0x2BFFB703) // Warm golden sunset amber
        FilterPreset.CYBERPUNK -> Color(0x2E8A2BE2)  // Sci-fi magenta glow
        FilterPreset.COOL_MIST -> Color(0x2B00F2FE)  // Dreamy arctic cyan ice
        FilterPreset.FOREST -> Color(0x1F2ECC71)     // Forest rich organic green
        FilterPreset.NIGHT_NEON -> Color(0x27FF5E00)  // Glowing warm street copper
        FilterPreset.AURORA_GREEN -> Color(0x2800FFCC) // Glowing boreal emerald/teal
        FilterPreset.ASTRO_DEEP -> Color(0x2E1A237E)  // Deep galactic navy/violet
    }

    if (tintColor != Color.Transparent) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tintColor)
        )
    }
}

@Composable
fun GridOverlayLines(gridType: GridType) {
    if (gridType == GridType.NONE) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val linePaintColor = Color.White.copy(alpha = 0.45f)
        val accentGoldColor = GloryGold.copy(alpha = 0.65f)

        when (gridType) {
            GridType.RULE_OF_THIRDS -> {
                // Two vertical lines
                drawLine(
                    color = linePaintColor,
                    start = Offset(width / 3f, 0f),
                    end = Offset(width / 3f, height),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = linePaintColor,
                    start = Offset(2f * width / 3f, 0f),
                    end = Offset(2f * width / 3f, height),
                    strokeWidth = 1.dp.toPx()
                )

                // Two horizontal lines
                drawLine(
                    color = linePaintColor,
                    start = Offset(0f, height / 3f),
                    end = Offset(width, height / 3f),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = linePaintColor,
                    start = Offset(0f, 2f * height / 3f),
                    end = Offset(width, 2f * height / 3f),
                    strokeWidth = 1.dp.toPx()
                )

                // Delicate Gold Center focal point indicators
                val centerOffset = Offset(width / 2f, height / 2f)
                drawCircle(
                    color = accentGoldColor,
                    radius = 4.dp.toPx(),
                    center = centerOffset
                )
            }
            GridType.GOLDEN_TRIANGLE -> {
                // Diagonals representing classic dynamic symmetrical crop lanes
                drawLine(
                    color = linePaintColor,
                    start = Offset(0f, 0f),
                    end = Offset(width, height),
                    strokeWidth = 1.dp.toPx()
                )
                // Intersecting perpendicular line 1: bottom-left to top-right diagonal
                drawLine(
                    color = linePaintColor,
                    start = Offset(0f, height),
                    end = Offset(width, 0f),
                    strokeWidth = 1.dp.toPx()
                )

                // Center crosshairs
                drawLine(
                    color = accentGoldColor,
                    start = Offset(width / 2f - 20.dp.toPx(), height / 2f),
                    end = Offset(width / 2f + 20.dp.toPx(), height / 2f),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    color = accentGoldColor,
                    start = Offset(width / 2f, height / 2f - 20.dp.toPx()),
                    end = Offset(width / 2f, height / 2f + 20.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
            else -> {}
        }
    }
}

// Convert Int to Dp safely within Compose Canvas drawings
private val Int.dp: androidx.compose.ui.unit.Dp
    get() = androidx.compose.ui.unit.Dp(this.toFloat())

private val Double.dp: androidx.compose.ui.unit.Dp
    get() = androidx.compose.ui.unit.Dp(this.toFloat())

@Composable
fun GlorySimulatorViewport(
    selectedFilter: FilterPreset,
    cameraMode: CameraMode,
    aspectRatio: AspectRatioPreset,
    exposureIndex: Int,
    isoValue: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SimulatorTransition")
    
    // Wave/Pulse animation for live feed simulation
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WaveOffset"
    )

    val neonPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "NeonPulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B14)),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. Generate stunning live background gradients matching the active filter
            val bgColors = when (selectedFilter) {
                FilterPreset.ORIGINAL -> listOf(Color(0xFF1F2937), Color(0xFF111827), Color(0xFF030712))
                FilterPreset.NEO_NOIR -> listOf(Color(0xFF374151), Color(0xFF1F2937), Color(0xFF030303))
                FilterPreset.GOLDEN_HOUR -> listOf(Color(0xFFD97706), Color(0xFF78350F), Color(0xFF1C0A06))
                FilterPreset.CYBERPUNK -> listOf(Color(0xFF4C1D95), Color(0xFF2E1065), Color(0xFF0B0214))
                FilterPreset.COOL_MIST -> listOf(Color(0xFF065F46), Color(0xFF047857), Color(0xFF022C22))
                FilterPreset.FOREST -> listOf(Color(0xFF064E3B), Color(0xFF14532D), Color(0xFF022C11))
                FilterPreset.NIGHT_NEON -> listOf(Color(0xFFC2410C), Color(0xFF7C2D12), Color(0xFF1B0721))
                FilterPreset.AURORA_GREEN -> listOf(Color(0xFF047857), Color(0xFF065F46), Color(0xFF02172C))
                FilterPreset.ASTRO_DEEP -> listOf(Color(0xFF1E1E38), Color(0xFF0B0E22), Color(0xFF02040A))
            }

            drawRect(
                brush = Brush.verticalGradient(bgColors),
                size = size
            )

            // 2. Draw Scenic Parallax Mountain Peaks or Glowing Grids based on Mode
            val accentColor = when (selectedFilter) {
                FilterPreset.GOLDEN_HOUR -> Color(0xFFFBBF24)
                FilterPreset.CYBERPUNK -> Color(0xFFF43F5E)
                FilterPreset.NEO_NOIR -> Color(0xFFE5E7EB)
                FilterPreset.COOL_MIST -> Color(0xFF22D3EE)
                else -> GloryGold
            }

            if (cameraMode == CameraMode.PRO) {
                // High-tech vector blueprint layout for Pro mode
                val gridStep = 40.dp.toPx()
                var currentX = 0f
                while (currentX < width) {
                    drawLine(
                        color = accentColor.copy(alpha = 0.08f * neonPulse),
                        start = Offset(currentX, 0f),
                        end = Offset(currentX, height),
                        strokeWidth = 1f
                    )
                    currentX += gridStep
                }
                var currentY = 0f
                while (currentY < height) {
                    drawLine(
                        color = accentColor.copy(alpha = 0.08f * neonPulse),
                        start = Offset(0f, currentY),
                        end = Offset(width, currentY),
                        strokeWidth = 1f
                    )
                    currentY += gridStep
                }

                // Waveform oscilloscope lines
                val path = Path()
                path.moveTo(0f, height * 0.5f)
                val segments = 20
                for (i in 0..segments) {
                    val px = (i.toFloat() / segments) * width
                    val py = height * 0.5f + (Math.sin((i * 0.5) + (waveOffset * 0.1f)) * 40f).toFloat()
                    path.lineTo(px, py)
                }
                drawPath(
                    path = path,
                    color = accentColor.copy(alpha = 0.45f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            } else {
                // Draw majestic mountain vectors (Sri Lanka style peaks)
                val peakColor1 = when (selectedFilter) {
                    FilterPreset.NEO_NOIR -> Color(0xFF1F2937)
                    FilterPreset.GOLDEN_HOUR -> Color(0xFF451A03)
                    FilterPreset.CYBERPUNK -> Color(0xFF2E0854)
                    FilterPreset.COOL_MIST -> Color(0xFF0F3E48)
                    FilterPreset.FOREST -> Color(0xFF0B3F21)
                    else -> Color(0xFF1E293B)
                }

                val peakColor2 = when (selectedFilter) {
                    FilterPreset.NEO_NOIR -> Color(0xFF111827)
                    FilterPreset.GOLDEN_HOUR -> Color(0xFF290800)
                    FilterPreset.CYBERPUNK -> Color(0xFF17022C)
                    FilterPreset.COOL_MIST -> Color(0xFF082730)
                    FilterPreset.FOREST -> Color(0xFF052D16)
                    else -> Color(0xFF0F172A)
                }

                // Draw Sun / Neon Orb rising or pulsing
                val sunCenterY = height * 0.4f + (waveOffset * 0.12f)
                val sunRadius = 64.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accentColor.copy(alpha = 0.85f), Color.Transparent),
                        center = Offset(width / 2f, sunCenterY),
                        radius = sunRadius * 1.5f
                    ),
                    radius = sunRadius * 1.5f,
                    center = Offset(width / 2f, sunCenterY)
                )

                drawCircle(
                    color = accentColor.copy(alpha = 0.6f * neonPulse),
                    radius = sunRadius,
                    center = Offset(width / 2f, sunCenterY)
                )

                // Background mountain paths
                val path1 = Path().apply {
                    moveTo(0f, height * 0.72f)
                    lineTo(width * 0.28f, height * 0.53f)
                    lineTo(width * 0.6f, height * 0.68f)
                    lineTo(width * 0.82f, height * 0.58f)
                    lineTo(width, height * 0.74f)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }
                drawPath(path = path1, color = peakColor1)

                // Foreground mountain paths (creating beautiful parallax perception)
                val path2 = Path().apply {
                    moveTo(0f, height * 0.84f)
                    lineTo(width * 0.22f, height * 0.68f)
                    lineTo(width * 0.52f, height * 0.59f)
                    lineTo(width * 0.76f, height * 0.75f)
                    lineTo(width, height * 0.64f)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }
                drawPath(path = path2, color = peakColor2)
            }

            // 3. Render Mode-specific focus overlay
            if (cameraMode == CameraMode.PORTRAIT) {
                // Focus circle targeting portrait focus
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f * neonPulse),
                    radius = 96.dp.toPx(),
                    center = Offset(width / 2f, height * 0.45f)
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.35f),
                    radius = 8.dp.toPx(),
                    center = Offset(width / 2f, height * 0.45f)
                )
            } else if (cameraMode == CameraMode.MACRO) {
                // Circular scanner pattern representing Macro lens magnification
                drawCircle(
                    color = accentColor.copy(alpha = 0.25f),
                    radius = 48.dp.toPx(),
                    center = Offset(width / 2f, height * 0.52f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f), waveOffset)
                    )
                )
            }
        }

        // 4. Beautiful overlay indicators alerting user simulator is active
        Column(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                .border(1.dp, GloryGold.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00FF87))
                )
                Text(
                    text = "LENS SIMULATOR ACTIVE",
                    color = GloryGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            Text(
                text = "සන්නිවේදක සිමියුලේටරය ක්‍රියාකාරී වේ",
                color = MutedSlate,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
