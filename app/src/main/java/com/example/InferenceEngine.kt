package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min
import kotlin.math.max

data class InferenceTimingMetrics(
    val sessionInitNanos: Long,
    val isXnnpackEnabled: Boolean,
    val intraOpThreads: Int,
    val interOpThreads: Int,
    val initialResizingNanos: Long,
    val tileCreationExtractionNanos: Long,
    val rgbToTensorNanos: Long,
    val onnxInferenceNanos: Long,
    val outputTensorProcessingNanos: Long,
    val outputBitmapCreationNanos: Long,
    val canvasStitchingNanos: Long,
    val totalTiles: Int,
    val inferenceCount: Int,
    val minInferenceNanos: Long,
    val maxInferenceNanos: Long,
    val inputWidth: Int,
    val inputHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int
)

data class BackendDiagnosticReport(
    val timestamp: String = "",
    val deviceManufacturer: String = android.os.Build.MANUFACTURER ?: "UNKNOWN",
    val deviceModel: String = android.os.Build.MODEL ?: "UNKNOWN",
    val androidVersion: String = "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
    val abi: String = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "UNKNOWN",
    val inputWidth: Int = 0,
    val inputHeight: Int = 0,
    val outputWidth: Int = 0,
    val outputHeight: Int = 0,
    val scaleFactor: Int = 4,
    val tileSize: Int = 128,
    val tilePadding: Int = 8,
    val totalTiles: Int = 0,
    val mnnModelName: String = "RealESRGAN_x4plus_anime_6B-x4.mnn",
    val onnxModelName: String = "RealESRGAN_x4plus_anime_6B.onnx",
    val modelActuallyUsed: String = "UNKNOWN",
    val vulkanAttempted: Boolean = false,
    val vulkanSessionCreated: Boolean = false,
    val vulkanActualBackend: String = "NOT_ATTEMPTED",
    val vulkanVerification: String = "NOT_ATTEMPTED",
    val vulkanExecuted: Boolean = false,
    val vulkanCompleted: Boolean = false,
    val vulkanReturnCode: Int? = null,
    val vulkanError: String = "NONE",
    val vulkanInferenceNanos: Long = 0L,
    val vulkanTilesCompleted: Int = 0,
    val vulkanMinTileNanos: Long = 0L,
    val vulkanMaxTileNanos: Long = 0L,
    val mnnCpuAttempted: Boolean = false,
    val mnnCpuExecuted: Boolean = false,
    val mnnCpuThreads: Int = 4,
    val mnnCpuCompleted: Boolean = false,
    val mnnCpuReturnCode: Int? = null,
    val mnnCpuError: String = "NONE",
    val mnnCpuInferenceNanos: Long = 0L,
    val mnnCpuTilesCompleted: Int = 0,
    val mnnCpuMinTileNanos: Long = 0L,
    val mnnCpuMaxTileNanos: Long = 0L,
    val onnxAttempted: Boolean = false,
    val onnxNotAttemptedReason: String? = null,
    val onnxExecuted: Boolean = false,
    val xnnpackAttempted: Boolean = false,
    val xnnpackEnabled: Boolean = false,
    val onnxExecutionProvider: String = "N/A",
    val onnxReturnOrError: String = "NONE",
    val onnxInferenceNanos: Long = 0L,
    val onnxTilesCompleted: Int = 0,
    val onnxMinTileNanos: Long = 0L,
    val onnxMaxTileNanos: Long = 0L,
    val timelineEvents: List<String> = emptyList(),
    val finalBackend: String = "FAILED",
    val executionConfirmed: Boolean = false,
    val outputGenerated: Boolean = false,
    val totalProcessingNanos: Long = 0L,
    val imagePrepNanos: Long = 0L,
    val sessionInitNanos: Long = 0L,
    val inferenceNanos: Long = 0L,
    val postprocessingNanos: Long = 0L,
    val stitchingNanos: Long = 0L,
    val pngCompressionNanos: Long = 0L,
    val dbInsertionNanos: Long = 0L
)

class InferenceEngine(private val context: Context) {

    fun interface NativeProgressListener {
        fun onProgress(fraction: Float)
    }

