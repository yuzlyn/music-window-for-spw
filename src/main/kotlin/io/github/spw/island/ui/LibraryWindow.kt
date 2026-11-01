package io.github.spw.island.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import io.github.spw.island.IslandController
import io.github.spw.island.IslandSettingsStore
import java.awt.Dimension
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
        owner: java.awt.Window? = null,
    ) {
        SwingUtilities.invokeLater {
            val existing = frame
            if (existing != null && existing.isDisplayable) {
                existing.isVisible = true
                existing.toFront()
                return@invokeLater
            }

            // JDialog 不会在任务栏里单独占一个按钮
            val window = JDialog(owner, "曲库").apply {
                defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
                setIconImage(IslandBadge.icon())
                size = Dimension(520, 640)
                minimumSize = Dimension(420, 480)
                setLocationRelativeTo(owner)
            }

            val panel = ComposePanel()
            panel.setContent {
                val settings by store.settings.collectAsState()
                IslandTheme(settings) {
                    // 必须有整窗底色，否则会露出 ComposePanel 的默认背景，导致看起来"反色"
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
        }
    }

    fun close() {
        SwingUtilities.invokeLater {
            runCatching { frame?.dispose() }
            frame = null
        }
    }
}
