package io.github.spw.island.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import io.github.spw.island.IslandController
import io.github.spw.island.IslandLog
import io.github.spw.island.IslandSettingsStore
import java.awt.Dimension
import java.awt.Frame
import java.awt.Window
import javax.swing.JDialog
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/**
 * 曲库窗口。
 *
 * 用真实 Compose 面板承载：离屏渲染的场景没有键盘/输入法通道，
 * 搜索框必须跑在带 IME 的 ComposePanel 上。
 */
object LibraryWindow {

    private var frame: JDialog? = null

    fun open(
        store: IslandSettingsStore,
        controller: IslandController,
        owner: Window? = null,
    ) {
        SwingUtilities.invokeLater {
            runCatching { openInternal(store, controller, owner) }
                .onFailure { IslandLog.warn("打开曲库窗口失败：${it.message}") }
        }
    }

    /** 曲库窗口是否处于显示状态，用于暂停自动收起。 */
    fun isOpen(): Boolean = frame?.isVisible == true

    fun close() {
        SwingUtilities.invokeLater {
            runCatching { frame?.dispose() }
            frame = null
        }
    }

    private fun openInternal(
        store: IslandSettingsStore,
        controller: IslandController,
        owner: Window?,
    ) {
        val existing = frame
        if (existing != null && existing.isDisplayable) {
            existing.isVisible = true
            existing.toFront()
            return
        }

        // owner 只能是 Frame/Dialog：JWindow 会抛 "Wrong parent window"。
        // 用 UTILITY 类型，同样不会出现在任务栏与 Alt-Tab。
        val parent = owner as? Frame ?: (owner as? java.awt.Dialog)
        val window = if (parent != null) JDialog(parent, "曲库") else JDialog()
        runCatching { window.type = Window.Type.UTILITY }
        window.apply {
            title = "曲库"
            defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
            runCatching { setIconImage(IslandBadge.icon()) }
            size = Dimension(520, 640)
            minimumSize = Dimension(420, 480)
            setLocationRelativeTo(parent)
        }

        val panel = ComposePanel()
        panel.setContent {
            val settings by store.settings.collectAsState()
            IslandTheme(settings) {
                // 整窗底色，避免露出 ComposePanel 默认背景（看起来像反色）
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    IslandLibraryPanel(
                        controller = controller,
                        settings = settings,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        window.contentPane.add(panel)
        window.isVisible = true
        frame = window
        IslandLog.info("曲库窗口已打开")
    }
}
