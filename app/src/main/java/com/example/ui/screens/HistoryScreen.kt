package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.AppViewModel
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
fun HistoryScreen(viewModel: AppViewModel, onOpenResult: () -> Unit = {}) {
    val items by viewModel.history.collectAsState(initial = emptyList())
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "Clear History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete all enhanced photos from your history?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearHistory()
                    }
                ) {
                    Text("Clear All", color = ErrorColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(PrimaryContainer.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoFixHigh,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "History",
                    style = MaterialTheme.typography.headlineSmall,
                    color = OnSurface
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = OnPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // AI Processed Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AI PROCESSED",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                    Text(
                        text = "${items.size} Photos",
                        style = MaterialTheme.typography.headlineMedium,
                        color = OnSurface
                    )
                }
                IconButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .ceramicShadow(
                            cornerRadius = 20.dp,
                            offsetX = 4.dp, offsetY = 4.dp, blurRadius = 10.dp
                        )
                        .background(SurfaceContainerLowest, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = ErrorColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Storage Summary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .ceramicShadow(
                        cornerRadius = 16.dp,
                        offsetX = 5.dp, offsetY = 5.dp, blurRadius = 14.dp
                    )
                    .background(SurfaceContainerLowest, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(SecondaryFixed.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = Secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "Processed Storage", style = MaterialTheme.typography.labelMedium, color = OnSurface)
                        Text(
                            text = "${formatFileSize(items.sumOf { it.sizeBytes })} total enhanced cache",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .background(SurfaceContainer, RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "Auto-Sync", style = MaterialTheme.typography.labelSmall, color = Primary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tabs (All, 4× Ultra, 2× Crisp)
            var selectedTabIndex by remember { mutableIntStateOf(0) }
            val tabs = listOf("All", "4× Ultra", "2× Crisp")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .ceramicInnerShadow(
                        cornerRadius = 24.dp,
                        offsetX = 2.dp, offsetY = 2.dp, blurRadius = 5.dp
                    )
                    .background(SurfaceContainer, RoundedCornerShape(24.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTabIndex == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .tactileClick(role = androidx.compose.ui.semantics.Role.Tab) { selectedTabIndex = index }
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .background(SecondaryContainer, RoundedCornerShape(20.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) OnSecondaryContainer else OnSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Image Grid with Tab Filtering
            val filteredItems = remember(items, selectedTabIndex) {
                when (selectedTabIndex) {
                    1 -> items.filter { it.scale == 4 || it.label.contains("4x", ignoreCase = true) || it.label.contains("4×", ignoreCase = true) }
                    2 -> items.filter { it.scale == 2 || it.label.contains("2x", ignoreCase = true) || it.label.contains("2×", ignoreCase = true) }
                    else -> items
                }
            }

            HistoryGrid(
                items = filteredItems,
                viewModel = viewModel,
                onItemClick = { entity ->
                    viewModel.openHistoricalResult(entity) {
                        onOpenResult()
                    }
                }
            )
        }
    }
}

@Composable
fun HistoryGrid(
    items: List<HistoryEntity>,
    viewModel: AppViewModel,
    onItemClick: (HistoryEntity) -> Unit = {}
) {
    val context = LocalContext.current

    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "No enhanced photos yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Enhanced photos will appear here after processing",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items.size) { index ->
                val item = items[index]
                var menuExpanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .ceramicShadow(
                            cornerRadius = 16.dp,
                            offsetX = 6.dp, offsetY = 6.dp, blurRadius = 16.dp
                        )
                        .background(SurfaceContainerLowest, RoundedCornerShape(16.dp))
                        .tactileClick(role = androidx.compose.ui.semantics.Role.Button) {
                            onItemClick(item)
                        }
                        .padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceContainer)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.enhancedUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Format Badge (PNG / JPEG / WEBP)
                        val badgeText = item.mimeType.substringAfter("/").uppercase().ifBlank { "IMAGE" }
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .background(SurfaceContainerLowest.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "More options",
                                    tint = OnSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Share,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.shareHistoryItem(context, item)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = ErrorColor) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = ErrorColor
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.deleteHistoryItem(item)
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        text = "${android.text.format.DateUtils.getRelativeTimeSpanString(item.timestamp)} • ${formatFileSize(item.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
