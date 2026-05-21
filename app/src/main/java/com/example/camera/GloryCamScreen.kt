package com.glorycam.app.camera

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.glorycam.app.ui.theme.*
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GloryCamScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Storage permissions: READ_MEDIA_IMAGES for Android 13+, READ_EXTERNAL_STORAGE for older
    val storagePermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasStoragePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasStoragePermission = isGranted
    }

    // Request storage permission on first launch if not granted
    LaunchedEffect(Unit) {
        if (!hasStoragePermission) {
            storagePermissionLauncher.launch(storagePermission)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                hasStoragePermission = ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Run photo directory scanner once on startup
    LaunchedEffect(Unit) {
        viewModel.scanPhotos(context)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DeepBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (hasCameraPermission) {
                // Show standard features
                GloryCustomCameraView(viewModel = viewModel)
            } else {
                // Show stunning permission request page
                GloryPermissionRequestView(onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                })
            }
        }
    }
}

@Composable
fun GloryPermissionRequestView(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = list {
                        add(DeepBackground)
                        add(DarkSurface)
                    }
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Elegant pulsing aperture graphic
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(GloryGold.copy(alpha = 0.15f), CircleShape)
                    .border(2.dp, GloryGold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Glory Cam Launcher",
                    tint = GloryGold,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Glory Cam",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = GloryGold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.testTag("perm_title")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Capture your moments in absolute cinematic glory. We require Camera permissions to display the interactive live viewfinder lens.",
                fontSize = 15.sp,
                color = WhiteCream.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GloryGold,
                    contentColor = DeepBackground
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("grant_permission_button")
            ) {
                Text(
                    text = "GRANT LENS PERMISSION",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun GloryCustomCameraView(viewModel: CameraViewModel) {
    val context = LocalContext.current
    var activeImageCapture: ImageCapture? by remember { mutableStateOf(null) }

    val lensFacing by viewModel.lensFacing.collectAsState()
    val flashMode by viewModel.flashMode.collectAsState()
    val gridType by viewModel.gridType.collectAsState()
    val aspectRatioPreset by viewModel.aspectRatioPreset.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val captureState by viewModel.captureState.collectAsState()
    val photosList by viewModel.photosList.collectAsState()
    val activePhoto by viewModel.activePhoto.collectAsState()
    val shutterFlashEffect by viewModel.shutterFlashEffect.collectAsState()
    val isNightModeOn by viewModel.isNightModeOn.collectAsState()

    val timerPreset by viewModel.timerPreset.collectAsState()
    val isWatermarkEnabled by viewModel.isWatermarkEnabled.collectAsState()
    val activeCountdown by viewModel.activeCountdown.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()

    val exposureIndex by viewModel.exposureIndex.collectAsState()
    val isoValue by viewModel.isoValue.collectAsState()
    val isProControlsOpen by viewModel.isProControlsOpen.collectAsState()

    var isGalleryOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Sleek top bar controls (Flash, Grid, Aspect Ratio, Night Mode, Timer, Watermark)
            CameraTopSettingsBar(
                flashMode = flashMode,
                gridType = gridType,
                aspectRatio = aspectRatioPreset,
                isNightMode = isNightModeOn,
                timerPreset = timerPreset,
                watermarkEnabled = isWatermarkEnabled,
                onNightModeToggle = { viewModel.toggleNightMode() },
                onFlashToggle = {
                    val nextFlash = when (flashMode) {
                        FlashModeState.OFF -> FlashModeState.ON
                        FlashModeState.ON -> FlashModeState.AUTO
                        FlashModeState.AUTO -> FlashModeState.OFF
                    }
                    viewModel.setFlashMode(nextFlash)
                },
                onGridToggle = {
                    val nextGrid = when (gridType) {
                        GridType.NONE -> GridType.RULE_OF_THIRDS
                        GridType.RULE_OF_THIRDS -> GridType.GOLDEN_TRIANGLE
                        GridType.GOLDEN_TRIANGLE -> GridType.NONE
                    }
                    viewModel.setGridType(nextGrid)
                },
                onRatioToggle = {
                    val nextRatio = when (aspectRatioPreset) {
                        AspectRatioPreset.RATIO_4_3 -> AspectRatioPreset.RATIO_16_9
                        AspectRatioPreset.RATIO_16_9 -> AspectRatioPreset.RATIO_1_1
                        AspectRatioPreset.RATIO_1_1 -> AspectRatioPreset.RATIO_4_3
                    }
                    viewModel.setAspectRatio(nextRatio)
                },
                onTimerToggle = {
                    val nextPreset = when (timerPreset) {
                        0 -> 3
                        3 -> 5
                        5 -> 10
                        else -> 0
                    }
                    viewModel.setTimerPreset(nextPreset)
                },
                onWatermarkToggle = {
                    viewModel.toggleWatermark()
                }
            )

            // 2. Center live viewfinder
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                CameraPreview(
                    lensFacing = lensFacing,
                    flashMode = flashMode,
                    gridType = gridType,
                    aspectRatioPreset = aspectRatioPreset,
                    selectedFilter = selectedFilter,
                    exposureIndex = exposureIndex,
                    onImageCaptureCreated = { activeImageCapture = it },
                    modifier = Modifier.fillMaxSize()
                )

                // Night Sight Active Banner overlay
                if (isNightModeOn) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .background(Color(0xE60A0E1A), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF00F2FE).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF00F2FE), CircleShape)
                        )
                        Text(
                            text = "AI NIGHT SIGHT: ACTIVE 🌙",
                            color = Color(0xFF00F2FE),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Shutter flash visual overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = shutterFlashEffect,
                    enter = fadeIn(animationSpec = tween(50)),
                    exit = fadeOut(animationSpec = tween(200))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    )
                }

                // Dynamic Multi-stage offline AI processing overlay
                if (captureState != CaptureState.Idle) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.70f)) // Beautiful glass dimmer
                            .clickable(enabled = false) {}, // Prevent interactive taps
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .width(320.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(DarkSurfaceOverlay)
                                .border(1.dp, GloryGold.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                                .padding(28.dp)
                        ) {
                            when (captureState) {
                                is CaptureState.GatheringLight -> {
                                    val stateVal = captureState as CaptureState.GatheringLight
                                    CircularProgressIndicator(
                                        progress = stateVal.progress,
                                        color = GloryGold,
                                        trackColor = GloryGold.copy(alpha = 0.2f),
                                        strokeWidth = 4.dp,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = "HOLD PHONE STILL",
                                        color = GloryLightGold,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "දුරකථනය නොසලකා තබාගන්න",
                                        color = GloryGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = stateVal.label,
                                        color = WhiteCream,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                CaptureState.AISensorOptimization -> {
                                    CircularProgressIndicator(color = GloryGold, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = "SENSOR CALIBRATION",
                                        color = GloryGold,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "සෙන්සර් ප්‍රශස්තකරණය",
                                        color = GloryGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Optimizing sub-pixel exposure matrices for low quality sensors...",
                                        color = MutedSlate,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                CaptureState.AIDenoising -> {
                                    CircularProgressIndicator(color = CyanAccent, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = "OFFLINE AI: DENOISING",
                                        color = CyanAccent,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "නොයිස් සහ ධූලි ඉවත් කිරීම",
                                        color = CyanAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Applying edge-preserving bilateral filtering to cancel digital noise...",
                                        color = MutedSlate,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                CaptureState.AIColorEnhancing -> {
                                    CircularProgressIndicator(color = GloryGold, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = "COLOR TUNING & SKIN GLOW",
                                        color = GloryGold,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "වර්ණ සහ මුහුණේ සම ප්‍රශස්තකරණය",
                                        color = GloryGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Recalibrating skin-tones and local contrast dynamic curves...",
                                        color = MutedSlate,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                CaptureState.AISharpening -> {
                                    CircularProgressIndicator(color = GloryLightGold, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = "LENS SHARPENING",
                                        color = GloryLightGold,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "කාචයේ තියුණුබව වැඩි කිරීම",
                                        color = GloryLightGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Applying unsharp matrix to recover details on budget optics...",
                                        color = MutedSlate,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                is CaptureState.Success -> {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = CyanAccent,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = "PICTURE ENHANCED!",
                                        color = CyanAccent,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "ඡායාරූපය සාර්ථකව සකසා ඇත!",
                                        color = CyanAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Pristine offline masterpiece loaded to gallery.",
                                        color = WhiteCream.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                is CaptureState.Error -> {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "Error",
                                        tint = RoseAccent,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = "CAPTURE ERROR",
                                        color = RoseAccent,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = (captureState as CaptureState.Error).message,
                                        color = WhiteCream.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }

                // 3. Self-Timer visual countdown overlay
                if (activeCountdown != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(enabled = false) {},
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = activeCountdown.toString(),
                                fontSize = 110.sp,
                                fontWeight = FontWeight.Black,
                                color = GloryGold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "PHOTO INCOMING...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = WhiteCream,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "රූපය ගැනීමට සූදානම් වන්න...",
                                fontSize = 12.sp,
                                color = GloryLightGold
                            )
                        }
                    }
                }

                // 7. Manual Pro Controls (Exposure and ISO) Slide Overlay Panel
                if (isProControlsOpen) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 68.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xF20B0C15)) // Translucent glassmorphic dark surface
                            .border(1.dp, GloryGold.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        // Title Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Pro Tuning",
                                    tint = GloryGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "MANUAL PRO CONTROLS",
                                    color = GloryLightGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = "Pro කැමරා පාලක (Manual)",
                                color = GloryGold.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = Color.White.copy(alpha = 0.1f)
                        )

                        // Exposure Slider Column
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "EXPOSURE COMPENSATION: " + (if (exposureIndex >= 0) "+$exposureIndex" else "$exposureIndex") + " EV",
                                    color = WhiteCream.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ආලෝක සංවේදීතාව (Exposure)",
                                    color = MutedSlate,
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("-4", color = MutedSlate, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Slider(
                                    value = exposureIndex.toFloat(),
                                    onValueChange = { viewModel.setExposureIndex(it.toInt()) },
                                    valueRange = -4f..4f,
                                    steps = 7,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = GloryGold,
                                        activeTrackColor = GloryGold,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                                        activeTickColor = Color.Transparent,
                                        inactiveTickColor = Color.Transparent
                                    )
                                )
                                Text("+4", color = MutedSlate, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ISO Row
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "SENSOR GAIN (ISO): " + if (isoValue == 400) "400 (Auto)" else "$isoValue",
                                    color = WhiteCream.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "සංවේදක Gain (ISO)",
                                    color = MutedSlate,
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isoOptions = listOf(100, 200, 400, 800, 1600, 3200)
                                isoOptions.forEach { value ->
                                    val isSelected = isoValue == value
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 2.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) GloryGold else Color.White.copy(alpha = 0.05f))
                                            .border(1.dp, if (isSelected) GloryGold else Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                            .clickable { viewModel.setIsoValue(value) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = value.toString(),
                                            color = if (isSelected) Color.Black else WhiteCream.copy(alpha = 0.8f),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 8. Elegant Toggle PRO Floating Capsule Button on bottom-right of Viewfinder
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isProControlsOpen) GloryGold else Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, GloryGold, RoundedCornerShape(18.dp))
                        .clickable { viewModel.toggleProControls() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("pro_controls_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Manual Pro Controls Trigger",
                            tint = if (isProControlsOpen) Color.Black else GloryGold,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "PRO CONTROLS",
                            color = if (isProControlsOpen) Color.Black else GloryGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // 3. Bottom controls (Filters list + Shutter actions)
            CameraBottomPityBar(
                photosList = photosList,
                selectedFilter = selectedFilter,
                selectedMode = selectedMode,
                onFilterSelected = { viewModel.selectFilter(it) },
                onModeSelected = { viewModel.selectCameraMode(it) },
                onCaptureClick = {
                    activeImageCapture?.let { ic ->
                        viewModel.capturePhoto(
                            context = context,
                            imageCapture = ic,
                            selectedFilter = selectedFilter
                        )
                    }
                },
                onLensToggleClick = { viewModel.toggleCameraLens() },
                onGalleryThumbnailClick = { isGalleryOpen = true }
            )
        }

        // Overlay Gallery Modal Sheet Drawer
        androidx.compose.animation.AnimatedVisibility(
            visible = isGalleryOpen,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(400)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(400))
        ) {
            GloryGalleryView(
                photos = photosList,
                activePhoto = activePhoto,
                onClose = { isGalleryOpen = false },
                onPhotoClick = { viewModel.setActivePhoto(it) },
                onDeleteClick = { viewModel.deletePhoto(context, it) },
                onBackFromFullscreen = { viewModel.setActivePhoto(null) }
            )
        }
    }
}

@Composable
fun CameraTopSettingsBar(
    flashMode: FlashModeState,
    gridType: GridType,
    aspectRatio: AspectRatioPreset,
    isNightMode: Boolean,
    timerPreset: Int,
    watermarkEnabled: Boolean,
    onNightModeToggle: () -> Unit,
    onFlashToggle: () -> Unit,
    onGridToggle: () -> Unit,
    onRatioToggle: () -> Unit,
    onTimerToggle: () -> Unit,
    onWatermarkToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepBackground)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = "GLORY CAM",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = GloryGold,
                letterSpacing = 1.2.sp,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = "Premium Live Lenses",
                fontSize = 10.sp,
                color = MutedSlate,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }

        // Action settings controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Self-Timer cyclic button
            IconButton(
                onClick = onTimerToggle,
                modifier = Modifier
                    .background(if (timerPreset > 0) Color(0xFF261D0F) else DarkSurface, CircleShape)
                    .border(
                        1.dp,
                        if (timerPreset > 0) GloryGold.copy(alpha = 0.8f) else GloryGold.copy(alpha = 0.15f),
                        CircleShape
                    )
                    .size(36.dp)
                    .testTag("timer_toggle")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Timer Preset",
                        tint = if (timerPreset > 0) GloryGold else MutedSlate,
                        modifier = Modifier.size(16.dp)
                    )
                    if (timerPreset > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 3.dp, y = 3.dp)
                                .background(GloryGold, CircleShape)
                                .padding(horizontal = 3.dp, vertical = 0.5.dp)
                        ) {
                            Text(
                                text = "${timerPreset}s",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                        }
                    }
                }
            }

            // Elegant Watermark Stamp toggle button
            IconButton(
                onClick = onWatermarkToggle,
                modifier = Modifier
                    .background(if (watermarkEnabled) Color(0xFF261D0F) else DarkSurface, CircleShape)
                    .border(
                        1.dp,
                        if (watermarkEnabled) GloryGold.copy(alpha = 0.8f) else GloryGold.copy(alpha = 0.15f),
                        CircleShape
                    )
                    .size(36.dp)
                    .testTag("watermark_toggle")
            ) {
                Icon(
                    imageVector = Icons.Default.Copyright,
                    contentDescription = "Elegant Watermark Stamp State",
                    tint = if (watermarkEnabled) GloryGold else MutedSlate,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Night Mode Toggle Button (Glowing Cyan representation of AI Night Sight)
            IconButton(
                onClick = onNightModeToggle,
                modifier = Modifier
                    .background(if (isNightMode) Color(0xFF132237) else DarkSurface, CircleShape)
                    .border(
                        1.dp,
                        if (isNightMode) Color(0xFF00F2FE).copy(alpha = 0.7f) else GloryGold.copy(alpha = 0.15f),
                        CircleShape
                    )
                    .size(36.dp)
                    .testTag("night_mode_toggle")
            ) {
                Icon(
                    imageVector = Icons.Default.NightsStay,
                    contentDescription = "AI Night Sight Mode",
                    tint = if (isNightMode) Color(0xFF00F2FE) else MutedSlate,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Flash Mode trigger cyclic button
            IconButton(
                onClick = onFlashToggle,
                modifier = Modifier
                    .background(DarkSurface, CircleShape)
                    .border(1.dp, GloryGold.copy(alpha = 0.15f), CircleShape)
                    .size(36.dp)
                    .testTag("flash_toggle")
            ) {
                val flashIcon = when (flashMode) {
                    FlashModeState.OFF -> Icons.Default.FlashOff
                    FlashModeState.ON -> Icons.Default.FlashOn
                    FlashModeState.AUTO -> Icons.Default.FlashAuto
                }
                Icon(
                    imageVector = flashIcon,
                    contentDescription = "Flash Mode",
                    tint = if (flashMode == FlashModeState.OFF) MutedSlate else GloryGold,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Grid layout cyclic button
            IconButton(
                onClick = onGridToggle,
                modifier = Modifier
                    .background(DarkSurface, CircleShape)
                    .border(1.dp, GloryGold.copy(alpha = 0.15f), CircleShape)
                    .size(36.dp)
                    .testTag("grid_toggle")
            ) {
                Icon(
                    imageVector = if (gridType == GridType.NONE) Icons.Default.GridOff else Icons.Default.GridOn,
                    contentDescription = "Grid State",
                    tint = if (gridType == GridType.NONE) MutedSlate else GloryGold,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Ratio cyclic button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(DarkSurface)
                    .border(1.dp, GloryGold.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                    .clickable { onRatioToggle() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("ratio_toggle"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = aspectRatio.displayName,
                    color = GloryGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CameraBottomPityBar(
    photosList: List<CapturedMedia>,
    selectedFilter: FilterPreset,
    selectedMode: CameraMode,
    onFilterSelected: (FilterPreset) -> Unit,
    onModeSelected: (CameraMode) -> Unit,
    onCaptureClick: () -> Unit,
    onLensToggleClick: () -> Unit,
    onGalleryThumbnailClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepBackground)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    if (dragAmount < -15) {
                        isExpanded = true
                    } else if (dragAmount > 15) {
                        isExpanded = false
                    }
                }
            }
            .padding(bottom = 24.dp)
    ) {
        // Interlocking Drag & Toggle Handle Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Pill shape handle
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Premium Sinhala & English swipe guides
                Text(
                    text = if (isExpanded) "SWIPE DOWN TO COLLAPSE ▽" else "SWIPE UP FOR FILTERS & MODES △",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = GloryGold.copy(alpha = 0.7f),
                    letterSpacing = 0.5.sp
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Linear scrollable filter strip overlay
                Text(
                    text = "PHOTO FILTERS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedSlate,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp)
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(FilterPreset.values()) { preset ->
                        val isSelected = selectedFilter == preset
                        val bubbleColor = when (preset) {
                            FilterPreset.ORIGINAL -> Brush.radialGradient(list { add(Color.Gray); add(Color.DarkGray) })
                            FilterPreset.NEO_NOIR -> Brush.verticalGradient(list { add(Color(0xFF8E9EAB)); add(Color(0xFFEEF2F3)) })
                            FilterPreset.GOLDEN_HOUR -> Brush.linearGradient(list { add(Color(0xFFF39C12)); add(Color(0xFFD35400)) })
                            FilterPreset.CYBERPUNK -> Brush.linearGradient(list { add(Color(0xFF8A2BE2)); add(Color(0xFF00FFFF)) })
                            FilterPreset.COOL_MIST -> Brush.horizontalGradient(list { add(Color(0xFF00FF87)); add(Color(0xFF60EFFF)) })
                            FilterPreset.FOREST -> Brush.radialGradient(list { add(Color(0xFF11998E)); add(Color(0xFF38EF7D)) })
                            FilterPreset.NIGHT_NEON -> Brush.linearGradient(list { add(Color(0xFFFF5E00)); add(Color(0xFFFFB703)) })
                            FilterPreset.AURORA_GREEN -> Brush.linearGradient(list { add(Color(0xFF00FFCC)); add(Color(0xFF00F2FE)) })
                            FilterPreset.ASTRO_DEEP -> Brush.radialGradient(list { add(Color(0xFF1A1C30)); add(Color(0xFF5D3FDB)) })
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { onFilterSelected(preset) }
                                .padding(horizontal = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(bubbleColor)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) GloryGold else Color.White.copy(alpha = 0.2f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        tint = if (preset == FilterPreset.NEO_NOIR) Color.Black else Color.White,
                                        contentDescription = "Active",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = preset.displayName,
                                fontSize = 12.sp,
                                color = if (isSelected) GloryGold else WhiteCream.copy(alpha = 0.6f),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Horizontal camera modes selector
                Text(
                    text = "CAMERA MODE (ක්‍රමය)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedSlate,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp)
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(CameraMode.values()) { mode ->
                        val isSelected = selectedMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) GloryGold else Color.White.copy(alpha = 0.04f))
                                .border(
                                    1.dp,
                                    if (isSelected) GloryGold else Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onModeSelected(mode) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .testTag("mode_tab_${mode.name.lowercase(Locale.ROOT)}")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = mode.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else WhiteCream
                                )
                                Text(
                                    text = mode.sinhalaName,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected) Color.Black.copy(alpha = 0.7f) else MutedSlate
                                )
                            }
                        }
                    }
                }
            }
        }

        // Major shutter action triggers (Gallery thumbnail, large Shutter trigger, Lens Switcher)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Gallery Launcher Button with Subtitle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.clickable { onGalleryThumbnailClick() }
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(DarkSurface)
                        .border(2.dp, GloryGold.copy(alpha = 0.45f), CircleShape)
                        .testTag("gallery_launcher"),
                    contentAlignment = Alignment.Center
                ) {
                    val latestPhoto = photosList.firstOrNull()
                    if (latestPhoto != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = latestPhoto.fileUri,
                                contentDescription = "Latest Photo Preview",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            // Overlaid tiny gallery badge indicator
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(20.dp)
                                    .background(GloryGold, CircleShape)
                                    .border(1.2.dp, DeepBackground, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Collections,
                                    contentDescription = "Badge",
                                    tint = DeepBackground,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = "Empty Gallery",
                            tint = GloryGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ගැලරිය",
                    color = GloryGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "GALLERY",
                    color = MutedSlate,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp
                )
            }

            // Middle: Core Shutter trigger
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(4.dp, GloryGold, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null, // Custom visual shrink below
                        onClick = onCaptureClick
                    )
                    .testTag("shutter_button"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(WhiteCream)
                        .border(1.dp, GloryDarkGold, CircleShape)
                )
            }

            // Right: Flip Rotating Lens Select with Subtitle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.clickable { onLensToggleClick() }
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(DarkSurface)
                        .border(1.dp, GloryGold.copy(alpha = 0.15f), CircleShape)
                        .testTag("camera_switch"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Flip Camera Lens",
                        tint = GloryGold,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "කැමරාව",
                    color = GloryGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "SWITCH",
                    color = MutedSlate,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun GloryGalleryView(
    photos: List<CapturedMedia>,
    activePhoto: CapturedMedia?,
    onClose: () -> Unit,
    onPhotoClick: (CapturedMedia) -> Unit,
    onDeleteClick: (CapturedMedia) -> Unit,
    onBackFromFullscreen: () -> Unit
) {
    var showDeleteConfirm: CapturedMedia? by remember { mutableStateOf(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .background(DarkSurface, CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Viewfinder",
                            tint = GloryGold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Glory Gallery",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = GloryGold
                        )
                        Text(
                            text = "${photos.size} Memories captured",
                            fontSize = 12.sp,
                            color = MutedSlate
                        )
                    }
                }
            }

            if (photos.isEmpty()) {
                // Cozy aesthetic empty view
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "No images",
                            tint = MutedSlate.copy(alpha = 0.4f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Memories Yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = GloryGold.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Hit the shutter back in the lens viewfinder to capture gorgeous photo moments with custom presets",
                            fontSize = 14.sp,
                            color = MutedSlate,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Elegant gallery grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("gallery_grid"),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(photos) { photo ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onPhotoClick(photo) }
                                .testTag("gallery_item_${photo.id}")
                        ) {
                            // Render with applied filter color Matrix in thumbnail!
                            AsyncImage(
                                model = photo.fileUri,
                                contentDescription = "Captured Image",
                                contentScale = ContentScale.Crop,
                                colorFilter = ColorFilter.colorMatrix(photo.filterApplied.getColorMatrix()),
                                modifier = Modifier.fillMaxSize()
                            )

                            // Small bottom preset tag overlay
                            if (photo.filterApplied != FilterPreset.ORIGINAL) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .background(
                                            GloryDarkGold.copy(alpha = 0.85f),
                                            RoundedCornerShape(topStart = 6.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = photo.filterApplied.displayName,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fullscreen Slide Image Viewer with Custom Preset Matrix Apply
        androidx.compose.animation.AnimatedVisibility(
            visible = activePhoto != null,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            activePhoto?.let { photo ->
                FullscreenPhotoViewer(
                    photo = photo,
                    onBack = onBackFromFullscreen,
                    onDelete = { showDeleteConfirm = photo }
                )
            }
        }

        // Modern gold-capped delete prompt modal
        showDeleteConfirm?.let { photoToDelete ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                containerColor = DarkSurface,
                title = {
                    Text("Delete Masterpiece?", color = GloryGold, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text("This captured glory moment will be permanently deleted from your gallery storage.", color = WhiteCream)
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteClick(photoToDelete)
                            showDeleteConfirm = null
                        }
                    ) {
                        Text("DELETE", color = RoseAccent, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = null }) {
                        Text("CANCEL", color = GloryGold)
                    }
                }
            )
        }
    }
}

@Composable
fun FullscreenPhotoViewer(
    photo: CapturedMedia,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val formattedDate = remember(photo.dateAdded) { formatter.format(Date(photo.dateAdded)) }
    var showExifByChoice by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = false) {}
    ) {
        // High fidelity zoomed view scale
        AsyncImage(
            model = photo.fileUri,
            contentDescription = "Fullscreen memory",
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .testTag("fullscreen_image"),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.colorMatrix(photo.getColorMatrix())
        )

        // Floating top panel back bar overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(list { add(Color.Black.copy(alpha = 0.75f)); add(Color.Transparent) }))
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .size(44.dp)
            ) {
                Icon(
                     imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                     contentDescription = "Back",
                     tint = GloryGold
                )
            }

            // Trash trigger
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .size(44.dp)
                    .testTag("delete_photo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Picture",
                    tint = RoseAccent
                )
            }
        }

        // Floating info panel details overlay at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(list { add(Color.Transparent); add(Color.Black.copy(alpha = 0.9f)) }))
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = photo.name,
                        color = WhiteCream,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(GloryGold.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Filter: ${photo.filterApplied.displayName}",
                                color = GloryGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "•",
                            color = MutedSlate,
                            fontSize = 11.sp
                        )
                        Text(
                            text = formattedDate,
                            color = MutedSlate,
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(
                    onClick = { showExifByChoice = !showExifByChoice },
                    modifier = Modifier
                        .background(if (showExifByChoice) GloryGold else Color.White.copy(alpha = 0.08f), CircleShape)
                        .size(44.dp)
                        .border(1.dp, GloryGold.copy(alpha = 0.35f), CircleShape)
                        .testTag("exif_info_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Show Photo Parameters Info",
                        tint = if (showExifByChoice) Color.Black else GloryGold,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Frosted metallic EXIF sheet overlay
        if (showExifByChoice) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFA080A10))
                    .border(1.dp, GloryGold.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = GloryGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "EXIF PARAMETERS & DETAILS",
                                color = GloryGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                        IconButton(
                            onClick = { showExifByChoice = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Dialog",
                                tint = MutedSlate,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                    ExifDetailRow("File ID (ඡායාරූපය)", photo.id)
                    ExifDetailRow("Physical Path (ස්ථානය)", photo.absolutePath)
                    ExifDetailRow("Capture Date (දිනය ගත්)", formattedDate)
                    ExifDetailRow("Lens Tuning Mode (ක්‍රමය)", "${photo.cameraMode.displayName} (${photo.cameraMode.sinhalaName})")
                    ExifDetailRow("AI Film Color (වර්ණ)", photo.filterApplied.displayName)
                    ExifDetailRow("Sensor Sensitivity (සංවේදනය)", "ISO ${photo.isoValue}")
                    ExifDetailRow("Exposure Index (EV)", "${if (photo.exposureIndex >= 0) "+" else ""}${photo.exposureIndex} EV")
                    ExifDetailRow("Virtual Aperture", "f/1.8 Low-Light Glass")
                    ExifDetailRow("Calculated Size", "${String.format(Locale.US, "%.2f", File(photo.absolutePath).length() / (1024f * 1024f))} MB")
                }
            }
        }
    }
}

@Composable
fun ExifDetailRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            color = MutedSlate,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = WhiteCream,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 16.dp)
        )
    }
}

// Extension to retrieve the filter preset of a CaputerdMedia or ORIGINAL if missing
fun CapturedMedia.getColorMatrix(): ColorMatrix {
    return this.filterApplied.getColorMatrix()
}

// Simple extension helper to clean up Kotlin code
private fun <T> list(builder: ArrayList<T>.() -> Unit): List<T> {
    return ArrayList<T>().apply(builder)
}
