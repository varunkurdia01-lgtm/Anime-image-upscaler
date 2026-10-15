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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.components.ceramicInnerShadow
import com.example.ui.components.ceramicShadow
import com.example.ui.components.tactileClick
import com.example.ui.theme.*

@Composable
fun EnhanceOptionsScreen(viewModel: com.example.AppViewModel, onBack: () -> Unit, onStartEnhance: () -> Unit) {
    val scaleIndex by viewModel.scaleIndex.collectAsState()
    val formatIndex by viewModel.formatIndex.collectAsState()
    val qualityIndex by viewModel.qualityIndex.collectAsState()
    val selectedImages by viewModel.selectedImages.collectAsState()



    val context = LocalContext.current
    var origW by remember { mutableIntStateOf(0) }
    var origH by remember { mutableIntStateOf(0) }
    val origText = if (origW > 0 && origH > 0) "${origW} × ${origH}" else "Detecting..."
    LaunchedEffect(selectedImages) {
        val uriStr = selectedImages.firstOrNull() ?: return@LaunchedEffect
        val dims = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.example.data.ImageSourceResolver.getImageDimensions(context, uriStr)
        }
        if (dims != null) {
            origW = dims.first
            origH = dims.second
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceContainerLow)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(imageVector = Icons.Outlined.ArrowBack, contentDescription = "Back", tint = OnSurface)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Enhance Studio",
                    style = MaterialTheme.typography.headlineSmall,
                    color = OnSurface
                )
            }
            Box(
                modifier = Modifier.size(32.dp).background(Primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Outlined.Person, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Preview Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .ceramicShadow(
                    cornerRadius = 24.dp,
                    offsetX = 6.dp, offsetY = 6.dp, blurRadius = 18.dp
                )
                .background(SurfaceContainerLowest, RoundedCornerShape(24.dp))
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceContainer)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(selectedImages.firstOrNull() ?: "")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Top Badge
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .background(SurfaceContainerLowest.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Outlined.AutoFixHigh, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Source: Image", style = MaterialTheme.typography.labelSmall, color = OnSurface)
                }

                // Bottom Badge
                val origText = if (origW > 0 && origH > 0) "${origW} × ${origH}" else "Detecting..."
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = origText, style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Options
        val upscalingSubtitle = if (scaleIndex == 0) "2× Precision Super-Resolution" else "4× Ultra Clarity Super-Resolution"
        OptionSection(title = "Upscaling", subtitle = upscalingSubtitle, options = listOf("2×", "4×"), selected = listOf("2×", "4×")[scaleIndex], onSelect = { viewModel.setScaleIndex(listOf("2×", "4×").indexOf(it)) })
        Spacer(modifier = Modifier.height(16.dp))
        val formatSubtitle = when (formatIndex) {
            0 -> "RGB Only • Standard Lossy Compression"
            1 -> "Preserves Alpha • Lossless Container"
            2 -> "Preserves Alpha • Efficient Modern Encoding"
            else -> "Standard Image Format"
        }
        OptionSection(
            title = "Output Format",
            subtitle = formatSubtitle,
            options = listOf("JPG", "PNG", "WEBP"),
            selected = listOf("JPG", "PNG", "WEBP")[formatIndex],
            onSelect = { viewModel.setFormatIndex(listOf("JPG", "PNG", "WEBP").indexOf(it)) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        val qualitySubtitle = when (formatIndex) {
            1 -> "Lossless Compression • Pixel-Perfect Fidelity"
            else -> if (qualityIndex == 0) "High: Optimized File Size • Balanced Quality" else "Maximum: Full Bitrate • Best Fidelity"
        }
        OptionSection(title = "Image Quality", subtitle = qualitySubtitle, options = listOf("High", "Maximum"), selected = listOf("High", "Maximum")[qualityIndex], onSelect = { viewModel.setQualityIndex(listOf("High", "Maximum").indexOf(it)) })

        Spacer(modifier = Modifier.height(24.dp))

        // Resolution Transformation Card
        val requestedScale = if (scaleIndex == 0) 2 else 4
        val outputText = if (origW > 0 && origH > 0) "${origW * requestedScale} × ${origH * requestedScale}" else "—"

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .ceramicShadow(
                    cornerRadius = 24.dp,
                    offsetX = 6.dp, offsetY = 6.dp, blurRadius = 16.dp
                )
                .background(SurfaceContainerLowest, RoundedCornerShape(24.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Original", style = MaterialTheme.typography.labelSmall, color = Outline)
                Text(text = origText, style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .ceramicInnerShadow(
                        cornerRadius = 16.dp,
                        offsetX = 2.dp, offsetY = 2.dp, blurRadius = 4.dp
                    )
                    .background(SurfaceContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "Output", style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(
                    text = outputText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val isSelectionValid = selectedImages.isNotEmpty()
        Button(
            onClick = onStartEnhance,
            enabled = isSelectionValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .ceramicShadow(
                    cornerRadius = 28.dp,
                    offsetX = 0.dp, offsetY = 8.dp, blurRadius = 24.dp,
                    darkAlpha = if (isSelectionValid) 0.35f else 0.08f, lightAlpha = 0f
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(0.dp),
            shape = CircleShape
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isSelectionValid) {
                            Brush.verticalGradient(listOf(GradientStart, GradientEnd))
                        } else {
                            Brush.verticalGradient(listOf(SurfaceContainerHigh, SurfaceContainerHighest))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.AutoFixHigh,
                        contentDescription = null,
                        tint = if (isSelectionValid) OnPrimary else OnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enhance & Upscale",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelectionValid) OnPrimary else OnSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Outlined.Info, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Neural Super-Resolution • Hardware Accelerated", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun OptionSection(title: String, subtitle: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = title.uppercase(), style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = if (selected == options.last()) Primary else Outline)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .ceramicInnerShadow(
                    cornerRadius = 22.dp,
                    offsetX = 3.dp, offsetY = 3.dp, blurRadius = 6.dp
                )
                .background(SurfaceContainer, RoundedCornerShape(22.dp))
                .padding(4.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .semantics {
                            this.selected = isSelected
                            role = Role.Tab
                        }
                        .tactileClick { onSelect(option) }
                        .background(if (isSelected) SecondaryContainer else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) OnSecondaryContainer else OnSurfaceVariant
                    )
                }
            }
        }
    }
}
