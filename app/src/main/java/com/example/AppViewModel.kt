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

enum class BatchItemStatus { Waiting, Processing, Success, Failed, Cancelled }
data class BatchItem(val originalUri: String, val status: BatchItemStatus = BatchItemStatus.Waiting, val processedItem: ProcessedItem? = null)
enum class ResultOrigin { Processing, History }

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val inferenceEngine = InferenceEngine(application)

    private val prefs = application.getSharedPreferences("pixelboost_settings", Context.MODE_PRIVATE)

    private val _themeIndex = MutableStateFlow(prefs.getInt("themeIndex", 0))
    val themeIndex = _themeIndex.asStateFlow()
    fun setThemeIndex(index: Int) {
        _themeIndex.value = index
        prefs.edit().putInt("themeIndex", index).apply()
    }
    fun setTheme(index: Int) {
        setThemeIndex(index)
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

    private val _manualOverride = MutableStateFlow(prefs.getString("manualBackendOverride", "AUTO") ?: "AUTO")
    val manualOverride = _manualOverride.asStateFlow()

    private val _calibratedWinner = MutableStateFlow(prefs.getString("calibratedWinner", "MNN_CPU") ?: "MNN_CPU")
    val calibratedWinner = _calibratedWinner.asStateFlow()

    private val _activeBackend = MutableStateFlow(
        if (_manualOverride.value != "AUTO") _manualOverride.value else _calibratedWinner.value
    )
    val activeBackend = _activeBackend.asStateFlow()

    private val _calibrationSummary = MutableStateFlow<CalibrationSummary?>(null)
    val calibrationSummary = _calibrationSummary.asStateFlow()

    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating = _isCalibrating.asStateFlow()

    fun setManualOverride(overrideChoice: String) {
        _manualOverride.value = overrideChoice
        prefs.edit().putString("manualBackendOverride", overrideChoice).apply()
        val effective = if (overrideChoice != "AUTO") overrideChoice else _calibratedWinner.value
        _activeBackend.value = effective
        inferenceEngine.activeBackendOverride = effective
    }

    private fun getConfigurationFingerprint(scale: Int): String {
        val deviceModel = android.os.Build.MODEL ?: "unknown"
        val sdkInt = android.os.Build.VERSION.SDK_INT
        val mnnModelFile = inferenceEngine.getModelFile("RealESRGAN_x4plus_anime_6B-x4.mnn")
        val size = mnnModelFile?.length() ?: 0L
        val mod = mnnModelFile?.lastModified() ?: 0L
        val hw = inferenceEngine.queryHardwareProfile()
        val tileSize = hw.recommendedTileSizeCpu
        val pad = 8
        val cpuThreads = hw.recommendedCpuThreads
        val versionCode = 1
        return "pb_v2_${deviceModel}_api${sdkInt}_scale${scale}_tile${tileSize}_pad${pad}_threads${cpuThreads}_size${size}_mod${mod}_v${versionCode}"
    }

    fun runHardwareCalibration(forceRefresh: Boolean = false, onComplete: ((CalibrationSummary) -> Unit)? = null) {
        viewModelScope.launch {
            _isCalibrating.value = true
            try {
                val scale = if (_scaleIndex.value == 0) 2 else 4
                val fingerprint = getConfigurationFingerprint(scale)
                val hw = inferenceEngine.queryHardwareProfile()
                val summary = inferenceEngine.runHardwareCalibration(
                    scale = scale,
                    tileSize = hw.recommendedTileSizeCpu,
                    pad = 8,
                    forcedFingerprint = fingerprint
                )
                _calibrationSummary.value = summary
                _calibratedWinner.value = summary.winningBackend
                prefs.edit()
                    .putString("calibratedWinner", summary.winningBackend)
                    .putString("cachedFingerprint", fingerprint)
                    .apply()

                val effective = if (_manualOverride.value != "AUTO") _manualOverride.value else summary.winningBackend
                _activeBackend.value = effective
                inferenceEngine.activeBackendOverride = effective

                val diagReport = BackendDiagnosticReport(
                    timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()),
                    tileSize = hw.recommendedTileSizeCpu,
                    tilePadding = 8,
                    finalBackend = summary.activeBackendUsed,
                    executionConfirmed = true
                )
                writeBenchmarkFile(getApplication(), diagReport)

                onComplete?.invoke(summary)
            } catch (e: Exception) {
                Log.e("AppViewModel", "Calibration failed: ${e.message}", e)
            } finally {
                _isCalibrating.value = false
            }
        }
    }

    fun checkAndRunCalibrationIfNeeded() {
        val scale = if (_scaleIndex.value == 0) 2 else 4
        val fingerprint = getConfigurationFingerprint(scale)
        val cachedFingerprint = prefs.getString("cachedFingerprint", "")
        if (cachedFingerprint != fingerprint || !prefs.contains("calibratedWinner")) {
            runHardwareCalibration(forceRefresh = true)
        } else {
            val winner = prefs.getString("calibratedWinner", "MNN_CPU") ?: "MNN_CPU"
            _calibratedWinner.value = winner
            val effective = if (_manualOverride.value != "AUTO") _manualOverride.value else winner
            _activeBackend.value = effective
            inferenceEngine.activeBackendOverride = effective
        }
    }

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private fun getFreeStorageGb(): Double {
        return try {
            val stat = android.os.StatFs(getApplication<Application>().filesDir.absolutePath)
            stat.availableBytes / (1024.0 * 1024.0 * 1024.0)
        } catch (e: Exception) {
            1.0
        }
    }

    private val _batchItems = MutableStateFlow<List<BatchItem>>(emptyList())
    val batchItems = _batchItems.asStateFlow()

    private val _resultOrigin = MutableStateFlow(ResultOrigin.Processing)
    val resultOrigin = _resultOrigin.asStateFlow()

    fun clearSelectedImages() {
        _selectedImages.value = emptyList()
        _batchItems.value = emptyList()
        _batchResults.value = emptyList()
        _resultBitmap.value = null
        _selectedResultIndex.value = 0
    }

    fun openHistoricalResult(entity: HistoryEntity, onReady: () -> Unit) {
        viewModelScope.launch {
            _resultOrigin.value = ResultOrigin.History
            _scaleIndex.value = if (entity.scale == 2 || entity.label.contains("2x", true) || entity.label.contains("2×", true)) 0 else 1
            val processed = ProcessedItem(
                originalUri = entity.originalUri,
                enhancedUri = entity.enhancedUri,
                width = if (entity.actualWidth > 0) entity.actualWidth else 0,
                height = if (entity.actualHeight > 0) entity.actualHeight else 0
            )
            _selectedImages.value = listOf(entity.originalUri)
            _batchResults.value = listOf(processed)
            _batchItems.value = listOf(BatchItem(entity.originalUri, BatchItemStatus.Success, processed))
            _selectedResultIndex.value = 0
            val bitmap = withContext(Dispatchers.IO) {
                loadBitmapFromUri(entity.enhancedUri)
            }
            _resultBitmap.value = bitmap
            onReady()
        }
    }


    private val db = AppDatabase.getDatabase(application)
    
    val history = db.historyDao().getAllHistory()
    val historyList = history

    init {
        viewModelScope.launch(Dispatchers.IO) {
            migrateExistingCacheHistory()
            checkAndRunCalibrationIfNeeded()
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
        if (index in _batchItems.value.indices) {
            _selectedResultIndex.value = index
            val item = _batchItems.value[index].processedItem ?: _batchResults.value.getOrNull(index)
            if (item != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    val bitmap = loadBitmapFromUri(item.enhancedUri)
                    _resultBitmap.value = bitmap
                }
            }
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
        _isProcessing.value = false
        _progress.value = 0f
        _resultBitmap.value = null
        _errorMessage.value = null
        _batchResults.value = emptyList()
        _selectedResultIndex.value = 0
        _batchItems.value = _batchItems.value.map { item ->
            if (item.status == BatchItemStatus.Processing || item.status == BatchItemStatus.Waiting) {
                item.copy(status = BatchItemStatus.Cancelled)
            } else {
                item
            }
        }
        UpscaleForegroundService.stop(getApplication())
    }

    fun setSelectedImages(urls: List<String>) {
        _selectedImages.value = urls
    }

    private var onBatchCompleteCallback: (() -> Unit)? = null

    fun startProcessing(onComplete: () -> Unit) {
        onBatchCompleteCallback = onComplete
        if (_isProcessing.value) {
            Log.d("PIXELBOOST", "startProcessing called while already processing, ignoring duplicate call.")
            return
        }

        if (_selectedImages.value.isEmpty()) {
            onComplete()
            return
        }

        if (getFreeStorageGb() < 0.1) {
            _errorMessage.value = "Insufficient storage space (at least 100 MB required)"
            return
        }

        val autoSaveEnabled = _saveEnhanced.value
        val exportFormatIdx = _formatIndex.value
        val exportQualityIdx = _qualityIndex.value
        val requestedScale = if (_scaleIndex.value == 0) 2 else 4

        val previousJob = processingJob
        cancelProcessing()

        _isProcessing.value = true
        _resultOrigin.value = ResultOrigin.Processing
        _batchItems.value = _selectedImages.value.map { BatchItem(it, BatchItemStatus.Waiting) }
        
        UpscaleForegroundService.onCancelRequested = {
            cancelProcessing()
        }

        val totalItems = _selectedImages.value.size
        UpscaleForegroundService.startOrUpdate(
            context = getApplication(),
            currentIndex = 1,
            totalCount = totalItems,
            progress = 0f
        )

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
                var lastNotifiedProgress = -1f
                var lastNotifiedIndex = -1
                for ((index, url) in _selectedImages.value.withIndex()) {
                    var bitmap: Bitmap? = null
                    var originalW = 0
                    var originalH = 0
                    try {
                        coroutineContext.ensureActive()
                        _currentProcessingIndex.value = index
                        _progress.value = 0f
                        _batchItems.value = _batchItems.value.toMutableList().also { list ->
                            if (index in list.indices) {
                                list[index] = list[index].copy(status = BatchItemStatus.Processing)
                            }
                        }
                        
                        if (index != lastNotifiedIndex) {
                            lastNotifiedIndex = index
                            lastNotifiedProgress = 0f
                            UpscaleForegroundService.startOrUpdate(
                                context = getApplication(),
                                currentIndex = index + 1,
                                totalCount = totalItems,
                                progress = 0f
                            )
                        }
                        
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
                            originalW = bitmap.width
                            originalH = bitmap.height
                            coroutineContext.ensureActive()
                            val finalResult = inferenceEngine.process(bitmap, requestedScale) { p ->
                                if (coroutineContext.isActive) {
                                    _progress.value = p
                                    if (p - lastNotifiedProgress >= 0.05f || p >= 0.99f) {
                                        lastNotifiedProgress = p
                                        UpscaleForegroundService.startOrUpdate(
                                            context = getApplication(),
                                            currentIndex = index + 1,
                                            totalCount = totalItems,
                                            progress = p
                                        )
                                    }
                                }
                            }
                            coroutineContext.ensureActive()
                            if (bitmap != finalResult) {
                                bitmap.recycle()
                                bitmap = null
                            }
                            
                            val isAiBackend = inferenceEngine.lastDiagnosticReport?.finalBackend?.startsWith("MNN") == true ||
                                inferenceEngine.lastDiagnosticReport?.finalBackend?.startsWith("ONNX") == true
                            val finalLabel = if (isAiBackend) {
                                if (requestedScale == 2) "2x AI Upscale" else "4x AI Upscale"
                            } else {
                                if (requestedScale == 2) "2x Enhanced" else "4x Enhanced"
                            }
                            
                            var compressionNanos = 0L
                            var dbInsertionNanos = 0L
                            var enhancedCachePath = ""
                            var persistentFileToExport: java.io.File? = null

                            // FORMAT & TRANSPARENCY HANDLING:
                            // When exportFormatIdx == 0 (JPEG) and finalResult.hasAlpha(), composite finalResult onto a white background
                            // before JPEG compression so transparent PNG/WEBP anime images don't get a black background.
                            val compositedBitmap = if (exportFormatIdx == 0 && finalResult.hasAlpha()) {
                                val opaque = Bitmap.createBitmap(finalResult.width, finalResult.height, Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(opaque)
                                canvas.drawColor(android.graphics.Color.WHITE)
                                canvas.drawBitmap(finalResult, 0f, 0f, null)
                                opaque
                            } else null
                            val bitmapToEncode = compositedBitmap ?: finalResult

                            try {
                                // Save to persistent filesDir for history respecting formatIndex and qualityIndex
                                withContext(Dispatchers.IO) {
                                    coroutineContext.ensureActive()
                                    val historyDir = java.io.File(getApplication<Application>().filesDir, "enhanced_history")
                                    if (!historyDir.exists()) {
                                        historyDir.mkdirs()
                                    }
                                    val ext = when (exportFormatIdx) {
                                        0 -> ".jpg"
                                        2 -> ".webp"
                                        else -> ".png"
                                    }
                                    val mime = when (exportFormatIdx) {
                                        0 -> "image/jpeg"
                                        2 -> "image/webp"
                                        else -> "image/png"
                                    }
                                    val compressFormat = when (exportFormatIdx) {
                                        0 -> Bitmap.CompressFormat.JPEG
                                        2 -> @Suppress("DEPRECATION") if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                                        else -> Bitmap.CompressFormat.PNG
                                    }
                                    val quality = getExportQuality(exportFormatIdx, exportQualityIdx)

                                    val persistentFile = java.io.File(historyDir, "PixelBoost_${System.currentTimeMillis()}_$index$ext")
                                    persistentFileToExport = persistentFile
                                    val tCompStart = SystemClock.elapsedRealtimeNanos()
                                    persistentFile.outputStream().use { out ->
                                        bitmapToEncode.compress(compressFormat, quality, out)
                                    }
                                    compressionNanos = SystemClock.elapsedRealtimeNanos() - tCompStart
                                    
                                    val tDbStart = SystemClock.elapsedRealtimeNanos()
                                    val entity = HistoryEntity(
                                        originalUri = url,
                                        enhancedUri = "file://${persistentFile.absolutePath}",
                                        timestamp = System.currentTimeMillis(),
                                        sizeBytes = persistentFile.length(),
                                        label = finalLabel,
                                        actualWidth = finalResult.width,
                                        actualHeight = finalResult.height,
                                        mimeType = mime,
                                        scale = requestedScale,
                                        originalWidth = originalW,
                                        originalHeight = originalH
                                    )
                                    db.historyDao().insert(entity)
                                    dbInsertionNanos = SystemClock.elapsedRealtimeNanos() - tDbStart
                                    enhancedCachePath = "file://${persistentFile.absolutePath}"
                                }
                            } finally {
                                if (compositedBitmap != null && !compositedBitmap.isRecycled) {
                                    compositedBitmap.recycle()
                                }
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
                            _batchItems.value = _batchItems.value.toMutableList().also { list ->
                                if (index in list.indices) {
                                    list[index] = list[index].copy(status = BatchItemStatus.Success, processedItem = item)
                                }
                            }

                            // Single-pass zero-copy auto save directly from persistent file
                            if (autoSaveEnabled && persistentFileToExport != null) {
                                coroutineContext.ensureActive()
                                try {
                                    val exportResult = exportFileToMediaStore(
                                        context = getApplication(),
                                        file = persistentFileToExport!!,
                                        formatIdx = exportFormatIdx
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

                            if (_resultBitmap.value == null) {
                                _resultBitmap.value = finalResult
                                _selectedResultIndex.value = index
                            } else if (finalResult != _resultBitmap.value && !finalResult.isRecycled) {
                                finalResult.recycle()
                            }
                            
                            val tTotalEnd = SystemClock.elapsedRealtimeNanos()
                            val totalProcessingNanos = tTotalEnd - tTotalStart

                            val diagnosticReport = inferenceEngine.lastDiagnosticReport?.copy(
                                outputWidth = finalResult.width,
                                outputHeight = finalResult.height,
                                totalProcessingNanos = totalProcessingNanos,
                                imagePrepNanos = decodingNanos + exifRotationNanos,
                                pngCompressionNanos = compressionNanos,
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
                        _batchItems.value = _batchItems.value.toMutableList().also { list ->
                            if (index in list.indices) {
                                list[index] = list[index].copy(status = BatchItemStatus.Cancelled)
                            }
                        }
                        throw c
                    } catch (t: OutOfMemoryError) {
                        Log.e("PIXELBOOST", "Image too large for this device's memory at index $index", t)
                        try {
                            bitmap?.recycle()
                        } catch (_: Throwable) {}
                        _batchItems.value = _batchItems.value.toMutableList().also { list ->
                            if (index in list.indices) {
                                list[index] = list[index].copy(status = BatchItemStatus.Failed)
                            }
                        }
                        _itemSaveStatuses.value = _itemSaveStatuses.value + (index to GallerySaveStatus.Failed)
                        _errorMessage.value = "Image too large for this device's memory"
                        continue
                    } catch (e: Throwable) {
                        Log.e("PIXELBOOST", "Error processing image at index $index", e)
                        try {
                            bitmap?.recycle()
                        } catch (_: Throwable) {}
                        _batchItems.value = _batchItems.value.toMutableList().also { list ->
                            if (index in list.indices) {
                                list[index] = list[index].copy(status = BatchItemStatus.Failed)
                            }
                        }
                        _itemSaveStatuses.value = _itemSaveStatuses.value + (index to GallerySaveStatus.Failed)
                        _errorMessage.value = "Error processing image: ${e.message}"
                        continue
                    }
                }
                val firstSuccessIdx = _batchItems.value.indexOfFirst { it.status == BatchItemStatus.Success }
                if (firstSuccessIdx != -1) {
                    _selectedResultIndex.value = firstSuccessIdx
                    if (_resultBitmap.value == null) {
                        val uriToLoad = _batchItems.value[firstSuccessIdx].processedItem?.enhancedUri 
                            ?: _batchResults.value.getOrNull(firstSuccessIdx)?.enhancedUri
                        if (uriToLoad != null) {
                            _resultBitmap.value = withContext(Dispatchers.IO) {
                                loadBitmapFromUri(uriToLoad)
                            }
                        }
                    }
                }
                val successCount = _batchItems.value.count { it.status == BatchItemStatus.Success }
                UpscaleForegroundService.complete(getApplication(), successCount.coerceAtLeast(1))
                if (coroutineContext.isActive) {
                    onBatchCompleteCallback?.invoke() ?: onComplete()
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
                _isProcessing.value = false
                UpscaleForegroundService.stop(getApplication())
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
        Log.i(TAG, "Session Init: ${formatMs(reportData.sessionInitNanos)}")
        Log.i(TAG, "Backend Inference: ${formatSec(reportData.inferenceNanos)}")
        Log.i(TAG, "Postprocessing: ${formatMs(reportData.postprocessingNanos)}")
        Log.i(TAG, "Stitching: ${formatMs(reportData.stitchingNanos)}")
        Log.i(TAG, "Encoding: ${formatSec(reportData.pngCompressionNanos)}")
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
                appendLine("BACKEND SELECTION & AUTO-TUNING")
                appendLine("-----------------------------------------------------")
                appendLine()
                appendLine("Active Backend Used: ${_activeBackend.value}")
                appendLine("Calibrated Auto Winner: ${_calibratedWinner.value}")
                appendLine("Manual Override Status: ${if (_manualOverride.value != "AUTO") "ACTIVE (${_manualOverride.value})" else "AUTO"}")
                appendLine()
                val calib = inferenceEngine.lastCalibrationSummary
                if (calib != null) {
                    appendLine("Configuration Fingerprint: ${calib.configurationFingerprint}")
                    appendLine("Calibration Wall-Clock Duration: ${formatMs(calib.calibrationWallClockDurationNanos)}")
                    appendLine()
                    appendLine("Stage 1 Screening Matrix (${reportData.tileSize + reportData.tilePadding * 2}x${reportData.tileSize + reportData.tilePadding * 2} padded tile):")
                    for (res in calib.results) {
                        appendLine("  - Backend: ${res.backendName}")
                        appendLine("    Configured & Available: ${if (res.isConfiguredAndAvailable) "YES" else "NO (${res.unavailableReason})"}")
                        if (res.isConfiguredAndAvailable) {
                            appendLine("    Warm-Up: ${formatMs(res.warmUpLatencyNanos)}")
                            appendLine("    Trials: ${res.trialLatenciesNanos.joinToString { formatMs(it) }}")
                            appendLine("    Median Latency: ${formatMs(res.medianLatencyNanos)}")
                            appendLine("    Dimensions Verified: ${if (res.outputDimensionsVerified) "YES" else "NO"}")
                            appendLine("    Pixel Entropy Valid: ${if (res.pixelEntropyValid) "YES" else "NO"}")
                        }
                    }
                    appendLine()
                    appendLine("Stage 2 Multi-Candidate Validation (4-tile grid):")
                    for (res in calib.results.filter { it.isConfiguredAndAvailable }) {
                        appendLine("  - Backend: ${res.backendName}")
                        appendLine("    Validation Duration: ${formatMs(res.multiTileValidationNanos)}")
                        appendLine("    Max Seam Delta: ${res.seamDiscontinuityMaxDelta?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "N/A"}")
                        appendLine("    Mean Absolute Error (MAE): ${res.meanAbsoluteErrorVsRef?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "N/A"}")
                        appendLine("    Validation Passed: ${if (res.validationPassed) "YES" else "NO"}")
                        appendLine("    Winner Designated: ${if (res.isSelectedWinner) "YES" else "NO"}")
                    }
                    appendLine()
                    appendLine("[DOCUMENTED CONSTRAINT]: Large-image exact resolution fidelity is constrained by on-device bitmap heap limits. An active OOM fallback in InferenceEngine may downscale buffers if heap space is exhausted. Refactoring heap budgeting is scheduled for Phase 2.")
                    appendLine()
                }
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
                appendLine("PERFORMANCE BREAKDOWN")
                appendLine("-----------------------------------------------------")
                appendLine()
                appendLine("Total Processing: ${formatSec(reportData.totalProcessingNanos)}")
                appendLine("Image Preparation: ${formatMs(reportData.imagePrepNanos)}")
                appendLine("Session Init: ${formatMs(reportData.sessionInitNanos)}")
                appendLine("Inference: ${formatSec(reportData.inferenceNanos)} (${reportData.finalBackend})")
                appendLine("Postprocessing: ${formatMs(reportData.postprocessingNanos)}")
                appendLine("Stitching: ${formatMs(reportData.stitchingNanos)}")
                appendLine("Encoding: ${formatSec(reportData.pngCompressionNanos)}")
                appendLine("History Database: ${formatMs(reportData.dbInsertionNanos)}")
                appendLine()
                appendLine("=====================================================")
                appendLine("END OF REPORT")
                appendLine("=====================================================")
                appendLine()
            }

            // 1. Write report directly into the user's Downloads/PixelBoost folder
            try {
                val downloadsDir = java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                    "PixelBoost"
                )
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val publicBenchmarkFile = java.io.File(downloadsDir, "PixelBoost_Benchmark.txt")
                publicBenchmarkFile.appendText(report)
                android.util.Log.i("PIXELBOOST_PERF", "Benchmark appended to Downloads/PixelBoost: ${publicBenchmarkFile.absolutePath}")
            } catch (e: Exception) {
                android.util.Log.w("PIXELBOOST_PERF", "Direct file write to Downloads/PixelBoost failed: ${e.message}")
            }

            // 2. Also write via MediaStore Downloads on Android 10+ (Q+) for scoped storage compliance
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    val resolver = context.contentResolver
                    val projection = arrayOf(android.provider.MediaStore.MediaColumns._ID)
                    val selection = "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                    val selectionArgs = arrayOf("PixelBoost_Benchmark.txt", "%${android.os.Environment.DIRECTORY_DOWNLOADS}/PixelBoost%")
                    val cursor = resolver.query(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null)
                    val existingUri = cursor?.use {
                        if (it.moveToFirst()) {
                            val id = it.getLong(it.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID))
                            android.content.ContentUris.withAppendedId(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                        } else null
                    }
                    val targetUri = existingUri ?: run {
                        val cv = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "PixelBoost_Benchmark.txt")
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DOWNLOADS}/PixelBoost")
                        }
                        resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                    }
                    targetUri?.let { uri ->
                        resolver.openOutputStream(uri, "wa")?.use { out ->
                            out.write(report.toByteArray())
                            out.flush()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("PIXELBOOST_PERF", "MediaStore Downloads write failed: ${e.message}")
                }
            }

            // 3. Always write to internal filesDir diagnostics as well
            val diagDir = java.io.File(context.filesDir, "diagnostics")
            if (!diagDir.exists()) {
                diagDir.mkdirs()
            }
            val benchmarkFile = java.io.File(diagDir, "PixelBoost_Benchmark.txt")
            benchmarkFile.appendText(report)
            android.util.Log.i("PIXELBOOST_PERF", "Benchmark appended to internal diagnostics: ${benchmarkFile.absolutePath}")
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
            val ext = file.extension.lowercase()
            val mime = when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                else -> "image/png"
            }
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mime
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

    private suspend fun exportFileToMediaStore(
        context: Context,
        file: java.io.File,
        formatIdx: Int
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
                file.inputStream().use { input ->
                    input.copyTo(out)
                }
                out.flush()
                true
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
            android.util.Log.e("PIXELBOOST", "Error saving file to gallery", e)
            insertedUri?.let { uri ->
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {}
            }
            Result.failure(e)
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

        val formatIdx = _formatIndex.value
        val qualityIdx = _qualityIndex.value
        _saveState.value = SaveState.Saving
        _itemSaveStatuses.value = _itemSaveStatuses.value + (currentIndex to GallerySaveStatus.Saving)

        viewModelScope.launch(Dispatchers.IO) {
            val currentItem = _batchItems.value.getOrNull(currentIndex)?.processedItem ?: _batchResults.value.getOrNull(currentIndex)
            val persistentPath = currentItem?.enhancedUri?.let { Uri.parse(it).path }
            val persistentFile = if (persistentPath != null) java.io.File(persistentPath) else null

            val exportResult = if (persistentFile != null && persistentFile.exists() && persistentFile.length() > 0) {
                // Single-pass zero-copy direct stream of already-compressed file
                exportFileToMediaStore(context, persistentFile, formatIdx)
            } else {
                val bitmap = _resultBitmap.value ?: run {
                    try {
                        if (persistentFile != null && persistentFile.exists()) BitmapFactory.decodeFile(persistentFile.absolutePath) else null
                    } catch (e: Exception) {
                        null
                    }
                }
                if (bitmap == null) {
                    _itemSaveStatuses.value = _itemSaveStatuses.value + (currentIndex to GallerySaveStatus.Failed)
                    _saveState.value = SaveState.Error(currentIndex, "Bitmap not available")
                    return@launch
                }
                exportBitmapToMediaStore(context, bitmap, formatIdx, qualityIdx)
            }

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

    fun deleteHistoryItem(entity: HistoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.historyDao().deleteById(entity.id)
                val path = Uri.parse(entity.enhancedUri).path
                if (path != null) {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e("PIXELBOOST", "Failed to delete history item ${entity.id}", e)
            }
        }
    }

    fun shareHistoryItem(context: Context, entity: HistoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = Uri.parse(entity.enhancedUri).path ?: return@launch
                val file = java.io.File(path)
                if (!file.exists()) return@launch
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = entity.mimeType.ifBlank { "image/png" }
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share Image").apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                Log.e("PIXELBOOST", "Failed to share history item ${entity.id}", e)
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
