package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.tactileClick(
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
): Modifier = composed {
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by actualInteractionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, animationSpec = tween(durationMillis = 100), label = "scale")
    this.graphicsLayer { scaleX = scale; scaleY = scale }.clickable(interactionSource = actualInteractionSource, indication = null, onClick = onClick)
}

fun Modifier.ceramicShadow(
    cornerRadius: Dp = 24.dp, offsetX: Dp = 6.dp, offsetY: Dp = 6.dp, blurRadius: Dp = 18.dp, darkAlpha: Float = 0.35f, lightAlpha: Float = 0.95f
) = composed {
    val isDark = com.example.ui.theme.LocalIsDarkTheme.current
    val actualDarkAlpha = if (isDark) 0.55f else darkAlpha
    val actualLightAlpha = if (isDark) 0.12f else lightAlpha
    val db = if (isDark) 0 else 166
    val dg = if (isDark) 0 else 180
    val d_b = if (isDark) 0 else 200
    this.drawBehind {
        val darkColor = android.graphics.Color.argb((255 * actualDarkAlpha).toInt(), db, dg, d_b)
        val lightColor = android.graphics.Color.argb((255 * actualLightAlpha).toInt(), 255, 255, 255)
        drawIntoCanvas { canvas ->
            val paintDark = android.graphics.Paint().apply { color = darkColor; maskFilter = android.graphics.BlurMaskFilter(blurRadius.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL) }
            val paintLight = android.graphics.Paint().apply { color = lightColor; maskFilter = android.graphics.BlurMaskFilter(blurRadius.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL) }
            canvas.nativeCanvas.drawRoundRect(offsetX.toPx(), offsetY.toPx(), size.width + offsetX.toPx(), size.height + offsetY.toPx(), cornerRadius.toPx(), cornerRadius.toPx(), paintDark)
            canvas.nativeCanvas.drawRoundRect(-offsetX.toPx(), -offsetY.toPx(), size.width - offsetX.toPx(), size.height - offsetY.toPx(), cornerRadius.toPx(), cornerRadius.toPx(), paintLight)
        }
    }
}

fun Modifier.ceramicInnerShadow(
    cornerRadius: Dp = 24.dp, offsetX: Dp = 3.dp, offsetY: Dp = 3.dp, blurRadius: Dp = 6.dp, darkAlpha: Float = 0.35f, lightAlpha: Float = 0.90f
) = composed {
    val isDark = com.example.ui.theme.LocalIsDarkTheme.current
    val actualDarkAlpha = if (isDark) 0.55f else darkAlpha
    val actualLightAlpha = if (isDark) 0.12f else lightAlpha
    val db = if (isDark) 0 else 166
    val dg = if (isDark) 0 else 180
    val d_b = if (isDark) 0 else 200
    this.drawWithContent {
        drawContent()
        val darkColor = android.graphics.Color.argb((255 * actualDarkAlpha).toInt(), db, dg, d_b)
        val lightColor = android.graphics.Color.argb((255 * actualLightAlpha).toInt(), 255, 255, 255)
        drawIntoCanvas { canvas ->
            val paintDark = android.graphics.Paint().apply { color = darkColor; maskFilter = android.graphics.BlurMaskFilter(blurRadius.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL); style = android.graphics.Paint.Style.STROKE; strokeWidth = offsetX.toPx() * 2 }
            val paintLight = android.graphics.Paint().apply { color = lightColor; maskFilter = android.graphics.BlurMaskFilter(blurRadius.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL); style = android.graphics.Paint.Style.STROKE; strokeWidth = offsetX.toPx() * 2 }
            canvas.nativeCanvas.save()
            val clipPath = android.graphics.Path().apply { addRoundRect(0f, 0f, size.width, size.height, cornerRadius.toPx(), cornerRadius.toPx(), android.graphics.Path.Direction.CW) }
            canvas.nativeCanvas.clipPath(clipPath)
            canvas.nativeCanvas.drawRoundRect(-offsetX.toPx(), -offsetY.toPx(), size.width + offsetX.toPx(), size.height + offsetY.toPx(), cornerRadius.toPx(), cornerRadius.toPx(), paintDark)
            canvas.nativeCanvas.drawRoundRect(offsetX.toPx(), offsetY.toPx(), size.width + offsetX.toPx(), size.height + offsetY.toPx(), cornerRadius.toPx(), cornerRadius.toPx(), paintLight)
            canvas.nativeCanvas.restore()
        }
    }
}