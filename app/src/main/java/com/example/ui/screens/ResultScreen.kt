package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.components.ceramicInnerShadow
import com.example.ui.components.ceramicShadow
import com.example.ui.components.tactileClick
import com.example.ui.theme.*
import androidx.activity.compose.BackHandler
import com.example.AppViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress

@Composable
fun ResultScreen(viewModel: com.example.AppViewModel, onBack: () -> Unit, onUpscaleAnother: () -> Unit) {
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    val resultBitmap by viewModel.resultBitmap.collectAsState()
    val selectedImages by viewModel.selectedImages.collectAsState()
    val batchResults by viewModel.batchResults.collectAsState()
    val batchItems by viewModel.batchItems.collectAsState()
    val selectedIndex by viewModel.selectedResultIndex.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val itemSaveStatuses by viewModel.itemSaveStatuses.collectAsState()
    var showMoreMenu by remember { mutableStateOf(false) }

    LaunchedEffect(selectedImages, batchResults, batchItems) {
        val hasAnySuccess = batchItems.any { it.status == com.example.BatchItemStatus.Success } || batchResults.isNotEmpty() || resultBitmap != null
        if (!hasAnySuccess && selectedImages.isEmpty()) {
            onBack()
        }
    }

    val currentBatchItem = batchItems.getOrNull(selectedIndex)
    val currentItem = currentBatchItem?.processedItem ?: batchResults.getOrNull(selectedIndex)
    val currentOriginalUri = currentItem?.originalUri
        ?: selectedImages.getOrNull(selectedIndex)
        ?: selectedImages.firstOrNull()
        ?: ""

    var origW by remember { mutableIntStateOf(0) }
    var origH by remember { mutableIntStateOf(0) }
    val scaleIndex by viewModel.scaleIndex.collectAsState()
    
    LaunchedEffect(currentOriginalUri) {
        if (currentOriginalUri.isNotEmpty()) {
            val dims = com.example.data.ImageSourceResolver.getImageDimensions(context, currentOriginalUri)
            if (dims != null) {
                origW = dims.first
                origH = dims.second
            }
        }
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
                    .ceramicShadow(cornerRadius = 20.dp, offsetX = 2.dp, offsetY = 2.dp, blurRadius = 8.dp)
                    .background(SurfaceContainerLow, CircleShape)
            ) {
                Icon(imageVector = Icons.Outlined.ArrowBack, contentDescription = "Back", tint = OnSurface)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Result", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
                val headerScaleText = if (scaleIndex == 0) "2× PRECISION" else "4× ULTRA CLARITY"
                Text(text = headerScaleText, style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { viewModel.shareResult(context) },
                    modifier = Modifier
                        .size(40.dp)
                        .ceramicShadow(cornerRadius = 20.dp, offsetX = 2.dp, offsetY = 2.dp, blurRadius = 8.dp)
                        .background(SurfaceContainerLow, CircleShape)
                ) {
                    Icon(imageVector = Icons.Outlined.Share, contentDescription = "Share", tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier
                            .size(40.dp)
                            .ceramicShadow(cornerRadius = 20.dp, offsetX = 2.dp, offsetY = 2.dp, blurRadius = 8.dp)
                            .background(SurfaceContainerLow, CircleShape)
                    ) {
                        Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = "More options", tint = OnSurfaceVariant)
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Save to Photos") },
                            onClick = {
                                showMoreMenu = false
                                viewModel.saveToGallery(context)
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Download, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                showMoreMenu = false
                                viewModel.shareResult(context)
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Share, contentDescription = null)
                            }
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {

        Spacer(modifier = Modifier.height(24.dp))

        // Image Comparison
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .ceramicShadow(cornerRadius = 28.dp, offsetX = 6.dp, offsetY = 6.dp, blurRadius = 18.dp)
                .background(SurfaceContainerLowest, RoundedCornerShape(28.dp))
                .padding(8.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
                var dragFraction by remember { mutableFloatStateOf(0.5f) }
                val currentFraction = dragFraction.coerceIn(0f, 1f)
                val offsetX = currentFraction * maxWidthPx

                // After Image (Base)
                if (resultBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = resultBitmap!!.asImageBitmap(),
                        contentDescription = "After",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(currentItem?.enhancedUri ?: currentOriginalUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "After",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                // Before Image (Clipped)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(currentFraction)
                        .clip(RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(currentOriginalUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Before",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(this@BoxWithConstraints.maxWidth)
                    )
                }

                // Badges
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .background(InverseSurface.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(6.dp).background(OutlineVariant, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "BEFORE", style = MaterialTheme.typography.labelSmall, color = InverseOnSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .background(InverseSurface.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(6.dp).background(SecondaryContainer, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "AFTER", style = MaterialTheme.typography.labelSmall, color = InverseOnSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }

                // Slider Handle
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .offset { IntOffset(offsetX.roundToInt() - 18.dp.roundToPx(), 0) }
                        .semantics {
                            role = Role.Image
                            progressBarRangeInfo = ProgressBarRangeInfo(current = currentFraction, range = 0f..1f)
                            contentDescription = "Before and after comparison slider, ${(currentFraction * 100).toInt()}% split"
                            setProgress { targetValue ->
                                dragFraction = targetValue.coerceIn(0f, 1f)
                                true
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                if (maxWidthPx > 0) {
                                    dragFraction = ((dragFraction * maxWidthPx + dragAmount.x) / maxWidthPx).coerceIn(0f, 1f)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.fillMaxHeight().width(2.dp).background(SurfaceContainerLowest.copy(alpha = 0.9f)))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(SurfaceContainerLowest, CircleShape)
                            .padding(2.dp)
                            .background(SurfaceContainerLowest, CircleShape)
                            .ceramicShadow(cornerRadius = 18.dp, offsetX = 0.dp, offsetY = 4.dp, blurRadius = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Outlined.Compare, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Metadata Tray
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .ceramicInnerShadow(cornerRadius = 16.dp, offsetX = 2.dp, offsetY = 2.dp, blurRadius = 6.dp)
                .background(SurfaceContainer, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = "Original", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text(text = "${origW} × ${origH}", style = MaterialTheme.typography.labelLarge, color = OnSurface)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.size(28.dp).background(SurfaceContainerLowest, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "Enhanced", style = MaterialTheme.typography.labelSmall, color = Primary)
                    val enhancedW = currentItem?.width ?: (if (scaleIndex == 0) origW * 2 else origW * 4)
                    val enhancedH = currentItem?.height ?: (if (scaleIndex == 0) origH * 2 else origH * 4)
                    Text(text = "${enhancedW} × ${enhancedH}", style = MaterialTheme.typography.labelLarge, color = OnSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
            Row(
                modifier = Modifier
                    .background(SecondaryFixed, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Outlined.AutoFixHigh, contentDescription = null, tint = OnSecondaryFixed, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                val badgeText = if (scaleIndex == 0) "2× Upscale" else "4× Upscale"
                Text(text = badgeText, style = MaterialTheme.typography.labelMedium, color = OnSecondaryFixed, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Batch Queue Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Batch Queue",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface
            )
            val totalCount = if (selectedImages.isNotEmpty()) selectedImages.size else if (batchItems.isNotEmpty()) batchItems.size else batchResults.size
            val completedCount = if (batchItems.isNotEmpty()) batchItems.count { it.status == com.example.BatchItemStatus.Success } else batchResults.size
            Text(
                text = "$completedCount of $totalCount Completed",
                style = MaterialTheme.typography.labelMedium,
                color = Primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val batchImages = selectedImages
            
            batchImages.forEachIndexed { index, url ->
                val isSelected = index == selectedIndex
                val bItem = batchItems.getOrNull(index)
                val isSuccess = bItem?.status == com.example.BatchItemStatus.Success && bItem.processedItem != null
                val isFailed = bItem?.status == com.example.BatchItemStatus.Failed
                val isCancelled = bItem?.status == com.example.BatchItemStatus.Cancelled

                val itemStatusDesc = when {
                    isSuccess -> "Completed"
                    isFailed -> "Failed"
                    isCancelled -> "Cancelled"
                    else -> "Processing"
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (isSelected) Modifier.background(SurfaceContainerLowest) else Modifier.background(Color.Transparent)
                        )
                        .then(
                            if (isSelected) Modifier.ceramicShadow(cornerRadius = 12.dp, offsetX = 2.dp, offsetY = 2.dp, blurRadius = 6.dp) else Modifier
                        )
                        .semantics {
                            selected = isSelected
                            role = Role.Button
                            contentDescription = "Batch item ${index + 1} of ${batchImages.size}, $itemStatusDesc, ${if (isSelected) "selected" else "not selected"}"
                        }
                        .tactileClick(role = Role.Button) {
                            if (isSuccess) {
                                viewModel.setSelectedResultIndex(index)
                            }
                        }
                ) {
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clip(RoundedCornerShape(10.dp))) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(bItem?.processedItem?.enhancedUri ?: url)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(2.dp, Primary, RoundedCornerShape(10.dp))
                            )
                        }
                        if (isFailed) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(ErrorColor.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = "Failed",
                                    tint = OnPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else if (isCancelled) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Outline.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Cancel,
                                    contentDescription = "Cancelled",
                                    tint = OnPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .size(16.dp)
                                .background(Primary, CircleShape)
                                .border(1.dp, SurfaceContainerLowest, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = OnPrimary,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // Save Button
        val currentSaveState = saveState
        val currentItemStatus = itemSaveStatuses[selectedIndex] ?: com.example.GallerySaveStatus.NotSaved
        val isCurrentItemSaving = currentSaveState is com.example.SaveState.Saving || currentItemStatus == com.example.GallerySaveStatus.Saving
        val isCurrentItemSaved = currentItemStatus == com.example.GallerySaveStatus.Saved || (currentSaveState is com.example.SaveState.Success && currentSaveState.itemIndex == selectedIndex)

        Button(
            onClick = {
                if (!isCurrentItemSaving && !isCurrentItemSaved) {
                    viewModel.saveToGallery(context)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .ceramicShadow(
                    cornerRadius = 28.dp,
                    offsetX = 0.dp, offsetY = 8.dp, blurRadius = 24.dp,
                    darkAlpha = 0.38f, lightAlpha = 0f
                ),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(0.dp),
            shape = CircleShape
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(GradientStart, GradientEnd))),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrentItemSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = OnPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Saving...", style = MaterialTheme.typography.labelLarge, color = OnPrimary)
                    } else if (isCurrentItemSaved) {
                        Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Saved to Photos", style = MaterialTheme.typography.labelLarge, color = OnPrimary)
                        if (currentSaveState is com.example.SaveState.Success) {
                            LaunchedEffect(saveState, selectedIndex) {
                                delay(2400)
                                viewModel.resetSaveState()
                            }
                        }
                    } else {
                        Icon(imageVector = Icons.Outlined.DownloadDone, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Save to Gallery", style = MaterialTheme.typography.labelLarge, color = OnPrimary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { viewModel.shareResult(context) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .ceramicShadow(cornerRadius = 24.dp, offsetX = 2.dp, offsetY = 2.dp, blurRadius = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerLowest, contentColor = OnSurface),
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Outlined.Share, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Share", style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = onUpscaleAnother,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .ceramicShadow(cornerRadius = 24.dp, offsetX = 2.dp, offsetY = 2.dp, blurRadius = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerLowest, contentColor = OnSurface),
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Outlined.Add, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Upscale Another", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
}
