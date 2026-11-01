package io.github.spw.island.platform

/**
 * 检测前台窗口是否全屏。
 *
 * 仅 Windows 实现：比较前台窗口矩形与主屏分辨率。
 * 其他平台返回 false，悬浮窗保持常显。
 */
object FullscreenDetector {

    private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    @Volatile
    private var failureLogged = false

    fun isForegroundFullscreen(ownWindowTitles: Collection<String> = emptyList()): Boolean {
        if (!isWindows) return false

        return runCatching {
            val user32 = com.sun.jna.platform.win32.User32.INSTANCE
            val hwnd = user32.GetForegroundWindow() ?: return false

            val title = CharArray(512)
            user32.GetWindowText(hwnd, title, title.size)
            val windowTitle = String(title).trim('\u0000')
            if (ownWindowTitles.any { it.isNotEmpty() && windowTitle == it }) return false

            val rect = com.sun.jna.platform.win32.WinDef.RECT()
            if (!user32.GetWindowRect(hwnd, rect)) return false

            val width = rect.right - rect.left
            val height = rect.bottom - rect.top
            if (width <= 0 || height <= 0) return false

            val screenWidth = user32.GetSystemMetrics(SM_CXSCREEN)
            val screenHeight = user32.GetSystemMetrics(SM_CYSCREEN)

            // 覆盖整个主屏，且没有可用的窗口装饰（全屏游戏/视频的常见形态）
            val coversScreen = width >= screenWidth - 2 && height >= screenHeight - 2
            val style = user32.GetWindowLong(hwnd, GWL_STYLE)
            val isPopup = style and WS_POPUP != 0
            coversScreen && (isPopup || windowTitle.isEmpty() || !hasCaption(style))
        }.getOrElse {
            if (!failureLogged) {
                failureLogged = true
                println("[SPW/ISLAND] 全屏检测不可用：${it.message}")
            }
            false
        }
    }

    private fun hasCaption(style: Int): Boolean = style and WS_CAPTION == WS_CAPTION

    private const val SM_CXSCREEN = 0
    private const val SM_CYSCREEN = 1
    private const val GWL_STYLE = -16
    private const val WS_POPUP = 0x80000000.toInt()
    private const val WS_CAPTION = 0x00C00000
}
