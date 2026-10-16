package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.ceramicShadow
import com.example.ui.components.tactileClick
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ImagePickerScreen
import com.example.ui.screens.EnhanceOptionsScreen
import com.example.ui.screens.ProcessingScreen
import com.example.ui.screens.ResultScreen
import com.example.ui.theme.*
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PixelBoostApp(viewModel: AppViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    Scaffold(
        bottomBar = {
            if (currentRoute in listOf("home", "history", "settings")) {
                BottomNavigationBar(navController = navController)
            }
        },
        containerColor = SurfaceContainerLow
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                androidx.compose.animation.slideInHorizontally(
                    initialOffsetX = { 300 },
                    animationSpec = androidx.compose.animation.core.tween(300)
                ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300))
            },
            exitTransition = {
                androidx.compose.animation.slideOutHorizontally(
                    targetOffsetX = { -300 },
                    animationSpec = androidx.compose.animation.core.tween(300)
                ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
            },
            popEnterTransition = {
                androidx.compose.animation.slideInHorizontally(
                    initialOffsetX = { -300 },
                    animationSpec = androidx.compose.animation.core.tween(300)
                ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300))
            },
            popExitTransition = {
                androidx.compose.animation.slideOutHorizontally(
                    targetOffsetX = { 300 },
                    animationSpec = androidx.compose.animation.core.tween(300)
                ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
            }
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToPicker = { navController.navigate("picker") },
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToHistory = { navController.navigate("history") },
                    onStartEnhance = { navController.navigate("enhance_options") },
                    onNavigateToProcessing = { navController.navigate("processing") },
                    onOpenResult = { entity ->
                        viewModel.openHistoricalResult(entity) {
                            navController.navigate("result")
                        }
                    }
                )
            }
            composable("history") {
                HistoryScreen(viewModel = viewModel, onOpenResult = { navController.navigate("result") })
            }
            composable("settings") {
                SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable("picker") {
                ImagePickerScreen(
                    onBack = { navController.popBackStack() },
                    onContinue = { urls ->
                        viewModel.setSelectedImages(urls)
                        navController.navigate("enhance_options")
                    }
                )
            }
            composable("enhance_options") {
                EnhanceOptionsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onStartEnhance = { navController.navigate("processing") }
                )
            }
            composable("processing") {
                ProcessingScreen(
                    viewModel = viewModel,
                    onCancel = {
                        viewModel.cancelProcessing()
                        if (!navController.popBackStack("enhance_options", false)) {
                            navController.navigate("home")
                        }
                    },
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate("home")
                        }
                    },
                    onFinish = {
                        navController.navigate("result") {
                            popUpTo("home") { inclusive = false }
                        }
                    }
                )
            }
            composable("result") {
                ResultScreen(
                    viewModel = viewModel,
                    onBack = {
                        if (viewModel.resultOrigin.value == ResultOrigin.History) {
                            navController.popBackStack("history", false)
                        } else {
                            navController.popBackStack("home", false)
                        }
                    },
                    onUpscaleAnother = {
                        viewModel.clearSelectedImages()
                        navController.popBackStack("home", false)
                    }
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(
        Triple("home", "Home", Icons.Outlined.Home),
        Triple("history", "History", Icons.Outlined.History),
        Triple("settings", "Settings", Icons.Outlined.Settings)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .ceramicShadow(
                    cornerRadius = 32.dp,
                    offsetX = 6.dp, offsetY = 6.dp, blurRadius = 18.dp
                )
                .background(SurfaceContainerLowest.copy(alpha = 0.9f), RoundedCornerShape(32.dp))
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = currentRoute == item.first
                
                Button(
                    onClick = {
                        navController.navigate(item.first) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) SecondaryFixed.copy(alpha = 0.4f) else Color.Transparent,
                        contentColor = if (isSelected) Primary else OnSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = item.third,
                            contentDescription = item.second,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.second,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

