import re

# 1. Fix AppViewModel.kt (Remove conflicting themeMode, keep themeIndex)
vm_path = 'app/src/main/java/com/example/AppViewModel.kt'
with open(vm_path, 'r') as f: vm = f.read()
vm = re.sub(r'private val _themeMode = MutableStateFlow\(0\)\s*val themeMode = _themeMode\.asStateFlow\(\)\s*fun setThemeMode\(mode: Int\) \{\s*_themeMode\.value = mode\s*\}', '', vm)
with open(vm_path, 'w') as f: f.write(vm)

# 2. Fix MainActivity.kt (Wire to themeIndex)
ma_path = 'app/src/main/java/com/example/MainActivity.kt'
with open(ma_path, 'r') as f: ma = f.read()
ma = ma.replace('val themeMode by appViewModel.themeMode.collectAsState()', 'val themeIndex by appViewModel.themeIndex.collectAsState()')
ma = ma.replace('when (themeMode)', 'when (themeIndex)')
ma = ma.replace('themeIndex = themeMode', 'themeIndex = themeIndex')
with open(ma_path, 'w') as f: f.write(ma)

# 3. Fix SettingsScreen.kt (Connect segmented control to themeIndex)
ss_path = 'app/src/main/java/com/example/ui/screens/SettingsScreen.kt'
with open(ss_path, 'r') as f: ss = f.read()
ss = ss.replace('selectedIndex = viewModel.themeMode.collectAsState().value, onSelect = { viewModel.setThemeMode(it) }', 'selectedIndex = viewModel.themeIndex.collectAsState().value, onSelect = { viewModel.setThemeIndex(it) }')
with open(ss_path, 'w') as f: f.write(ss)

# 4. Fix Theme.kt (Provide Dark Theme State locally)
theme_path = 'app/src/main/java/com/example/ui/theme/Theme.kt'
with open(theme_path, 'r') as f: theme = f.read()
if 'LocalIsDarkTheme' not in theme:
    theme = theme.replace('import androidx.core.view.WindowCompat', 'import androidx.core.view.WindowCompat\nimport androidx.compose.runtime.CompositionLocalProvider\nimport androidx.compose.runtime.staticCompositionLocalOf')
    theme = theme.replace('private val LightColorScheme', 'val LocalIsDarkTheme = staticCompositionLocalOf { false }\n\nprivate val LightColorScheme')
    theme = theme.replace('MaterialTheme(\n        colorScheme = colorScheme,\n        typography = Typography,\n        content = content\n    )', 'CompositionLocalProvider(LocalIsDarkTheme provides isDark) {\n        MaterialTheme(\n            colorScheme = colorScheme,\n            typography = Typography,\n            content = content\n        )\n    }')
with open(theme_path, 'w') as f: f.write(theme)

# 5. Fix Color.kt (Adaptive Colors for Dark Neumorphism)
color_path = 'app/src/main/java/com/example/ui/theme/Color.kt'
color_code = """package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Primary = Color(0xFF006194)
val PrimaryContainer = Color(0xFF007BB9)
val OnPrimary = Color(0xFFFFFFFF)
val Secondary = Color(0xFF00668A)
val SecondaryContainer = Color(0xFF40C2FD)
val OnSecondaryContainer = Color(0xFF004D6A)
val ErrorColor = Color(0xFFBA1A1A)
val GradientStart = Color(0xFF40C2FD)
val GradientEnd = Color(0xFF006194)
val SecondaryFixed = Color(0xFFC4E7FF)
val OnSecondaryFixed = Color(0xFF001E2C)

val SurfaceBright @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF0B0F14) else Color(0xFFF8F9FF)
val SurfaceContainerLowest @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF18222C) else Color(0xFFFFFFFF)
val SurfaceContainerLow @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF141C24) else Color(0xFFEFF4FF)
val SurfaceContainer @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF0F151B) else Color(0xFFE5EEFF)
val SurfaceContainerHigh @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF213145) else Color(0xFFDCE9FF)
val SurfaceContainerHighest @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF2A3C54) else Color(0xFFD3E4FE)
val OnSurface @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFFFFFFF) else Color(0xFF0B1C30)
val OnSurfaceVariant @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFBFC7D2) else Color(0xFF3F4850)
val Outline @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF5A6675) else Color(0xFF707881)
val OutlineVariant @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF3F4850) else Color(0xFFBFC7D2)
val InverseSurface @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFEAF1FF) else Color(0xFF213145)
val InverseOnSurface @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF213145) else Color(0xFFEAF1FF)
"""
with open(color_path, 'w') as f: f.write(color_code)

