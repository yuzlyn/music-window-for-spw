package io.github.spw.island

import com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi
import com.xuncorp.spw.workshop.api.WorkshopApi
import com.xuncorp.spw.workshop.api.config.ConfigHelper
import com.xuncorp.spw.workshop.api.config.ConfigManager
import io.github.spw.island.theme.FluentAccent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.function.Consumer

enum class ThemeMode(val value: String, val label: String) {
    System("system", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色"),
    ;

    companion object {
        fun fromValue(value: String): ThemeMode = entries.firstOrNull { it.value == value } ?: System
    }
}

/** 动画策略：性能优先时关闭波浪流动与所有过渡。 */
enum class MotionMode(val value: String, val label: String) {
    Full("full", "动画优先"),
    Low("low", "性能优先"),
    ;

    val lowMotion: Boolean get() = this == Low

    companion object {
        fun fromValue(value: String): MotionMode = entries.firstOrNull { it.value == value } ?: Full
    }
}

/** 收起时的形态。 */
enum class CollapsedStyle(val value: String, val label: String) {
    /** 只显示圆形专辑封面。 */
    Cover("cover", "专辑封面"),

    /** 长条胶囊：左封面 + 右歌词，宽度更小。 */
    Capsule("capsule", "胶囊歌词"),
    ;

    companion object {
        fun fromValue(value: String): CollapsedStyle =
            entries.firstOrNull { it.value == value } ?: Cover
    }
}

/** 全屏第三方应用时的行为。 */
enum class FullscreenBehavior(val value: String, val label: String) {
    Keep("keep", "全屏时保持显示"),
    Hide("hide", "全屏时自动隐藏"),
    ;

    companion object {
        fun fromValue(value: String): FullscreenBehavior =
            entries.firstOrNull { it.value == value } ?: Keep
    }
}

data class IslandSettings(
    val accent: FluentAccent = FluentAccent.Blue,
    val themeMode: ThemeMode = ThemeMode.System,
    val motion: MotionMode = MotionMode.Full,
    val fullscreen: FullscreenBehavior = FullscreenBehavior.Keep,
    val showCover: Boolean = true,
    val showLyric: Boolean = true,
    val showTranslation: Boolean = true,
    val alwaysOnTop: Boolean = true,
    val startCollapsed: Boolean = false,
    val collapsedStyle: CollapsedStyle = CollapsedStyle.Cover,
    val opacity: Float = 1f,
    val lyricSize: Int = 18,
)

/**
 * 配置读写。
 *
 * 配置界面按组拆成 config.json（外观）与 behavior.json（行为），
 * 两个文件都监听；面板内修改的外观项会写回 config.json。
 */
@OptIn(UnstableSpwWorkshopApi::class)
class IslandSettingsStore {

    private val _settings = MutableStateFlow(IslandSettings())
    val settings: StateFlow<IslandSettings> = _settings.asStateFlow()

    private var manager: ConfigManager? = null
    private var appearanceConfig: ConfigHelper? = null
    private var behaviorConfig: ConfigHelper? = null
    private var listener: Consumer<ConfigHelper>? = null
    private var scope: CoroutineScope? = null

    fun bind() {
        val configManager = WorkshopApi.manager.createConfigManager()
        manager = configManager
        appearanceConfig = configManager.getConfig(APPEARANCE_FILE)
        behaviorConfig = configManager.getConfig(BEHAVIOR_FILE)

        val changeListener = Consumer<ConfigHelper> { reload() }
        listener = changeListener
        configManager.addConfigChangeListener(APPEARANCE_FILE, changeListener)
        configManager.addConfigChangeListener(BEHAVIOR_FILE, changeListener)

        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        reload()
    }

    fun unbind() {
        val configManager = manager
        val changeListener = listener
        if (configManager != null && changeListener != null) {
            runCatching { configManager.removeConfigChangeListener(changeListener) }
        }
        listener = null
        manager = null
        appearanceConfig = null
        behaviorConfig = null
        scope?.cancel()
        scope = null
    }

    fun reload() {
        val appearance = appearanceConfig ?: return
        val behavior = behaviorConfig

        fun readBoolean(key: String, default: Boolean): Boolean =
            behavior?.get(key, default) ?: appearance.get(key, default)

        fun readFloat(key: String, default: Float): Float =
            behavior?.get(key, default) ?: appearance.get(key, default)

        fun readString(key: String, default: String): String =
            behavior?.get(key, default) ?: appearance.get(key, default)

        _settings.value = IslandSettings(
            accent = FluentAccent.fromValue(appearance.get(KEY_ACCENT, FluentAccent.Blue.value)),
            themeMode = ThemeMode.fromValue(appearance.get(KEY_MODE, ThemeMode.System.value)),
            motion = MotionMode.fromValue(readString(KEY_MOTION, MotionMode.Full.value)),
            fullscreen = FullscreenBehavior.fromValue(readString(KEY_FULLSCREEN, FullscreenBehavior.Keep.value)),
            showCover = appearance.get(KEY_SHOW_COVER, true),
            showLyric = appearance.get(KEY_SHOW_LYRIC, true),
            showTranslation = appearance.get(KEY_SHOW_TRANSLATION, true),
            alwaysOnTop = readBoolean(KEY_ALWAYS_ON_TOP, true),
            startCollapsed = readBoolean(KEY_START_COLLAPSED, false),
            // 该项在「行为」组里（behavior.json），必须走 readString 的回退逻辑
            collapsedStyle = CollapsedStyle.fromValue(
                readString(KEY_COLLAPSED_STYLE, CollapsedStyle.Cover.value),
            ),
            opacity = readFloat(KEY_OPACITY, 100f).coerceIn(40f, 100f) / 100f,
            lyricSize = appearance.get(KEY_LYRIC_SIZE, 18f).toInt().coerceIn(12, 28),
        )
    }

    fun dataDirectory(): File? = appearanceConfig?.getConfigPath()?.toFile()?.parentFile

    /** 托盘菜单里切换"全屏时自动隐藏"，写回 behavior.json。 */
    fun setFullscreenBehavior(behavior: FullscreenBehavior) {
        _settings.value = _settings.value.copy(fullscreen = behavior)

        val helper = behaviorConfig ?: return
        helper.set(KEY_FULLSCREEN, behavior.value)
        scope?.launch { runCatching { helper.save() } }
    }

    fun update(transform: (IslandSettings) -> IslandSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated

        val helper = appearanceConfig ?: return
        helper.set(KEY_ACCENT, updated.accent.value)
        helper.set(KEY_MODE, updated.themeMode.value)

        scope?.launch { runCatching { helper.save() } }
    }

    companion object {
        const val APPEARANCE_FILE = "config.json"
        const val BEHAVIOR_FILE = "behavior.json"

        private const val KEY_ACCENT = "appearance.accent"
        private const val KEY_MODE = "appearance.mode"
        private const val KEY_MOTION = "behavior.motion"
        private const val KEY_FULLSCREEN = "behavior.fullscreen"
        private const val KEY_SHOW_COVER = "content.showCover"
        private const val KEY_SHOW_LYRIC = "content.showLyric"
        private const val KEY_SHOW_TRANSLATION = "content.showTranslation"
        private const val KEY_ALWAYS_ON_TOP = "window.alwaysOnTop"
        private const val KEY_START_COLLAPSED = "window.startCollapsed"
        private const val KEY_COLLAPSED_STYLE = "appearance.collapsedStyle"
        private const val KEY_OPACITY = "window.opacity"
        private const val KEY_LYRIC_SIZE = "content.lyricSize"
    }
}
