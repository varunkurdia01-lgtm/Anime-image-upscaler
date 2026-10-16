package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.example.AppViewModel
import com.example.BuildConfig
import com.example.data.HistoryEntity
import com.example.ui.components.ceramicInnerShadow
import com.example.ui.components.ceramicShadow
import com.example.ui.components.tactileClick
import com.example.ui.theme.*
import java.util.Locale

private fun formatFileSize(bytes: Long): String {
    return if (bytes < 1024 * 1024) {
        "${(bytes / 1024).coerceAtLeast(1)} KB"
    } else {
        String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

@Composable
fun SettingsScreen(viewModel: AppViewModel, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val themeIndex by viewModel.themeIndex.collectAsState()
    val historyItems by viewModel.historyList.collectAsState(initial = emptyList())
    val scaleIndex by viewModel.scaleIndex.collectAsState()
    val formatIndex by viewModel.formatIndex.collectAsState()
    val qualityIndex by viewModel.qualityIndex.collectAsState()
    val saveEnhanced by viewModel.saveEnhanced.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    val cacheSizeBytes = remember(historyItems) {
        val historyBytes = historyItems.sumOf { it.sizeBytes }
        var cacheBytes = 0L
        try {
            context.cacheDir?.listFiles()?.forEach { file ->
                cacheBytes += file.length()
            }
        } catch (_: Exception) {}
        historyBytes + cacheBytes
    }

    val appVersion = remember {
        try {
            "v" + BuildConfig.VERSION_NAME.ifBlank { "1.0.0" }
        } catch (_: Throwable) {
            "v1.0.0"
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Text(
                    text = "Choose Theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    val themeOptions = listOf("System default", "Light", "Dark")
                    themeOptions.forEachIndexed { index, option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setTheme(index)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeIndex == index,
                                onClick = {
                                    viewModel.setTheme(index)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge,
                                color = OnSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = {
                Text(
                    text = "Clear Cache & History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will permanently delete all enhanced photos and temporary cache files (${formatFileSize(cacheSizeBytes)}). This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        viewModel.clearHistory()
                        Toast.makeText(context, "Cache cleared successfully", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Clear", color = ErrorColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                Text(
                    text = "Privacy Policy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "PixelBoost is 100% private and on-device:\n\n" +
                        "• Zero Cloud Uploads: All neural super-resolution and image processing models run strictly on your device's hardware.\n" +
                        "• Zero Analytics: No tracking, telemetry, or user analytics are collected or transmitted.\n" +
                        "• Full Data Ownership: Photos and processing history are stored locally in your app's private sandboxed storage.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Understood")
                }
            }
        )
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = {
                Text(
                    text = "Terms of Service",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "PixelBoost Terms of Service:\n\n" +
                        "• Personal Use: PixelBoost provides offline neural image enhancement using hardware acceleration (Vulkan & CPU).\n" +
                        "• Processing Performance: Upscaling duration depends directly on device hardware capabilities.\n" +
                        "• Rights & Output: You retain 100% copyright and ownership of all enhanced images produced by this app.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceContainerLow)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerLowest)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .ceramicShadow(
                        cornerRadius = 20.dp,
                        offsetX = 4.dp, offsetY = 4.dp, blurRadius = 10.dp
                    )
                    .background(SurfaceContainerLow, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = OnSurface
                )
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = OnSurface
            )
            Spacer(modifier = Modifier.width(40.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Section 1: Appearance
            SettingsSection(title = "Appearance", icon = Icons.Outlined.Palette) {
                val themeLabel = when (themeIndex) {
                    1 -> "Light Mode"
                    2 -> "Dark Mode"
                    else -> "System Default"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThemeDialog = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Theme", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(Primary, CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = themeLabel, style = MaterialTheme.typography.labelSmall, color = Primary)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                SegmentedControl(
                    options = listOf("System", "Light", "Dark"),
                    selectedIndex = themeIndex,
                    onSelect = { viewModel.setTheme(it) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: Upscaling
            SettingsSection(title = "Upscaling", icon = Icons.Outlined.AutoAwesome) {
                SettingsRowWithPill(
                    title = "Default Scale",
                    subtitle = "Neural super-resolution multiplier",
                    options = listOf("2×", "4×"),
                    selectedIndex = scaleIndex,
                    onSelect = { viewModel.setScaleIndex(it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                SettingsRowWithPill(
                    title = "Default Format",
                    subtitle = "Export encoding container",
                    options = listOf("JPG", "PNG", "WEBP"),
                    selectedIndex = formatIndex,
                    onSelect = { viewModel.setFormatIndex(it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                val qualitySubtitle = if (qualityIndex == 0) {
                    "High: Smaller file size • great quality"
                } else {
                    "Maximum: Larger file size • best fidelity"
                }
                SettingsRowWithPill(
                    title = "Image Quality",
                    subtitle = qualitySubtitle,
                    options = listOf("High", "Maximum"),
                    selectedIndex = qualityIndex,
                    onSelect = { viewModel.setQualityIndex(it) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: Saving
            SettingsSection(title = "Saving", icon = Icons.Outlined.Save) {
                SettingsToggleRow(
                    title = "Save enhanced images",
                    subtitle = "Sync automatically to Camera Roll",
                    icon = Icons.Outlined.PhotoLibrary,
                    isChecked = saveEnhanced,
                    onToggle = { viewModel.setSaveEnhanced(it) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3.5: Engine & Hardware Diagnostics
            val activeBackend by viewModel.activeBackend.collectAsState()
            val calibratedWinner by viewModel.calibratedWinner.collectAsState()
            val manualOverride by viewModel.manualOverride.collectAsState()
            val calibrationSummary by viewModel.calibrationSummary.collectAsState()
            val isCalibrating by viewModel.isCalibrating.collectAsState()

            var showBenchmarkDialog by remember { mutableStateOf(false) }

            if (showBenchmarkDialog && calibrationSummary != null) {
                val summary = calibrationSummary!!
                AlertDialog(
                    onDismissRequest = { showBenchmarkDialog = false },
                    title = {
                        Text(
                            text = "Comparative Hardware Benchmark",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "Auto-Tuned Winner: ${summary.winningBackend}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                            Text(
                                text = "Calibration Duration: ${String.format(Locale.US, "%.1f ms", summary.calibrationWallClockDurationNanos / 1_000_000.0)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Stage 1 Screening (ms/tile):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            summary.results.forEach { res ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (res.isSelectedWinner) Primary.copy(alpha = 0.1f) else SurfaceContainerLow
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = res.backendName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (res.isSelectedWinner) {
                                                Text(
                                                    text = "WINNER",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Primary
                                                )
                                            }
                                        }
                                        if (res.isConfiguredAndAvailable) {
                                            Text(
                                                text = "Median: ${String.format(Locale.US, "%.1f ms", res.medianLatencyNanos / 1_000_000.0)}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                text = "Verified: Dim ${if (res.outputDimensionsVerified) "OK" else "FAIL"} • Entropy ${if (res.pixelEntropyValid) "OK" else "FAIL"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OnSurfaceVariant
                                            )
                                        } else {
                                            Text(
                                                text = "Unavailable: ${res.unavailableReason ?: "Failed"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ErrorColor
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "[CONSTRAINT]: Large-image exact 2x/4x dimensions are bounded by Android bitmap heap allocation limits. Silent downscaling fallback is active on OOM.",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showBenchmarkDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            SettingsSection(title = "Engine & Hardware Diagnostics", icon = Icons.Outlined.Speed) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Active Neural Engine",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurface
                            )
                            val statusText = if (manualOverride != "AUTO") {
                                "Manual Override: $manualOverride"
                            } else {
                                "Auto-Tuned Winner: $calibratedWinner"
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .background(Primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = activeBackend,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Engine Override Strategy",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = OnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val overrideOptions = listOf("AUTO", "MNN_VULKAN", "MNN_CPU", "ONNX_XNNPACK")
                    val overrideLabels = listOf("Auto", "MNN GPU", "MNN CPU", "ONNX CPU")
                    val selectedIdx = overrideOptions.indexOf(manualOverride).coerceAtLeast(0)

                    SegmentedControl(
                        options = overrideLabels,
                        selectedIndex = selectedIdx,
                        onSelect = { idx ->
                            viewModel.setManualOverride(overrideOptions[idx])
                            Toast.makeText(context, "Engine set to: ${overrideLabels[idx]}", Toast.LENGTH_SHORT).show()
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                viewModel.runHardwareCalibration(forceRefresh = true) {
                                    showBenchmarkDialog = true
                                }
                            },
                            enabled = !isCalibrating,
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isCalibrating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = OnPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Testing...")
                            } else {
                                Icon(imageVector = Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Run Hardware Benchmark")
                            }
                        }

                        if (calibrationSummary != null) {
                            OutlinedButton(
                                onClick = { showBenchmarkDialog = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("View Matrix")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 4: Storage
            SettingsSection(title = "Storage", icon = Icons.Outlined.Storage) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(32.dp).background(SurfaceContainerLow, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Outlined.Info, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Output Folder", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                            Text(text = "Pictures/PixelBoost", style = MaterialTheme.typography.labelSmall, color = Primary)
                        }
                    }
                    Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null, tint = OnSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(32.dp).background(SurfaceContainerLow, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Clear History", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                            Text(
                                text = "${formatFileSize(cacheSizeBytes)} cached",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                    Button(
                        onClick = { showClearCacheDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerLow, contentColor = OnSurface),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .ceramicShadow(cornerRadius = 16.dp, offsetX = 2.dp, offsetY = 2.dp, blurRadius = 6.dp)
                            .tactileClick { showClearCacheDialog = true }
                    ) {
                        Text(text = "Clear", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 5: About
            SettingsSection(title = "About", icon = Icons.Outlined.Info) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPrivacyDialog = true }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Privacy Policy", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                    Icon(imageVector = Icons.Outlined.ArrowOutward, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTermsDialog = true }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Terms of Service", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                    Icon(imageVector = Icons.Outlined.ArrowOutward, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Build Version", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                    Text(text = appVersion, style = MaterialTheme.typography.labelSmall, color = Primary)
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun SettingsSection(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = title.uppercase(), style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .ceramicShadow(
                    cornerRadius = 16.dp,
                    offsetX = 6.dp, offsetY = 6.dp, blurRadius = 18.dp
                )
                .background(SurfaceContainerLowest, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .ceramicInnerShadow(
                cornerRadius = 24.dp,
                offsetX = 2.dp, offsetY = 2.dp, blurRadius = 4.dp
            )
            .background(SurfaceContainer, RoundedCornerShape(24.dp))
            .padding(4.dp)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (isSelected) {
                            Modifier.background(Brush.verticalGradient(listOf(GradientStart, GradientEnd)))
                        } else Modifier
                    )
                    .tactileClick { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) OnPrimary else OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsRowWithPill(title: String, subtitle: String, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Row(
            modifier = Modifier
                .ceramicInnerShadow(
                    cornerRadius = 24.dp,
                    offsetX = 2.dp, offsetY = 2.dp, blurRadius = 4.dp
                )
                .background(SurfaceContainer, RoundedCornerShape(24.dp))
                .padding(2.dp)
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (isSelected) {
                                Modifier.background(Brush.verticalGradient(listOf(GradientStart, GradientEnd)))
                            } else Modifier
                        )
                        .tactileClick { onSelect(index) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) OnPrimary else OnSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsToggleRow(title: String, subtitle: String, icon: ImageVector, isChecked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().tactileClick { onToggle(!isChecked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier.size(32.dp).background(SurfaceContainerLow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = if (isChecked) Primary else OnSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            }
        }
        // Custom Toggle
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 28.dp)
                .ceramicInnerShadow(
                    cornerRadius = 14.dp,
                    offsetX = 1.dp, offsetY = 1.dp, blurRadius = 3.dp
                )
                .background(if (isChecked) PrimaryContainer else SurfaceContainerHigh, RoundedCornerShape(14.dp))
                .padding(2.dp),
            contentAlignment = if (isChecked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .ceramicShadow(
                        cornerRadius = 12.dp,
                        offsetX = 0.dp, offsetY = 2.dp, blurRadius = 5.dp
                    )
                    .background(if (isChecked) OnSurface else SurfaceContainerLowest, CircleShape)
            )
        }
    }
}
