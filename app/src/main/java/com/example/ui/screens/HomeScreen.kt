package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.components.ceramicInnerShadow
import com.example.ui.components.ceramicShadow
import com.example.ui.components.tactileClick
import com.example.ui.theme.*
import com.example.R

@Composable
fun HomeScreen(viewModel: com.example.AppViewModel, onNavigateToPicker: () -> Unit = {}, onNavigateToSettings: () -> Unit = {}, onNavigateToHistory: () -> Unit = {}, onStartEnhance: () -> Unit = {}) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.setSelectedImages(uris.map { it.toString() })
            onStartEnhance()
        }
    }
    val fileBrowserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.setSelectedImages(uris.map { it.toString() })
            onStartEnhance()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceContainerLow)
    ) {
        // Top App Bar
        TopBar()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Logo and Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // App Logo
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .ceramicShadow(
                                cornerRadius = 12.dp,
                                offsetX = 4.dp, offsetY = 4.dp, blurRadius = 12.dp
                            )
                            .background(SurfaceContainerLowest, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "PixelBoost",
                            style = MaterialTheme.typography.headlineMedium,
                            color = OnSurface,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Sharper Memories",
                            style = MaterialTheme.typography.labelSmall,
                            color = Secondary
                        )
                    }
                }

                // Settings Button
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(40.dp)
                        .ceramicInnerShadow(
                            cornerRadius = 20.dp,
                            offsetX = 2.dp, offsetY = 2.dp, blurRadius = 4.dp
                        )
                        .background(SurfaceContainerLowest, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Intro Text
            Text(
                text = "Good Morning, let's make your photos look better.",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurfaceVariant,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Upscale your images",
                style = MaterialTheme.typography.headlineLarge,
                color = OnSurface,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Enhance your photos with AI up to 4×.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Hero Tactile Import Card
            ImportCard(
                onNavigateToPicker = {
                    photoPickerLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onBrowseFiles = {
                    fileBrowserLauncher.launch(arrayOf("image/*"))
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Recent Enhancements Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent",
                    style = MaterialTheme.typography.headlineSmall,
                    color = OnSurface
                )
                Text(
                    text = "See All",
                    style = MaterialTheme.typography.labelMedium,
                    color = Primary,
                    modifier = Modifier.tactileClick { onNavigateToHistory() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Recent Grid
            RecentGrid(viewModel)
        }
    }
}

@Composable
fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerLowest)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.AutoFixHigh,
                contentDescription = "Home",
                tint = Primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Home",
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        com.example.ui.components.ProfileAvatar()
    }
}

@Composable
fun ImportCard(onNavigateToPicker: () -> Unit = {}, onBrowseFiles: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .ceramicShadow(
                cornerRadius = 24.dp,
                offsetX = 6.dp, offsetY = 6.dp, blurRadius = 20.dp
            )
            .background(SurfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon Chamber
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(GradientStart, GradientEnd)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.AddPhotoAlternate, // Placeholder for add_photo_alternate
                contentDescription = "Import",
                tint = OnPrimary,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Import Images",
            style = MaterialTheme.typography.headlineSmall,
            color = OnSurface
        )
        Text(
            text = "Select one or multiple photos",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Primary Button
        Button(
            onClick = onNavigateToPicker,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .ceramicShadow(
                    cornerRadius = 24.dp,
                    offsetX = 0.dp, offsetY = 6.dp, blurRadius = 18.dp,
                    darkAlpha = 0.32f, lightAlpha = 0f
                ),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(0.dp),
            shape = CircleShape
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(listOf(GradientStart, GradientEnd)),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        tint = OnPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Choose from Gallery",
                        style = MaterialTheme.typography.labelLarge,
                        color = OnPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Secondary Button
        Button(
            onClick = onBrowseFiles,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .ceramicShadow(
                    cornerRadius = 24.dp,
                    offsetX = 4.dp, offsetY = 4.dp, blurRadius = 10.dp
                ),
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerLow),
            shape = CircleShape
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome, // placeholder
                    contentDescription = null,
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Browse Files",
                    style = MaterialTheme.typography.labelLarge,
                    color = OnSurface
                )
            }
        }
    }
}

@Composable
fun RecentGrid(viewModel: com.example.AppViewModel) {
    val items by viewModel.history.collectAsState(initial = emptyList())
    

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items.take(3)) { item ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .ceramicShadow(
                            cornerRadius = 16.dp,
                            offsetX = 4.dp, offsetY = 4.dp, blurRadius = 12.dp
                        )
                        .background(SurfaceContainerLowest, RoundedCornerShape(16.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.enhancedUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .ceramicShadow(
                            cornerRadius = 12.dp,
                            offsetX = 2.dp, offsetY = 2.dp, blurRadius = 6.dp
                        )
                        .background(SurfaceContainerLowest, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                }
            }
        }
    }
}
