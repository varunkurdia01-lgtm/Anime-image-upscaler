package com.example

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Temporary developer-only diagnostic Activity for running MNN CPU vs Vulkan
 * benchmark directly on physical Android hardware.
 *
 * This Activity is completely isolated from production pipelines and does not
 * affect MainActivity, AppViewModel, or InferenceEngine.
 */
class MNNBenchmarkActivity : Activity() {

    private val activityScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var statusText: TextView
    private lateinit var reportText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var copyButton: Button
    private lateinit var shareButton: Button
    private lateinit var rerunButton: Button

    private var currentReport: String = ""

    companion object {
        private const val TAG = "PHYSICAL_MNN_VULKAN_TEST"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(32, 48, 32, 32)
        }

        val headerText = TextView(this).apply {
            text = "MNN CPU vs Vulkan Benchmark"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 16)
        }
        rootLayout.addView(headerText)

        statusText = TextView(this).apply {
            text = "Initializing benchmark..."
            setTextColor(Color.parseColor("#BB86FC"))
            textSize = 14f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 16)
        }
        rootLayout.addView(statusText)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.VISIBLE
        }
        rootLayout.addView(progressBar)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 16, 0, 16)
        }

        copyButton = Button(this).apply {
            text = "Copy Report"
            isEnabled = false
            setOnClickListener {
                if (currentReport.isNotBlank()) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("MNN Vulkan Benchmark Report", currentReport)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this@MNNBenchmarkActivity, "Report copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        buttonRow.addView(copyButton)

        val spacer1 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 1)
        }
        buttonRow.addView(spacer1)

        shareButton = Button(this).apply {
            text = "Share Report"
            isEnabled = false
            setOnClickListener {
                if (currentReport.isNotBlank()) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "PixelBoost MNN Vulkan Benchmark")
                        putExtra(Intent.EXTRA_TEXT, currentReport)
                    }
                    startActivity(Intent.createChooser(intent, "Share Benchmark Report"))
                }
            }
        }
        buttonRow.addView(shareButton)

        val spacer2 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 1)
        }
        buttonRow.addView(spacer2)

        rerunButton = Button(this).apply {
            text = "Rerun"
            isEnabled = false
            setOnClickListener {
                startBenchmark()
            }
        }
        buttonRow.addView(rerunButton)

        rootLayout.addView(buttonRow)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(24, 24, 24, 24)
        }

        reportText = TextView(this).apply {
            text = "Starting benchmark on device..."
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scrollView.addView(reportText)
        rootLayout.addView(scrollView)

        setContentView(rootLayout)

        startBenchmark()
    }

    private fun startBenchmark() {
        progressBar.visibility = View.VISIBLE
        copyButton.isEnabled = false
        shareButton.isEnabled = false
        rerunButton.isEnabled = false
        statusText.text = "Running MNN CPU vs Vulkan on device... Please wait (10-30s)"
        reportText.text = "Preparing model and executing benchmark...\nModel: RealESRGAN_x4plus_anime_6B-x4.mnn\nInput: 192x192 RGB -> 768x768 RGB"

        activityScope.launch {
            val report = withContext(Dispatchers.Default) {
                try {
                    MNNBenchmark.runVulkanBenchmark(applicationContext)
                } catch (e: Exception) {
                    "PHYSICAL_MNN_VULKAN_TEST\n\nERROR: Benchmark threw exception:\n${e.stackTraceToString()}"
                }
            }

            currentReport = report
            Log.i(TAG, "\n========== BENCHMARK REPORT START ==========\n$report\n========== BENCHMARK REPORT END ==========")

            progressBar.visibility = View.GONE
            copyButton.isEnabled = true
            shareButton.isEnabled = true
            rerunButton.isEnabled = true
            statusText.text = "Benchmark Finished!"
            reportText.text = report
        }
    }
}
