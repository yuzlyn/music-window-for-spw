package io.github.spw.island.ui

import java.awt.Point
import java.awt.Window
import javax.swing.Timer

/**
 * 窗口位移调度器。
 *
 * 参考 SPW-island 的做法：鼠标拖动只记录目标坐标，
 * 真正的 setLocation 由固定帧率统一执行，多个指针事件合并成一次移动。
 * 这样可以避免在高频指针回调里同步移动窗口造成的积压和卡顿。
 */
object WindowMoveScheduler {

    private var timer: Timer? = null
    private var host: Window? = null
    private var target: Point? = null
    private var applied: Point? = null

    fun begin(window: Window, frameIntervalMs: Int) {
        timer?.stop()
        host = window
        applied = window.location
        target = applied

        timer = Timer(frameIntervalMs.coerceIn(8, 50)) { tick() }.apply { start() }
    }

    fun submit(x: Int, y: Int) {
        target = Point(x, y)
    }

    fun end() {
        tick()
        timer?.stop()
        timer = null
        host = null
        target = null
        applied = null
    }

    private fun tick() {
        val window = host ?: return
        val next = target ?: return
        if (next == applied) return

        applied = next
        runCatching { window.setLocation(next) }
    }
}
