package io.github.spw.island.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fluent 风格进度条：细轨道 + 强调色填充 + 圆形拖拽指示点。
 * 无动画，绘制成本低于波浪形态。
 */
@Composable
fun FluentProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant,
    height: Dp = 14.dp,
    onSeek: ((Float) -> Unit)? = null,
) {
    val seekModifier = if (onSeek == null) {
        Modifier
    } else {
        Modifier.pointerInput(onSeek) {
            detectDragGestures(
                onDragStart = { offset ->
                    onSeek((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                },
                onDrag = { change, _ ->
                    change.consume()
                    onSeek((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                },
            )
        }.pointerInput(onSeek) {
            detectTapGestures { offset ->
                onSeek((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
            }
        }
    }

    Canvas(modifier = modifier.fillMaxWidth().height(height).then(seekModifier)) {
        val clamped = progress.coerceIn(0f, 1f)
        val trackHeight = size.height * 0.28f
        val centerY = size.height / 2f
        val thumbRadius = size.height * 0.34f
        val radius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = radius,
        )

        val filledWidth = size.width * clamped
        if (filledWidth > 0f) {
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(filledWidth, trackHeight),
                cornerRadius = radius,
            )
        }

        val thumbX = (size.width * clamped).coerceIn(thumbRadius, size.width - thumbRadius)
        drawCircle(color = color, radius = thumbRadius, center = Offset(thumbX, centerY))
    }
}
