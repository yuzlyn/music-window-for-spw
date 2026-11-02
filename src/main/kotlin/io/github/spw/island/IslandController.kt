package io.github.spw.island

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.xuncorp.spw.workshop.api.PluginPermission
import com.xuncorp.spw.workshop.api.PluginPermissionDeniedException
import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint.LyricsLine
import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint.MediaItem
import com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi
import com.xuncorp.spw.workshop.api.WorkshopApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image

/**
 * 悬浮窗的运行时状态。
 *
 * 播放回调可能在任意线程，全部写入 StateFlow；
 * 曲库查询、封面解码等阻塞操作放在 IO 线程。
 */
@OptIn(UnstableSpwWorkshopApi::class)
class IslandController(private val store: IslandSettingsStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val track = MutableStateFlow<MediaItem?>(null)
    val isPlaying = MutableStateFlow(false)
    val positionMs = MutableStateFlow(0L)
    val lyrics = MutableStateFlow<List<LyricsLine>>(emptyList())
    val currentLine = MutableStateFlow<LyricsLine?>(null)
    val cover = MutableStateFlow<ImageBitmap?>(null)

    val collapsed = MutableStateFlow(false)
    val visible = MutableStateFlow(true)
    val fullscreenActive = MutableStateFlow(false)

    val listExpanded = MutableStateFlow(false)
    val library = MutableStateFlow<List<MediaItem>>(emptyList())
    val libraryLoading = MutableStateFlow(false)
    val libraryError = MutableStateFlow<String?>(null)
    val selectedTrack = MutableStateFlow<MediaItem?>(null)

    @Volatile
    private var loadedCoverId: String? = null
    private var syncJob: Job? = null
    private var lyricsGateUntil = 0L
    private var lastCommandAt = 0L

    init {
        collapsed.value = store.settings.value.startCollapsed
    }

    // region 播放回调

    fun onIsPlayingChanged(playing: Boolean) {
        isPlaying.value = playing
        if (playing) refreshTrack()
    }

    /**
     * 播放/暂停切换。
     *
     * 先本地翻转状态再调 API：宿主的回调有延迟，等回调会让按钮"点了没反应"，
     * 250ms 防抖避免连点产生 pause→play 抖动。
     */
    fun togglePlayPause() {
        val now = System.currentTimeMillis()
        if (now - lastCommandAt < 250) return
        lastCommandAt = now

        val playing = isPlaying.value
        isPlaying.value = !playing
        runCatching {
            if (playing) WorkshopApi.playback.pause() else WorkshopApi.playback.play()
        }.onFailure {
            isPlaying.value = playing
            handleFailure("切换播放状态失败", it)
        }
    }

    fun onPositionUpdated(position: Long) {
        positionMs.value = position
        syncLineFromTimeline(position)
    }

    fun onLyricsLineUpdated(line: LyricsLine?) {
        // 换歌瞬间可能收到上一首的残留事件，直接丢弃
        if (System.currentTimeMillis() < lyricsGateUntil) return
        currentLine.value = line
    }

    fun onLyricsLinesUpdated(lines: List<LyricsLine>) {
        if (System.currentTimeMillis() < lyricsGateUntil) return
        lyrics.value = lines
        currentLine.value = null
        syncLineFromTimeline(positionMs.value)
    }

    fun onSeekTo(position: Long) {
        positionMs.value = position
        syncLineFromTimeline(position)
    }

    private fun syncLineFromTimeline(position: Long) {
        val timeline = lyrics.value
        // 宿主多数歌曲只通过单行回调推歌词，此时没有整表，
        // 不能在这里清空，否则每秒的位置更新会把刚收到的歌词抹掉。
        if (timeline.isEmpty()) return
        val line = timeline.lastOrNull { it.startTime <= position }
        if (line == null) {
            // 还没到第一句（前奏/乐器段）
            currentLine.value = null
            return
        }
        currentLine.value = line
    }

    // endregion

    /** 轮询当前歌曲，切歌后立即重新同步歌词与封面。 */
    fun startAutoSync() {
        if (syncJob != null) return
        syncJob = scope.launch {
            while (isActive) {
                delay(1500)
                runCatching {
                    val current = WorkshopApi.playback.getCurrentMediaItem()
                    if (current?.id != track.value?.id) refreshTrack()
                }
            }
        }
    }

    /** 手动重同步（托盘菜单）。 */
    fun resyncNow() {
        lyrics.value = emptyList()
        currentLine.value = null
        cover.value = null
        loadedCoverId = null
        refreshTrack()
    }

    /** 刷新当前歌曲与封面。 */
    fun refreshTrack() {
        scope.launch {
            val item = runCatching { WorkshopApi.playback.getCurrentMediaItem() }
                .onFailure { handleFailure("读取当前歌曲失败", it) }
                .getOrNull()

            // 换歌时先清掉旧的歌词与封面，避免新旧错位；
            // 并开启 800ms 的门闩，丢弃上一首迟到的歌词事件
            if (item?.id != track.value?.id) {
                lyricsGateUntil = System.currentTimeMillis() + 500
                lyrics.value = emptyList()
                currentLine.value = null
                cover.value = null
                loadedCoverId = null
            }

            track.value = item
            if (item == null) {
                cover.value = null
                loadedCoverId = null
                return@launch
            }

            if (item.id.isNotEmpty() && item.id != loadedCoverId) {
                loadCover(item.id)
            }
        }
    }

    private suspend fun loadCover(id: String) {
        if (!hasLibraryPermission()) {
            IslandLog.warn("无 library-read 权限，跳过封面 id=$id")
            return
        }

        IslandLog.info("封面请求 id=$id")
        val bytes = runCatching {
            WorkshopApi.library.getCoverById(id).toCompletableFuture().get()
        }.onFailure { handleFailure("读取封面失败", it) }.getOrNull()

        if (bytes == null || bytes.isEmpty()) {
            IslandLog.warn("封面为空 id=$id")
            cover.value = null
            loadedCoverId = id
            return
        }

        val bitmap = runCatching {
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }.onFailure { IslandLog.warn("封面解码失败 id=$id：${it.message}") }.getOrNull()

        // 加载期间可能已经切歌，只有仍是当前歌曲才写入
        if (track.value?.id != id) {
            IslandLog.info("封面丢弃（已切歌）id=$id")
            return
        }

        cover.value = bitmap
        loadedCoverId = id
        IslandLog.info("封面写入 id=$id bytes=${bytes.size} 解码=${bitmap != null}")
    }

    /** 按比例跳转；用于进度条点击与拖动。 */
    fun seekToFraction(fraction: Float) {
        val duration = track.value?.duration ?: return
        if (duration <= 0L) return

        val target = (duration * fraction.coerceIn(0f, 1f)).toLong()
        positionMs.value = target
        runCatching { WorkshopApi.playback.seekTo(target) }
    }

    fun openLibrary() {
        listExpanded.value = true
        loadLibrary()
    }

    fun closeLibrary() {
        listExpanded.value = false
    }

    fun toggleLibrary() {
        if (listExpanded.value) closeLibrary() else openLibrary()
    }

    fun loadLibrary() {
        if (!hasLibraryPermission()) {
            libraryError.value = "缺少曲库读取权限，请在插件配置中授权"
            return
        }

        libraryLoading.value = true
        libraryError.value = null

        runCatching { WorkshopApi.library.getAllTracks() }
            .onFailure {
                libraryLoading.value = false
                handleFailure("读取曲库失败", it)
                libraryError.value = it.message ?: "读取曲库失败"
            }
            .onSuccess { stage ->
                stage.whenComplete { tracks, failure ->
                    libraryLoading.value = false
                    if (failure != null) {
                        handleFailure("读取曲库失败", failure)
                        libraryError.value = failure.message ?: "读取曲库失败"
                    } else {
                        library.value = tracks.orEmpty().sortedWith(
                            compareBy({ it.title.lowercase() }, { it.artist.lowercase() }),
                        )
                    }
                }
            }
    }

    fun selectTrack(item: MediaItem) {
        selectedTrack.value = item
    }

    fun toggleCollapsed() {
        collapsed.value = !collapsed.value
        if (collapsed.value) closeLibrary()
    }

    fun toggleVisible() {
        visible.value = !visible.value
    }

    fun show() {
        visible.value = true
    }

    fun hide() {
        visible.value = false
    }

    fun release() {
        scope.cancel()
        cover.value = null
    }

    private fun hasLibraryPermission(): Boolean =
        runCatching { WorkshopApi.manager.isPermissionGranted(PluginPermission.LIBRARY_READ) }
            .getOrDefault(false)

    private fun handleFailure(action: String, failure: Throwable) {
        val cause = (failure as? java.util.concurrent.CompletionException)?.cause ?: failure
        if (cause is PluginPermissionDeniedException) {
            println("[SPW/ISLAND] $action：缺少 library-read 权限")
        } else {
            println("[SPW/ISLAND] $action：${cause.message}")
        }
    }
}
