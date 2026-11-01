package io.github.spw.island.ui.offscreen

import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import javax.swing.JComponent


/**
 * 只负责把离屏渲染好的整帧贴到窗口上。
 *
 * 支持"遮罩展开"：窗口瞬间变成目标尺寸，画面按圆角矩形从指定区域逐步露出，
 * 不做任何缩放，避免拉伸变形。
 */
class IslandSurface : JComponent() {

    private var frame: BufferedImage? = null
    private var revealRect: Rectangle2D? = null

    init {
        isOpaque = false
        isFocusable = true
    }

    fun present(image: BufferedImage) {
        frame = image
        repaint()
    }

    fun currentFrame(): BufferedImage? = frame

    /** 设置遮罩矩形；传 null 表示完整显示。 */
    fun setReveal(rect: Rectangle2D?) {
        revealRect = rect
        repaint()
    }

    override fun paintComponent(graphics: Graphics) {
        val image = frame ?: return
        if (width <= 0 || height <= 0) return

        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )

            revealRect?.let { rect ->
                if (rect.width <= 0.5 || rect.height <= 0.5) return
                g.clip(
                    RoundRectangle2D.Float(
                        rect.x.toFloat(),
                        rect.y.toFloat(),
                        rect.width.toFloat(),
                        rect.height.toFloat(),
                        CORNER_RADIUS,
                        CORNER_RADIUS,
                    ),
                )
            }

            g.drawImage(image, 0, 0, width, height, null)
        } finally {
            g.dispose()
        }
    }

    private companion object {
        const val CORNER_RADIUS = 16f
    }
}
