package io.github.spw.island

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import io.github.spw.island.platform.FullscreenDetector
import io.github.spw.island.ui.IslandContent
import io.github.spw.island.ui.IslandListContent
import io.github.spw.island.ui.IslandTray
import io.github.spw.island.ui.LibraryWindow
import io.github.spw.island.ui.WindowMoveScheduler
import io.github.spw.island.ui.offscreen.IslandSurface
import io.github.spw.island.ui.offscreen.SceneRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Color
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.JWindow
import javax.swing.SwingUtilities
import javax.swing.Timer
import io.github.spw.island.ui.IslandBadge
import kotlin.math.abs

/**
 * 独立悬浮窗（离屏渲染版）。
 *
 * 窗口只贴一张位图，拖动与缩放都不触发 Compose 布局：
 * - 拖动：只移动窗口；
 * - 展开/收起：窗口尺寸逐帧补间，期间把上一帧按新尺寸拉伸，结束后再重建场景。
 */
class IslandApplication(
    private val store: IslandSettingsStore,
    private val controller: IslandController,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var islandFrame: JWindow? = null
    private var surface: IslandSurface? = null
    private var renderer: SceneRenderer? = null
    private var frameTimer: Timer? = null
    private var resizeTimer: Timer? = null
    private var revealTimer: Timer? = null
    private var autoCollapseTimer: Timer? = null
    private var watchJob: Job? = null

    private var dragging = false
    private var pressPoint: Point? = null
    private var dragOrigin: Point? = null
    private var pressOnProgress = false
    private var lastRenderNanos = 0L

    fun start() {
        SwingUtilities.invokeLater {
            val frame = createFrame()
            islandFrame = frame
            rebuildRenderer()
            startFrameLoop()
            applyIslandVisibility()
            IslandTray.install(store, controller, frame)
            restartAutoCollapse()
        }

        watchJob = scope.launch {
            while (isActive) {
                val behavior = store.settings.value.fullscreen
                controller.fullscreenActive.value =
                    behavior == FullscreenBehavior.Hide &&
                    FullscreenDetector.isForegroundFullscreen(listOf(TITLE))
                delay(1200)
            }
        }

        scope.launch { controller.visible.collect { SwingUtilities.invokeLater { applyIslandVisibility() } } }
        scope.launch { controller.fullscreenActive.collect { SwingUtilities.invokeLater { applyIslandVisibility() } } }
        scope.launch {
            store.settings.collect {
                SwingUtilities.invokeLater {
                    applyIslandVisibility()
                    // 样式或尺寸配置变化后窗口要跟着变
                    val frame = islandFrame
                    if (frame != null && frame.size != targetSize()) {
                        applyIslandMode()
                    }
                    restartAutoCollapse()
                }
            }
        }
        scope.launch { controller.collapsed.collect { SwingUtilities.invokeLater { applyIslandMode() } } }
        scope.launch {
            controller.listExpanded.collect { expanded ->
                if (!expanded) return@collect
                // 曲库改为独立 Compose 窗口：离屏场景没有输入法通道，搜索框需要真实 IME
                SwingUtilities.invokeLater {
                    controller.listExpanded.value = false
                    LibraryWindow.open(store, controller, islandFrame)
                }
            }
        }
    }

    fun stop() {
        watchJob?.cancel()
        scope.cancel()
        frameTimer?.stop()
        autoCollapseTimer?.stop()
        autoCollapseTimer = null
        resizeTimer?.stop()
        frameTimer = null
        resizeTimer = null
        IslandTray.uninstall()
        LibraryWindow.close()

        val frame = islandFrame
        val scene = renderer
        islandFrame = null
        surface = null
        renderer = null

        SwingUtilities.invokeLater {
            runCatching { scene?.close() }
            runCatching { frame?.dispose() }
        }
    }

    // region 窗口

    private fun createFrame(): JWindow {
        // JWindow：没有标题栏，也不会出现在任务栏/Alt-Tab 里
        val frame = JWindow().apply {
            size = targetSize()
            isAlwaysOnTop = store.settings.value.alwaysOnTop
            setIconImage(IslandBadge.icon())
        }

        // 逐像素透明：圆角由图像 alpha 决定，不会出现窗口底色描边
        runCatching { frame.background = Color(0, 0, 0, 0) }
        (frame.contentPane as? JComponent)?.let {
            it.isOpaque = false
            it.background = Color(0, 0, 0, 0)
        }
        (frame.rootPane as? JComponent)?.isOpaque = false

        val surface = IslandSurface()
        installInput(surface)
        this.surface = surface

        frame.contentPane.add(surface)
        placeBottomEnd(frame)
        return frame
    }

    private fun targetSize(): Dimension {
        val settings = store.settings.value
        return when {
            controller.collapsed.value -> if (settings.collapsedStyle == CollapsedStyle.Capsule) {
                Dimension(settings.capsuleWidth, settings.capsuleHeight)
            } else {
                Dimension(settings.collapsedSize, settings.collapsedSize)
            }

            controller.listExpanded.value -> Dimension(settings.expandedWidth, LIST_HEIGHT)
            else -> Dimension(settings.expandedWidth, settings.expandedHeight)
        }
    }

    private fun rebuildRenderer() {
        val surface = surface ?: return
        val width = surface.width.takeIf { it > 0 } ?: targetSize().width
        val height = surface.height.takeIf { it > 0 } ?: targetSize().height

        renderer?.close()
        IslandLog.info("重建离屏场景 ${width}x$height")
        renderer = SceneRenderer(
            width = width,
            height = height,
            density = Density(1f),
            content = { IslandContentRoot() },
        )
        renderNow()
    }

    private fun startFrameLoop() {
        frameTimer?.stop()
        autoCollapseTimer?.stop()
        autoCollapseTimer = null
        frameTimer = Timer(16) {
            val scene = renderer ?: return@Timer
            if (!scene.invalidated) return@Timer
            renderNow()
        }.apply { start() }
    }

    private fun renderNow() {
        val scene = renderer ?: return

        val now = System.nanoTime()
        if (scene.slowPath && now - lastRenderNanos < 33_000_000L) return
        lastRenderNanos = now

        val image = scene.render(now)
        if (image == null) {
            IslandLog.warn("渲染返回空帧")
            return
        }
        surface?.present(image)
    }

    // endregion

    // region 形态切换（带动画）

    /**
     * 形态切换：窗口尺寸一次到位（不做逐帧拉伸），
     * 展开时用圆角遮罩从封面区域"长开"，画面不缩放。
     */
    private fun applyIslandMode() {
        val frame = islandFrame ?: return
        val target = targetSize()
        val expanding = target.width > frame.width

        // 以左上角为锚点：收缩/展开时左上角不动，窗口向右下延伸或向左上收拢
        if (frame.size != target) {
            frame.size = target
            val fixed = clampFullyInside(frame.location.x, frame.location.y, target.width, target.height)
            frame.setLocation(fixed)
            surface?.revalidate()
        }

        val animated = store.settings.value.motion == MotionMode.Full

        SwingUtilities.invokeLater {
            rebuildRenderer()
            // 性能优先：完全不做动画
            if (expanding && animated) {
                animateReveal(target)
            } else {
                revealTimer?.stop()
                surface?.setReveal(null)
            }
            restartAutoCollapse()
        }
    }

    /** 展开 N 秒后自动收起；0 表示关闭。鼠标操作会重新计时。 */
    private fun restartAutoCollapse() {
        autoCollapseTimer?.stop()
        autoCollapseTimer = null

        val seconds = store.settings.value.autoCollapseSeconds
        if (seconds <= 0) return
        if (controller.collapsed.value) return

        autoCollapseTimer = Timer(seconds * 1000) {
            autoCollapseTimer = null
            if (!controller.collapsed.value && !dragging && !LibraryWindow.isOpen()) {
                controller.toggleCollapsed()
            }
        }.apply {
            isRepeats = false
            start()
        }
    }

    /** 从封面矩形遮罩展开到整窗，纯裁剪，无缩放。 */
    private fun animateReveal(target: Dimension) {
        revealTimer?.stop()

        val from = Rectangle2D.Float(COVER_LEFT, COVER_TOP, COVER_SIZE, COVER_SIZE)
        val to = Rectangle2D.Float(0f, 0f, target.width.toFloat(), target.height.toFloat())
        val startedAt = System.nanoTime()

        revealTimer = Timer(16) { event ->
            val elapsed = ((System.nanoTime() - startedAt) / 1_000_000L).toFloat()
            val t = (elapsed / 180f).coerceIn(0f, 1f)
            val eased = 1f - (1f - t) * (1f - t) * (1f - t)

            surface?.setReveal(
                Rectangle2D.Float(
                    from.x + (to.x - from.x) * eased,
                    from.y + (to.y - from.y) * eased,
                    from.width + (to.width - from.width) * eased,
                    from.height + (to.height - from.height) * eased,
                ),
            )

            if (t >= 1f) {
                (event.source as? Timer)?.stop()
                surface?.setReveal(null)
            }
        }.apply { start() }
    }

    // endregion

    // region 输入

    private fun installInput(surface: IslandSurface) {
        surface.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                surface.requestFocusInWindow()
                pressPoint = event.locationOnScreen
                dragOrigin = islandFrame?.location
                dragging = false
                pressOnProgress = isOnProgressBar(event.x, event.y)
                renderer?.press(event.x.toFloat(), event.y.toFloat(), System.currentTimeMillis())
                restartAutoCollapse()
            }

            override fun mouseReleased(event: MouseEvent) {
                val wasDragging = dragging
                dragging = false
                pressPoint = null
                dragOrigin = null
                pressOnProgress = false

                if (wasDragging) {
                    // 拖动期间已经发过取消释放，这里不再补发，避免算成一次点击
                    // 松手后把窗口完整推回屏幕内，避免贴边时只露出一点
                    islandFrame?.let { frame ->
                        val fixed = clampFullyInside(
                            frame.location.x,
                            frame.location.y,
                            frame.width,
                            frame.height,
                        )
                        WindowMoveScheduler.submit(fixed.x, fixed.y)
                    }
                    WindowMoveScheduler.end()
                } else {
                    renderer?.release(event.x.toFloat(), event.y.toFloat(), System.currentTimeMillis())
                }
            }

            override fun mouseExited(event: MouseEvent) {
                renderer?.exit(System.currentTimeMillis())
            }
        })

        surface.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(event: MouseEvent) {
                // 在进度条上拖动时交给 Compose 处理跳转，不移动窗口
                if (pressOnProgress) return

                val start = pressPoint ?: return
                val origin = dragOrigin ?: return
                val deltaX = event.locationOnScreen.x - start.x
                val deltaY = event.locationOnScreen.y - start.y

                if (!dragging && maxOf(abs(deltaX), abs(deltaY)) < 4) return
                if (!dragging) {
                    dragging = true
                    // 先向场景补一个"移出窗口"的释放，取消可能被识别的点击
                    renderer?.release(-1000f, -1000f, System.currentTimeMillis())
                    islandFrame?.let { WindowMoveScheduler.begin(it, 16) }
                }

                // 夹在屏幕范围内，避免拖飞后再也看不到窗口
                val frame = islandFrame
                val size = frame?.size ?: return
                val target = clampToScreen(origin.x + deltaX, origin.y + deltaY, size.width, size.height)
                WindowMoveScheduler.submit(target.x, target.y)
            }

            override fun mouseMoved(event: MouseEvent) {
                renderer?.move(event.x.toFloat(), event.y.toFloat(), System.currentTimeMillis())
            }
        })

        surface.addMouseWheelListener { event ->
            renderer?.scroll(
                event.x.toFloat(),
                event.y.toFloat(),
                event.preciseWheelRotation.toFloat(),
                System.currentTimeMillis(),
            )
        }
    }

    // endregion

    @Composable
    private fun IslandContentRoot() {
        val settings by store.settings.collectAsState()
        val collapsed by controller.collapsed.collectAsState()
        val listExpanded by controller.listExpanded.collectAsState()

        val onCoverClick = { controller.toggleCollapsed() }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                collapsed -> IslandContent(
                    controller = controller,
                    settings = settings,
                    collapsed = true,
                    onCoverClick = onCoverClick,
                )

                listExpanded -> IslandListContent(
                    controller = controller,
                    settings = settings,
                    onCoverClick = onCoverClick,
                )

                else -> IslandContent(
                    controller = controller,
                    settings = settings,
                    collapsed = false,
                    onCoverClick = onCoverClick,
                )
            }
        }
    }

    private fun applyIslandVisibility() {
        val frame = islandFrame ?: return
        val settings = store.settings.value
        val hiddenByFullscreen =
            settings.fullscreen == FullscreenBehavior.Hide && controller.fullscreenActive.value
        val shouldShow = controller.visible.value && !hiddenByFullscreen

        runCatching {
            if (frame.isVisible != shouldShow) frame.isVisible = shouldShow
            if (frame.isAlwaysOnTop != settings.alwaysOnTop) frame.isAlwaysOnTop = settings.alwaysOnTop
        }
    }

    /** 展开态进度条的命中区域（随窗口宽度变化）。 */
    private fun isOnProgressBar(x: Int, y: Int): Boolean {
        if (controller.collapsed.value || controller.listExpanded.value) return false
        val width = store.settings.value.expandedWidth
        return x in 104..(width - 8) && y in 56..92
    }

    /** 把窗口完整压回屏幕范围内；窗口比屏幕还大时贴左上角。 */
    private fun clampFullyInside(x: Int, y: Int, width: Int, height: Int): Point {
        val screen = Toolkit.getDefaultToolkit().screenSize
        val maxX = (screen.width - width).coerceAtLeast(0)
        val maxY = (screen.height - height).coerceAtLeast(0)
        return Point(x.coerceIn(0, maxX), y.coerceIn(0, maxY))
    }

    /** 保证窗口至少有一块留在屏幕内（允许部分出屏，但不允许完全消失）。 */
    private fun clampToScreen(x: Int, y: Int, width: Int, height: Int): Point {
        val screen = Toolkit.getDefaultToolkit().screenSize
        val visible = 24
        val maxX = screen.width - visible
        val maxY = screen.height - visible
        val minX = visible - width
        val minY = 0
        return Point(x.coerceIn(minX, maxX), y.coerceIn(minY, maxY))
    }

    private fun placeBottomEnd(frame: JWindow) {
        val screen = Toolkit.getDefaultToolkit().screenSize
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(
            GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration,
        )
        val x = screen.width - frame.width - 32 - insets.right
        val y = screen.height - frame.height - 56 - insets.bottom
        frame.setLocation(x, y)
    }

    companion object {
        const val TITLE = "音乐浮窗"

        private const val EXPANDED_WIDTH = 392
        private const val EXPANDED_HEIGHT = 148
        private const val LIST_HEIGHT = 560
        private const val COLLAPSED_SIZE = 88
        private const val CAPSULE_WIDTH = 220
        private const val CAPSULE_HEIGHT = 56

        // 展开态封面在窗口中的位置，用于遮罩展开动画
        private const val COVER_LEFT = 12f
        private const val COVER_TOP = 12f
        private const val COVER_SIZE = 84f
    }
}
