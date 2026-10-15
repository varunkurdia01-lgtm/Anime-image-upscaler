package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.HistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProcessedItem(
    val originalUri: String,
    val enhancedUri: String,
    val width: Int,
    val height: Int
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val inferenceEngine = InferenceEngine(application)

    private val prefs = application.getSharedPreferences("pixelboost_settings", Context.MODE_PRIVATE)

        

    private val _themeIndex = MutableStateFlow(prefs.getInt("themeIndex", 0))
    val themeIndex = _themeIndex.asStateFlow()
    fun setThemeIndex(index: Int) {
        _themeIndex.value = index
        prefs.edit().putInt("themeIndex", index).apply()
    }
    private val _scaleIndex = MutableStateFlow(prefs.getInt("scaleIndex", 1))
    val scaleIndex = _scaleIndex.asStateFlow()
    fun setScaleIndex(index: Int) {
        _scaleIndex.value = index
        prefs.edit().putInt("scaleIndex", index).apply()
    }

    private val _formatIndex = MutableStateFlow(prefs.getInt("formatIndex", 1))
    val formatIndex = _formatIndex.asStateFlow()
    fun setFormatIndex(index: Int) {
        _formatIndex.value = index
        prefs.edit().putInt("formatIndex", index).apply()
    }

    private val _qualityIndex = MutableStateFlow(prefs.getInt("qualityIndex", 1))
    val qualityIndex = _qualityIndex.asStateFlow()
    fun setQualityIndex(index: Int) {
        _qualityIndex.value = index
        prefs.edit().putInt("qualityIndex", index).apply()
    }

    private val _saveEnhanced = MutableStateFlow(prefs.getBoolean("saveEnhanced", true))
    val saveEnhanced = _saveEnhanced.asStateFlow()
    fun setSaveEnhanced(save: Boolean) {
        _saveEnhanced.value = save
        prefs.edit().putBoolean("saveEnhanced", save).apply()
    }


    private val db = AppDatabase.getDatabase(application)
    
    val history = db.historyDao().getAllHistory()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            migrateExistingCacheHistory()
        }
    }

    private suspend fun migrateExistingCacheHistory() {
        try {
            val historyDir = java.io.File(getApplication<Application>().filesDir, "enhanced_history")
            if (!historyDir.exists()) {
                historyDir.mkdirs()
            }
            val historyList = history.first()
            for (entity in historyList) {
                if (entity.enhancedUri.contains("/cache/")) {
                    val oldPath = Uri.parse(entity.enhancedUri).path ?: continue
                    val oldFile = java.io.File(oldPath)
                    if (oldFile.exists() && oldFile.isFile) {
                        val newFile = java.io.File(historyDir, oldFile.name)
                        if (!newFile.exists()) {
                            val copied = oldFile.copyTo(newFile, overwrite = false)
                            if (copied.exists() && copied.length() == oldFile.length()) {
                                db.historyDao().update(entity.copy(enhancedUri = "file://${newFile.absolutePath}"))
                                oldFile.delete()
                            }
                        } else {
                            db.historyDao().update(entity.copy(enhancedUri = "file://${newFile.absolutePath}"))
                            oldFile.delete()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val _selectedImages = MutableStateFlow<List<String>>(emptyList())
    val selectedImages = _selectedImages.asStateFlow()
    
    private val _currentProcessingIndex = MutableStateFlow(0)
    val currentProcessingIndex = _currentProcessingIndex.asStateFlow()
    
    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()
    
    private val _resultBitmap = MutableStateFlow<Bitmap?>(null)
    val resultBitmap = _resultBitmap.asStateFlow()

    private val _batchResults = MutableStateFlow<List<ProcessedItem>>(emptyList())
    val batchResults = _batchResults.asStateFlow()

    private val _selectedResultIndex = MutableStateFlow(0)
    val selectedResultIndex = _selectedResultIndex.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    private val _itemSaveStatuses = MutableStateFlow<Map<Int, GallerySaveStatus>>(emptyMap())
    val itemSaveStatuses = _itemSaveStatuses.asStateFlow()

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

    private fun loadBitmapFromUri(uriString: String): Bitmap? {
        return try {
            val path = Uri.parse(uriString).path ?: return null
            val file = java.io.File(path)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        } catch (e: Exception) {
            null
        }
    }

    fun setSelectedResultIndex(index: Int) {
        if (index in _batchResults.value.indices) {
            _selectedResultIndex.value = index
            val item = _batchResults.value[index]
            _resultBitmap.value = loadBitmapFromUri(item.enhancedUri)
        }
    }
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    fun clearError() { _errorMessage.value = null }

    private var processingJob: Job? = null

    fun cancelProcessing() {
        val job = processingJob
        if (job != null) {
            job.cancel()
            processingJob = null
        }
        inferenceEngine.cancel()
        _progress.value = 0f
        _resultBitmap.value = null
        _errorMessage.value = null
        _batchResults.value = emptyList()
        _selectedResultIndex.value = 0
    }

    fun setSelectedImages(urls: List<String>) {
        _selectedImages.value = urls
    }

    fun startProcessing(onComplete: () -> Unit) {
        if (_selectedImages.value.isEmpty()) {
            onComplete()
            return
        }

        val autoSaveEnabled = _saveEnhanced.value
        val exportFormatIdx = _formatIndex.value
        val exportQualityIdx = _qualityIndex.value

        val previousJob = processingJob
        cancelProcessing()
        
        processingJob = viewModelScope.launch {
            var tTotalStart = SystemClock.elapsedRealtimeNanos()
            var decodingNanos = 0L
            var exifRotationNanos = 0L
            try {
                previousJob?.join()
                _resultBitmap.value = null
                _progress.value = 0f
                _errorMessage.value = null
                _batchResults.value = emptyList()
                _selectedResultIndex.value = 0
                _itemSaveStatuses.value = emptyMap()

                val results = mutableListOf<ProcessedItem>()
                for ((index, url) in _selectedImages.value.withIndex()) {
                    var bitmap: Bitmap? = null
                    try {
                        coroutineContext.ensureActive()
                        _currentProcessingIndex.value = index
                        _progress.value = 0f
                        
                        tTotalStart = SystemClock.elapsedRealtimeNanos()
                        decodingNanos = 0L
                        exifRotationNanos = 0L
                        
                        bitmap = withContext(Dispatchers.IO) {
                            coroutineContext.ensureActive()
                            val contentResolver = getApplication<Application>().contentResolver
                            val uri = Uri.parse(url)
                            
                            var decodedBitmap: Bitmap? = null
                            val tDecodeStart = SystemClock.elapsedRealtimeNanos()
                            contentResolver.openInputStream(uri)?.use {
                                val options = BitmapFactory.Options().apply {
                                    inPreferredConfig = Bitmap.Config.ARGB_8888
                                    inMutable = true
                                }
                                decodedBitmap = BitmapFactory.decodeStream(it, null, options)
                            }
                            val tDecodeEnd = SystemClock.elapsedRealtimeNanos()
                            decodingNanos = tDecodeEnd - tDecodeStart
                            
                            coroutineContext.ensureActive()
                            val tExifStart = SystemClock.elapsedRealtimeNanos()
                            var orientation = ExifInterface.ORIENTATION_NORMAL
                            contentResolver.openInputStream(uri)?.use {
                                val exif = ExifInterface(it)
                                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                            }
                            
                            if (decodedBitmap != null) {
                                val matrix = Matrix()
                                when (orientation) {
                                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                                }
                                if (!matrix.isIdentity) {
                                    val rotated = Bitmap.createBitmap(decodedBitmap!!, 0, 0, decodedBitmap!!.width, decodedBitmap!!.height, matrix, true)
                                    if (rotated != decodedBitmap) {
                                        decodedBitmap!!.recycle()
                                        decodedBitmap = rotated
                                    }
                                }
                            }
                            val tExifEnd = SystemClock.elapsedRealtimeNanos()
                            exifRotationNanos = tExifEnd - tExifStart
                            decodedBitmap
                        }
                        
                        if (bitmap != null) {
                            coroutineContext.ensureActive()
                            val result = inferenceEngine.process(bitmap) { p ->
                                if (coroutineContext.isActive) {
                                    _progress.value = p
                                }
                            }
                            coroutineContext.ensureActive()
                            val metrics = inferenceEngine.lastTimingMetrics
                            
                            var finalResult = result
                            val isAiBackend = inferenceEngine.lastDiagnosticReport?.finalBackend?.startsWith("MNN") == true ||
                                inferenceEngine.lastDiagnosticReport?.finalBackend?.startsWith("ONNX") == true
                            var finalLabel = if (isAiBackend) "4x AI Upscale" else "4x Enhanced"
                            val tFinalScaleStart = SystemClock.elapsedRealtimeNanos()
                            if (_scaleIndex.value == 0) {
                                finalResult = Bitmap.createScaledBitmap(result, result.width / 2, result.height / 2, true)
                                if (finalResult != result) {
                                    result.recycle()
                                }
                                finalLabel = if (isAiBackend) "2x AI Upscale" else "2x Enhanced"
                            }
                            val finalScalingNanos = SystemClock.elapsedRealtimeNanos() - tFinalScaleStart
                            
                            var pngCompressionNanos = 0L
                            var dbInsertionNanos = 0L
                            var enhancedCachePath = ""
                            
                            // Save to persistent filesDir for history automatically
                            withContext(Dispatchers.IO) {
                                coroutineContext.ensureActive()
                                val historyDir = java.io.File(getApplication<Application>().filesDir, "enhanced_history")
                                if (!historyDir.exists()) {
                                    historyDir.mkdirs()
                                }
                                val persistentFile = java.io.File(historyDir, "PixelBoost_${System.currentTimeMillis()}_$index.png")
                                val tPngStart = SystemClock.elapsedRealtimeNanos()
                                persistentFile.outputStream().use { out ->
                                    finalResult.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                                val tPngEnd = SystemClock.elapsedRealtimeNanos()
                                pngCompressionNanos = tPngEnd - tPngStart
                                
                                val tDbStart = SystemClock.elapsedRealtimeNanos()
                                val entity = HistoryEntity(
                                    originalUri = url,
                                    enhancedUri = "file://${persistentFile.absolutePath}",
                                    timestamp = System.currentTimeMillis(),
                                    sizeBytes = persistentFile.length(),
                                    label = finalLabel
                                )
                                db.historyDao().insert(entity)
                                val tDbEnd = SystemClock.elapsedRealtimeNanos()
                                dbInsertionNanos = tDbEnd - tDbStart
                                enhancedCachePath = "file://${persistentFile.absolutePath}"
                            }

                            coroutineContext.ensureActive()
                            val item = ProcessedItem(
                                originalUri = url,
                                enhancedUri = enhancedCachePath,
                                width = finalResult.width,
                                height = finalResult.height
                            )
                            results.add(item)
                            _batchResults.value = results.toList()

                            if (autoSaveEnabled) {
                                coroutineContext.ensureActive()
                                try {
                                    val exportResult = exportBitmapToMediaStore(
                                        context = getApplication(),
                                        bitmap = finalResult,
                                        formatIdx = exportFormatIdx,
                                        qualityIdx = exportQualityIdx
                                    )
                                    val status = if (exportResult.isSuccess) GallerySaveStatus.Saved else GallerySaveStatus.Failed
                                    _itemSaveStatuses.value = _itemSaveStatuses.value + (index to status)
                                } catch (c: CancellationException) {
                                    throw c
                                } catch (e: Exception) {
                                    Log.w("PIXELBOOST", "Auto-save failed for item $index", e)
                                    _itemSaveStatuses.value = _itemSaveStatuses.value + (index to GallerySaveStatus.Failed)
                                }
                            }

                            if (index == 0) {
                                _resultBitmap.value = finalResult
                            }
                            
                            val tTotalEnd = SystemClock.elapsedRealtimeNanos()
                            val totalProcessingNanos = tTotalEnd - tTotalStart

                            val diagnosticReport = inferenceEngine.lastDiagnosticReport?.copy(
                                outputWidth = finalResult.width,
                                outputHeight = finalResult.height,
                                totalProcessingNanos = totalProcessingNanos,
                                imagePrepNanos = decodingNanos + exifRotationNanos,
                                pngCompressionNanos = pngCompressionNanos,
                                dbInsertionNanos = dbInsertionNanos,
                                outputGenerated = true
                            )
                            
                            writeBenchmarkFile(
                                context = getApplication(),
                                reportData = diagnosticReport
                            )

                            logPerformanceSummary(diagnosticReport)
                        }
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: OutOfMemoryError) {
                        Log.e("PIXELBOOST", "Image too large for this device's memory at index $index", t)
                        try {
                            bitmap?.recycle()
                        } catch (_: Throwable) {}
                        _itemSaveStatuses.value = _itemSaveStatuses.value + (index to GallerySaveStatus.Failed)
                        _errorMessage.value = "Image too large for this device's memory"
                        continue
                    } catch (e: Throwable) {
                        Log.e("PIXELBOOST", "Error processing image at index $index", e)
                        try {
                            bitmap?.recycle()
                        } catch (_: Throwable) {}
                        _itemSaveStatuses.value = _itemSaveStatuses.value + (index to GallerySaveStatus.Failed)
                        _errorMessage.value = "Error processing image: ${e.message}"
                        continue
                    }
                }
                if (_batchResults.value.isNotEmpty()) {
                    _selectedResultIndex.value = 0
                    if (_resultBitmap.value == null) {
                        _resultBitmap.value = loadBitmapFromUri(_batchResults.value[0].enhancedUri)
                    }
                }
                if (coroutineContext.isActive) {
                    onComplete()
                }
            } catch (c: CancellationException) {
                Log.i("PIXELBOOST", "Processing job cancelled successfully")
                _progress.value = 0f
                _resultBitmap.value = null
                _batchResults.value = emptyList()
                _selectedResultIndex.value = 0
                _itemSaveStatuses.value = emptyMap()
            } catch (e: Exception) {
                _errorMessage.value = "ERROR: ${e.message}"
                _progress.value = 0f
                _resultBitmap.value = null
                _batchResults.value = emptyList()
                _selectedResultIndex.value = 0
                _itemSaveStatuses.value = emptyMap()
                e.printStackTrace()
                val totalProcessingNanos = SystemClock.elapsedRealtimeNanos() - tTotalStart
                val failureReport = (inferenceEngine.lastDiagnosticReport ?: BackendDiagnosticReport()).copy(
                    totalProcessingNanos = totalProcessingNanos,
                    imagePrepNanos = decodingNanos + exifRotationNanos,
                    finalBackend = "FAILED",
                    executionConfirmed = false,
                    outputGenerated = false
                )
                writeBenchmarkFile(
                    context = getApplication(),
                    reportData = failureReport
                )
                logPerformanceSummary(failureReport)
            } finally {
                processingJob = null
            }
        }
    }

    private fun logPerformanceSummary(reportData: BackendDiagnosticReport?) {
        if (reportData == null) return
        val TAG = "PIXELBOOST_PERF"
        fun formatMs(nanos: Long): String = String.format(java.util.Locale.US, "%.2f ms", nanos / 1_000_000.0)
        fun formatSec(nanos: Long): String = String.format(java.util.Locale.US, "%.2f s (%.2f ms)", nanos / 1_000_000_000.0, nanos / 1_000_000.0)

        Log.i(TAG, "========== PIXELBOOST RUNTIME DIAGNOSTIC SUMMARY ==========")
        Log.i(TAG, "Final Backend: ${reportData.finalBackend} (Confirmed: ${reportData.executionConfirmed})")
        Log.i(TAG, "Model Actually Used: ${reportData.modelActuallyUsed}")
        Log.i(TAG, "Input: ${reportData.inputWidth}x${reportData.inputHeight} -> Output: ${reportData.outputWidth}x${reportData.outputHeight}")
        Log.i(TAG, "Total Processing Duration: ${formatSec(reportData.totalProcessingNanos)}")
        Log.i(TAG, "Image Preparation: ${formatMs(reportData.imagePrepNanos)}")
        Log.i(TAG, "Backend Inference: ${formatSec(reportData.inferenceNanos)}")
        Log.i(TAG, "PNG Encoding: ${formatSec(reportData.pngCompressionNanos)}")
        Log.i(TAG, "Database Insertion: ${formatMs(reportData.dbInsertionNanos)}")
        Log.i(TAG, "===========================================================")
    }

    private fun writeBenchmarkFile(
        context: Context,
        reportData: BackendDiagnosticReport?
    ) {
        if (reportData == null) return
        try {
            fun formatMs(nanos: Long): String = String.format(java.util.Locale.US, "%.2f ms", nanos / 1_000_000.0)
            fun formatSec(nanos: Long): String = String.format(java.util.Locale.US, "%.2f s (%.2f ms)", nanos / 1_000_000_000.0, nanos / 1_000_000.0)

            val report = buildString {
                appendLine("=====================================================")
                appendLine("PIXELBOOST RUNTIME DIAGNOSTIC REPORT")
                appendLine("=====================================================")
                appendLine()
                appendLine("Timestamp: ${reportData.timestamp}")
                appendLine("Device: ${reportData.deviceManufacturer} ${reportData.deviceModel}")
                appendLine("Android: ${reportData.androidVersion}")
                appendLine("ABI: ${reportData.abi}")
                appendLine()
                appendLine("-----------------------------------------------------")
                appendLine("IMAGE")
                appendLine("-----------------------------------------------------")
                appendLine()
                appendLine("Input: ${reportData.inputWidth}x${reportData.inputHeight}")
                appendLine("Output: ${reportData.outputWidth}x${reportData.outputHeight}")
                appendLine("Scale: ${reportData.scaleFactor}x")
                appendLine("Tile: ${reportData.tileSize}x${reportData.tileSize}")
                appendLine("Padding: ${reportData.tilePadding} px")
                appendLine("Total Tiles: ${reportData.totalTiles}")
                appendLine()
                appendLine("-----------------------------------------------------")
                appendLine("MODEL")
                appendLine("-----------------------------------------------------")
                appendLine()
                appendLine("MNN Model: ${reportData.mnnModelName}")
                appendLine("ONNX Model: ${reportData.onnxModelName}")
                appendLine("Model Actually Used: ${reportData.modelActuallyUsed}")
                appendLine()
                appendLine("-----------------------------------------------------")
                appendLine("BACKEND SELECTION")
                appendLine("-----------------------------------------------------")
                appendLine()
                appendLine("Primary Backend:")
                appendLine("MNN Vulkan")
                appendLine()
                appendLine("Fallback 1:")
                appendLine("MNN CPU")
                appendLine()
                appendLine("Fallback 2:")
                appendLine("ONNX Runtime / XNNPACK")
                appendLine()
                appendLine("-----------------------------------------------------")
                appendLine("MNN VULKAN")
                appendLine("-----------------------------------------------------")
                appendLine()
                appendLine("Attempted: ${if (reportData.vulkanAttempted) "YES" else "NO"}")
                appendLine("Session Created: ${if (reportData.vulkanSessionCreated) "YES" else "NO"}")
                appendLine("Actual Backend: ${reportData.vulkanActualBackend}")
                appendLine("Backend Verification: ${reportData.vulkanVerification}")
                appendLine("Executed: ${if (reportData.vulkanExecuted) "YES" else "NO"}")
                appendLine("Completed: ${if (reportData.vulkanCompleted) "YES" else "NO"}")
                appendLine("Return Code: ${reportData.vulkanReturnCode?.toString() ?: "N/A"}")
                appendLine("Error: ${reportData.vulkanError}")
                appendLine("Inference Duration: ${if (reportData.vulkanExecuted) formatSec(reportData.vulkanInferenceNanos) else "N/A"}")
                appendLine("Average Tile: ${if (reportData.vulkanExecuted && reportData.vulkanTilesCompleted > 0) formatMs(reportData.vulkanInferenceNanos / reportData.vulkanTilesCompleted) else "N/A"}")
                appendLine("Min Tile: ${if (reportData.vulkanExecuted && reportData.vulkanMinTileNanos > 0) formatMs(reportData.vulkanMinTileNanos) else "N/A"}")
                appendLine("Max Tile: ${if (reportData.vulkanExecuted && reportData.vulkanMaxTileNanos > 0) formatMs(reportData.vulkanMaxTileNanos) else "N/A"}")
                appendLine()
                appendLine("-----------------------------------------------------")
                appendLine("MNN CPU")
                appendLine("-----------------------------------------------------")
                appendLine()
                appendLine("Attempted: ${if (reportData.mnnCpuAttempted) "YES" else "NO"}")
                appendLine("Executed: ${if (reportData.mnnCpuExecuted) "YES" else "NO"}")
                appendLine("CPU Threads: ${if (reportData.mnnCpuAttempted) reportData.mnnCpuThreads.toString() else "N/A"}")
                appendLine("Completed: ${if (reportData.mnnCpuCompleted) "YES" else "NO"}")
                appendLine("Return Code: ${reportData.mnnCpuReturnCode?.toString() ?: "N/A"}")
                appendLine("Error: ${reportData.mnnCpuError}")
                appendLine("Inference Duration: ${if (reportData.mnnCpuExecuted) formatSec(reportData.mnnCpuInferenceNanos) else "N/A"}")
                appendLine("Average Tile: ${if (reportData.mnnCpuExecuted && reportData.mnnCpuTilesCompleted > 0) formatMs(reportData.mnnCpuInferenceNanos / reportData.mnnCpuTilesCompleted) else "N/A"}")
                appendLine("Min Tile: ${if (reportData.mnnCpuExecuted && reportData.mnnCpuMinTileNanos > 0) formatMs(reportData.mnnCpuMinTileNanos) else "N/A"}")
                appendLine("Max Tile: ${if (reportData.mnnCpuExecuted && reportData.mnnCpuMaxTileNanos > 0) formatMs(reportData.mnnCpuMaxTileNanos) else "N/A"}")
                appendLine()
                appendLine("-----------------------------------------------------")
                appendLine("ONNX / XNNPACK")
                appendLine("-----------------------------------------------------")
                appendLine()
                appendLine("Attempted: ${if (reportData.onnxAttempted) "YES" else "NO"}")
                appendLine("Executed: ${if (reportData.onnxExecuted) "YES" else "NO"}")
                appendLine("XNNPACK Attempted: ${if (reportData.onnxAttempted) (if (reportData.xnnpackAttempted) "YES" else "NO") else "N/A"}")
                appendLine("XNNPACK Enabled: ${if (reportData.onnxAttempted) (if (reportData.xnnpackEnabled) "YES" else "NO") else "N/A"}")
                appendLine("Execution Provider: ${if (reportData.onnxAttempted) reportData.onnxExecutionProvider else "N/A"}")
                appendLine("Return/Error: ${if (reportData.onnxAttempted) reportData.onnxReturnOrError else (reportData.onnxNotAttemptedReason ?: "NOT_ATTEMPTED")}")
                appendLine("Inference Duration: ${if (reportData.onnxExecuted) formatSec(reportData.onnxInferenceNanos) else "N/A"}")
                appendLine()
                appendLine("-----------------------------------------------------")
                appendLine("BACKEND TIMELINE")
                appendLine("-----------------------------------------------------")
                appendLine()
                if (reportData.timelineEvents.isNotEmpty()) {
                    reportData.timelineEvents.forEach { event ->
                        appendLine(event)
                    }
                } else {
                    appendLine("No events recorded")
                }
                appendLine()
                appendLine("-----------------------------------------------------")
                appendLine("FINAL RESULT")
                appendLine("-----------------------------------------------------")
                appendLine()
                appendLine("Final Backend: ${reportData.finalBackend}")
                appendLine("Execution Confirmed: ${if (reportData.executionConfirmed) "YES" else "NO"}")
                appendLine("Output Generated: ${if (reportData.outputGenerated) "YES" else "NO"}")
                appendLine("Output Resolution: ${if (reportData.outputGenerated) "${reportData.outputWidth}x${reportData.outputHeight}" else "N/A"}")
                appendLine()
                appendLine("-----------------------------------------------------")
                appendLine("PERFORMANCE")
                appendLine("-----------------------------------------------------")
                appendLine()
                appendLine("Total Processing: ${formatSec(reportData.totalProcessingNanos)}")
                appendLine("Image Preparation: ${formatMs(reportData.imagePrepNanos)}")
                appendLine("Inference: ${formatSec(reportData.inferenceNanos)} (${reportData.finalBackend})")
                appendLine("Postprocessing: ${formatMs(reportData.postprocessingNanos)}")
                appendLine("Stitching: ${formatMs(reportData.stitchingNanos)}")
                appendLine("PNG Encoding: ${formatSec(reportData.pngCompressionNanos)}")
                appendLine("History Database: ${formatMs(reportData.dbInsertionNanos)}")
                appendLine()
                appendLine("=====================================================")
                appendLine("END OF REPORT")
                appendLine("=====================================================")
                appendLine()
            }

            val diagDir = java.io.File(context.filesDir, "diagnostics")
            if (!diagDir.exists()) {
                diagDir.mkdirs()
            }
            val benchmarkFile = java.io.File(diagDir, "PixelBoost_Benchmark.txt")
            benchmarkFile.appendText(report)
            android.util.Log.i("PIXELBOOST_PERF", "Benchmark appended to: ${benchmarkFile.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("PIXELBOOST_PERF", "Failed writing benchmark report", e)
        }
    }

    
    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            db.historyDao().clearHistory()
            val historyDir = java.io.File(getApplication<Application>().filesDir, "enhanced_history")
            if (historyDir.exists()) {
                historyDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("PixelBoost_")) {
                        file.delete()
                    }
                }
            }
            val cacheDir = getApplication<Application>().cacheDir
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("PixelBoost_")) {
                    file.delete()
                }
            }
        }
    }

    fun shareResult(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentItem = _batchResults.value.getOrNull(_selectedResultIndex.value)
            val file = if (currentItem != null) {
                java.io.File(Uri.parse(currentItem.enhancedUri).path ?: "")
            } else {
                val historyList = history.first()
                val latest = historyList.firstOrNull() ?: return@launch
                java.io.File(Uri.parse(latest.enhancedUri).path ?: "")
            }
            if (!file.exists()) return@launch
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share Image").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun getExportQuality(formatIndex: Int, qualityIndex: Int): Int {
        return when (formatIndex) {
            0 -> if (qualityIndex == 0) 90 else 100 // JPEG: High -> 90, Maximum -> 100
            2 -> if (qualityIndex == 0) 85 else 100 // WEBP: High -> 85, Maximum -> 100
            else -> 100 // PNG: Lossless
        }
    }

    private suspend fun exportBitmapToMediaStore(
        context: Context,
        bitmap: Bitmap,
        formatIdx: Int,
        qualityIdx: Int
    ): Result<Uri> = withContext(Dispatchers.IO) {
        var insertedUri: Uri? = null
        try {
            val ext = when (formatIdx) {
                0 -> ".jpg"
                2 -> ".webp"
                else -> ".png"
            }
            val mime = when (formatIdx) {
                0 -> "image/jpeg"
                2 -> "image/webp"
                else -> "image/png"
            }
            val compressFormat = when (formatIdx) {
                0 -> Bitmap.CompressFormat.JPEG
                2 -> @Suppress("DEPRECATION") if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                else -> Bitmap.CompressFormat.PNG
            }
            val quality = getExportQuality(formatIdx, qualityIdx)

            val filename = "PixelBoost_${System.currentTimeMillis()}$ext"
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/PixelBoost")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))
            insertedUri = uri

            val writeSucceeded = context.contentResolver.openOutputStream(uri)?.use { out ->
                val compressed = bitmap.compress(compressFormat, quality, out)
                out.flush()
                compressed
            } ?: false

            if (!writeSucceeded) {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {}
                return@withContext Result.failure(Exception("Failed to write image"))
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val updateValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, updateValues, null, null)
            }

            Result.success(uri)
        } catch (c: CancellationException) {
            insertedUri?.let { uri ->
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {}
            }
            throw c
        } catch (e: Exception) {
            android.util.Log.e("PIXELBOOST", "Error saving to gallery", e)
            insertedUri?.let { uri ->
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {}
            }
            Result.failure(e)
        }
    }

    fun saveToGallery(context: Context) {
        val currentIndex = _selectedResultIndex.value
        if (_itemSaveStatuses.value[currentIndex] == GallerySaveStatus.Saved) return
        if (_saveState.value is SaveState.Saving) return

        val currentItem = _batchResults.value.getOrNull(currentIndex)
        val bitmap = _resultBitmap.value ?: run {
            try {
                val path = currentItem?.enhancedUri?.let { Uri.parse(it).path }
                if (path != null) BitmapFactory.decodeFile(path) else null
            } catch (e: Exception) {
                null
            }
        }
        if (bitmap == null) {
            _saveState.value = SaveState.Error(currentIndex, "Bitmap not available")
            return
        }
        val formatIdx = _formatIndex.value
        val qualityIdx = _qualityIndex.value
        _saveState.value = SaveState.Saving
        _itemSaveStatuses.value = _itemSaveStatuses.value + (currentIndex to GallerySaveStatus.Saving)

        viewModelScope.launch(Dispatchers.IO) {
            val exportResult = exportBitmapToMediaStore(context, bitmap, formatIdx, qualityIdx)
            if (exportResult.isSuccess) {
                _itemSaveStatuses.value = _itemSaveStatuses.value + (currentIndex to GallerySaveStatus.Saved)
                _saveState.value = SaveState.Success(currentIndex)
            } else {
                val ex = exportResult.exceptionOrNull()
                _itemSaveStatuses.value = _itemSaveStatuses.value + (currentIndex to GallerySaveStatus.Failed)
                _saveState.value = SaveState.Error(currentIndex, ex?.localizedMessage ?: "Save failed")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelProcessing()
        inferenceEngine.close()
    }
}

enum class GallerySaveStatus {
    NotSaved,
    Saving,
    Saved,
    Failed
}

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data class Success(val itemIndex: Int) : SaveState
    data class Error(val itemIndex: Int, val message: String) : SaveState
}
