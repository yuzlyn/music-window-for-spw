package io.github.spw.island

import com.xuncorp.spw.workshop.api.PluginContext
import com.xuncorp.spw.workshop.api.PluginPermission
import com.xuncorp.spw.workshop.api.SpwPlugin
import com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi
import com.xuncorp.spw.workshop.api.WorkshopApi

/**
 * 插件入口。
 *
 * start() 只做轻量注册：读配置、启动独立的 Compose 悬浮窗应用、注册快捷键。
 * 曲库与封面读取都在 IO 线程，UI 通过 StateFlow 驱动。
 */
@OptIn(UnstableSpwWorkshopApi::class)
class MainPlugin(pluginContext: PluginContext) : SpwPlugin(pluginContext) {

    private val store = IslandSettingsStore()
    private val controller = IslandController(store)

    @Volatile
    private var bound = false

    private var islandApplication: IslandApplication? = null
    private var hotkey: AutoCloseable? = null

    override fun start() {
        bound = runCatching { store.bind() }
            .onFailure { log("读取配置失败：${it.message}") }
            .isSuccess

        IslandLog.attach(store.dataDirectory())
        IslandLog.info("插件启动，配置读取${if (bound) "成功" else "失败"}")

        IslandRuntime.settingsStore = store
        IslandRuntime.controller = controller
        controller.collapsed.value = store.settings.value.startCollapsed

        islandApplication = IslandApplication(store, controller).also { it.start() }
        controller.refreshTrack()
        controller.startAutoSync()
        registerHotkey()

        log("音乐浮窗已启动（${store.settings.value.motion.label}）")
    }

    private fun registerHotkey() {
        val granted = runCatching {
            WorkshopApi.manager.isPermissionGranted(PluginPermission.KEY_BINDINGS)
        }.getOrDefault(false)

        if (!granted) {
            log("未获得 key-bindings 权限，跳过快捷键注册")
            return
        }

        runCatching {
            hotkey = WorkshopApi.manager.keyBindingManager.register(
                actionId = "toggle-island",
                title = "显示/隐藏音乐浮窗",
                defaultShortcut = null,
                hasGlobal = true,
                handler = Runnable { controller.toggleVisible() },
            )
        }.onFailure {
            log("快捷键注册失败：${it.message}")
        }
    }

    override fun stop() {
        runCatching { hotkey?.close() }
        hotkey = null

        islandApplication?.stop()
        islandApplication = null

        controller.release()
        IslandRuntime.controller = null
        IslandRuntime.settingsStore = null

        runCatching { store.unbind() }
        log("音乐浮窗已停止")
    }

    override fun delete() {
        IslandRuntime.controller = null
        IslandRuntime.settingsStore = null
    }

    private fun log(message: String) {
        println("[SPW/ISLAND] $message")
    }
}
