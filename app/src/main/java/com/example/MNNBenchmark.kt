package com.example

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class MNNBenchmarkResult(
    val rawReport: String,
    val isSuccess: Boolean,
    val status: String,
    val mnnVersion: String = "",
    val modelName: String = "",
    val loadTimeMs: Double = 0.0,
    val sessionCreationTimeMs: Double = 0.0,
    val inputPrepTimeMs: Double = 0.0,
    val inferenceTimeMs: Double = 0.0,
    val outputExtractTimeMs: Double = 0.0,
    val totalTimeMs: Double = 0.0,
    val inputWidth: Int = 0,
    val inputHeight: Int = 0,
    val outputWidth: Int = 0,
    val outputHeight: Int = 0,
    val minVal: Float = 0.0f,
    val maxVal: Float = 0.0f,
    val meanVal: Float = 0.0f,
    val isFinite: Boolean = false,
    val is4xScale: Boolean = false
)

object MNNBenchmark {
    private const val TAG = "PIXELBOOST_MNN_CPU_BENCHMARK"
    private const val DEFAULT_ASSET_MODEL = "models/RealESRGAN_x4plus_anime_6B-x4.mnn"

    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("c++_shared")
        } catch (t: Throwable) {
            // May already be loaded
        }
        try {
            System.loadLibrary("MNN")
            try {
                System.loadLibrary("MNN_Vulkan")
                Log.i(TAG, "Successfully loaded libMNN_Vulkan.so")
            } catch (t: Throwable) {
                Log.w(TAG, "libMNN_Vulkan.so not loaded via System.loadLibrary: ${t.message}")
            }
            System.loadLibrary("MNNBenchmark")
            isNativeLoaded = true
            Log.i(TAG, "Successfully loaded libMNN.so and libMNNBenchmark.so")
        } catch (e: UnsatisfiedLinkError) {
            isNativeLoaded = false
            Log.e(TAG, "Failed to load native libraries: ${e.message}", e)
        }
    }

    private external fun nativeInitialize(modelPath: String): Boolean
    private external fun nativeRunCpuBenchmark(testWidth: Int, testHeight: Int): String
    private external fun nativeRelease()
    private external fun nativeRunFullBenchmark(modelPath: String, testWidth: Int, testHeight: Int): String
    private external fun nativeRunVulkanBenchmarkSuite(
        modelPath: String,
        testWidth: Int,
        testHeight: Int,
        deviceModel: String,
        androidVersion: String,
        abi: String,
        inputPixels: FloatArray?
    ): String

    fun loadBenchmarkImage(context: Context, assetPath: String = "benchmark/benchmark_input.png"): FloatArray? {
        return try {
            val bitmap = context.assets.open(assetPath).use {
                android.graphics.BitmapFactory.decodeStream(it)
            } ?: return null
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val floats = FloatArray(w * h * 3)
            for (i in 0 until (w * h)) {
                val p = pixels[i]
                floats[i * 3 + 0] = ((p shr 16) and 0xFF) / 255.0f
                floats[i * 3 + 1] = ((p shr 8) and 0xFF) / 255.0f
                floats[i * 3 + 2] = (p and 0xFF) / 255.0f
            }
            Log.i(TAG, "Loaded benchmark image fixture $assetPath (${w}x${h})")
            floats
        } catch (e: Exception) {
            Log.w(TAG, "Could not load asset image $assetPath: ${e.message}")
            null
        }
    }

    fun runVulkanBenchmark(
        context: Context,
        assetPath: String = DEFAULT_ASSET_MODEL,
        width: Int = 192,
        height: Int = 192
    ): String {
        if (!isNativeLoaded) {
            val err = "VULKAN STATUS: BLOCKED - Native libraries not loaded"
            Log.e(TAG, err)
            return err
        }
        return try {
            val file = copyAssetToInternalStorage(context, assetPath)
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
            val androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            val inputPixels = loadBenchmarkImage(context)
            val report = nativeRunVulkanBenchmarkSuite(
                file.absolutePath,
                width,
                height,
                deviceModel,
                androidVersion,
                abi,
                inputPixels
            )
            Log.i(TAG, report)
            report
        } catch (e: Exception) {
            val err = "VULKAN STATUS: BLOCKED - Exception: ${e.message}"
            Log.e(TAG, err, e)
            err
        }
    }

    fun copyAssetToInternalStorage(context: Context, assetPath: String): File {
        val fileName = File(assetPath).name
        val outFile = File(context.filesDir, fileName)
        if (!outFile.exists() || outFile.length() == 0L) {
            Log.i(TAG, "Extracting asset $assetPath to ${outFile.absolutePath}")
            context.assets.open(assetPath).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile
    }

    fun initialize(context: Context, assetPath: String = DEFAULT_ASSET_MODEL): Boolean {
        if (!isNativeLoaded) {
            Log.e(TAG, "Cannot initialize: native libraries not loaded")
            return false
        }
        return try {
            val file = copyAssetToInternalStorage(context, assetPath)
            nativeInitialize(file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "initialize failed: ${e.message}", e)
            false
        }
    }

    fun runCpuBenchmark(width: Int = 32, height: Int = 32): MNNBenchmarkResult {
        if (!isNativeLoaded) {
            val err = "Native libraries not loaded"
            Log.e(TAG, err)
            return MNNBenchmarkResult(rawReport = err, isSuccess = false, status = "FAIL")
        }
        val report = nativeRunCpuBenchmark(width, height)
        Log.i(TAG, report)
        return parseReport(report)
    }

    fun release() {
        if (isNativeLoaded) {
            nativeRelease()
        }
    }

    fun runFullBenchmark(
        context: Context,
        assetPath: String = DEFAULT_ASSET_MODEL,
        width: Int = 32,
        height: Int = 32
    ): MNNBenchmarkResult {
        if (!isNativeLoaded) {
            val err = "Native libraries not loaded"
            Log.e(TAG, err)
            return MNNBenchmarkResult(rawReport = err, isSuccess = false, status = "FAIL")
        }
        return try {
            val file = copyAssetToInternalStorage(context, assetPath)
            val report = nativeRunFullBenchmark(file.absolutePath, width, height)
            Log.i(TAG, report)
            parseReport(report)
        } catch (e: Exception) {
            val err = "Exception during benchmark: ${e.message}"
            Log.e(TAG, err, e)
            MNNBenchmarkResult(rawReport = err, isSuccess = false, status = "FAIL")
        }
    }

    private fun parseReport(report: String): MNNBenchmarkResult {
        val lines = report.lines()
        var version = ""
        var model = ""
        var loadTime = 0.0
        var sessionTime = 0.0
        var inputPrepTime = 0.0
        var inferTime = 0.0
        var outputTime = 0.0
        var totalTime = 0.0
        var inW = 0
        var inH = 0
        var outW = 0
        var outH = 0
        var minVal = 0.0f
        var maxVal = 0.0f
        var meanVal = 0.0f
        var finite = false
        var status = "UNKNOWN"

        var currentSection = ""

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.endsWith(":")) {
                currentSection = trimmed.dropLast(1)
                continue
            }
            if (trimmed.startsWith("MNN version:")) {
                version = trimmed.substringAfter("MNN version:").trim()
            } else if (trimmed.startsWith("Model:")) {
                model = trimmed.substringAfter("Model:").trim()
            } else if (trimmed.startsWith("Model load:")) {
                loadTime = trimmed.substringAfter("Model load:").substringBefore("ms").trim().toDoubleOrNull() ?: 0.0
            } else if (trimmed.startsWith("Session creation:")) {
                sessionTime = trimmed.substringAfter("Session creation:").substringBefore("ms").trim().toDoubleOrNull() ?: 0.0
            } else if (trimmed.startsWith("Input preparation:")) {
                inputPrepTime = trimmed.substringAfter("Input preparation:").substringBefore("ms").trim().toDoubleOrNull() ?: 0.0
            } else if (trimmed.startsWith("Inference:")) {
                inferTime = trimmed.substringAfter("Inference:").substringBefore("ms").trim().toDoubleOrNull() ?: 0.0
            } else if (trimmed.startsWith("Output extraction:")) {
                outputTime = trimmed.substringAfter("Output extraction:").substringBefore("ms").trim().toDoubleOrNull() ?: 0.0
            } else if (trimmed.startsWith("Total:")) {
                totalTime = trimmed.substringAfter("Total:").substringBefore("ms").trim().toDoubleOrNull() ?: 0.0
            } else if (currentSection == "Input") {
                if (trimmed.startsWith("Width:")) inW = trimmed.substringAfter("Width:").trim().toIntOrNull() ?: 0
                if (trimmed.startsWith("Height:")) inH = trimmed.substringAfter("Height:").trim().toIntOrNull() ?: 0
            } else if (currentSection == "Output") {
                if (trimmed.startsWith("Width:")) outW = trimmed.substringAfter("Width:").trim().toIntOrNull() ?: 0
                if (trimmed.startsWith("Height:")) outH = trimmed.substringAfter("Height:").trim().toIntOrNull() ?: 0
            } else if (currentSection == "Validation") {
                if (trimmed.startsWith("Finite:")) finite = trimmed.substringAfter("Finite:").trim().equals("YES", ignoreCase = true)
                if (trimmed.startsWith("Min:")) minVal = trimmed.substringAfter("Min:").trim().toFloatOrNull() ?: 0.0f
                if (trimmed.startsWith("Max:")) maxVal = trimmed.substringAfter("Max:").trim().toFloatOrNull() ?: 0.0f
                if (trimmed.startsWith("Mean:")) meanVal = trimmed.substringAfter("Mean:").trim().toFloatOrNull() ?: 0.0f
            } else if (currentSection == "Status") {
                if (trimmed.isNotEmpty()) status = trimmed
            }
        }

        val success = status.contains("PASS")
        val is4x = (inW > 0 && inH > 0 && outW == inW * 4 && outH == inH * 4)

        return MNNBenchmarkResult(
            rawReport = report,
            isSuccess = success,
            status = status,
            mnnVersion = version,
            modelName = model,
            loadTimeMs = loadTime,
            sessionCreationTimeMs = sessionTime,
            inputPrepTimeMs = inputPrepTime,
            inferenceTimeMs = inferTime,
            outputExtractTimeMs = outputTime,
            totalTimeMs = totalTime,
            inputWidth = inW,
            inputHeight = inH,
            outputWidth = outW,
            outputHeight = outH,
            minVal = minVal,
            maxVal = maxVal,
            meanVal = meanVal,
            isFinite = finite,
            is4xScale = is4x
        )
    }
}
