package com.glorycam.app.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraViewModel : ViewModel() {

    private val _photosList = MutableStateFlow<List<CapturedMedia>>(emptyList())
    val photosList: StateFlow<List<CapturedMedia>> = _photosList.asStateFlow()

    private val _lensFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lensFacing: StateFlow<Int> = _lensFacing.asStateFlow()

    private val _flashMode = MutableStateFlow(FlashModeState.OFF)
    val flashMode: StateFlow<FlashModeState> = _flashMode.asStateFlow()

    private val _selectedFilter = MutableStateFlow(FilterPreset.ORIGINAL)
    val selectedFilter: StateFlow<FilterPreset> = _selectedFilter.asStateFlow()

    private val _gridType = MutableStateFlow(GridType.RULE_OF_THIRDS)
    val gridType: StateFlow<GridType> = _gridType.asStateFlow()

    private val _aspectRatioPreset = MutableStateFlow(AspectRatioPreset.RATIO_4_3)
    val aspectRatioPreset: StateFlow<AspectRatioPreset> = _aspectRatioPreset.asStateFlow()

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _isNightModeOn = MutableStateFlow(false)
    val isNightModeOn: StateFlow<Boolean> = _isNightModeOn.asStateFlow()

    private val _exposureIndex = MutableStateFlow(0)
    val exposureIndex: StateFlow<Int> = _exposureIndex.asStateFlow()

    private val _isoValue = MutableStateFlow(400) // Options: 100, 200, 400, 800, 1600, 3200
    val isoValue: StateFlow<Int> = _isoValue.asStateFlow()

    private val _isProControlsOpen = MutableStateFlow(false)
    val isProControlsOpen: StateFlow<Boolean> = _isProControlsOpen.asStateFlow()

    private val _selectedMode = MutableStateFlow(CameraMode.STANDARD)
    val selectedMode: StateFlow<CameraMode> = _selectedMode.asStateFlow()

    private val _timerPreset = MutableStateFlow(0) // 0s, 3s, 5s, 10s
    val timerPreset: StateFlow<Int> = _timerPreset.asStateFlow()

    private val _activeCountdown = MutableStateFlow<Int?>(null)
    val activeCountdown: StateFlow<Int?> = _activeCountdown.asStateFlow()

    private val _isWatermarkEnabled = MutableStateFlow(true)
    val isWatermarkEnabled: StateFlow<Boolean> = _isWatermarkEnabled.asStateFlow()

    // Auto exposure: true when not in PRO mode (CameraX handles ISO/shutter automatically)
    private val _isAutoExposureActive = MutableStateFlow(true)
    val isAutoExposureActive: StateFlow<Boolean> = _isAutoExposureActive.asStateFlow()

    private val _activePhoto = MutableStateFlow<CapturedMedia?>(null)
    val activePhoto: StateFlow<CapturedMedia?> = _activePhoto.asStateFlow()

    private val _shutterFlashEffect = MutableStateFlow(false)
    val shutterFlashEffect: StateFlow<Boolean> = _shutterFlashEffect.asStateFlow()

    fun selectCameraMode(mode: CameraMode) {
        _selectedMode.value = mode
        _isNightModeOn.value = mode == CameraMode.NIGHT
        _isProControlsOpen.value = mode == CameraMode.PRO
        // Auto exposure is active in all modes except PRO (where user sets ISO/EV manually)
        _isAutoExposureActive.value = mode != CameraMode.PRO
    }

    fun toggleAutoExposure() {
        _isAutoExposureActive.value = !_isAutoExposureActive.value
    }

    fun setTimerPreset(seconds: Int) {
        _timerPreset.value = seconds
    }

    fun toggleWatermark() {
        _isWatermarkEnabled.value = !_isWatermarkEnabled.value
    }

    fun toggleProControls() {
        _isProControlsOpen.value = !_isProControlsOpen.value
        if (_isProControlsOpen.value) {
            _selectedMode.value = CameraMode.PRO
        } else if (_selectedMode.value == CameraMode.PRO) {
            _selectedMode.value = CameraMode.STANDARD
        }
    }

    fun setExposureIndex(index: Int) {
        _exposureIndex.value = index.coerceIn(-4, 4)
    }

    fun setIsoValue(value: Int) {
        _isoValue.value = value
    }

    fun toggleNightMode() {
        _isNightModeOn.value = !_isNightModeOn.value
        _selectedMode.value = if (_isNightModeOn.value) CameraMode.NIGHT else CameraMode.STANDARD
    }

    fun setNightMode(enabled: Boolean) {
        _isNightModeOn.value = enabled
        _selectedMode.value = if (enabled) CameraMode.NIGHT else CameraMode.STANDARD
    }

    fun toggleCameraLens() {
        _lensFacing.value = if (_lensFacing.value == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    fun setFlashMode(mode: FlashModeState) {
        _flashMode.value = mode
    }

    fun setGridType(type: GridType) {
        _gridType.value = type
    }

    fun setAspectRatio(ratio: AspectRatioPreset) {
        _aspectRatioPreset.value = ratio
    }

    fun selectFilter(preset: FilterPreset) {
        _selectedFilter.value = preset
    }

    fun setActivePhoto(photo: CapturedMedia?) {
        _activePhoto.value = photo
    }

    fun triggerShutterFlash() {
        viewModelScope.launch {
            _shutterFlashEffect.value = true
            kotlinx.coroutines.delay(100)
            _shutterFlashEffect.value = false
        }
    }

    fun scanPhotos(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let {
                File(it, "GloryCam").apply { mkdirs() }
            }
            val targetDir = mediaDir ?: context.filesDir
            val files = targetDir.listFiles { file -> 
                file.name.endsWith(".jpg", ignoreCase = true) || 
                file.name.endsWith(".jpeg", ignoreCase = true)
            }
            val mappedList = files?.sortedByDescending { it.lastModified() }?.map { file ->
                var detectedFilter = FilterPreset.ORIGINAL
                for (preset in FilterPreset.values()) {
                    if (file.name.contains("_FLTR_${preset.name}")) {
                        detectedFilter = preset
                        break
                    }
                }

                var detectedMode = CameraMode.STANDARD
                for (mode in CameraMode.values()) {
                    if (file.name.contains("_MODE_${mode.name}")) {
                        detectedMode = mode
                        break
                    }
                }
                // Compatibility fallback
                if (file.name.contains("_NIGHT_") && detectedMode == CameraMode.STANDARD) {
                    detectedMode = CameraMode.NIGHT
                }

                var evVal = 0
                val evRegex = "_EV_([+-]?\\d+)".toRegex()
                evRegex.find(file.name)?.let { match ->
                    evVal = match.groupValues[1].toIntOrNull() ?: 0
                }

                var isoVal = 400
                val isoRegex = "_ISO_(\\d+)".toRegex()
                isoRegex.find(file.name)?.let { match ->
                    isoVal = match.groupValues[1].toIntOrNull() ?: 400
                }

                val displayName = file.nameWithoutExtension
                    .substringBefore("_FLTR_")
                    .substringBefore("_MODE_")
                    .substringBefore("_EV_")
                    .substringBefore("_ISO_")

                CapturedMedia(
                    id = file.name,
                    name = displayName,
                    fileUri = Uri.fromFile(file),
                    absolutePath = file.absolutePath,
                    dateAdded = file.lastModified(),
                    filterApplied = detectedFilter,
                    cameraMode = detectedMode,
                    exposureIndex = evVal,
                    isoValue = isoVal
                )
            } ?: emptyList()
            _photosList.value = mappedList
        }
    }

    fun addPhoto(context: Context, file: File, filterPreset: FilterPreset) {
        viewModelScope.launch(Dispatchers.IO) {
            // Give it a scan to refresh state
            scanPhotos(context)
        }
    }

    fun deletePhoto(context: Context, photo: CapturedMedia) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(photo.absolutePath)
                if (file.exists()) {
                    file.delete()
                }
                if (_activePhoto.value?.id == photo.id) {
                    _activePhoto.value = null
                }
                scanPhotos(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearCaptureState() {
        _captureState.value = CaptureState.Idle
    }

    fun setCaptureState(state: CaptureState) {
        _captureState.value = state
    }

    fun captureSimulatedPhoto(
        context: Context,
        selectedFilter: FilterPreset
    ) {
        if (_captureState.value != CaptureState.Idle) return

        val seconds = _timerPreset.value
        if (seconds > 0) {
            viewModelScope.launch {
                for (i in seconds downTo 1) {
                    _activeCountdown.value = i
                    delay(1000)
                }
                _activeCountdown.value = null
                executeSimulatedCapture(context, selectedFilter)
            }
        } else {
            viewModelScope.launch {
                executeSimulatedCapture(context, selectedFilter)
            }
        }
    }

    fun capturePhoto(
        context: Context,
        imageCapture: ImageCapture,
        selectedFilter: FilterPreset
    ) {
        if (_captureState.value != CaptureState.Idle) return

        val seconds = _timerPreset.value
        if (seconds > 0) {
            viewModelScope.launch {
                for (i in seconds downTo 1) {
                    _activeCountdown.value = i
                    delay(1000)
                }
                _activeCountdown.value = null
                executeCapture(context, imageCapture, selectedFilter)
            }
        } else {
            viewModelScope.launch {
                executeCapture(context, imageCapture, selectedFilter)
            }
        }
    }

    private suspend fun executeCapture(
        context: Context,
        imageCapture: ImageCapture,
        selectedFilter: FilterPreset
    ) {
        val isNight = _isNightModeOn.value || _selectedMode.value == CameraMode.NIGHT
        val activeMode = _selectedMode.value
        val watermark = _isWatermarkEnabled.value

        // Step 1: Long exposure sensor light gathering simulation
        if (isNight) {
            setCaptureState(CaptureState.GatheringLight(0.1f, "🌙 NIGHT SIGHT: Integrating shadows and ISO curves...\n(ISO මට්ටම සකසමින්...)"))
            delay(500)
            setCaptureState(CaptureState.GatheringLight(0.4f, "🌙 NIGHT SIGHT: Gathering nocturnal ambient photon flux...\n(රාත්‍රී ආලෝකය ඒකාබද්ධ කරමින්...)"))
            delay(600)
            setCaptureState(CaptureState.GatheringLight(0.7f, "🌙 NIGHT SIGHT: Consolidating sub-exposures for low-light...\n(උප-ප්‍රශස්තකරණ ඡායාරූප සලකමින්...)"))
            delay(600)
            setCaptureState(CaptureState.GatheringLight(1.0f, "🌙 NIGHT SIGHT: Custom low-light sensor optimization...\n(රාත්‍රී ආලෝක ධාරාවන් එක්රැස් කරමින්...)"))
            delay(400)
        } else {
            val modeLabel = when (activeMode) {
                CameraMode.PORTRAIT -> "Adjusting dual-lens portrait aperture...\n(පසුබිම බොඳ කිරීමේ ප්‍රශස්තකරණය...)"
                CameraMode.MACRO -> "Fine-focusing macro lens proximity...\n(ලඟම ඇති වස්තූන්ගේ තියුණුබව...)"
                CameraMode.PRO -> "Measuring raw digital light index...\n(ප්‍රෝ කැමරා අගයන් සලකමින්...)"
                else -> "Calibrating sensor & ambient light context...\n(සෙන්සර් ක්‍රමාංකනය කරමින්...)"
            }
            setCaptureState(CaptureState.GatheringLight(0.1f, modeLabel))
            delay(300)
            setCaptureState(CaptureState.GatheringLight(0.4f, "Gathering surrounding atmospheric light...\n(වටපිටාවේ ආලෝකය හඳුනාගනිමින්...)"))
            delay(400)
            setCaptureState(CaptureState.GatheringLight(0.7f, "Integrating physical lens focal matrices...\n(කාචයේ ආලෝක අනුපාත සලකමින්...)"))
            delay(400)
            setCaptureState(CaptureState.GatheringLight(1.0f, "Aligning low-light sensor pixels...\n(පික්සල් පෙළගැස්වීම් සිදුකරමින්...)"))
            delay(200)
        }

        // Flash shutter right before capturing
        triggerShutterFlash()

        // Get standard destination directory safely
        val mediaDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let {
            File(it, "GloryCam").apply { mkdirs() }
        }
        val outputDirectory = mediaDir ?: context.filesDir

        // Create unique filename encoding all applied metadata
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        val exp = _exposureIndex.value
        val iso = _isoValue.value
        val filename = "GLORY_${timestamp}_MODE_${activeMode.name}_FLTR_${selectedFilter.name}_EV_${exp}_ISO_${iso}.jpg"
        val file = File(outputDirectory, filename)

        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
        val mainExecutor = ContextCompat.getMainExecutor(context)

        // Capture physical outputs
        imageCapture.takePicture(
            outputOptions,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    viewModelScope.launch {
                        // Step 2: Adaptive Sensor dynamic range mapping
                        setCaptureState(CaptureState.AISensorOptimization)
                        delay(650)

                        // Step 3: Offline AI edge-preserving bilateral denoise
                        setCaptureState(CaptureState.AIDenoising)
                        delay(750)

                        // Step 4: Cinematic skin-tone contrast enhancement color pop
                        setCaptureState(CaptureState.AIColorEnhancing)
                        delay(700)

                        // Step 5: Advanced unsharp details lens sharpening matrix algorithm
                        setCaptureState(CaptureState.AISharpening)

                        withContext(Dispatchers.IO) {
                            // Rotation fix: correct EXIF orientation before AI processing
                            fixImageRotation(file)
                        }

                        // Step 6: Offline AI Noise Reduction (TFLite / software bilateral)
                        setCaptureState(CaptureState.AINoiseReduction)
                        withContext(Dispatchers.IO) {
                            GloryNoiseReductionEngine.reduceNoise(file, context)
                        }
                        delay(600)

                        withContext(Dispatchers.IO) {
                            // Run offline AI enhancement pipeline
                            GloryOfflineAIEngine.processAndOptimizeImage(
                                sourceFile = file,
                                isNightMode = isNight,
                                exposureIndex = exp,
                                isoValue = iso,
                                cameraMode = activeMode,
                                watermarkEnabled = watermark,
                                isAutoExposure = _isAutoExposureActive.value
                            )

                            // Save to system gallery (Samsung Gallery, Google Photos, etc.)
                            saveToSystemGallery(context, file, filename)
                        }
                        delay(500)

                        // Complete successfully & show confirmation
                        setCaptureState(CaptureState.Success(Uri.fromFile(file)))
                        addPhoto(context, file, selectedFilter)

                        delay(1500)
                        setCaptureState(CaptureState.Idle)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("GloryCam", "Photo capture failed: ${exception.message}", exception)
                    setCaptureState(CaptureState.Error(exception.message ?: "Unknown hardware error"))
                    viewModelScope.launch {
                        delay(2500)
                        setCaptureState(CaptureState.Idle)
                    }
                }
            }
        )
    }

    private suspend fun executeSimulatedCapture(
        context: Context,
        selectedFilter: FilterPreset
    ) {
        val isNight = _isNightModeOn.value || _selectedMode.value == CameraMode.NIGHT
        val activeMode = _selectedMode.value
        val watermark = _isWatermarkEnabled.value

        // Step 1: Long exposure sensor light gathering simulation
        if (isNight) {
            setCaptureState(CaptureState.GatheringLight(0.1f, "🌙 NIGHT SIGHT: Integrating shadows and ISO curves...\n(ISO මට්ටම සකසමින්...)"))
            delay(500)
            setCaptureState(CaptureState.GatheringLight(0.4f, "🌙 NIGHT SIGHT: Gathering nocturnal ambient photon flux...\n(රාත්‍රී ආලෝකය ඒකාබද්ධ කරමින්...)"))
            delay(600)
            setCaptureState(CaptureState.GatheringLight(0.7f, "🌙 NIGHT SIGHT: Consolidating sub-exposures for low-light...\n(උප-ප්‍රශස්තකරණ ඡායාරූප සලකමින්...)"))
            delay(600)
            setCaptureState(CaptureState.GatheringLight(1.0f, "🌙 NIGHT SIGHT: Custom low-light sensor optimization...\n(රාත්‍රී ආලෝක ධාරාවන් එක්රැස් කරමින්...)"))
            delay(400)
        } else {
            val modeLabel = when (activeMode) {
                CameraMode.PORTRAIT -> "Adjusting dual-lens portrait aperture...\n(පසුබිම බොඳ කිරීමේ ප්‍රශස්තකරණය...)"
                CameraMode.MACRO -> "Fine-focusing macro lens proximity...\n(ලඟම ඇති වස්තූන්ගේ තියුණුබව...)"
                CameraMode.PRO -> "Measuring raw digital light index...\n(ප්‍රෝ කැමරා අගයන් සලකමින්...)"
                else -> "Calibrating sensor & ambient light context...\n(සෙන්සර් ක්‍රමාංකනය කරමින්...)"
            }
            setCaptureState(CaptureState.GatheringLight(0.1f, modeLabel))
            delay(300)
            setCaptureState(CaptureState.GatheringLight(0.4f, "Gathering surrounding atmospheric light...\n(වටපිටාවේ ආලෝකය හඳුනාගනිමින්...)"))
            delay(400)
            setCaptureState(CaptureState.GatheringLight(0.7f, "Integrating physical lens focal matrices...\n(කාචයේ ආලෝක අනුපාත සලකමින්...)"))
            delay(400)
            setCaptureState(CaptureState.GatheringLight(1.0f, "Aligning low-light sensor pixels...\n(පික්සල් පෙළගැස්වීම් සිදුකරමින්...)"))
            delay(200)
        }

        // Flash shutter right before capturing
        triggerShutterFlash()

        // Get standard destination directory safely
        val mediaDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let {
            File(it, "GloryCam").apply { mkdirs() }
        }
        val outputDirectory = mediaDir ?: context.filesDir

        // Create unique filename encoding all applied metadata
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        val exp = _exposureIndex.value
        val iso = _isoValue.value
        val filename = "GLORY_${timestamp}_MODE_${activeMode.name}_FLTR_${selectedFilter.name}_EV_${exp}_ISO_${iso}.jpg"
        val file = File(outputDirectory, filename)

        // Generate a beautiful programmatic scenic bitmap canvas
        withContext(Dispatchers.IO) {
            try {
                val width = 1080
                val height = 1440
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)

                // 2. Draw aesthetic scenery gradient matching chosen preset
                val paint = android.graphics.Paint()
                val bgGrad = when (selectedFilter) {
                    FilterPreset.ORIGINAL -> android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFF141A29.toInt(), 0xFF080C14.toInt(), android.graphics.Shader.TileMode.CLAMP)
                    FilterPreset.NEO_NOIR -> android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFF434343.toInt(), 0xFF090909.toInt(), android.graphics.Shader.TileMode.CLAMP)
                    FilterPreset.GOLDEN_HOUR -> android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFFF39C12.toInt(), 0xFF2C1B18.toInt(), android.graphics.Shader.TileMode.CLAMP)
                    FilterPreset.CYBERPUNK -> android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFF8A2BE2.toInt(), 0xFF0A0216.toInt(), android.graphics.Shader.TileMode.CLAMP)
                    FilterPreset.COOL_MIST -> android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFF00FF87.toInt(), 0xFF051C2C.toInt(), android.graphics.Shader.TileMode.CLAMP)
                    FilterPreset.FOREST -> android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFF11998E.toInt(), 0xFF0A1F13.toInt(), android.graphics.Shader.TileMode.CLAMP)
                    FilterPreset.NIGHT_NEON -> android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFFFF5E00.toInt(), 0xFF100720.toInt(), android.graphics.Shader.TileMode.CLAMP)
                    FilterPreset.AURORA_GREEN -> android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFF00FFCC.toInt(), 0xFF010C15.toInt(), android.graphics.Shader.TileMode.CLAMP)
                    FilterPreset.ASTRO_DEEP -> android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFF0F2027.toInt(), 0xFF2C5364.toInt(), android.graphics.Shader.TileMode.CLAMP)
                }
                paint.shader = bgGrad
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

                // Draw scenic landmarks (mountain silhouettes)
                paint.reset()
                paint.style = android.graphics.Paint.Style.FILL

                val sunPaint = android.graphics.Paint().apply {
                    color = when (selectedFilter) {
                        FilterPreset.GOLDEN_HOUR -> 0xFFE67E22.toInt()
                        FilterPreset.CYBERPUNK -> 0xFFFF007F.toInt()
                        FilterPreset.NEO_NOIR -> 0xFFDDDDDD.toInt()
                        FilterPreset.AURORA_GREEN -> 0xFF00FF99.toInt()
                        else -> 0xFFF1C40F.toInt()
                    }
                    alpha = 180
                }
                // Center sun
                canvas.drawCircle(width / 2f, height / 3f, 150f, sunPaint)

                // Background mountain silhouette peaks
                val peakPath1 = android.graphics.Path().apply {
                    moveTo(0f, height * 0.7f)
                    lineTo(width * 0.3f, height * 0.45f)
                    lineTo(width * 0.65f, height * 0.65f)
                    lineTo(width * 0.85f, height * 0.5f)
                    lineTo(width.toFloat(), height * 0.72f)
                    lineTo(width.toFloat(), height.toFloat())
                    lineTo(0f, height.toFloat())
                    close()
                }
                paint.color = when (selectedFilter) {
                    FilterPreset.NEO_NOIR -> 0xFF222222.toInt()
                    FilterPreset.GOLDEN_HOUR -> 0xFF5D2D1B.toInt()
                    FilterPreset.CYBERPUNK -> 0xFF3D063A.toInt()
                    FilterPreset.COOL_MIST -> 0xFF0A2E36.toInt()
                    FilterPreset.FOREST -> 0xFF0A301D.toInt()
                    else -> 0xFF1B263B.toInt()
                }
                canvas.drawPath(peakPath1, paint)

                // Foreground closer range peaks
                val peakPath2 = android.graphics.Path().apply {
                    moveTo(0f, height * 0.82f)
                    lineTo(width * 0.2f, height * 0.65f)
                    lineTo(width * 0.5f, height * 0.53f)
                    lineTo(width * 0.75f, height * 0.7f)
                    lineTo(width.toFloat(), height * 0.6f)
                    lineTo(width.toFloat(), height.toFloat())
                    lineTo(0f, height.toFloat())
                    close()
                }
                paint.color = when (selectedFilter) {
                    FilterPreset.NEO_NOIR -> 0xFF111111.toInt()
                    FilterPreset.GOLDEN_HOUR -> 0xFF351509.toInt()
                    FilterPreset.CYBERPUNK -> 0xFF210324.toInt()
                    FilterPreset.COOL_MIST -> 0xFF051C2C.toInt()
                    FilterPreset.FOREST -> 0xFF051E10.toInt()
                    else -> 0xFF0D1B2A.toInt()
                }
                canvas.drawPath(peakPath2, paint)

                // If Night Mode, add a galaxy of stars
                if (isNight) {
                    val pPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        style = android.graphics.Paint.Style.FILL
                    }
                    val random = java.util.Random(1337)
                    for (k in 0..72) {
                        val sx = random.nextFloat() * width
                        val sy = random.nextFloat() * (height * 0.55f)
                        val sr = 1.2f + random.nextFloat() * 2.5f
                        canvas.drawCircle(sx, sy, sr, pPaint)
                    }
                }

                // Add grid lines
                val linePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    alpha = 40
                    strokeWidth = 2f
                    style = android.graphics.Paint.Style.STROKE
                }
                canvas.drawLine(width / 3f, 0f, width / 3f, height.toFloat(), linePaint)
                canvas.drawLine(2f * width / 3f, 0f, 2f * width / 3f, height.toFloat(), linePaint)
                canvas.drawLine(0f, height / 3f, width.toFloat(), height / 3f, linePaint)
                canvas.drawLine(0f, 2f * height / 3f, width.toFloat(), 2f * height / 3f, linePaint)

                // Save to disk
                val fos = java.io.FileOutputStream(file)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, fos)
                fos.flush()
                fos.close()
                bitmap.recycle()
            } catch (ex: Exception) {
                Log.e("CameraViewModel", "Failed to write simulated capture bitmap", ex)
            }
        }

        // Step 2: Adaptive Sensor dynamic range mapping
        setCaptureState(CaptureState.AISensorOptimization)
        delay(650)

        // Step 3: Offline AI edge-preserving bilateral denoise
        setCaptureState(CaptureState.AIDenoising)
        delay(750)

        // Step 4: Cinematic skin-tone contrast enhancement color pop
        setCaptureState(CaptureState.AIColorEnhancing)
        delay(700)

        // Step 5: Advanced unsharp details lens sharpening matrix algorithm
        setCaptureState(CaptureState.AISharpening)

        // Run actual pixel calculation on background Dispatcher for perfect UI responsiveness
        withContext(Dispatchers.IO) {
            GloryOfflineAIEngine.processAndOptimizeImage(
                sourceFile = file,
                isNightMode = isNight,
                exposureIndex = exp,
                isoValue = iso,
                cameraMode = activeMode,
                watermarkEnabled = watermark
            )
        }
        delay(500)

        // Complete successfully & show confirmation
        setCaptureState(CaptureState.Success(Uri.fromFile(file)))
        addPhoto(context, file, selectedFilter)

        delay(1500)
        setCaptureState(CaptureState.Idle)
    }
    /**
     * Reads EXIF orientation tag and physically rotates the JPEG bitmap
     * so it displays correctly in all gallery apps (Samsung, Google, etc.)
     */
    private fun fixImageRotation(file: File) {
        try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) return

            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            bitmap.recycle()

            FileOutputStream(file).use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, 97, out)
            }
            rotated.recycle()

            // Reset EXIF orientation to NORMAL after physical rotation
            val exifOut = ExifInterface(file.absolutePath)
            exifOut.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
            )
            exifOut.saveAttributes()
        } catch (e: Exception) {
            Log.e("GloryCam", "Rotation fix failed: ${e.message}")
        }
    }

    /**
     * Inserts the processed photo into the system MediaStore so it appears
     * in Samsung Gallery, Google Photos, and all other gallery apps.
     * Handles both Android 10+ (RELATIVE_PATH) and older versions (media scan).
     */
    private fun saveToSystemGallery(context: Context, file: File, fileName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ — insert via MediaStore with RELATIVE_PATH
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/GloryCam")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                )
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        file.inputStream().copyTo(stream)
                    }
                }
            } else {
                // Android 9 and below — broadcast media scan so gallery picks it up
                val mediaScanIntent = android.content.Intent(
                    android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE
                )
                mediaScanIntent.data = Uri.fromFile(file)
                context.sendBroadcast(mediaScanIntent)
            }
        } catch (e: Exception) {
            Log.e("GloryCam", "Gallery save failed: ${e.message}")
        }
    }

}