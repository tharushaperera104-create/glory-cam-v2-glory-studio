package com.glorycam.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * GloryNoiseReductionEngine
 *
 * Offline AI noise reduction pipeline.
 * Primary: TensorFlow Lite + Real-ESRGAN model (place realesrgan.tflite in app/src/main/assets/)
 * Fallback: Software bilateral filter approximation (always available, no model needed)
 *
 * Usage: GloryNoiseReductionEngine.reduceNoise(file)
 */
object GloryNoiseReductionEngine {

    private const val TAG = "GloryNoiseReduction"
    private const val MODEL_FILE = "realesrgan.tflite"

    /**
     * Main entry point. Tries TFLite first, falls back to software filter.
     */
    fun reduceNoise(sourceFile: File, context: Context? = null): Boolean {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return false

        // Try TFLite Real-ESRGAN if model asset is available
        if (context != null && isTFLiteModelAvailable(context)) {
            val success = runTFLiteNoiseReduction(sourceFile, context)
            if (success) {
                Log.d(TAG, "TFLite Real-ESRGAN noise reduction applied")
                return true
            }
            Log.w(TAG, "TFLite failed — falling back to software filter")
        }

        // Software bilateral noise reduction (always works, no model required)
        return runSoftwareNoiseReduction(sourceFile)
    }

    // ─────────────────────────────────────────────
    // TFLite Real-ESRGAN (requires realesrgan.tflite in assets)
    // ─────────────────────────────────────────────

    private fun isTFLiteModelAvailable(context: Context): Boolean {
        return try {
            context.assets.list("")?.contains(MODEL_FILE) == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Runs Real-ESRGAN inference via TensorFlow Lite.
     * Splits image into 128x128 tiles, runs inference on each, stitches back.
     * GPU delegate is enabled automatically if available.
     */
    private fun runTFLiteNoiseReduction(sourceFile: File, context: Context): Boolean {
        return try {
            // Load TFLite interpreter dynamically to avoid hard crash if TFLite not in classpath
            val interpreterClass = Class.forName("org.tensorflow.lite.Interpreter")
            val optionsClass = Class.forName("org.tensorflow.lite.Interpreter\$Options")

            val options = optionsClass.getDeclaredConstructor().newInstance()

            // Try GPU delegate
            try {
                val gpuDelegateClass = Class.forName("org.tensorflow.lite.gpu.GpuDelegate")
                val gpuDelegate = gpuDelegateClass.getDeclaredConstructor().newInstance()
                val addDelegateMethod = optionsClass.getMethod("addDelegate",
                    Class.forName("org.tensorflow.lite.Delegate"))
                addDelegateMethod.invoke(options, gpuDelegate)
                Log.d(TAG, "GPU delegate enabled for TFLite inference")
            } catch (e: Exception) {
                Log.d(TAG, "GPU delegate unavailable, using CPU: ${e.message}")
            }

            // Load model from assets
            val modelBuffer = loadModelBuffer(context, MODEL_FILE)
            val interpreter = interpreterClass
                .getConstructor(java.nio.ByteBuffer::class.java, optionsClass)
                .newInstance(modelBuffer, options)

            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return false

            // Process image in 128x128 tiles for memory efficiency
            val processed = processBitmapInTiles(bitmap, interpreter, interpreterClass)
            bitmap.recycle()

            // Save result
            FileOutputStream(sourceFile).use { out ->
                processed.compress(Bitmap.CompressFormat.JPEG, 97, out)
            }
            processed.recycle()

            // Close interpreter
            interpreterClass.getMethod("close").invoke(interpreter)
            true
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "TFLite not available in classpath")
            false
        } catch (e: Exception) {
            Log.e(TAG, "TFLite inference failed: ${e.message}")
            false
        }
    }

    private fun loadModelBuffer(context: Context, modelFile: String): java.nio.ByteBuffer {
        val assetFd = context.assets.openFd(modelFile)
        val inputStream = assetFd.createInputStream()
        val bytes = inputStream.readBytes()
        inputStream.close()
        val buffer = java.nio.ByteBuffer.allocateDirect(bytes.size)
        buffer.put(bytes)
        buffer.rewind()
        return buffer
    }

    private fun processBitmapInTiles(
        bitmap: Bitmap,
        interpreter: Any,
        interpreterClass: Class<*>
    ): Bitmap {
        val tileSize = 128
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val runMethod = interpreterClass.getMethod("run", Any::class.java, Any::class.java)

        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val tileW = minOf(tileSize, width - x)
                val tileH = minOf(tileSize, height - y)
                val tile = Bitmap.createBitmap(bitmap, x, y, tileW, tileH)

                // Input: [1, tileH, tileW, 3] float array
                val input = Array(1) { Array(tileH) { Array(tileW) { FloatArray(3) } } }
                for (row in 0 until tileH) {
                    for (col in 0 until tileW) {
                        val px = tile.getPixel(col, row)
                        input[0][row][col][0] = ((px shr 16 and 0xFF) / 255f)
                        input[0][row][col][1] = ((px shr 8 and 0xFF) / 255f)
                        input[0][row][col][2] = ((px and 0xFF) / 255f)
                    }
                }

                // Output: [1, tileH, tileW, 3]
                val output = Array(1) { Array(tileH) { Array(tileW) { FloatArray(3) } } }
                try {
                    runMethod.invoke(interpreter, input, output)

                    // Write output pixels back
                    val tileResult = Bitmap.createBitmap(tileW, tileH, Bitmap.Config.ARGB_8888)
                    for (row in 0 until tileH) {
                        for (col in 0 until tileW) {
                            val r = (output[0][row][col][0].coerceIn(0f, 1f) * 255).toInt()
                            val g = (output[0][row][col][1].coerceIn(0f, 1f) * 255).toInt()
                            val b = (output[0][row][col][2].coerceIn(0f, 1f) * 255).toInt()
                            tileResult.setPixel(col, row, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
                        }
                    }
                    canvas.drawBitmap(tileResult, x.toFloat(), y.toFloat(), null)
                    tileResult.recycle()
                } catch (e: Exception) {
                    Log.w(TAG, "Tile inference failed at ($x,$y): ${e.message}")
                }
                tile.recycle()
                x += tileSize
            }
            y += tileSize
        }
        return result
    }

