package io.github.spw.island.ui.offscreen

import androidx.compose.runtime.Composable
import io.github.spw.island.IslandLog
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt

/**
 * 离屏渲染宿主。
 *
 * 用 Compose 的 ImageComposeScene 把界面渲染成 Skia 图，再转成 BufferedImage。
 * 窗口里不挂实时 Compose 表面，拖动时只有窗口位移，不触发任何渲染。
 */
class SceneRenderer(
    private val width: Int,
    private val height: Int,
    density: Density,
    content: @Composable () -> Unit,
) : AutoCloseable {

    private val scene = ImageComposeScene(
        width,
        height,
        density,
        Dispatchers.Unconfined,
        content,
    )

    // 注意：不能在 apply 块里写 width/height，那会解析成 Bitmap 自身的宽高（此刻为 0）
    private val bitmap = Bitmap().also { it.allocN32Pixels(this.width, this.height, false) }
    private val canvas = org.jetbrains.skia.Canvas(bitmap)
    private val info: ImageInfo = bitmap.imageInfo
    private val rowBytes = this.width * 4

    private val frame: BufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE)
    private val pixels: IntArray = (frame.raster.dataBuffer as DataBufferInt).data

    var lastFrame: BufferedImage? = null
        private set

    val invalidated: Boolean
        get() = runCatching { scene.hasInvalidations() }.getOrDefault(true)

    /** PNG 兜底路径较慢，调用方可据此限帧。 */
    val slowPath: Boolean
        get() = path == Path.Png

    fun render(nanoTime: Long): BufferedImage? {
        val image = runCatching { scene.render(nanoTime) }
            .onFailure { IslandLog.error("render 失败：${it.message}") }
            .getOrNull() ?: return lastFrame

        val converted = convert(image)
        if (converted == null) {
            // 画一个纯色回退帧：如果窗口仍然空白，说明问题在贴图而不是像素转换
            fillFallback()
            return frame
        }

        lastFrame = converted
        return converted
    }

    /** 依次尝试：位图直读 → 画布绘制后读 → PNG 编码解码；成功后固定使用该路径。 */
    private fun convert(image: Image): BufferedImage? {
        if (path == Path.Png) return convertViaPng(image)

        val result = convertViaBitmap(image)
        if (result != null) return result

        if (path == Path.Auto) {
            val png = convertViaPng(image)
            if (png != null) {
                path = Path.Png
                IslandLog.info("像素路径切换为 PNG 编码（位图读取不可用）")
            }
            return png
        }
        return null
    }

    /** PNG 兜底：只依赖 Image 编码，不依赖 Bitmap 分配。 */
    private fun convertViaPng(image: Image): BufferedImage? = runCatching {
        val data = image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)
            ?: return null
        val bytes = data.bytes
        if (bytes.isEmpty()) return null
        javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
    }.onFailure {
        if (!warnedPng) {
            warnedPng = true
            IslandLog.warn("PNG 兜底失败：${it.message}")
        }
    }.getOrNull()

    private fun convertViaBitmap(image: Image): BufferedImage? = runCatching {
        // 直接 readPixels 在部分 Skiko 后端会失败；
        // 先把图绘到自建的 raster 位图上，再从位图读像素。
        runCatching {
            canvas.clear(0)
            canvas.drawImage(image, 0f, 0f)
        }.onFailure {
            if (!warnedDraw) {
                warnedDraw = true
                IslandLog.warn("画布绘制失败：${it.message}")
            }
        }

        var bytes = bitmap.readPixels(info, rowBytes, 0, 0)
        if (bytes == null) {
            // 次级路径：让 Skia 直接把像素写进我们的 Pixmap
            runCatching { image.peekPixels(bitmap.peekPixels()) }
            bytes = bitmap.readPixels(info, rowBytes, 0, 0)
        }
        if (bytes == null) {
            if (!warnedPixels) {
                warnedPixels = true
                IslandLog.warn("Bitmap.readPixels 返回 null，info=$info")
            }
            return null
        }

        var index = 0
        var pixel = 0
        while (pixel < pixels.size && index + 3 < bytes.size) {
            val blue = bytes[index].toInt() and 0xFF
            val green = bytes[index + 1].toInt() and 0xFF
            val red = bytes[index + 2].toInt() and 0xFF
            val alpha = bytes[index + 3].toInt() and 0xFF
            pixels[pixel] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            index += 4
            pixel++
        }

        val center = pixels[pixels.size / 2 + pixels.size / 4]
        if (firstConversion) {
            firstConversion = false
            IslandLog.info(
                "首帧转换完成 size=${image.width}x${image.height} bytes=${bytes.size} " +
                    "center=${Integer.toHexString(center)}",
            )
        }
        frame
    }.onFailure {
        IslandLog.error("像素转换异常：${it.message}")
    }.getOrNull()

    private fun fillFallback() {
        java.util.Arrays.fill(pixels, FALLBACK_COLOR)
        IslandLog.warn("使用回退色填充画面")
    }

    private var firstConversion = true
    private var warnedDraw = false
    private var warnedPixels = false
    private var warnedPng = false
    private var path = Path.Auto

    private enum class Path { Auto, Bitmap, Png }

    private companion object {
        const val FALLBACK_COLOR = 0xFF7F3FBF.toInt()
    }

    fun press(x: Float, y: Float, timeMillis: Long) = send(PointerEventType.Press, x, y, timeMillis)

    fun move(x: Float, y: Float, timeMillis: Long) = send(PointerEventType.Move, x, y, timeMillis)

    fun release(x: Float, y: Float, timeMillis: Long) = send(PointerEventType.Release, x, y, timeMillis)

    fun exit(timeMillis: Long) {
        runCatching { scene.sendPointerEvent(PointerEventType.Exit, Offset.Zero, timeMillis = timeMillis) }
    }

    fun scroll(x: Float, y: Float, deltaY: Float, timeMillis: Long) {
        runCatching {
            scene.sendPointerEvent(
                eventType = PointerEventType.Scroll,
                position = Offset(x, y),
                scrollDelta = Offset(0f, deltaY),
                timeMillis = timeMillis,
            )
        }
    }

    private fun send(eventType: PointerEventType, x: Float, y: Float, timeMillis: Long) {
        runCatching {
            scene.sendPointerEvent(eventType = eventType, position = Offset(x, y), timeMillis = timeMillis)
        }
    }

    override fun close() {
        runCatching { scene.close() }
        runCatching { bitmap.close() }
    }
}
