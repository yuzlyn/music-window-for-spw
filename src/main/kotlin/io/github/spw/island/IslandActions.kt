package io.github.spw.island

import com.xuncorp.spw.workshop.api.WorkshopApi

/** 配置界面按钮的静态入口。 */
class IslandActions {
    companion object {

        @JvmStatic
        fun showIsland() {
            val controller = IslandRuntime.controller
            if (controller == null) {
                WorkshopApi.ui.toast("音乐浮窗尚未启用", WorkshopApi.Ui.ToastType.Warning)
                return
            }
            controller.show()
        }

        @JvmStatic
        fun hideIsland() {
            IslandRuntime.controller?.hide()
        }

        @JvmStatic
        fun openLibrary() {
            val controller = IslandRuntime.controller
            if (controller == null) {
                WorkshopApi.ui.toast("音乐浮窗尚未启用", WorkshopApi.Ui.ToastType.Warning)
                return
            }
            controller.show()
            controller.openLibrary()
        }

        @JvmStatic
        fun showStatus() {
            val controller = IslandRuntime.controller
            val settings = IslandRuntime.settingsStore?.settings?.value
            if (controller == null || settings == null) {
                WorkshopApi.ui.toast("音乐浮窗尚未启用", WorkshopApi.Ui.ToastType.Warning)
                return
            }
            WorkshopApi.ui.toast(
                "音乐浮窗 · ${settings.accent.label}强调色 · ${settings.motion.label} · ${settings.fullscreen.label}",
                WorkshopApi.Ui.ToastType.Success,
            )
        }
    }
}