    // ─────────────────────────────────────────────
    // Software Bilateral Filter (no model needed)
    // Approximates noise reduction by:
    // 1. Multi-pass soft blur for noise smoothing
    // 2. Edge-preserving overlay to recover detail
    // 3. Mild luminance correction to restore natural colors
    // ─────────────────────────────────────────────

    private fun runSoftwareNoiseReduction(sourceFile: File): Boolean {
        var bitmap: Bitmap? = null
        var smoothed: Bitmap? = null
        var result: Bitmap? = null
        return try {
            val options = BitmapFactory.Options().apply {
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, options)
                ?: return false

            val width = bitmap.width
            val height = bitmap.height

            // Pass 1: Create softly smoothed version (approximates bilateral blur)
            smoothed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val smoothCanvas = Canvas(smoothed)
            val blurPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

            // Base layer
            smoothCanvas.drawBitmap(bitmap, 0f, 0f, blurPaint)

            // Multi-offset passes at low alpha to average out noise
            val passes = listOf(1f, 2f, 3f)
            blurPaint.alpha = 38
            for (offset in passes) {
                smoothCanvas.drawBitmap(bitmap, -offset, 0f, blurPaint)
                smoothCanvas.drawBitmap(bitmap, offset, 0f, blurPaint)
                smoothCanvas.drawBitmap(bitmap, 0f, -offset, blurPaint)
                smoothCanvas.drawBitmap(bitmap, 0f, offset, blurPaint)
            }

            // Pass 2: Blend smoothed + original to preserve edges
            // result = 65% smoothed + 35% original (edge-preserving mix)
            result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val resultCanvas = Canvas(result)

            val basePaint = Paint().apply { alpha = 255 }
            resultCanvas.drawBitmap(smoothed, 0f, 0f, basePaint)

            val edgePaint = Paint().apply { alpha = 90 }
            resultCanvas.drawBitmap(bitmap, 0f, 0f, edgePaint)

            // Pass 3: Mild color correction to keep natural tones
            // Slight contrast/saturation lift to counter the blur softness
            val finalResult = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val finalCanvas = Canvas(finalResult)
            val correctionMatrix = ColorMatrix().apply {
                setSaturation(1.05f) // Gentle saturation restore
            }
            val corrPaint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(correctionMatrix)
            }
            finalCanvas.drawBitmap(result, 0f, 0f, corrPaint)
            result.recycle()

            // Save
            FileOutputStream(sourceFile).use { out ->
                finalResult.compress(Bitmap.CompressFormat.JPEG, 97, out)
            }
            finalResult.recycle()
            Log.d(TAG, "Software noise reduction applied to ${sourceFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Software noise reduction failed: ${e.message}")
            false
        } finally {
            try { bitmap?.recycle() } catch (_: Exception) {}
            try { smoothed?.recycle() } catch (_: Exception) {}
        }
    }
}
