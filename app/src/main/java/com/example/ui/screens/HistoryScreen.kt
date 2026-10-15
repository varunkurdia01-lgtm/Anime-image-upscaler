package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.components.ceramicInnerShadow
import com.example.ui.components.ceramicShadow
import com.example.ui.components.tactileClick
import com.example.ui.theme.*

@Composable
fun HistoryScreen(viewModel: com.example.AppViewModel, onOpenResult: (com.example.data.HistoryEntity) -> Unit = {}) {
    val items by viewModel.history.collectAsState(initial = emptyList())
    val context = LocalContext.current
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear History?") },
            text = { Text("This will permanently remove all enhanced photos from your history and free up storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        viewModel.clearHistory()
                    }
                ) {
                    Text("Clear", color = ErrorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
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
                        imageVector = Icons.Outlined.AutoFixHigh, // auto_fix_high
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
                    imageVector = Icons.Outlined.Person, // person
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
                onClick = { showClearConfirmDialog = true },
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
        val totalStorageBytes = items.sumOf { it.sizeBytes }
        val storageFormatted = String.format(java.util.Locale.US, "%.1f MB total enhanced cache", totalStorageBytes / (1024.0 * 1024.0))

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
                    Text(text = storageFormatted, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tabs
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
                        .semantics {
                            selected = isSelected
                            role = Role.Tab
                        }
                        .tactileClick(role = Role.Tab) { selectedTabIndex = index }
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

        val filteredItems = remember(items, selectedTabIndex) {
            when (selectedTabIndex) {
                1 -> items.filter { it.label.contains("4x", ignoreCase = true) }
                2 -> items.filter { it.label.contains("2x", ignoreCase = true) }
                else -> items
            }
        }

        // Image Grid
        HistoryGrid(filteredItems, onOpenResult)
    }
}
}

@Composable
fun HistoryGrid(
    items: List<com.example.data.HistoryEntity>,
    onOpenResult: (com.example.data.HistoryEntity) -> Unit = {}
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No enhanced photos found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items.size) { index ->
            val item = items[index]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .ceramicShadow(
                        cornerRadius = 16.dp,
                        offsetX = 6.dp, offsetY = 6.dp, blurRadius = 16.dp
                    )
                    .background(SurfaceContainerLowest, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .semantics {
                        role = Role.Button
                        contentDescription = "Enhanced photo ${item.label}, ${android.text.format.DateUtils.getRelativeTimeSpanString(item.timestamp)}"
                    }
                    .tactileClick(role = Role.Button) { onOpenResult(item) }
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = "${android.text.format.DateUtils.getRelativeTimeSpanString(item.timestamp)} • ${item.sizeBytes / (1024 * 1024)} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