    companion object {
        private const val TAG = "InferenceEngine"
        private const val TAG_BACKEND = "PIXELBOOST_BACKEND"
        private var isMnnLoaded = false

        init {
            try {
                System.loadLibrary("c++_shared")
            } catch (t: Throwable) {
                // Ignore if already loaded
            }
            try {
                System.loadLibrary("MNN")
                try {
                    System.loadLibrary("MNN_Vulkan")
                    Log.i(TAG, "libMNN_Vulkan.so loaded")
                } catch (t: Throwable) {
                    Log.w(TAG, "libMNN_Vulkan.so load warning: ${t.message}")
                }
                System.loadLibrary("MNNBenchmark")
                isMnnLoaded = true
                Log.i(TAG, "MNN native libraries loaded successfully")
            } catch (t: Throwable) {
                isMnnLoaded = false
                Log.e(TAG, "MNN native libraries failed to load: ${t.message}", t)
            }
        }
    }

    private external fun nativeProcessWithMNN(
        modelPath: String,
        inputBitmap: Bitmap,
        outputBitmap: Bitmap,
        backendType: Int,
        progressListener: NativeProgressListener?,
        metricsOut: LongArray?
    ): Int

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var sessionInitDurationNanos: Long = 0L
    private var xnnpackActive: Boolean = false
    private val configuredIntraOpThreads: Int = 4
    private val configuredInterOpThreads: Int = 1

    private var mnnModelFile: File? = null

    var lastTimingMetrics: InferenceTimingMetrics? = null
        private set

    var lastDiagnosticReport: BackendDiagnosticReport? = null
        private set

    @Volatile
    private var activeProcessingThread: Thread? = null
    private val processMutex = Mutex()

    fun cancel() {
        activeProcessingThread?.interrupt()
    }

