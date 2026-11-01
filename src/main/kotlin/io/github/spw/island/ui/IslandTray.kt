package io.github.spw.island.ui

import io.github.spw.island.FullscreenBehavior
import io.github.spw.island.IslandController
import io.github.spw.island.IslandLog
import io.github.spw.island.IslandSettingsStore
import java.awt.CheckboxMenuItem
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import javax.swing.SwingUtilities

/**
 * 系统托盘图标。
 *
 * 用 AWT 原生 PopupMenu（Windows 上是系统菜单），
 * 视觉风格跟随 Windows，而不是 Swing 默认外观。
 */
object IslandTray {

    private var trayIcon: TrayIcon? = null

    fun install(
        store: IslandSettingsStore,
        controller: IslandController,
        owner: java.awt.Window?,
    ) {
        if (trayIcon != null) return
        if (!SystemTray.isSupported()) {
            IslandLog.warn("系统托盘不可用，跳过托盘图标")
            return
        }

        runCatching {
            val menu = PopupMenu()

            val toggle = MenuItem("显示 / 隐藏悬浮窗")
            toggle.addActionListener { controller.toggleVisible() }
            menu.add(toggle)

            val library = MenuItem("打开曲库")
            library.addActionListener {
                SwingUtilities.invokeLater {
                    controller.show()
                    LibraryWindow.open(store, controller, owner)
                }
            }
            menu.add(library)

            menu.addSeparator()

            val fullscreen = CheckboxMenuItem(
                "全屏时自动隐藏",
                store.settings.value.fullscreen == FullscreenBehavior.Hide,
            )
            fullscreen.addItemListener {
                val hide = fullscreen.state
                store.setFullscreenBehavior(
                    if (hide) FullscreenBehavior.Hide else FullscreenBehavior.Keep,
                )
            }
            menu.add(fullscreen)

            menu.addSeparator()

            val close = MenuItem("关闭悬浮窗")
            close.addActionListener { controller.hide() }
            menu.add(close)

            val icon = TrayIcon(IslandBadge.tray(16), "音乐浮窗", menu).apply {
                isImageAutoSize = true
                addActionListener {
                    // 双击托盘图标切换显示
                    if (it.actionCommand == null) controller.toggleVisible()
                }
            }

            SystemTray.getSystemTray().add(icon)
            trayIcon = icon
            IslandLog.info("托盘图标已安装")
        }.onFailure {
            IslandLog.warn("安装托盘图标失败：${it.message}")
        }
    }

    fun uninstall() {
        val icon = trayIcon ?: return
        trayIcon = null
        runCatching { SystemTray.getSystemTray().remove(icon) }
    }
}