# 6. Fix NeumorphicModifier.kt (Adaptive Shadows)
mod_path = 'app/src/main/java/com/example/ui/components/NeumorphicModifier.kt'
with open(mod_path, 'r') as f: mod = f.read()
new_ceramic = """fun Modifier.ceramicShadow(
    cornerRadius: Dp = 24.dp,
    offsetX: Dp = 6.dp,
    offsetY: Dp = 6.dp,
    blurRadius: Dp = 18.dp,
    darkAlpha: Float = 0.35f,
    lightAlpha: Float = 0.95f
) = composed {
    val isDark = com.example.ui.theme.LocalIsDarkTheme.current
    val actualDarkAlpha = if (isDark) 0.8f else darkAlpha
    val actualLightAlpha = if (isDark) 0.03f else lightAlpha
    val db = if (isDark) 0 else 166
    val dg = if (isDark) 0 else 180
    val d_b = if (isDark) 0 else 200

    this.drawBehind {
        val darkColor = android.graphics.Color.argb((255 * actualDarkAlpha).toInt(), db, dg, d_b)
        val lightColor = android.graphics.Color.argb((255 * actualLightAlpha).toInt(), 255, 255, 255)
        drawIntoCanvas { canvas ->
            val paintDark = android.graphics.Paint().apply {
                color = darkColor
                maskFilter = android.graphics.BlurMaskFilter(blurRadius.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            val paintLight = android.graphics.Paint().apply {
                color = lightColor
                maskFilter = android.graphics.BlurMaskFilter(blurRadius.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            canvas.nativeCanvas.drawRoundRect(offsetX.toPx(), offsetY.toPx(), size.width + offsetX.toPx(), size.height + offsetY.toPx(), cornerRadius.toPx(), cornerRadius.toPx(), paintDark)
            canvas.nativeCanvas.drawRoundRect(-offsetX.toPx(), -offsetY.toPx(), size.width - offsetX.toPx(), size.height - offsetY.toPx(), cornerRadius.toPx(), cornerRadius.toPx(), paintLight)
        }
    }
}

fun Modifier.ceramicInnerShadow(
    cornerRadius: Dp = 24.dp,
    offsetX: Dp = 3.dp,
    offsetY: Dp = 3.dp,
    blurRadius: Dp = 6.dp,
    darkAlpha: Float = 0.35f,
    lightAlpha: Float = 0.90f
) = composed {
    val isDark = com.example.ui.theme.LocalIsDarkTheme.current
    val actualDarkAlpha = if (isDark) 0.8f else darkAlpha
    val actualLightAlpha = if (isDark) 0.03f else lightAlpha
    val db = if (isDark) 0 else 166
    val dg = if (isDark) 0 else 180
    val d_b = if (isDark) 0 else 200

    this.drawWithContent {
        drawContent()
        val darkColor = android.graphics.Color.argb((255 * actualDarkAlpha).toInt(), db, dg, d_b)
        val lightColor = android.graphics.Color.argb((255 * actualLightAlpha).toInt(), 255, 255, 255)
        drawIntoCanvas { canvas ->
            val paintDark = android.graphics.Paint().apply {
                color = darkColor
                maskFilter = android.graphics.BlurMaskFilter(blurRadius.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = offsetX.toPx() * 2
            }
            val paintLight = android.graphics.Paint().apply {
                color = lightColor
                maskFilter = android.graphics.BlurMaskFilter(blurRadius.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = offsetX.toPx() * 2
            }
            canvas.nativeCanvas.save()
            val clipPath = android.graphics.Path().apply { addRoundRect(0f, 0f, size.width, size.height, cornerRadius.toPx(), cornerRadius.toPx(), android.graphics.Path.Direction.CW) }
            canvas.nativeCanvas.clipPath(clipPath)
            canvas.nativeCanvas.drawRoundRect(-offsetX.toPx(), -offsetY.toPx(), size.width + offsetX.toPx(), size.height + offsetY.toPx(), cornerRadius.toPx(), cornerRadius.toPx(), paintDark)
            canvas.nativeCanvas.drawRoundRect(offsetX.toPx(), offsetY.toPx(), size.width + offsetX.toPx(), size.height + offsetY.toPx(), cornerRadius.toPx(), cornerRadius.toPx(), paintLight)
            canvas.nativeCanvas.restore()
        }
    }
}"""
mod = re.sub(r'fun Modifier\.ceramicShadow\(.*', new_ceramic, mod, flags=re.DOTALL)
with open(mod_path, 'w') as f: f.write(mod)
