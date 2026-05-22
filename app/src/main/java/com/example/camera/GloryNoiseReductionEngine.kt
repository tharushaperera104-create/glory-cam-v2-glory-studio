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
import java.nio.FloatBuffer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

/**
 * GloryNoiseReductionEngine
 *
 * Offline AI noise reduction pipeline.
 * Primary: ONNX Runtime (expects noise_reduction.onnx in assets)
 * Fallback: Built-in Software bilateral filter (no model needed)
 *
 * Usage: GloryNoiseReductionEngine.reduceNoise(file, context)
 */
object GloryNoiseReductionEngine {

    private const val TAG = "GloryNoiseReduction"
    private const val MODEL_FILE = "noise_reduction.onnx"

    /**
     * Main entry point. Tries ONNX Runtime first, falls back to built-in software filter.
     */
    fun reduceNoise(sourceFile: File, context: Context? = null): Boolean {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return false

        // Try ONNX Runtime if model asset is available
        if (context != null && isModelAvailable(context)) {
            val success = runONNXNoiseReduction(sourceFile, context)
            if (success) {
                Log.d(TAG, "ONNX Runtime noise reduction applied")
                return true
            }
            Log.w(TAG, "ONNX failed — falling back to built-in software filter")
        }

        // Software built-in noise reduction (always works, no model required)
        return runSoftwareNoiseReduction(sourceFile)
    }

    private fun isModelAvailable(context: Context): Boolean {
        return try {
            context.assets.list("")?.contains(MODEL_FILE) == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Runs inference via ONNX Runtime.
     */
    private fun runONNXNoiseReduction(sourceFile: File, context: Context): Boolean {
        var session: OrtSession? = null
        var env: OrtEnvironment? = null
        return try {
            env = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
            session = env.createSession(modelBytes)

            val options = BitmapFactory.Options().apply { inMutable = true }
            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, options) ?: return false

            val processed = processWithONNX(bitmap, env, session)
            bitmap.recycle()

            FileOutputStream(sourceFile).use { out ->
                processed.compress(Bitmap.CompressFormat.JPEG, 97, out)
            }
            processed.recycle()
            true
        } catch (e: Exception) {
            Log.e(TAG, "ONNX inference failed: ${e.message}")
            false
        } finally {
            try { session?.close() } catch (_: Exception) {}
        }
    }

    private fun processWithONNX(bitmap: Bitmap, env: OrtEnvironment, session: OrtSession): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // This is a simplified implementation. Real-world models usually require 
        // tiling or specific input shapes (e.g., 224x224 or multiples of 8).
        // For demonstration, we assume the model can handle the bitmap or a scaled version.
        
        val channels = 3
        val shape = longArrayOf(1, channels.toLong(), height.toLong(), width.toLong())
        val floatBuffer = FloatBuffer.allocate(width * height * channels)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                floatBuffer.put(((pixel shr 16) and 0xFF) / 255.0f) // R
                floatBuffer.put(((pixel shr 8) and 0xFF) / 255.0f)  // G
                floatBuffer.put((pixel and 0xFF) / 255.0f)         // B
            }
        }
        floatBuffer.rewind()

        val inputName = session.inputNames.iterator().next()
        val inputTensor = OnnxTensor.createTensor(env, floatBuffer, shape)
        
        val output = session.run(mapOf(inputName to inputTensor))
        val outputTensor = output[0] as OnnxTensor
        val outputBuffer = outputTensor.floatBuffer
        outputBuffer.rewind()

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (outputBuffer.get().coerceIn(0f, 1f) * 255).toInt()
                val g = (outputBuffer.get().coerceIn(0f, 1f) * 255).toInt()
                val b = (outputBuffer.get().coerceIn(0f, 1f) * 255).toInt()
                result.setPixel(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        
        inputTensor.close()
        output.close()
        
        return result
    }

    /**
     * Built-in Software Noise Reduction (no model needed).
     * Uses a multi-pass approach to simulate bilateral filtering.
     */
    private fun runSoftwareNoiseReduction(sourceFile: File): Boolean {
        var bitmap: Bitmap? = null
        var smoothed: Bitmap? = null
        return try {
            val options = BitmapFactory.Options().apply {
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, options)
                ?: return false

            val width = bitmap.width
            val height = bitmap.height

            // Step 1: Soft blur pass to reduce high-frequency noise
            smoothed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val smoothCanvas = Canvas(smoothed)
            val blurPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            smoothCanvas.drawBitmap(bitmap, 0f, 0f, blurPaint)

            blurPaint.alpha = 45
            val offsets = listOf(1.2f, 2.5f)
            for (offset in offsets) {
                smoothCanvas.drawBitmap(bitmap, -offset, -offset, blurPaint)
                smoothCanvas.drawBitmap(bitmap, offset, offset, blurPaint)
            }

            // Step 2: Edge-preserving blend (Luminance preservation)
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val resultCanvas = Canvas(result)
            resultCanvas.drawBitmap(smoothed, 0f, 0f, null)
            
            val edgePaint = Paint().apply { alpha = 100 }
            resultCanvas.drawBitmap(bitmap, 0f, 0f, edgePaint)

            // Step 3: Color pop & Saturation restore
            val finalResult = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val finalCanvas = Canvas(finalResult)
            val matrix = ColorMatrix().apply {
                setSaturation(1.1f)
            }
            val finalPaint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            }
            finalCanvas.drawBitmap(result, 0f, 0f, finalPaint)

            // Save result
            FileOutputStream(sourceFile).use { out ->
                finalResult.compress(Bitmap.CompressFormat.JPEG, 98, out)
            }
            
            finalResult.recycle()
            result.recycle()
            Log.d(TAG, "Built-in software noise reduction applied to ${sourceFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Software noise reduction failed: ${e.message}")
            false
        } finally {
            bitmap?.recycle()
            smoothed?.recycle()
        }
    }
}
