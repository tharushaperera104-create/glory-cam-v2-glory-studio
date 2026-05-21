package com.glorycam.app.camera

import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture

enum class FlashModeState(val title: String, val flashModeValue: Int) {
    OFF("OFF", ImageCapture.FLASH_MODE_OFF),
    ON("ON", ImageCapture.FLASH_MODE_ON),
    AUTO("AUTO", ImageCapture.FLASH_MODE_AUTO)
}

enum class GridType(val displayName: String) {
    NONE("No Grid"),
    RULE_OF_THIRDS("3x3 Grid"),
    GOLDEN_TRIANGLE("Cross Grid")
}

enum class CameraMode(val displayName: String, val sinhalaName: String) {
    STANDARD("STANDARD", "සාමාන්‍ය"),
    PORTRAIT("PORTRAIT", "පෝට්රේට්"),
    MACRO("MACRO", "මැක්‍රෝ"),
    PRO("PRO", "ප්‍රෝ"),
    NIGHT("NIGHT", "රාත්‍රී")
}

enum class AspectRatioPreset(val displayName: String, val value: Float) {
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_1_1("1:1", 1f)
}

sealed interface CaptureState {
    object Idle : CaptureState
    data class GatheringLight(val progress: Float, val label: String) : CaptureState
    object AISensorOptimization : CaptureState
    object AIDenoising : CaptureState
    object AIColorEnhancing : CaptureState
    object AISharpening : CaptureState
    data class Success(val fileUri: Uri) : CaptureState
    data class Error(val message: String) : CaptureState
}

data class CapturedMedia(
    val id: String,
    val name: String,
    val fileUri: Uri,
    val absolutePath: String,
    val dateAdded: Long,
    val filterApplied: FilterPreset = FilterPreset.ORIGINAL,
    val cameraMode: CameraMode = CameraMode.STANDARD,
    val exposureIndex: Int = 0,
    val isoValue: Int = 400
)
