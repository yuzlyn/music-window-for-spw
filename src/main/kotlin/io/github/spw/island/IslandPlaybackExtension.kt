package io.github.spw.island

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint
import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint.LyricsLine
import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint.State
import org.pf4j.Extension

/**
 * 把播放器事件转发给悬浮窗。
 *
 * 回调可能在任意线程，只做状态写入，不做阻塞操作。
 */
@Extension
class IslandPlaybackExtension : PlaybackExtensionPoint {

    private val controller: IslandController?
        get() = IslandRuntime.controller

    override fun onStateChanged(state: State) {
        if (state == State.Idle) {
            controller?.onIsPlayingChanged(false)
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        controller?.onIsPlayingChanged(isPlaying)
    }

    override fun onSeekTo(position: Long) {
        controller?.onSeekTo(position)
    }

    override fun onLyricsLineUpdated(lyricsLine: LyricsLine?) {
        controller?.onLyricsLineUpdated(lyricsLine)
    }

    override fun onLyricsLinesUpdated(lyricsLines: List<LyricsLine>) {
        controller?.onLyricsLinesUpdated(lyricsLines)
    }

    override fun onPositionUpdated(position: Long) {
        controller?.onPositionUpdated(position)
    }
}