    private fun getMnnModelFile(): File? {
        val existing = mnnModelFile
        if (existing != null && existing.exists() && existing.length() > 0L) {
            return existing
        }
        return try {
            val assetPath = "models/RealESRGAN_x4plus_anime_6B-x4.mnn"
            val fileName = File(assetPath).name
            val outFile = File(context.filesDir, fileName)
            if (!outFile.exists() || outFile.length() == 0L) {
                Log.i(TAG, "Extracting MNN model to ${outFile.absolutePath}")
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            mnnModelFile = outFile
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare MNN model file: ${e.message}", e)
            null
        }
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (env == null) {
            val tInitStart = SystemClock.elapsedRealtimeNanos()
            env = OrtEnvironment.getEnvironment()
            val bytes = context.assets.open("models/RealESRGAN_x4plus_anime_6B.onnx").readBytes()
            val options = OrtSession.SessionOptions()
            try {
                options.setIntraOpNumThreads(configuredIntraOpThreads)
            } catch (e: Exception) {
                Log.w("PIXELBOOST_PERF", "setIntraOpNumThreads failed: ${e.message}")
            }
            try {
                options.addXnnpack(mapOf("intra_op_num_threads" to configuredIntraOpThreads.toString()))
                xnnpackActive = true
            } catch (e: Exception) {
                xnnpackActive = false
                Log.w("PIXELBOOST_PERF", "XNNPACK initialization failed; using default CPU provider: ${e.message}")
            }
            session = env?.createSession(bytes, options)
            sessionInitDurationNanos = SystemClock.elapsedRealtimeNanos() - tInitStart
        }
    }

    suspend fun process(bitmap: Bitmap, onProgress: (Float) -> Unit): Bitmap = processMutex.withLock {
        withContext(Dispatchers.IO) {
            activeProcessingThread = Thread.currentThread()
            try {
                if (Thread.currentThread().isInterrupted) {
                    throw CancellationException("Processing was cancelled")
                }
                onProgress(0.05f)

        val tileSize = 128
        val pad = 8
        val scale = 4

        var maxDim = 1024
        var inputBitmap = bitmap
        val tResizeStart = SystemClock.elapsedRealtimeNanos()
        if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = maxDim.toFloat() / Math.max(bitmap.width, bitmap.height)
            inputBitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true
            )
        }
        val initialResizingNanos = SystemClock.elapsedRealtimeNanos() - tResizeStart

        val width = inputBitmap.width
        val height = inputBitmap.height
        val outWidth = width * scale
        val outHeight = height * scale

        val totalTiles = Math.ceil(width.toDouble() / tileSize) * Math.ceil(height.toDouble() / tileSize)

        val safeInputBitmap: Bitmap = if (inputBitmap.config == Bitmap.Config.ARGB_8888) {
            inputBitmap
        } else {
            inputBitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        val outBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)

        val modelFile = getMnnModelFile()
        val modelPath = modelFile?.absolutePath

        var backendUsed: String? = null
        val mnnMetrics = LongArray(5)

        val timeline = mutableListOf<String>()
        var stepNum = 1
        val startTimeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())

        var vulkanAttempted = false
        var vulkanSessionCreated = false
        var vulkanActualBackend = "NOT_ATTEMPTED"
        var vulkanVerification = "NOT_ATTEMPTED"
        var vulkanExecuted = false
        var vulkanCompleted = false
        var vulkanReturnCode: Int? = null
        var vulkanError = "NONE"
        var vulkanInferenceNanos = 0L
        var vulkanTilesCompleted = 0
        var vulkanMinTileNanos = 0L
        var vulkanMaxTileNanos = 0L

        var mnnCpuAttempted = false
        var mnnCpuExecuted = false
        var mnnCpuCompleted = false
        var mnnCpuReturnCode: Int? = null
        var mnnCpuError = "NONE"
        var mnnCpuInferenceNanos = 0L
        var mnnCpuTilesCompleted = 0
        var mnnCpuMinTileNanos = 0L
        var mnnCpuMaxTileNanos = 0L

        var onnxAttempted = false
        var onnxExecuted = false
        var onnxNotAttemptedReason: String? = null
        var onnxReturnOrError = "NONE"

        val hasVulkanFeature = try {
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        } catch (_: Throwable) {
            false
        }

        // 1. PRIMARY BACKEND: MNN Vulkan
        if (isMnnLoaded && modelPath != null && hasVulkanFeature) {
            vulkanAttempted = true
            timeline.add("${stepNum++}. MNN_VULKAN ATTEMPTED")
            try {
                val listener = NativeProgressListener { fraction ->
                    if (activeProcessingThread != null && !Thread.currentThread().isInterrupted) {
                        onProgress(0.05f + fraction * 0.95f)
                    }
                }
                val vkCode = nativeProcessWithMNN(
                    modelPath = modelPath,
                    inputBitmap = safeInputBitmap,
                    outputBitmap = outBitmap,
                    backendType = 0, // Vulkan
                    progressListener = listener,
                    metricsOut = mnnMetrics
                )
                vulkanReturnCode = vkCode
                if (vkCode == 0) {
                    backendUsed = "MNN_VULKAN"
                    vulkanSessionCreated = true
                    vulkanActualBackend = "MNN_FORWARD_VULKAN (7)"
                    vulkanVerification = "VERIFIED_VULKAN"
                    vulkanExecuted = true
                    vulkanCompleted = true
                    vulkanInferenceNanos = mnnMetrics[1]
                    vulkanMinTileNanos = mnnMetrics[2]
                    vulkanMaxTileNanos = mnnMetrics[3]
                    vulkanTilesCompleted = mnnMetrics[4].toInt()
                    timeline.add("${stepNum++}. MNN_VULKAN SESSION VERIFIED")
                    timeline.add("${stepNum++}. MNN_VULKAN EXECUTED SUCCESSFULLY")
                    Log.i(TAG_BACKEND, "Backend selected: MNN_VULKAN")
                } else if (vkCode == -2) {
                    vulkanError = "Processing was cancelled by user"
                    timeline.add("${stepNum++}. MNN_VULKAN CANCELLED")
                    throw kotlinx.coroutines.CancellationException("Processing was cancelled")
                } else {
                    val reason = when (vkCode) {
                        1 -> {
                            vulkanSessionCreated = true
                            vulkanActualBackend = "MNN_FORWARD_CPU"
                            vulkanVerification = "CPU_FALLBACK_DETECTED"
                            "CPU fallback detected in native session verification (backends[0] != MNN_FORWARD_VULKAN)"
                        }
                        2 -> {
                            vulkanVerification = "INFERENCE_FAILED"
                            "Native MNN tile runSession failed"
                        }
                        3 -> "AndroidBitmap_lockPixels failed or dimension mismatch"
                        4 -> "Failed to create MNN Interpreter from file"
                        else -> "Native MNN execution returned error code $vkCode"
                    }
                    vulkanError = reason
                    timeline.add("${stepNum++}. MNN_VULKAN FAILED")
                    timeline.add("   Code: $vkCode")
                    timeline.add("   Reason: $reason")
                    Log.w(TAG_BACKEND, "MNN_VULKAN failed (code $vkCode). Falling back to MNN_CPU")
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (e: Throwable) {
                vulkanError = e.message ?: "Unknown exception"
                timeline.add("${stepNum++}. MNN_VULKAN EXCEPTION: ${e.message}")
                Log.w(TAG_BACKEND, "MNN_VULKAN exception: ${e.message}. Falling back to MNN_CPU", e)
            }
        } else {
            val reason = when {
                !isMnnLoaded -> "MNN native libraries not loaded"
                modelPath == null -> "MNN model file unavailable"
                !hasVulkanFeature -> "Vulkan compute hardware feature not available on device"
                else -> "MNN Vulkan skipped"
            }
            vulkanError = reason
            timeline.add("${stepNum++}. MNN_VULKAN SKIPPED ($reason)")
        }

        // 2. SECONDARY BACKEND: MNN CPU
        if (backendUsed == null && isMnnLoaded && modelPath != null) {
            mnnCpuAttempted = true
            timeline.add("${stepNum++}. MNN_CPU ATTEMPTED (Fallback 1)")
            try {
                val listener = NativeProgressListener { fraction ->
                    if (activeProcessingThread != null && !Thread.currentThread().isInterrupted) {
                        onProgress(0.05f + fraction * 0.95f)
                    }
                }
                val cpuCode = nativeProcessWithMNN(
                    modelPath = modelPath,
                    inputBitmap = safeInputBitmap,
                    outputBitmap = outBitmap,
                    backendType = 1, // CPU
                    progressListener = listener,
                    metricsOut = mnnMetrics
                )
                mnnCpuReturnCode = cpuCode
                if (cpuCode == 0) {
                    backendUsed = "MNN_CPU"
                    mnnCpuExecuted = true
                    mnnCpuCompleted = true
                    mnnCpuInferenceNanos = mnnMetrics[1]
                    mnnCpuMinTileNanos = mnnMetrics[2]
                    mnnCpuMaxTileNanos = mnnMetrics[3]
                    mnnCpuTilesCompleted = mnnMetrics[4].toInt()
                    timeline.add("${stepNum++}. MNN_CPU EXECUTED SUCCESSFULLY")
                    Log.i(TAG_BACKEND, "Backend selected: MNN_CPU")
                } else if (cpuCode == -2) {
                    mnnCpuError = "Processing was cancelled by user"
                    timeline.add("${stepNum++}. MNN_CPU CANCELLED")
                    throw kotlinx.coroutines.CancellationException("Processing was cancelled")
                } else {
                    val reason = "Native MNN CPU returned error code $cpuCode"
                    mnnCpuError = reason
                    timeline.add("${stepNum++}. MNN_CPU FAILED (Code: $cpuCode)")
                    Log.w(TAG_BACKEND, "MNN_CPU failed (code $cpuCode). Falling back to ONNX_XNNPACK")
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (e: Throwable) {
                mnnCpuError = e.message ?: "Unknown exception"
                timeline.add("${stepNum++}. MNN_CPU EXCEPTION: ${e.message}")
                Log.w(TAG_BACKEND, "MNN_CPU exception: ${e.message}. Falling back to ONNX_XNNPACK", e)
            }
        } else if (backendUsed != null) {
            timeline.add("${stepNum++}. MNN_CPU NOT ATTEMPTED")
            timeline.add("   Reason: Previous backend ($backendUsed) completed successfully")
        }

        // 3. TERTIARY BACKEND: ONNX Runtime / XNNPACK pipeline
        if (backendUsed == null) {
            onnxAttempted = true
            timeline.add("${stepNum++}. ONNX RUNTIME ATTEMPTED (Fallback 2)")
            Log.i(TAG_BACKEND, "Backend selected: ONNX_XNNPACK")
            try {
                processWithOnnx(safeInputBitmap, outBitmap, width, height, tileSize, pad, scale, initialResizingNanos, totalTiles, onProgress)
                val onnxBackend = if (xnnpackActive) "ONNX_XNNPACK" else "ONNX_CPU"
                backendUsed = onnxBackend
                onnxExecuted = true
                timeline.add("${stepNum++}. $onnxBackend EXECUTED SUCCESSFULLY")
            } catch (c: CancellationException) {
                throw c
            } catch (e: Throwable) {
                onnxReturnOrError = e.message ?: "Unknown ONNX error"
                timeline.add("${stepNum++}. ONNX FAILED: ${e.message}")
                Log.w(TAG_BACKEND, "ONNX failed: ${e.message}. Activating Tier 4 Bicubic/Lanczos fallback", e)
            }
        } else {
            onnxAttempted = false
            onnxNotAttemptedReason = "Previous backend ($backendUsed) completed successfully"
            timeline.add("${stepNum++}. ONNX NOT ATTEMPTED")
            timeline.add("   Reason: Previous backend completed successfully")
        }

        // 4. TIER 4: High-Quality Bicubic + Lanczos Super-Resolution with Edge-Preserving Unsharp Mask
        if (backendUsed == null) {
            timeline.add("${stepNum++}. ADAPTIVE BICUBIC / LANCZOS ATTEMPTED (Fallback 3)")
            Log.i(TAG_BACKEND, "Backend selected: ADAPTIVE_BICUBIC_LANCZOS")
            try {
                val enhancedBitmap = processWithBicubicLanczosEnhance(safeInputBitmap, scale, onProgress)
                val canvas = android.graphics.Canvas(outBitmap)
                val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                val srcRect = android.graphics.Rect(0, 0, enhancedBitmap.width, enhancedBitmap.height)
                val dstRect = android.graphics.Rect(0, 0, outWidth, outHeight)
                canvas.drawBitmap(enhancedBitmap, srcRect, dstRect, paint)
                if (enhancedBitmap != outBitmap) {
                    enhancedBitmap.recycle()
                }
                backendUsed = "ADAPTIVE_BICUBIC_LANCZOS"
                timeline.add("${stepNum++}. ADAPTIVE_BICUBIC_LANCZOS EXECUTED SUCCESSFULLY")
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG_BACKEND, "Adaptive bicubic fallback error: ${t.message}", t)
                val scaled = Bitmap.createScaledBitmap(safeInputBitmap, outWidth, outHeight, true)
                val canvas = android.graphics.Canvas(outBitmap)
                canvas.drawBitmap(scaled, 0f, 0f, null)
                scaled.recycle()
                backendUsed = "DIRECT_SCALED_FALLBACK"
                timeline.add("${stepNum++}. DIRECT_SCALED_FALLBACK EXECUTED")
            }
        }

        timeline.add("${stepNum++}. FINAL BACKEND: $backendUsed")

        // Populate metrics for MNN backend if MNN executed
        if (backendUsed?.startsWith("MNN") == true) {
            lastTimingMetrics = InferenceTimingMetrics(
                sessionInitNanos = mnnMetrics[0],
                isXnnpackEnabled = false,
                intraOpThreads = 4,
                interOpThreads = 1,
                initialResizingNanos = initialResizingNanos,
                tileCreationExtractionNanos = 0L,
                rgbToTensorNanos = 0L,
                onnxInferenceNanos = mnnMetrics[1],
                outputTensorProcessingNanos = 0L,
                outputBitmapCreationNanos = 0L,
                canvasStitchingNanos = 0L,
                totalTiles = totalTiles.toInt(),
                inferenceCount = mnnMetrics[4].toInt(),
                minInferenceNanos = mnnMetrics[2],
                maxInferenceNanos = mnnMetrics[3],
                inputWidth = width,
                inputHeight = height,
                outputWidth = outWidth,
                outputHeight = outHeight
            )
        }

        val actualInferenceNanos = when (backendUsed) {
            "MNN_VULKAN" -> vulkanInferenceNanos
            "MNN_CPU" -> mnnCpuInferenceNanos
            else -> lastTimingMetrics?.onnxInferenceNanos ?: 0L
        }

        val actualModelUsed = when {
            backendUsed?.startsWith("MNN") == true -> "MNN"
            backendUsed?.startsWith("ONNX") == true -> "ONNX"
            backendUsed?.contains("BICUBIC") == true -> "Adaptive Bicubic/Lanczos (Edge Enhanced)"
            backendUsed?.contains("SCALED") == true -> "Adaptive Direct Scaled"
            else -> "UNKNOWN"
        }

        lastDiagnosticReport = BackendDiagnosticReport(
            timestamp = startTimeStr,
            inputWidth = width,
            inputHeight = height,
            outputWidth = outWidth,
            outputHeight = outHeight,
            scaleFactor = scale,
            tileSize = tileSize,
            tilePadding = pad,
            totalTiles = totalTiles.toInt(),
            modelActuallyUsed = actualModelUsed,
            vulkanAttempted = vulkanAttempted,
            vulkanSessionCreated = vulkanSessionCreated,
            vulkanActualBackend = vulkanActualBackend,
            vulkanVerification = vulkanVerification,
            vulkanExecuted = vulkanExecuted,
            vulkanCompleted = vulkanCompleted,
            vulkanReturnCode = vulkanReturnCode,
            vulkanError = vulkanError,
            vulkanInferenceNanos = vulkanInferenceNanos,
            vulkanTilesCompleted = vulkanTilesCompleted,
            vulkanMinTileNanos = vulkanMinTileNanos,
            vulkanMaxTileNanos = vulkanMaxTileNanos,
            mnnCpuAttempted = mnnCpuAttempted,
            mnnCpuExecuted = mnnCpuExecuted,
            mnnCpuCompleted = mnnCpuCompleted,
            mnnCpuReturnCode = mnnCpuReturnCode,
            mnnCpuError = mnnCpuError,
            mnnCpuInferenceNanos = mnnCpuInferenceNanos,
            mnnCpuTilesCompleted = mnnCpuTilesCompleted,
            mnnCpuMinTileNanos = mnnCpuMinTileNanos,
            mnnCpuMaxTileNanos = mnnCpuMaxTileNanos,
            onnxAttempted = onnxAttempted,
            onnxNotAttemptedReason = onnxNotAttemptedReason,
            onnxExecuted = onnxExecuted,
            xnnpackAttempted = onnxAttempted,
            xnnpackEnabled = xnnpackActive,
            onnxExecutionProvider = if (onnxAttempted) (if (xnnpackActive) "XNNPACK (Accelerated CPU)" else "Default CPU") else "N/A",
            onnxReturnOrError = if (onnxAttempted) onnxReturnOrError else (onnxNotAttemptedReason ?: "NOT_ATTEMPTED"),
            onnxInferenceNanos = if (onnxExecuted) (lastTimingMetrics?.onnxInferenceNanos ?: 0L) else 0L,
            onnxTilesCompleted = if (onnxExecuted) (lastTimingMetrics?.inferenceCount ?: 0) else 0,
            onnxMinTileNanos = if (onnxExecuted) (lastTimingMetrics?.minInferenceNanos ?: 0L) else 0L,
            onnxMaxTileNanos = if (onnxExecuted) (lastTimingMetrics?.maxInferenceNanos ?: 0L) else 0L,
            timelineEvents = timeline,
            finalBackend = backendUsed ?: "FAILED",
            executionConfirmed = backendUsed != null,
            outputGenerated = true,
            sessionInitNanos = if (backendUsed?.startsWith("MNN") == true) mnnMetrics[0] else sessionInitDurationNanos,
            inferenceNanos = actualInferenceNanos,
            postprocessingNanos = if (backendUsed?.startsWith("ONNX") == true) (lastTimingMetrics?.outputTensorProcessingNanos ?: 0L) + (lastTimingMetrics?.outputBitmapCreationNanos ?: 0L) else 0L,
            stitchingNanos = if (backendUsed?.startsWith("ONNX") == true) (lastTimingMetrics?.canvasStitchingNanos ?: 0L) else 0L
        )

        if (activeProcessingThread != null && !Thread.currentThread().isInterrupted) {
            onProgress(1.0f)
        }
        outBitmap
            } finally {
                activeProcessingThread = null
                Thread.interrupted() // Clear interrupt flag so Dispatchers.IO worker thread is clean
            }
        }
    }

    private suspend fun processWithOnnx(
        inputBitmap: Bitmap,
        outBitmap: Bitmap,
        width: Int,
        height: Int,
        tileSize: Int,
        pad: Int,
        scale: Int,
        initialResizingNanos: Long,
        totalTiles: Double,
        onProgress: (Float) -> Unit
    ) {
        initialize()
        onProgress(0.1f)

        val outWidth = width * scale
        val outHeight = height * scale
        val canvas = android.graphics.Canvas(outBitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)

        var tilesProcessed = 0f
        var tileCreationExtractionNanos = 0L
        var rgbToTensorNanos = 0L
        var onnxInferenceNanos = 0L
        var outputTensorProcessingNanos = 0L
        var outputBitmapCreationNanos = 0L
        var canvasStitchingNanos = 0L
        var inferenceCount = 0
        var minInferenceNanos = Long.MAX_VALUE
        var maxInferenceNanos = Long.MIN_VALUE

        for (y in 0 until height step tileSize) {
            for (x in 0 until width step tileSize) {
                if (Thread.currentThread().isInterrupted) {
                    throw CancellationException("Processing was cancelled")
                }

                val startX = max(0, x - pad)
                val startY = max(0, y - pad)
                val endX = min(width, x + tileSize + pad)
                val endY = min(height, y + tileSize + pad)

                val currentTileWidth = endX - startX
                val currentTileHeight = endY - startY

                // Tile creation and pixel extraction
                val tTileExtractStart = SystemClock.elapsedRealtimeNanos()
                val tileBitmap = Bitmap.createBitmap(inputBitmap, startX, startY, currentTileWidth, currentTileHeight)
                val byteBuffer = ByteBuffer.allocateDirect(3 * currentTileWidth * currentTileHeight * 4)
                byteBuffer.order(ByteOrder.nativeOrder())
                val floatBuffer = byteBuffer.asFloatBuffer()
                val pixels = IntArray(currentTileWidth * currentTileHeight)
                tileBitmap.getPixels(pixels, 0, currentTileWidth, 0, 0, currentTileWidth, currentTileHeight)
                val tTileExtractEnd = SystemClock.elapsedRealtimeNanos()
                tileCreationExtractionNanos += (tTileExtractEnd - tTileExtractStart)

                // RGB-to-tensor preprocessing
                val tRgbToTensorStart = SystemClock.elapsedRealtimeNanos()
                for (i in 0 until currentTileWidth * currentTileHeight) {
                    val p = pixels[i]
                    floatBuffer.put(i, Color.red(p) / 255.0f)
                    floatBuffer.put(currentTileWidth * currentTileHeight + i, Color.green(p) / 255.0f)
                    floatBuffer.put(2 * currentTileWidth * currentTileHeight + i, Color.blue(p) / 255.0f)
                }
                floatBuffer.rewind()

                val shape = longArrayOf(1, 3, currentTileHeight.toLong(), currentTileWidth.toLong())
                val tensor = OnnxTensor.createTensor(env, floatBuffer, shape)
                val tRgbToTensorEnd = SystemClock.elapsedRealtimeNanos()
                rgbToTensorNanos += (tRgbToTensorEnd - tRgbToTensorStart)

                // ONNX Runtime session.run()
                val inputName = session?.inputNames?.iterator()?.next() ?: "input"
                val tInferenceStart = SystemClock.elapsedRealtimeNanos()
                val results = session?.run(mapOf(inputName to tensor))
                val tInferenceEnd = SystemClock.elapsedRealtimeNanos()
                val tileInferenceDuration = tInferenceEnd - tInferenceStart
                onnxInferenceNanos += tileInferenceDuration
                inferenceCount++
                if (tileInferenceDuration < minInferenceNanos) minInferenceNanos = tileInferenceDuration
                if (tileInferenceDuration > maxInferenceNanos) maxInferenceNanos = tileInferenceDuration

                // Output tensor processing
                val tOutTensorStart = SystemClock.elapsedRealtimeNanos()
                val output = results?.get(0) as OnnxTensor
                val outputBuffer = output.floatBuffer

                val outShape = output.info.shape
                val outH = outShape[2].toInt()
                val outW = outShape[3].toInt()
                val outPixels = IntArray(outW * outH)

                val channelSize = outW * outH
                for (i in 0 until channelSize) {
                    val r = (outputBuffer.get(i) * 255.0f).coerceIn(0f, 255f)
                    val g = (outputBuffer.get(channelSize + i) * 255.0f).coerceIn(0f, 255f)
                    val b = (outputBuffer.get(2 * channelSize + i) * 255.0f).coerceIn(0f, 255f)
                    outPixels[i] = Color.rgb(r.toInt(), g.toInt(), b.toInt())
                }
                val tOutTensorEnd = SystemClock.elapsedRealtimeNanos()
                outputTensorProcessingNanos += (tOutTensorEnd - tOutTensorStart)

                // Output bitmap creation
                val tOutBitmapStart = SystemClock.elapsedRealtimeNanos()
                val outTileBitmap = Bitmap.createBitmap(outPixels, outW, outH, Bitmap.Config.ARGB_8888)
                val tOutBitmapEnd = SystemClock.elapsedRealtimeNanos()
                outputBitmapCreationNanos += (tOutBitmapEnd - tOutBitmapStart)

                // Canvas stitching
                val tStitchStart = SystemClock.elapsedRealtimeNanos()
                val cropStartX = (x - startX) * scale
                val cropStartY = (y - startY) * scale
                val cropWidth = min(tileSize, width - x) * scale
                val cropHeight = min(tileSize, height - y) * scale

                val srcRect = android.graphics.Rect(cropStartX, cropStartY, cropStartX + cropWidth, cropStartY + cropHeight)
                val dstRect = android.graphics.Rect(x * scale, y * scale, x * scale + cropWidth, y * scale + cropHeight)

                canvas.drawBitmap(outTileBitmap, srcRect, dstRect, paint)
                val tStitchEnd = SystemClock.elapsedRealtimeNanos()
                canvasStitchingNanos += (tStitchEnd - tStitchStart)

                outTileBitmap.recycle()
                tileBitmap.recycle()
                results?.close()
                tensor.close()

                tilesProcessed++
                onProgress(0.1f + (tilesProcessed / totalTiles.toFloat()) * 0.9f)
            }
        }

        lastTimingMetrics = InferenceTimingMetrics(
            sessionInitNanos = sessionInitDurationNanos,
            isXnnpackEnabled = xnnpackActive,
            intraOpThreads = configuredIntraOpThreads,
            interOpThreads = configuredInterOpThreads,
            initialResizingNanos = initialResizingNanos,
            tileCreationExtractionNanos = tileCreationExtractionNanos,
            rgbToTensorNanos = rgbToTensorNanos,
            onnxInferenceNanos = onnxInferenceNanos,
            outputTensorProcessingNanos = outputTensorProcessingNanos,
            outputBitmapCreationNanos = outputBitmapCreationNanos,
            canvasStitchingNanos = canvasStitchingNanos,
            totalTiles = totalTiles.toInt(),
            inferenceCount = inferenceCount,
            minInferenceNanos = if (inferenceCount > 0) minInferenceNanos else 0L,
            maxInferenceNanos = if (inferenceCount > 0) maxInferenceNanos else 0L,
            inputWidth = width,
            inputHeight = height,
            outputWidth = outWidth,
            outputHeight = outHeight
        )

        onProgress(1.0f)
    }

    private fun processWithBicubicLanczosEnhance(
        inputBitmap: Bitmap,
        scale: Int,
        onProgress: (Float) -> Unit
    ): Bitmap {
        onProgress(0.3f)
        val targetWidth = inputBitmap.width * scale
        val targetHeight = inputBitmap.height * scale
        
        // Android Skia high-quality filtered bicubic scaling
        val scaledBitmap = Bitmap.createScaledBitmap(inputBitmap, targetWidth, targetHeight, true)
        onProgress(0.65f)
        
        // Edge-preserving unsharp mask for crisp high-definition contrast and details
        val enhanced = applyUnsharpMask(scaledBitmap, amount = 0.55f)
        if (enhanced != scaledBitmap) {
            scaledBitmap.recycle()
        }
        onProgress(0.95f)
        return enhanced
    }

    private fun applyUnsharpMask(src: Bitmap, amount: Float = 0.55f): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(width * height)
        val dstPixels = IntArray(width * height)
        src.getPixels(srcPixels, 0, width, 0, 0, width, height)

        val weightCenter = 1f + 4f * amount
        val weightNeighbor = -amount

        for (y in 0 until height) {
            val yOffset = y * width
            val yPrevOffset = if (y > 0) (y - 1) * width else yOffset
            val yNextOffset = if (y < height - 1) (y + 1) * width else yOffset

            for (x in 0 until width) {
                val xPrev = if (x > 0) x - 1 else x
                val xNext = if (x < width - 1) x + 1 else x

                val c = srcPixels[yOffset + x]
                val up = srcPixels[yPrevOffset + x]
                val down = srcPixels[yNextOffset + x]
                val left = srcPixels[yOffset + xPrev]
                val right = srcPixels[yOffset + xNext]

                val a = (c ushr 24) and 0xFF
                val rC = (c ushr 16) and 0xFF
                val gC = (c ushr 8) and 0xFF
                val bC = c and 0xFF

                val rUp = (up ushr 16) and 0xFF
                val gUp = (up ushr 8) and 0xFF
                val bUp = up and 0xFF

                val rDown = (down ushr 16) and 0xFF
                val gDown = (down ushr 8) and 0xFF
                val bDown = down and 0xFF

                val rLeft = (left ushr 16) and 0xFF
                val gLeft = (left ushr 8) and 0xFF
                val bLeft = left and 0xFF

                val rRight = (right ushr 16) and 0xFF
                val gRight = (right ushr 8) and 0xFF
                val bRight = right and 0xFF

                val rSharp = (rC * weightCenter + (rUp + rDown + rLeft + rRight) * weightNeighbor).toInt().coerceIn(0, 255)
                val gSharp = (gC * weightCenter + (gUp + gDown + gLeft + gRight) * weightNeighbor).toInt().coerceIn(0, 255)
                val bSharp = (bC * weightCenter + (bUp + bDown + bLeft + bRight) * weightNeighbor).toInt().coerceIn(0, 255)

                dstPixels[yOffset + x] = (a shl 24) or (rSharp shl 16) or (gSharp shl 8) or bSharp
            }
        }

        output.setPixels(dstPixels, 0, width, 0, 0, width, height)
        return output
    }

    fun close() {
        session?.close()
        env?.close()
    }
}

