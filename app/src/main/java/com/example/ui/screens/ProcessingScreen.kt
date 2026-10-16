package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.activity.compose.BackHandler
import com.example.ui.components.ceramicInnerShadow
import com.example.ui.components.ceramicShadow
import com.example.ui.components.tactileClick
import com.example.ui.theme.*
import com.example.AppViewModel

@Composable
fun ProcessingScreen(
    viewModel: com.example.AppViewModel,
    onCancel: () -> Unit,
    onNavigateBack: () -> Unit = onCancel,
    onFinish: () -> Unit
) {
    val progress by viewModel.progress.collectAsState()
    val errorText by viewModel.errorMessage.collectAsState()
    val scaleIndex by viewModel.scaleIndex.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    BackHandler {
        onNavigateBack()
    }
    
    LaunchedEffect(Unit) {
        viewModel.startProcessing {
            onFinish()
        }
    }

    LaunchedEffect(isProcessing) {
        if (!isProcessing && viewModel.batchItems.value.any { it.status == com.example.BatchItemStatus.Success }) {
            onFinish()
        }
    }

    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

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
            Spacer(modifier = Modifier.width(40.dp))
            Text(
                text = "Processing",
                style = MaterialTheme.typography.headlineSmall,
                color = OnSurface
            )
            IconButton(
                onClick = {
                    viewModel.cancelProcessing()
                    onCancel()
                },
                modifier = Modifier
                    .size(40.dp)
                    .ceramicShadow(
                        cornerRadius = 20.dp,
                        offsetX = 4.dp, offsetY = 4.dp, blurRadius = 10.dp
                    )
                    .background(SurfaceContainerLow, CircleShape)
            ) {
                Icon(imageVector = Icons.Outlined.Close, contentDescription = "Cancel", tint = OnSurfaceVariant)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

        Spacer(modifier = Modifier.height(40.dp))

        // Circular Progress
        Box(
            modifier = Modifier
                .size(192.dp)
                .ceramicShadow(
                    cornerRadius = 96.dp,
                    offsetX = 6.dp, offsetY = 6.dp, blurRadius = 18.dp
                )
                .background(SurfaceContainerLow, CircleShape)
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .ceramicInnerShadow(
                        cornerRadius = 84.dp,
                        offsetX = 3.dp, offsetY = 3.dp, blurRadius = 8.dp
                    )
                    .background(SurfaceContainerLow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val arcBgColor = SurfaceContainerHighest.copy(alpha = 0.6f)
                val progressColor = SecondaryContainer
                androidx.compose.foundation.Canvas(modifier = Modifier.size(160.dp)) {
                    drawArc(
                        color = arcBgColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = 9.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.displayMedium,
                    color = OnSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text(text = "Enhancing image", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val selectedImages by viewModel.selectedImages.collectAsState()
            val currentIndex by viewModel.currentProcessingIndex.collectAsState()
            Text(text = "${currentIndex + 1} of ${selectedImages.size}", style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            Spacer(modifier = Modifier.width(6.dp))
            Box(modifier = Modifier.size(4.dp).background(OutlineVariant, CircleShape))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = if (scaleIndex == 0) "2× Upscale" else "4× Upscale", style = MaterialTheme.typography.bodyMedium, color = Secondary, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Queue list
        val batchItems by viewModel.batchItems.collectAsState()
        val currentIndexState by viewModel.currentProcessingIndex.collectAsState()

        batchItems.forEachIndexed { index, item ->
            val statusText = when (item.status) {
                com.example.BatchItemStatus.Waiting -> "Waiting..."
                com.example.BatchItemStatus.Processing -> "Enhancing... ${(animatedProgress * 100).toInt()}%"
                com.example.BatchItemStatus.Success -> "Completed"
                com.example.BatchItemStatus.Failed -> "Failed"
                com.example.BatchItemStatus.Cancelled -> "Cancelled"
            }
            val isCurrent = index == currentIndexState && item.status == com.example.BatchItemStatus.Processing

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .ceramicShadow(
                        cornerRadius = 16.dp,
                        offsetX = 4.dp, offsetY = 4.dp, blurRadius = 12.dp
                    )
                    .background(if (isCurrent) SurfaceContainerLowest else SurfaceContainerLowest.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(item.originalUri).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isCurrent) Box(modifier = Modifier.fillMaxSize().background(Primary.copy(alpha = 0.1f)))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "Image ${index + 1}", style = MaterialTheme.typography.labelLarge, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = statusText, style = MaterialTheme.typography.bodySmall, color = if (isCurrent) Primary else OnSurfaceVariant)
                    }
                }
                when (item.status) {
                    com.example.BatchItemStatus.Processing -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = SecondaryContainer, strokeWidth = 3.dp)
                    }
                    com.example.BatchItemStatus.Success -> {
                        Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
                    }
                    com.example.BatchItemStatus.Failed -> {
                        Icon(imageVector = Icons.Outlined.ErrorOutline, contentDescription = null, tint = ErrorColor, modifier = Modifier.size(22.dp))
                    }
                    com.example.BatchItemStatus.Cancelled -> {
                        Icon(imageVector = Icons.Outlined.Cancel, contentDescription = null, tint = Outline, modifier = Modifier.size(22.dp))
                    }
                    com.example.BatchItemStatus.Waiting -> {
                        Icon(imageVector = Icons.Outlined.HourglassEmpty, contentDescription = null, tint = Outline, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (errorText != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(bottom = 16.dp)
                    .background(ErrorColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = errorText!!, style = MaterialTheme.typography.bodySmall, color = ErrorColor)
            }
        }
        Button(
            onClick = {
                viewModel.cancelProcessing()
                onCancel()
            },
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerLow, contentColor = ErrorColor),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(48.dp)
                .ceramicShadow(cornerRadius = 24.dp, offsetX = 2.dp, offsetY = 2.dp, blurRadius = 8.dp),
            shape = CircleShape
        ) {
            Icon(imageVector = Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Cancel", style = MaterialTheme.typography.labelLarge)
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}
}
