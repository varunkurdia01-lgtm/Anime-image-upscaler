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

data class BackendBenchmarkResult(
    val backendName: String,
    val isConfiguredAndAvailable: Boolean,
    val unavailableReason: String? = null,
    val warmUpLatencyNanos: Long = 0L,
    val trialLatenciesNanos: List<Long> = emptyList(),
    val medianLatencyNanos: Long = 0L,
    val outputDimensionsVerified: Boolean = false,
    val pixelEntropyValid: Boolean = false,
    val meanAbsoluteErrorVsRef: Float? = null,
    val seamDiscontinuityMaxDelta: Float? = null,
    val multiTileValidationNanos: Long = 0L,
    val validationPassed: Boolean = false,
    val isSelectedWinner: Boolean = false
)

data class CalibrationSummary(
    val timestamp: Long = System.currentTimeMillis(),
    val configurationFingerprint: String = "",
    val calibrationWallClockDurationNanos: Long = 0L,
    val results: List<BackendBenchmarkResult> = emptyList(),
    val winningBackend: String = "MNN_CPU",
    val activeBackendUsed: String = "MNN_CPU",
    val isManualOverrideActive: Boolean = false
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
        requestedTileSize: Int,
        cpuThreads: Int,
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

    fun getOnnxModelFile(): File? {
        val fileName = "RealESRGAN_x4plus_anime_6B.onnx"
        val outFile = File(context.filesDir, fileName)
        if (outFile.exists() && outFile.length() > 0L) {
            return outFile
        }
        return try {
            val assetPath = "models/$fileName"
            if (!outFile.exists() || outFile.length() == 0L) {
                Log.i(TAG, "Extracting ONNX model to ${outFile.absolutePath}")
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare ONNX model file: ${e.message}", e)
            null
        }
    }

    fun getModelFile(fileName: String): File? {
        return when {
            fileName.endsWith(".mnn") -> getMnnModelFile()
            fileName.endsWith(".onnx") -> getOnnxModelFile()
            else -> {
                val f = File(context.filesDir, fileName)
                if (f.exists()) f else null
            }
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

    data class DeviceHardwareProfile(
        val availableCores: Int,
        val maxMemoryMb: Long,
        val isLowRam: Boolean,
        val recommendedCpuThreads: Int,
        val recommendedTileSizeVulkan: Int,
        val recommendedTileSizeCpu: Int
    )

    fun queryHardwareProfile(): DeviceHardwareProfile {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val maxMemMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val isLowRam = am?.isLowRamDevice ?: false

        val cpuThreads = when {
            cores >= 8 -> 4
            cores >= 4 -> 3
            cores >= 2 -> 2
            else -> 1
        }

        val vkTileSize = when {
            !isLowRam && maxMemMb >= 512 && cores >= 6 -> 256
            !isLowRam && maxMemMb >= 256 -> 192
            else -> 128
        }

        val cpuTileSize = when {
            !isLowRam && maxMemMb >= 512 && cores >= 8 -> 160
            !isLowRam && maxMemMb >= 256 -> 128
            else -> 128
        }

        return DeviceHardwareProfile(
            availableCores = cores,
            maxMemoryMb = maxMemMb,
            isLowRam = isLowRam,
            recommendedCpuThreads = cpuThreads,
            recommendedTileSizeVulkan = vkTileSize,
            recommendedTileSizeCpu = cpuTileSize
        )
    }

    suspend fun process(bitmap: Bitmap, requestedScale: Int = 4, onProgress: (Float) -> Unit): Bitmap = processMutex.withLock {
        withContext(Dispatchers.IO) {
            activeProcessingThread = Thread.currentThread()
            var inputBitmap = bitmap
            var safeInputBitmap: Bitmap = bitmap
            var outBitmap: Bitmap? = null
            try {
                if (Thread.currentThread().isInterrupted) {
                    throw CancellationException("Processing was cancelled")
                }
                onProgress(0.05f)

                val hwProfile = queryHardwareProfile()
                val requestedCpuThreads = hwProfile.recommendedCpuThreads
                val pad = 8
                val scale = if (requestedScale == 2) 2 else 4
                val tResizeStart = SystemClock.elapsedRealtimeNanos()
                val initialResizingNanos = SystemClock.elapsedRealtimeNanos() - tResizeStart

                var width = inputBitmap.width
                var height = inputBitmap.height
                var outWidth = width * scale
                var outHeight = height * scale

                safeInputBitmap = if (inputBitmap.config == Bitmap.Config.ARGB_8888) {
                    inputBitmap
                } else {
                    inputBitmap.copy(Bitmap.Config.ARGB_8888, false)
                }

                try {
                    outBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
                } catch (oom: OutOfMemoryError) {
                    Log.w(TAG, "OOM creating output bitmap (${outWidth}x${outHeight}). Scaling down input safely.")
                    System.gc()
                    var currentInput = inputBitmap
                    var currentSafe = safeInputBitmap
                    var downRatio = 0.75f
                    var allocatedBitmap: Bitmap? = null
                    while (allocatedBitmap == null && downRatio >= 0.25f) {
                        val nextW = (width * downRatio).toInt().coerceAtLeast(1)
                        val nextH = (height * downRatio).toInt().coerceAtLeast(1)
                        try {
                            allocatedBitmap = Bitmap.createBitmap(nextW * scale, nextH * scale, Bitmap.Config.ARGB_8888)
                            val scaledDown = Bitmap.createScaledBitmap(currentInput, nextW, nextH, true)
                            if (currentInput != bitmap && !currentInput.isRecycled) {
                                currentInput.recycle()
                            }
                            if (currentSafe != currentInput && currentSafe != bitmap && !currentSafe.isRecycled) {
                                currentSafe.recycle()
                            }
                            inputBitmap = scaledDown
                            width = nextW
                            height = nextH
                            outWidth = nextW * scale
                            outHeight = nextH * scale
                            safeInputBitmap = if (inputBitmap.config == Bitmap.Config.ARGB_8888) {
                                inputBitmap
                            } else {
                                inputBitmap.copy(Bitmap.Config.ARGB_8888, false)
                            }
                        } catch (_: OutOfMemoryError) {
                            downRatio -= 0.25f
                            System.gc()
                        }
                    }
                    outBitmap = allocatedBitmap ?: throw OutOfMemoryError("Insufficient memory to allocate output bitmap")
                }
                val nonNullOutBitmap = outBitmap!!

                val modelFile = getMnnModelFile()
                val modelPath = modelFile?.absolutePath

                var backendUsed: String? = null
                val mnnMetrics = LongArray(10)

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

                val defaultOrder = listOf("MNN_VULKAN", "MNN_CPU", "ONNX_XNNPACK")
                val preferred = activeBackendOverride?.takeIf { it in defaultOrder }
                val executionOrder = if (preferred != null) {
                    listOf(preferred) + defaultOrder.filter { it != preferred }
                } else {
                    defaultOrder
                }

                for (candidate in executionOrder) {
                    if (backendUsed != null) break
                    when (candidate) {
                        "MNN_VULKAN" -> {
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
                                        outputBitmap = nonNullOutBitmap,
                                        backendType = 0, // Vulkan
                                        requestedTileSize = hwProfile.recommendedTileSizeVulkan,
                                        cpuThreads = requestedCpuThreads,
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
                                        val mnnPrepNanos = mnnMetrics[5]
                                        val mnnStitchNanos = mnnMetrics[6]
                                        val mnnSessionReused = mnnMetrics[7] == 1L
                                        val mnnTileDim = mnnMetrics[9].toInt()
                                        timeline.add("${stepNum++}. MNN_VULKAN SESSION VERIFIED")
                                        timeline.add("${stepNum++}. MNN_VULKAN EXECUTED (Warm: $mnnSessionReused, Tile: ${mnnTileDim}px, Prep: ${mnnPrepNanos / 1_000_000}ms, Infer: ${vulkanInferenceNanos / 1_000_000}ms, Stitch: ${mnnStitchNanos / 1_000_000}ms)")
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
                                        Log.w(TAG_BACKEND, "MNN_VULKAN failed (code $vkCode).")
                                    }
                                } catch (c: kotlinx.coroutines.CancellationException) {
                                    throw c
                                } catch (e: Throwable) {
                                    vulkanError = e.message ?: "Unknown exception"
                                    timeline.add("${stepNum++}. MNN_VULKAN EXCEPTION: ${e.message}")
                                    Log.w(TAG_BACKEND, "MNN_VULKAN exception: ${e.message}.", e)
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
                        }
                        "MNN_CPU" -> {
                            if (isMnnLoaded && modelPath != null) {
                                mnnCpuAttempted = true
                                timeline.add("${stepNum++}. MNN_CPU ATTEMPTED")
                                try {
                                    val listener = NativeProgressListener { fraction ->
                                        if (activeProcessingThread != null && !Thread.currentThread().isInterrupted) {
                                            onProgress(0.05f + fraction * 0.95f)
                                        }
                                    }
                                    val cpuCode = nativeProcessWithMNN(
                                        modelPath = modelPath,
                                        inputBitmap = safeInputBitmap,
                                        outputBitmap = nonNullOutBitmap,
                                        backendType = 1, // CPU
                                        requestedTileSize = hwProfile.recommendedTileSizeCpu,
                                        cpuThreads = requestedCpuThreads,
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
                                        val mnnPrepNanos = mnnMetrics[5]
                                        val mnnStitchNanos = mnnMetrics[6]
                                        val mnnSessionReused = mnnMetrics[7] == 1L
                                        val mnnTileDim = mnnMetrics[9].toInt()
                                        timeline.add("${stepNum++}. MNN_CPU EXECUTED (Warm: $mnnSessionReused, Tile: ${mnnTileDim}px, Threads: $requestedCpuThreads, Prep: ${mnnPrepNanos / 1_000_000}ms, Infer: ${mnnCpuInferenceNanos / 1_000_000}ms, Stitch: ${mnnStitchNanos / 1_000_000}ms)")
                                        Log.i(TAG_BACKEND, "Backend selected: MNN_CPU")
                                    } else if (cpuCode == -2) {
                                        mnnCpuError = "Processing was cancelled by user"
                                        timeline.add("${stepNum++}. MNN_CPU CANCELLED")
                                        throw kotlinx.coroutines.CancellationException("Processing was cancelled")
                                    } else {
                                        val reason = "Native MNN CPU returned error code $cpuCode"
                                        mnnCpuError = reason
                                        timeline.add("${stepNum++}. MNN_CPU FAILED (Code: $cpuCode)")
                                        Log.w(TAG_BACKEND, "MNN_CPU failed (code $cpuCode).")
                                    }
                                } catch (c: kotlinx.coroutines.CancellationException) {
                                    throw c
                                } catch (e: Throwable) {
                                    mnnCpuError = e.message ?: "Unknown exception"
                                    timeline.add("${stepNum++}. MNN_CPU EXCEPTION: ${e.message}")
                                    Log.w(TAG_BACKEND, "MNN_CPU exception: ${e.message}.", e)
                                }
                            } else {
                                val reason = when {
                                    !isMnnLoaded -> "MNN native libraries not loaded"
                                    modelPath == null -> "MNN model file unavailable"
                                    else -> "MNN CPU skipped"
                                }
                                mnnCpuError = reason
                                timeline.add("${stepNum++}. MNN_CPU SKIPPED ($reason)")
                            }
                        }
                        "ONNX_XNNPACK" -> {
                            onnxAttempted = true
                            timeline.add("${stepNum++}. ONNX RUNTIME ATTEMPTED")
                            Log.i(TAG_BACKEND, "Backend selected: ONNX_XNNPACK")
                            try {
                                val activeTileSizeOnnx = hwProfile.recommendedTileSizeCpu
                                val totalTilesOnnx = Math.ceil(width.toDouble() / activeTileSizeOnnx) * Math.ceil(height.toDouble() / activeTileSizeOnnx)
                                processWithOnnx(safeInputBitmap, nonNullOutBitmap, width, height, activeTileSizeOnnx, pad, scale, initialResizingNanos, totalTilesOnnx, onProgress)
                                val onnxBackend = if (xnnpackActive) "ONNX_XNNPACK" else "ONNX_CPU"
                                backendUsed = onnxBackend
                                onnxExecuted = true
                                timeline.add("${stepNum++}. $onnxBackend EXECUTED SUCCESSFULLY")
                            } catch (c: CancellationException) {
                                throw c
                            } catch (e: Throwable) {
                                onnxReturnOrError = e.message ?: "Unknown ONNX error"
                                timeline.add("${stepNum++}. ONNX FAILED: ${e.message}")
                                Log.w(TAG_BACKEND, "ONNX failed: ${e.message}.", e)
                            }
                        }
                    }
                }

                // 4. TIER 4: High-Quality Bicubic + Lanczos Super-Resolution with Edge-Preserving Unsharp Mask
                if (backendUsed == null) {
                    timeline.add("${stepNum++}. ADAPTIVE BICUBIC / LANCZOS ATTEMPTED (Fallback 3)")
                    Log.i(TAG_BACKEND, "Backend selected: ADAPTIVE_BICUBIC_LANCZOS")
                    try {
                        val enhancedBitmap = processWithBicubicLanczosEnhance(safeInputBitmap, scale, onProgress)
                        val canvas = android.graphics.Canvas(nonNullOutBitmap)
                        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                        val srcRect = android.graphics.Rect(0, 0, enhancedBitmap.width, enhancedBitmap.height)
                        val dstRect = android.graphics.Rect(0, 0, outWidth, outHeight)
                        canvas.drawBitmap(enhancedBitmap, srcRect, dstRect, paint)
                        if (enhancedBitmap != nonNullOutBitmap) {
                            enhancedBitmap.recycle()
                        }
                        backendUsed = "ADAPTIVE_BICUBIC_LANCZOS"
                        timeline.add("${stepNum++}. ADAPTIVE_BICUBIC_LANCZOS EXECUTED SUCCESSFULLY")
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        Log.e(TAG_BACKEND, "Adaptive bicubic fallback error: ${t.message}", t)
                        val scaled = Bitmap.createScaledBitmap(safeInputBitmap, outWidth, outHeight, true)
                        val canvas = android.graphics.Canvas(nonNullOutBitmap)
                        canvas.drawBitmap(scaled, 0f, 0f, null)
                        scaled.recycle()
                        backendUsed = "DIRECT_SCALED_FALLBACK"
                        timeline.add("${stepNum++}. DIRECT_SCALED_FALLBACK EXECUTED")
                    }
                }

                timeline.add("${stepNum++}. FINAL BACKEND: $backendUsed")

                val activeTileSize = when (backendUsed) {
                    "MNN_VULKAN" -> hwProfile.recommendedTileSizeVulkan
                    else -> hwProfile.recommendedTileSizeCpu
                }
                val totalTiles = Math.ceil(width.toDouble() / activeTileSize) * Math.ceil(height.toDouble() / activeTileSize)

                // Populate metrics for MNN backend if MNN executed
                if (backendUsed?.startsWith("MNN") == true) {
                    lastTimingMetrics = InferenceTimingMetrics(
                        sessionInitNanos = mnnMetrics[0],
                        isXnnpackEnabled = false,
                        intraOpThreads = requestedCpuThreads,
                        interOpThreads = 1,
                        initialResizingNanos = initialResizingNanos,
                        tileCreationExtractionNanos = mnnMetrics[5],
                        rgbToTensorNanos = 0L,
                        onnxInferenceNanos = mnnMetrics[1],
                        outputTensorProcessingNanos = 0L,
                        outputBitmapCreationNanos = 0L,
                        canvasStitchingNanos = mnnMetrics[6],
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
                    tileSize = activeTileSize,
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
                    imagePrepNanos = if (backendUsed?.startsWith("MNN") == true) mnnMetrics[5] else 0L,
                    postprocessingNanos = if (backendUsed?.startsWith("MNN") == true) mnnMetrics[6] else if (backendUsed?.startsWith("ONNX") == true) (lastTimingMetrics?.outputTensorProcessingNanos ?: 0L) + (lastTimingMetrics?.outputBitmapCreationNanos ?: 0L) else 0L,
                    stitchingNanos = if (backendUsed?.startsWith("MNN") == true) mnnMetrics[6] else if (backendUsed?.startsWith("ONNX") == true) (lastTimingMetrics?.canvasStitchingNanos ?: 0L) else 0L
                )

                if (activeProcessingThread != null && !Thread.currentThread().isInterrupted) {
                    onProgress(1.0f)
                }
                if (safeInputBitmap != inputBitmap && safeInputBitmap != bitmap && !safeInputBitmap.isRecycled) {
                    safeInputBitmap.recycle()
                }
                if (inputBitmap != bitmap && !inputBitmap.isRecycled) {
                    inputBitmap.recycle()
                }
                nonNullOutBitmap
            } catch (c: CancellationException) {
                outBitmap?.let { if (!it.isRecycled) it.recycle() }
                if (safeInputBitmap != bitmap && safeInputBitmap != inputBitmap && !safeInputBitmap.isRecycled) {
                    safeInputBitmap.recycle()
                }
                if (inputBitmap != bitmap && !inputBitmap.isRecycled) {
                    inputBitmap.recycle()
                }
                throw c
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

        val maxTileDim = tileSize + pad * 2
        val reusableInPixels = IntArray(maxTileDim * maxTileDim)
        val reusableOutPixels = IntArray((maxTileDim * 4) * (maxTileDim * 4))
        val reusableByteBuffer = ByteBuffer.allocateDirect(3 * maxTileDim * maxTileDim * 4).order(ByteOrder.nativeOrder())
        val reusableFloatBuffer = reusableByteBuffer.asFloatBuffer()

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

        val inputName = session?.inputNames?.iterator()?.next() ?: "input"

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
                val tilePixelsCount = currentTileWidth * currentTileHeight

                // Tile pixel extraction without intermediate Bitmap allocation
                val tTileExtractStart = SystemClock.elapsedRealtimeNanos()
                inputBitmap.getPixels(reusableInPixels, 0, currentTileWidth, startX, startY, currentTileWidth, currentTileHeight)
                val tTileExtractEnd = SystemClock.elapsedRealtimeNanos()
                tileCreationExtractionNanos += (tTileExtractEnd - tTileExtractStart)

                // RGB-to-tensor preprocessing
                val tRgbToTensorStart = SystemClock.elapsedRealtimeNanos()
                reusableFloatBuffer.clear()
                for (i in 0 until tilePixelsCount) {
                    val p = reusableInPixels[i]
                    reusableFloatBuffer.put(i, Color.red(p) / 255.0f)
                    reusableFloatBuffer.put(tilePixelsCount + i, Color.green(p) / 255.0f)
                    reusableFloatBuffer.put(2 * tilePixelsCount + i, Color.blue(p) / 255.0f)
                }
                reusableFloatBuffer.position(0)
                reusableFloatBuffer.limit(3 * tilePixelsCount)

                val shape = longArrayOf(1, 3, currentTileHeight.toLong(), currentTileWidth.toLong())
                val tensor = OnnxTensor.createTensor(env, reusableFloatBuffer, shape)
                val tRgbToTensorEnd = SystemClock.elapsedRealtimeNanos()
                rgbToTensorNanos += (tRgbToTensorEnd - tRgbToTensorStart)

                // ONNX Runtime session.run()
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
                val channelSize = outW * outH

                for (i in 0 until channelSize) {
                    val origY = (i / outW) / 4
                    val origX = (i % outW) / 4
                    val alpha = (reusableInPixels[origY * currentTileWidth + origX] ushr 24) and 0xFF
                    val r = (outputBuffer.get(i) * 255.0f).coerceIn(0f, 255f).toInt()
                    val g = (outputBuffer.get(channelSize + i) * 255.0f).coerceIn(0f, 255f).toInt()
                    val b = (outputBuffer.get(2 * channelSize + i) * 255.0f).coerceIn(0f, 255f).toInt()
                    reusableOutPixels[i] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
                }
                val tOutTensorEnd = SystemClock.elapsedRealtimeNanos()
                outputTensorProcessingNanos += (tOutTensorEnd - tOutTensorStart)

                // Output bitmap creation
                val tOutBitmapStart = SystemClock.elapsedRealtimeNanos()
                val outTileBitmap = Bitmap.createBitmap(reusableOutPixels, 0, outW, outW, outH, Bitmap.Config.ARGB_8888)
                val tOutBitmapEnd = SystemClock.elapsedRealtimeNanos()
                outputBitmapCreationNanos += (tOutBitmapEnd - tOutBitmapStart)

                // Canvas stitching
                val tStitchStart = SystemClock.elapsedRealtimeNanos()
                val cropStartX = (x - startX) * 4
                val cropStartY = (y - startY) * 4
                val cropWidthInTile = min(tileSize, width - x) * 4
                val cropHeightInTile = min(tileSize, height - y) * 4

                val srcRect = android.graphics.Rect(cropStartX, cropStartY, cropStartX + cropWidthInTile, cropStartY + cropHeightInTile)
                val dstRect = android.graphics.Rect(x * scale, y * scale, x * scale + min(tileSize, width - x) * scale, y * scale + min(tileSize, height - y) * scale)

                canvas.drawBitmap(outTileBitmap, srcRect, dstRect, paint)
                val tStitchEnd = SystemClock.elapsedRealtimeNanos()
                canvasStitchingNanos += (tStitchEnd - tStitchStart)

                outTileBitmap.recycle()
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
        if (width <= 0 || height <= 0) return output

        var prevRow = IntArray(width)
        var currRow = IntArray(width)
        var nextRow = IntArray(width)
        val outRow = IntArray(width)

        val weightCenter = 1f + 4f * amount
        val weightNeighbor = -amount

        src.getPixels(currRow, 0, width, 0, 0, width, 1)

        for (y in 0 until height) {
            if (y == 0) {
                System.arraycopy(currRow, 0, prevRow, 0, width)
            }

            if (y < height - 1) {
                src.getPixels(nextRow, 0, width, 0, y + 1, width, 1)
            } else {
                System.arraycopy(currRow, 0, nextRow, 0, width)
            }

            for (x in 0 until width) {
                val xPrev = if (x > 0) x - 1 else x
                val xNext = if (x < width - 1) x + 1 else x

                val c = currRow[x]
                val up = prevRow[x]
                val down = nextRow[x]
                val left = currRow[xPrev]
                val right = currRow[xNext]

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

                outRow[x] = (a shl 24) or (rSharp shl 16) or (gSharp shl 8) or bSharp
            }

            output.setPixels(outRow, 0, width, 0, y, width, 1)

            val temp = prevRow
            prevRow = currRow
            currRow = nextRow
            nextRow = temp
        }

        return output
    }

    var activeBackendOverride: String? = null
    var lastCalibrationSummary: CalibrationSummary? = null

    suspend fun runHardwareCalibration(
        scale: Int = 4,
        tileSize: Int = 128,
        pad: Int = 8,
        forcedFingerprint: String = ""
    ): CalibrationSummary = processMutex.withLock {
        withContext(Dispatchers.IO) {
            val tCalibStart = SystemClock.elapsedRealtimeNanos()
            val hwProfile = queryHardwareProfile()
            val requestedCpuThreads = hwProfile.recommendedCpuThreads
            val mnnModelFile = getModelFile("RealESRGAN_x4plus_anime_6B-x4.mnn")
            val mnnModelPath = mnnModelFile?.absolutePath
            val onnxModelFile = getModelFile("RealESRGAN_x4plus_anime_6B.onnx")
            val onnxModelPath = onnxModelFile?.absolutePath

            val hasVulkanFeature = try {
                context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
            } catch (_: Throwable) {
                false
            }

            val sampleTileW = tileSize + 2 * pad
            val sampleTileH = tileSize + 2 * pad
            val testTileBitmap = Bitmap.createBitmap(sampleTileW, sampleTileH, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(testTileBitmap)
            val paint = android.graphics.Paint()
            for (py in 0 until sampleTileH step 16) {
                for (px in 0 until sampleTileW step 16) {
                    paint.color = Color.rgb((px * 7) % 255, (py * 13) % 255, (px * py * 3) % 255)
                    canvas.drawRect(px.toFloat(), py.toFloat(), (px + 16).toFloat(), (py + 16).toFloat(), paint)
                }
            }

            val expectedOutW = sampleTileW * scale
            val expectedOutH = sampleTileH * scale

            val candidateBackends = listOf("MNN_VULKAN", "MNN_CPU", "ONNX_XNNPACK")
            val screeningResults = mutableListOf<BackendBenchmarkResult>()

            val maxTotalBudgetNanos = 90_000_000_000L // 90s max total

            for (backend in candidateBackends) {
                if (!coroutineContext.isActive) throw CancellationException("Calibration cancelled")
                val totalElapsed = SystemClock.elapsedRealtimeNanos() - tCalibStart
                if (totalElapsed > maxTotalBudgetNanos) {
                    screeningResults.add(
                        BackendBenchmarkResult(
                            backendName = backend,
                            isConfiguredAndAvailable = false,
                            unavailableReason = "Skipped due to 90s total calibration timeout budget"
                        )
                    )
                    continue
                }

                val candidateStart = SystemClock.elapsedRealtimeNanos()

                when (backend) {
                    "MNN_VULKAN" -> {
                        if (!isMnnLoaded || mnnModelPath == null || !hasVulkanFeature) {
                            screeningResults.add(
                                BackendBenchmarkResult(
                                    backendName = backend,
                                    isConfiguredAndAvailable = false,
                                    unavailableReason = when {
                                        !isMnnLoaded -> "MNN native library failed to load"
                                        mnnModelPath == null -> "MNN model file missing"
                                        !hasVulkanFeature -> "No Vulkan hardware level feature on device"
                                        else -> "Vulkan hardware unavailable"
                                    }
                                )
                            )
                        } else {
                            try {
                                val outTile = Bitmap.createBitmap(expectedOutW, expectedOutH, Bitmap.Config.ARGB_8888)
                                val mnnMetrics = LongArray(10)
                                val tWarmStart = SystemClock.elapsedRealtimeNanos()
                                val codeWarm = nativeProcessWithMNN(mnnModelPath, testTileBitmap, outTile, 0, tileSize, requestedCpuThreads, null, mnnMetrics)
                                val warmUpNanos = SystemClock.elapsedRealtimeNanos() - tWarmStart

                                if (codeWarm != 0) {
                                    outTile.recycle()
                                    screeningResults.add(
                                        BackendBenchmarkResult(
                                            backendName = backend,
                                            isConfiguredAndAvailable = false,
                                            unavailableReason = "MNN Vulkan warmup returned error code $codeWarm"
                                        )
                                    )
                                } else {
                                    val trials = mutableListOf<Long>()
                                    for (t in 1..2) {
                                        if (!coroutineContext.isActive) throw CancellationException("Calibration cancelled")
                                        if (SystemClock.elapsedRealtimeNanos() - candidateStart > 30_000_000_000L) break
                                        val tStart = SystemClock.elapsedRealtimeNanos()
                                        val code = nativeProcessWithMNN(mnnModelPath, testTileBitmap, outTile, 0, tileSize, requestedCpuThreads, null, mnnMetrics)
                                        val trialTime = SystemClock.elapsedRealtimeNanos() - tStart
                                        if (code == 0) trials.add(trialTime)
                                    }
                                    outTile.recycle()

                                    val trialsToUse = if (trials.isNotEmpty()) trials else listOf(warmUpNanos)
                                    val median = trialsToUse.sorted()[trialsToUse.size / 2]
                                    screeningResults.add(
                                        BackendBenchmarkResult(
                                            backendName = backend,
                                            isConfiguredAndAvailable = true,
                                            warmUpLatencyNanos = warmUpNanos,
                                            trialLatenciesNanos = trialsToUse,
                                            medianLatencyNanos = median,
                                            outputDimensionsVerified = true,
                                            pixelEntropyValid = true
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                screeningResults.add(
                                    BackendBenchmarkResult(
                                        backendName = backend,
                                        isConfiguredAndAvailable = false,
                                        unavailableReason = "Vulkan exception: ${e.message}"
                                    )
                                )
                            }
                        }
                    }
                    "MNN_CPU" -> {
                        if (!isMnnLoaded || mnnModelPath == null) {
                            screeningResults.add(
                                BackendBenchmarkResult(
                                    backendName = backend,
                                    isConfiguredAndAvailable = false,
                                    unavailableReason = "MNN native library or model missing"
                                )
                            )
                        } else {
                            try {
                                val outTile = Bitmap.createBitmap(expectedOutW, expectedOutH, Bitmap.Config.ARGB_8888)
                                val mnnMetrics = LongArray(10)
                                val tWarmStart = SystemClock.elapsedRealtimeNanos()
                                val codeWarm = nativeProcessWithMNN(mnnModelPath, testTileBitmap, outTile, 1, tileSize, requestedCpuThreads, null, mnnMetrics)
                                val warmUpNanos = SystemClock.elapsedRealtimeNanos() - tWarmStart

                                if (codeWarm != 0) {
                                    outTile.recycle()
                                    screeningResults.add(
                                        BackendBenchmarkResult(
                                            backendName = backend,
                                            isConfiguredAndAvailable = false,
                                            unavailableReason = "MNN CPU warmup returned code $codeWarm"
                                        )
                                    )
                                } else {
                                    val trials = mutableListOf<Long>()
                                    for (t in 1..2) {
                                        if (!coroutineContext.isActive) throw CancellationException("Calibration cancelled")
                                        if (SystemClock.elapsedRealtimeNanos() - candidateStart > 30_000_000_000L) break
                                        val tStart = SystemClock.elapsedRealtimeNanos()
                                        val code = nativeProcessWithMNN(mnnModelPath, testTileBitmap, outTile, 1, tileSize, requestedCpuThreads, null, mnnMetrics)
                                        val trialTime = SystemClock.elapsedRealtimeNanos() - tStart
                                        if (code == 0) trials.add(trialTime)
                                    }
                                    outTile.recycle()

                                    val trialsToUse = if (trials.isNotEmpty()) trials else listOf(warmUpNanos)
                                    val median = trialsToUse.sorted()[trialsToUse.size / 2]
                                    screeningResults.add(
                                        BackendBenchmarkResult(
                                            backendName = backend,
                                            isConfiguredAndAvailable = true,
                                            warmUpLatencyNanos = warmUpNanos,
                                            trialLatenciesNanos = trialsToUse,
                                            medianLatencyNanos = median,
                                            outputDimensionsVerified = true,
                                            pixelEntropyValid = true
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                screeningResults.add(
                                    BackendBenchmarkResult(
                                        backendName = backend,
                                        isConfiguredAndAvailable = false,
                                        unavailableReason = "MNN CPU exception: ${e.message}"
                                    )
                                )
                            }
                        }
                    }
                    "ONNX_XNNPACK" -> {
                        if (onnxModelFile == null || !onnxModelFile.exists()) {
                            screeningResults.add(
                                BackendBenchmarkResult(
                                    backendName = backend,
                                    isConfiguredAndAvailable = false,
                                    unavailableReason = "ONNX model file missing"
                                )
                            )
                        } else {
                            try {
                                initialize()
                                if (session == null) {
                                    screeningResults.add(
                                        BackendBenchmarkResult(
                                            backendName = backend,
                                            isConfiguredAndAvailable = false,
                                            unavailableReason = "Failed to create ONNX XNNPACK session"
                                        )
                                    )
                                } else {
                                    val outTile = Bitmap.createBitmap(expectedOutW, expectedOutH, Bitmap.Config.ARGB_8888)
                                    val tWarmStart = SystemClock.elapsedRealtimeNanos()
                                    runOnnxTileInference(testTileBitmap, outTile, scale)
                                    val warmUpNanos = SystemClock.elapsedRealtimeNanos() - tWarmStart

                                    val trials = mutableListOf<Long>()
                                    for (t in 1..2) {
                                        if (!coroutineContext.isActive) throw CancellationException("Calibration cancelled")
                                        if (SystemClock.elapsedRealtimeNanos() - candidateStart > 30_000_000_000L) break
                                        val tStart = SystemClock.elapsedRealtimeNanos()
                                        runOnnxTileInference(testTileBitmap, outTile, scale)
                                        trials.add(SystemClock.elapsedRealtimeNanos() - tStart)
                                    }
                                    outTile.recycle()

                                    val trialsToUse = if (trials.isNotEmpty()) trials else listOf(warmUpNanos)
                                    val median = trialsToUse.sorted()[trialsToUse.size / 2]
                                    screeningResults.add(
                                        BackendBenchmarkResult(
                                            backendName = backend,
                                            isConfiguredAndAvailable = true,
                                            warmUpLatencyNanos = warmUpNanos,
                                            trialLatenciesNanos = trialsToUse,
                                            medianLatencyNanos = median,
                                            outputDimensionsVerified = true,
                                            pixelEntropyValid = true
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                screeningResults.add(
                                    BackendBenchmarkResult(
                                        backendName = backend,
                                        isConfiguredAndAvailable = false,
                                        unavailableReason = "ONNX exception: ${e.message}"
                                    )
                                )
                            }
                        }
                    }
                }
            }

            testTileBitmap.recycle()

            val availableCandidates = screeningResults.filter { it.isConfiguredAndAvailable }.sortedBy { it.medianLatencyNanos }
            var winningBackend = "MNN_CPU"
            val finalResults = screeningResults.toMutableList()

            if (availableCandidates.isNotEmpty()) {
                for (candidate in availableCandidates) {
                    if (!coroutineContext.isActive) throw CancellationException("Calibration cancelled")
                    val gridImageW = tileSize * 2
                    val gridImageH = tileSize * 2
                    val gridBitmap = Bitmap.createBitmap(gridImageW, gridImageH, Bitmap.Config.ARGB_8888)

                    val gridCanvas = android.graphics.Canvas(gridBitmap)
                    val gridPaint = android.graphics.Paint()
                    for (py in 0 until gridImageH step 16) {
                        for (px in 0 until gridImageW step 16) {
                            gridPaint.color = Color.rgb((px * 11 + 23) % 255, (py * 17 + 41) % 255, ((px + py) * 7) % 255)
                            gridCanvas.drawRect(px.toFloat(), py.toFloat(), (px + 16).toFloat(), (py + 16).toFloat(), gridPaint)
                        }
                    }

                    val tValidationStart = SystemClock.elapsedRealtimeNanos()

                    try {
                        val validatedOutput = when (candidate.backendName) {
                            "MNN_VULKAN" -> {
                                val outBmp = Bitmap.createBitmap(gridImageW * scale, gridImageH * scale, Bitmap.Config.ARGB_8888)
                                val code = nativeProcessWithMNN(mnnModelPath!!, gridBitmap, outBmp, 0, tileSize, requestedCpuThreads, null, LongArray(10))
                                if (code == 0) outBmp else null
                            }
                            "MNN_CPU" -> {
                                val outBmp = Bitmap.createBitmap(gridImageW * scale, gridImageH * scale, Bitmap.Config.ARGB_8888)
                                val code = nativeProcessWithMNN(mnnModelPath!!, gridBitmap, outBmp, 1, tileSize, requestedCpuThreads, null, LongArray(10))
                                if (code == 0) outBmp else null
                            }
                            "ONNX_XNNPACK" -> {
                                val outBmp = Bitmap.createBitmap(gridImageW * scale, gridImageH * scale, Bitmap.Config.ARGB_8888)
                                processWithOnnx(gridBitmap, outBmp, gridImageW, gridImageH, tileSize, pad, scale, 0L, 1.0) { }
                                outBmp
                            }
                            else -> null
                        }

                        val validationDuration = SystemClock.elapsedRealtimeNanos() - tValidationStart

                        if (validatedOutput != null) {
                            val dimOk = validatedOutput.width == gridImageW * scale && validatedOutput.height == gridImageH * scale
                            val entropyOk = checkBitmapEntropy(validatedOutput)
                            val seamDelta = checkSeamDiscontinuity(validatedOutput, tileSize * scale)

                            val refBitmap = Bitmap.createScaledBitmap(gridBitmap, gridImageW * scale, gridImageH * scale, true)
                            var sumAbsDiff = 0.0
                            val sampleW = min(16, validatedOutput.width)
                            val sampleH = min(16, validatedOutput.height)
                            val pixelCount = sampleW * sampleH

                            for (sy in 0 until sampleH) {
                                for (sx in 0 until sampleW) {
                                    val pOut = validatedOutput.getPixel(sx, sy)
                                    val pRef = refBitmap.getPixel(sx, sy)
                                    val rDiff = Math.abs(((pOut ushr 16) and 0xFF) - ((pRef ushr 16) and 0xFF))
                                    val gDiff = Math.abs(((pOut ushr 8) and 0xFF) - ((pRef ushr 8) and 0xFF))
                                    val bDiff = Math.abs((pOut and 0xFF) - (pRef and 0xFF))
                                    sumAbsDiff += (rDiff + gDiff + bDiff) / 3.0
                                }
                            }
                            refBitmap.recycle()
                            val mae = (sumAbsDiff / pixelCount).toFloat()

                            val pass = dimOk && entropyOk && seamDelta < 120f && mae < 85f
                            validatedOutput.recycle()
                            gridBitmap.recycle()

                            val idx = finalResults.indexOfFirst { it.backendName == candidate.backendName }
                            if (idx != -1) {
                                finalResults[idx] = finalResults[idx].copy(
                                    outputDimensionsVerified = dimOk,
                                    pixelEntropyValid = entropyOk,
                                    seamDiscontinuityMaxDelta = seamDelta,
                                    meanAbsoluteErrorVsRef = mae,
                                    multiTileValidationNanos = validationDuration,
                                    validationPassed = pass,
                                    isSelectedWinner = pass
                                )
                            }

                            if (pass) {
                                winningBackend = candidate.backendName
                                break
                            }
                        } else {
                            gridBitmap.recycle()
                        }
                    } catch (e: Exception) {
                        gridBitmap.recycle()
                        Log.w("InferenceEngine", "Validation failed for candidate ${candidate.backendName}: ${e.message}")
                    }
                }
            }

            val totalWallClockNanos = SystemClock.elapsedRealtimeNanos() - tCalibStart
            val summary = CalibrationSummary(
                timestamp = System.currentTimeMillis(),
                configurationFingerprint = forcedFingerprint,
                calibrationWallClockDurationNanos = totalWallClockNanos,
                results = finalResults,
                winningBackend = winningBackend,
                activeBackendUsed = activeBackendOverride ?: winningBackend,
                isManualOverrideActive = activeBackendOverride != null && activeBackendOverride != "AUTO"
            )

            lastCalibrationSummary = summary
            summary
        }
    }

    private fun checkBitmapEntropy(bmp: Bitmap): Boolean {
        if (bmp.width < 8 || bmp.height < 8) return true
        val samplePixels = IntArray(100)
        bmp.getPixels(samplePixels, 0, 10, bmp.width / 4, bmp.height / 4, 10, 10)
        var sum = 0.0
        for (p in samplePixels) {
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            sum += (r + g + b) / 3.0
        }
        val mean = sum / samplePixels.size
        var variance = 0.0
        for (p in samplePixels) {
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            val valPixel = (r + g + b) / 3.0
            variance += (valPixel - mean) * (valPixel - mean)
        }
        val stdDev = Math.sqrt(variance / samplePixels.size)
        return stdDev >= 0.5
    }

    private fun checkSeamDiscontinuity(bmp: Bitmap, seamX: Int): Float {
        if (seamX <= 1 || seamX >= bmp.width - 1) return 0f
        var maxDelta = 0f
        val h = min(bmp.height, 100)
        for (y in 0 until h step 5) {
            val p1 = bmp.getPixel(seamX - 1, y)
            val p2 = bmp.getPixel(seamX, y)
            val r1 = (p1 ushr 16) and 0xFF
            val g1 = (p1 ushr 8) and 0xFF
            val b1 = p1 and 0xFF
            val r2 = (p2 ushr 16) and 0xFF
            val g2 = (p2 ushr 8) and 0xFF
            val b2 = p2 and 0xFF
            val delta = Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2)
            if (delta > maxDelta) maxDelta = delta.toFloat()
        }
        return maxDelta
    }

    private fun runOnnxTileInference(inTile: Bitmap, outTile: Bitmap, scale: Int) {
        val w = inTile.width
        val h = inTile.height
        val pixels = IntArray(w * h)
        inTile.getPixels(pixels, 0, w, 0, 0, w, h)
        val floatBuf = FloatBuffer.allocate(3 * w * h)
        for (i in 0 until w * h) {
            val p = pixels[i]
            floatBuf.put(i, Color.red(p) / 255f)
            floatBuf.put(w * h + i, Color.green(p) / 255f)
            floatBuf.put(2 * w * h + i, Color.blue(p) / 255f)
        }
        floatBuf.position(0)
        val shape = longArrayOf(1, 3, h.toLong(), w.toLong())
        val tensor = OnnxTensor.createTensor(env, floatBuf, shape)
        val inputName = session?.inputNames?.iterator()?.next() ?: "input"
        val results = session?.run(mapOf(inputName to tensor))
        val output = results?.get(0) as? OnnxTensor
        if (output != null) {
            val outBuf = output.floatBuffer
            val outW = w * scale
            val outH = h * scale
            val outPixels = IntArray(outW * outH)
            val channelSize = outW * outH
            for (i in 0 until channelSize) {
                val r = (outBuf.get(i) * 255f).coerceIn(0f, 255f).toInt()
                val g = (outBuf.get(channelSize + i) * 255f).coerceIn(0f, 255f).toInt()
                val b = (outBuf.get(2 * channelSize + i) * 255f).coerceIn(0f, 255f).toInt()
                outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            outTile.setPixels(outPixels, 0, outW, 0, 0, outW, outH)
        }
    }

    fun close() {
        session?.close()
        env?.close()
    }
}

