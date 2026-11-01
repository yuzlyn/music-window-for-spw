package io.github.spw.island.ui

import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Image
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/**
 * 插件图标：圆角方形 + 音符，避免使用 JDK 默认的 Java 图标。
 * 运行时生成，不需要打包图片资源。
 */
object IslandBadge {

    private var cached: Image? = null
    private var cachedTray: Image? = null

    /**
     * 托盘图标：Fluent 风格的单色音符。
     * 白色填充 + 深色描边，深色/浅色任务栏都能看清。
     */
    fun tray(size: Int = 16): Image {
        cachedTray?.let { return it }

        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

            val unit = size / 16f
            val heads = listOf(
                Ellipse2D.Float(3.4f * unit, 10.4f * unit, 4.0f * unit, 3.4f * unit),
                Ellipse2D.Float(7.6f * unit, 8.8f * unit, 4.0f * unit, 3.4f * unit),
            )
            val stem = Path2D.Float().apply {
                moveTo(7.2f * unit, 10.4f * unit)
                lineTo(7.2f * unit, 3.4f * unit)
                lineTo(11.4f * unit, 2.4f * unit)
                lineTo(11.4f * unit, 9.2f * unit)
            }

            // 描边
            g.color = Color(0x20, 0x20, 0x20, 220)
            g.stroke = BasicStroke(2.4f * unit, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            heads.forEach { g.draw(it) }
            g.draw(stem)

            // 填充
            g.color = Color.WHITE
            heads.forEach { g.fill(it) }
            g.stroke = BasicStroke(1.3f * unit, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(stem)
        } finally {
            g.dispose()
        }

        cachedTray = image
        return image
    }

    fun icon(size: Int = 64): Image {
        cached?.let { return it }

        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

            val radius = size * 0.28f
            g.paint = GradientPaint(
                0f,
                0f,
                Color(0x00, 0x5F, 0xB8),
                size.toFloat(),
                size.toFloat(),
                Color(0x60, 0xCD, 0xFF),
            )
            g.fill(RoundRectangle2D.Float(0f, 0f, size.toFloat(), size.toFloat(), radius, radius))

            // 音符：两个符头 + 一条符干 + 旗
            val unit = size / 16f
            g.color = Color.WHITE
            g.fill(Ellipse2D.Float(4.2f * unit, 10.2f * unit, 3.4f * unit, 2.8f * unit))
            g.fill(Ellipse2D.Float(7.6f * unit, 8.8f * unit, 3.4f * unit, 2.8f * unit))

            g.stroke = BasicStroke(1.1f * unit, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val stem = Path2D.Float().apply {
                moveTo(7.5f * unit, 10.1f * unit)
                lineTo(7.5f * unit, 4.4f * unit)
                lineTo(11.0f * unit, 3.6f * unit)
                lineTo(11.0f * unit, 9.0f * unit)
            }
            g.draw(stem)
        } finally {
            g.dispose()
        }

        cached = image
        return image
    }
}
