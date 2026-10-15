package com.example.ui.screens

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import com.example.ui.components.ceramicInnerShadow
import com.example.ui.components.ceramicShadow
import com.example.ui.components.tactileClick
import com.example.ui.theme.*

@Composable
fun SettingsScreen(viewModel: com.example.AppViewModel, onBack: () -> Unit = {}) {
    val scaleIndex by viewModel.scaleIndex.collectAsState()
    val formatIndex by viewModel.formatIndex.collectAsState()
    val qualityIndex by viewModel.qualityIndex.collectAsState()
    val saveEnhanced by viewModel.saveEnhanced.collectAsState()

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
                    imageVector = Icons.Outlined.ArrowBack, // arrow_back
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
        SettingsSection(title = "Appearance", icon = Icons.Outlined.Palette) { // placeholder icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Theme", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(Primary, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Light Mode", style = MaterialTheme.typography.labelSmall, color = Primary)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            SegmentedControl(options = listOf("System", "Light", "Dark"), selectedIndex = viewModel.themeIndex.collectAsState().value, onSelect = { viewModel.setThemeIndex(it) })
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 2: Upscaling
        SettingsSection(title = "Upscaling", icon = Icons.Outlined.AutoAwesome) {
            SettingsRowWithPill(title = "Default Scale", subtitle = "Neural super-resolution multiplier", options = listOf("2×", "4×"), selectedIndex = scaleIndex, onSelect = { viewModel.setScaleIndex(it) })
            Spacer(modifier = Modifier.height(16.dp))
            SettingsRowWithPill(title = "Default Format", subtitle = "Export encoding container", options = listOf("JPG", "PNG", "WEBP"), selectedIndex = formatIndex, onSelect = { viewModel.setFormatIndex(it) })
            Spacer(modifier = Modifier.height(16.dp))
            val qualitySubtitle = if (qualityIndex == 0) {
                "High: Smaller file size • great quality"
            } else {
                "Maximum: Larger file size • best fidelity"
            }
            SettingsRowWithPill(title = "Image Quality", subtitle = qualitySubtitle, options = listOf("High", "Maximum"), selectedIndex = qualityIndex, onSelect = { viewModel.setQualityIndex(it) })
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 3: Saving
        SettingsSection(title = "Saving", icon = Icons.Outlined.Save) {
            SettingsToggleRow(title = "Save enhanced images", subtitle = "Sync automatically to Camera Roll", icon = Icons.Outlined.PhotoLibrary, isChecked = saveEnhanced, onToggle = { viewModel.setSaveEnhanced(it) })
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
                        Text(text = "248.5 MB cached", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                }
                Button(
                    onClick = { viewModel.clearHistory() },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerLow, contentColor = OnSurface),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp).ceramicShadow(cornerRadius = 16.dp, offsetX = 2.dp, offsetY = 2.dp, blurRadius = 6.dp).tactileClick { viewModel.clearHistory() }
                ) {
                    Text(text = "Clear", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))

        // Section 5: About
        SettingsSection(title = "About", icon = Icons.Outlined.Info) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Privacy Policy", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                Icon(imageVector = Icons.Outlined.ArrowOutward, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Terms of Service", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                Icon(imageVector = Icons.Outlined.ArrowOutward, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Build Version", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                Text(text = "v2.4.0 (PRO)", style = MaterialTheme.typography.labelSmall, color = Primary)
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
