package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF006194),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF007BB9),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondary = androidx.compose.ui.graphics.Color(0xFF00668A),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF40C2FD),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF004D6A),
    error = ErrorColor,
    onError = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    background = androidx.compose.ui.graphics.Color(0xFFF8F9FF),
    onBackground = androidx.compose.ui.graphics.Color(0xFF0B1C30),
    surface = androidx.compose.ui.graphics.Color(0xFFF8F9FF),
    onSurface = androidx.compose.ui.graphics.Color(0xFF0B1C30),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEFF4FF),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF3F4850),
    outline = androidx.compose.ui.graphics.Color(0xFF707881),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFBFC7D2)
)

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF000000),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF2A2A2A),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF000000),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF2A2A2A),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    error = ErrorColor,
    onError = androidx.compose.ui.graphics.Color(0xFF000000),
    background = androidx.compose.ui.graphics.Color(0xFF000000),
    onBackground = androidx.compose.ui.graphics.Color(0xFFF1F1F1),
    surface = androidx.compose.ui.graphics.Color(0xFF000000),
    onSurface = androidx.compose.ui.graphics.Color(0xFFF1F1F1),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF000000),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF888888),
    outline = androidx.compose.ui.graphics.Color(0xFF333333),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF1A1A1A)
)

@Composable
fun MyApplicationTheme(
    themeIndex: Int = 0,

    content: @Composable () -> Unit
) {
    val isDark = when(themeIndex) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val statusBarBg = (if (isDark) androidx.compose.ui.graphics.Color(0xFF000000) else androidx.compose.ui.graphics.Color(0xFFFFFFFF)).copy(alpha = 0.8f).toArgb()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = statusBarBg
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    CompositionLocalProvider(LocalIsDarkTheme provides isDark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
