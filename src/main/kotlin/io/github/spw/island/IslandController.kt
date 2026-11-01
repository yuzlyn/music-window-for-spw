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

    init {
        collapsed.value = store.settings.value.startCollapsed
    }

    // region 播放回调

    fun onIsPlayingChanged(playing: Boolean) {
        isPlaying.value = playing
        if (playing) refreshTrack()
    }

    fun onPositionUpdated(position: Long) {
        positionMs.value = position
        syncLineFromTimeline(position)
    }

    fun onLyricsLineUpdated(line: LyricsLine?) {
        currentLine.value = line
    }

    fun onLyricsLinesUpdated(lines: List<LyricsLine>) {
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
        if (timeline.isEmpty()) return
        val line = timeline.lastOrNull { it.startTime <= position } ?: timeline.first()
        currentLine.value = line
    }

    // endregion

    /** 刷新当前歌曲与封面。 */
    fun refreshTrack() {
        scope.launch {
            val item = runCatching { WorkshopApi.playback.getCurrentMediaItem() }
                .onFailure { handleFailure("读取当前歌曲失败", it) }
                .getOrNull()

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
        if (!hasLibraryPermission()) return

        val bytes = runCatching {
            WorkshopApi.library.getCoverById(id).toCompletableFuture().get()
        }.onFailure { handleFailure("读取封面失败", it) }.getOrNull()

        if (bytes == null || bytes.isEmpty()) {
            cover.value = null
            loadedCoverId = id
            return
        }

        val bitmap = runCatching {
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()

        cover.value = bitmap
        loadedCoverId = id
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
