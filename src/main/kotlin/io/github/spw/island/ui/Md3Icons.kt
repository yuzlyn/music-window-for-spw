package io.github.spw.island.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 插件自带的极简图标，避免依赖 material-icons。 */
enum class IslandIcon {
    Play,
    Pause,
    Previous,
    Next,
    Library,
    Collapse,
    Close,
}

@Composable
fun IslandIconView(
    icon: IslandIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)

        when (icon) {
            IslandIcon.Play -> {
                val path = Path().apply {
                    moveTo(w * 0.32f, h * 0.22f)
                    lineTo(w * 0.78f, h * 0.5f)
                    lineTo(w * 0.32f, h * 0.78f)
                    close()
                }
                drawPath(path, tint)
            }

            IslandIcon.Pause -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.3f, h * 0.24f),
                    size = Size(w * 0.13f, h * 0.52f),
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.57f, h * 0.24f),
                    size = Size(w * 0.13f, h * 0.52f),
                )
            }

            IslandIcon.Previous, IslandIcon.Next -> {
                val mirrored = icon == IslandIcon.Next
                val barX = if (mirrored) 0.76f else 0.24f
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * barX, h * 0.26f),
                    size = Size(w * 0.09f, h * 0.48f),
                )
                val path = Path().apply {
                    if (mirrored) {
                        moveTo(w * 0.28f, h * 0.28f)
                        lineTo(w * 0.62f, h * 0.5f)
                        lineTo(w * 0.28f, h * 0.72f)
                    } else {
                        moveTo(w * 0.72f, h * 0.28f)
                        lineTo(w * 0.38f, h * 0.5f)
                        lineTo(w * 0.72f, h * 0.72f)
                    }
                    close()
                }
                drawPath(path, tint)
            }

            IslandIcon.Library -> {
                for (index in 0 until 3) {
                    val y = h * (0.3f + index * 0.2f)
                    drawCircle(tint, radius = w * 0.045f, center = Offset(w * 0.28f, y))
                    drawLine(
                        color = tint,
                        start = Offset(w * 0.42f, y),
                        end = Offset(w * 0.78f, y),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                }
            }

            IslandIcon.Collapse -> {
                drawLine(
                    color = tint,
                    start = Offset(w * 0.3f, h * 0.42f),
                    end = Offset(w * 0.5f, h * 0.62f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.5f, h * 0.62f),
                    end = Offset(w * 0.7f, h * 0.42f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }

            IslandIcon.Close -> {
                drawLine(
                    color = tint,
                    start = Offset(w * 0.3f, h * 0.3f),
                    end = Offset(w * 0.7f, h * 0.7f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.7f, h * 0.3f),
                    end = Offset(w * 0.3f, h * 0.7f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
