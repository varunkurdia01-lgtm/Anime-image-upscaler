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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.tactileClick(
    interactionSource: MutableInteractionSource? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = composed {
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by actualInteractionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = actualInteractionSource,
            indication = null,
            role = role,
            onClick = onClick
        )
}

fun Modifier.ceramicShadow(
    cornerRadius: Dp = 24.dp,
    offsetX: Dp = 6.dp,
    offsetY: Dp = 6.dp,
    blurRadius: Dp = 18.dp,
    darkAlpha: Float = 0.35f,
    lightAlpha: Float = 0.95f
) = composed {
    val isDark = com.example.ui.theme.LocalIsDarkTheme.current
    val actualDarkAlpha = if (isDark) 0.55f else darkAlpha
    val actualLightAlpha = if (isDark) 0.12f else lightAlpha
    val db = if (isDark) 0 else 166
    val dg = if (isDark) 0 else 180
    val d_b = if (isDark) 0 else 200

    val darkColor = remember(actualDarkAlpha, db, dg, d_b) {
        android.graphics.Color.argb((255 * actualDarkAlpha).toInt(), db, dg, d_b)
    }
    val lightColor = remember(actualLightAlpha) {
        android.graphics.Color.argb((255 * actualLightAlpha).toInt(), 255, 255, 255)
    }

    val density = LocalDensity.current
    val offsetXPx = with(density) { offsetX.toPx() }
    val offsetYPx = with(density) { offsetY.toPx() }
    val blurRadiusPx = with(density) { blurRadius.toPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }

    val paintDark = remember(darkColor, blurRadiusPx) {
        android.graphics.Paint().apply {
            color = darkColor
            if (blurRadiusPx > 0f) {
                maskFilter = android.graphics.BlurMaskFilter(blurRadiusPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
        }
    }
    val paintLight = remember(lightColor, blurRadiusPx) {
        android.graphics.Paint().apply {
            color = lightColor
            if (blurRadiusPx > 0f) {
                maskFilter = android.graphics.BlurMaskFilter(blurRadiusPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
        }
    }

    this.drawBehind {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(
                offsetXPx,
                offsetYPx,
                size.width + offsetXPx,
                size.height + offsetYPx,
                cornerRadiusPx,
                cornerRadiusPx,
                paintDark
            )
            canvas.nativeCanvas.drawRoundRect(
                -offsetXPx,
                -offsetYPx,
                size.width - offsetXPx,
                size.height - offsetYPx,
                cornerRadiusPx,
                cornerRadiusPx,
                paintLight
            )
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
    val actualDarkAlpha = if (isDark) 0.55f else darkAlpha
    val actualLightAlpha = if (isDark) 0.12f else lightAlpha
    val db = if (isDark) 0 else 166
    val dg = if (isDark) 0 else 180
    val d_b = if (isDark) 0 else 200

    val darkColor = remember(actualDarkAlpha, db, dg, d_b) {
        android.graphics.Color.argb((255 * actualDarkAlpha).toInt(), db, dg, d_b)
    }
    val lightColor = remember(actualLightAlpha) {
        android.graphics.Color.argb((255 * actualLightAlpha).toInt(), 255, 255, 255)
    }

    val density = LocalDensity.current
    val offsetXPx = with(density) { offsetX.toPx() }
    val offsetYPx = with(density) { offsetY.toPx() }
    val blurRadiusPx = with(density) { blurRadius.toPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val strokeWidthPx = offsetXPx * 2

    val paintDark = remember(darkColor, blurRadiusPx, strokeWidthPx) {
        android.graphics.Paint().apply {
            color = darkColor
            if (blurRadiusPx > 0f) {
                maskFilter = android.graphics.BlurMaskFilter(blurRadiusPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = strokeWidthPx
        }
    }
    val paintLight = remember(lightColor, blurRadiusPx, strokeWidthPx) {
        android.graphics.Paint().apply {
            color = lightColor
            if (blurRadiusPx > 0f) {
                maskFilter = android.graphics.BlurMaskFilter(blurRadiusPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = strokeWidthPx
        }
    }
    val clipPath = remember { android.graphics.Path() }

    this.drawWithContent {
        drawContent()
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.save()
            clipPath.reset()
            clipPath.addRoundRect(
                0f, 0f, size.width, size.height,
                cornerRadiusPx, cornerRadiusPx,
                android.graphics.Path.Direction.CW
            )
            canvas.nativeCanvas.clipPath(clipPath)
            canvas.nativeCanvas.drawRoundRect(
                -offsetXPx,
                -offsetYPx,
                size.width + offsetXPx,
                size.height + offsetYPx,
                cornerRadiusPx,
                cornerRadiusPx,
                paintDark
            )
            canvas.nativeCanvas.drawRoundRect(
                offsetXPx,
                offsetYPx,
                size.width + offsetXPx,
                size.height + offsetYPx,
                cornerRadiusPx,
                cornerRadiusPx,
                paintLight
            )
            canvas.nativeCanvas.restore()
        }
    }
}