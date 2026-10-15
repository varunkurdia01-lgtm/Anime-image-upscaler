package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.components.ceramicInnerShadow
import com.example.ui.components.ceramicShadow
import com.example.ui.components.tactileClick
import com.example.ui.theme.*

data class ImageItem(val uri: String, val bucket: String)

@Composable
fun ImagePickerScreen(onBack: () -> Unit, onContinue: (List<String>) -> Unit) {
    var selectedUris by remember { mutableStateOf(emptyList<String>()) }
    var images by remember { mutableStateOf(emptyList<ImageItem>()) }
    val context = LocalContext.current
    
    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newItems = uris.map { ImageItem(it.toString(), "Selected") }
            images = (images + newItems).distinctBy { it.uri }
            selectedUris = (selectedUris + uris.map { it.toString() }).distinct()
        }
    }

    LaunchedEffect(Unit) {
        if (images.isEmpty()) {
            photoPickerLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val dynamicBuckets = remember(images) { images.map { it.bucket }.distinct().sorted() }
    val tabs = listOf("All") + dynamicBuckets
    val filteredImages = remember(images, selectedTabIndex) {
        if (selectedTabIndex == 0) images.map { it.uri }
        else images.filter { it.bucket == tabs[selectedTabIndex] }.map { it.uri }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.ArrowBack, contentDescription = "Back", tint = OnSurface)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Enhance Studio",
                        style = MaterialTheme.typography.headlineSmall,
                        color = OnSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.AddPhotoAlternate, contentDescription = "Add Photos", tint = Primary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.size(32.dp).background(Primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Outlined.Person, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tabs
            if (tabs.size > 1) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).ceramicInnerShadow(cornerRadius = 24.dp, offsetX = 2.dp, offsetY = 2.dp, blurRadius = 5.dp).background(SurfaceContainer, RoundedCornerShape(24.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(tabs.size) { index ->
                        val title = tabs[index]
                        val isSelected = selectedTabIndex == index
                        Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).tactileClick { selectedTabIndex = index }.then(if (isSelected) Modifier.background(SecondaryContainer, RoundedCornerShape(20.dp)) else Modifier).padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(text = title, style = MaterialTheme.typography.labelMedium, color = if (isSelected) OnSecondaryContainer else OnSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            if (filteredImages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No images selected",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Choose photos from your device gallery to upscale",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            shape = CircleShape
                        ) {
                            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Photo Picker")
                        }
                    }
                }
            } else {
                // Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredImages) { url ->
                        val isSelected = selectedUris.contains(url)
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .tactileClick {
                                    selectedUris = if (isSelected) selectedUris - url else selectedUris + url
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(url)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isSelected) {
                                Box(modifier = Modifier.fillMaxSize().background(Primary.copy(alpha = 0.15f)))
                            }
                            // Badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(24.dp)
                                    .background(
                                        if (isSelected) SecondaryContainer else SurfaceContainerLowest.copy(alpha = 0.8f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null, tint = OnSecondaryContainer, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Sheet CTA
        if (selectedUris.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .ceramicShadow(cornerRadius = 28.dp, offsetX = 0.dp, offsetY = 12.dp, blurRadius = 36.dp, darkAlpha = 0.45f, lightAlpha = 0f)
                    .background(SurfaceBright.copy(alpha = 0.95f), RoundedCornerShape(28.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(SecondaryContainer, CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "${selectedUris.size} images selected", style = MaterialTheme.typography.labelLarge, color = OnSurface)
                        }
                        IconButton(onClick = { selectedUris = emptyList() }, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Outlined.Close, contentDescription = "Clear", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        selectedUris.take(5).forEach { uri ->
                            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(uri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onContinue(selectedUris) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp),
                        shape = CircleShape
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(GradientStart, GradientEnd))),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Continue", style = MaterialTheme.typography.labelLarge, color = OnPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
