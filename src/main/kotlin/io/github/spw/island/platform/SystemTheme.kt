package io.github.spw.island.platform

/**
 * 读取 Windows 的应用深浅色设置。
 *
 * 离屏渲染的 Compose 场景拿不到宿主的窗口主题信息，
 * 因此直接读注册表，避免一直按浅色渲染。
 */
object SystemTheme {

    private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    fun isDark(): Boolean {
        if (!isWindows) return false
        return runCatching {
            com.sun.jna.platform.win32.Advapi32Util.registryGetIntValue(
                com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER,
                "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "AppsUseLightTheme",
            ) == 0
        }.getOrDefault(false)
    }
}
