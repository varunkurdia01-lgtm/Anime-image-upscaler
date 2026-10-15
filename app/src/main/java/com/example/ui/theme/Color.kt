package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// TRUE MONOCHROME AMOLED NEUMORPHISM (MOCKUP MATCH)
val Primary @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFFFFFFF) else Color(0xFF006194)
val PrimaryContainer @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF262626) else Color(0xFF007BB9)
val OnPrimary @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF000000) else Color(0xFFFFFFFF)
val Secondary @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFE0E0E0) else Color(0xFF00668A)
val SecondaryContainer @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF262626) else Color(0xFF40C2FD)
val OnSecondaryContainer @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFFFFFFF) else Color(0xFF004D6A)
val ErrorColor = Color(0xFFE57373)
val GradientStart @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF2E2E2E) else Color(0xFF40C2FD)
val GradientEnd @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF1A1A1A) else Color(0xFF006194)
val SecondaryFixed @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF262626) else Color(0xFFC4E7FF)
val OnSecondaryFixed @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFFFFFFF) else Color(0xFF001E2C)

// SURFACES: Crucial for Shadow Visibility
val SurfaceBright @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF000000) else Color(0xFFF8F9FF) // Pure Black App Background
val SurfaceContainerLow @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF000000) else Color(0xFFEFF4FF) 
val SurfaceContainerLowest @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF141414) else Color(0xFFFFFFFF) // Base Cards (Charcoal Grey)
val SurfaceContainer @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF080808) else Color(0xFFE5EEFF) // Recessed Wells (Darker)
val SurfaceContainerHigh @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF262626) else Color(0xFFDCE9FF) // Toggles / Active Thumbs
val SurfaceContainerHighest @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF2E2E2E) else Color(0xFFD3E4FE)
val OnSurface @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFF5F5F5) else Color(0xFF0B1C30) // Crisp White Text
val OnSurfaceVariant @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFA0A0A0) else Color(0xFF3F4850) // Soft Grey Text
val Outline @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF404040) else Color(0xFF707881)
val OutlineVariant @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF1A1A1A) else Color(0xFFBFC7D2)
val InverseSurface @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFEAF1FF) else Color(0xFF213145)
val InverseOnSurface @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF141414) else Color(0xFFEAF1FF)
