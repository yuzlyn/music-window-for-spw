package io.github.spw.island

/** 运行期单例，供扩展点与静态配置按钮访问。 */
object IslandRuntime {

    @Volatile
    var controller: IslandController? = null

    @Volatile
    var settingsStore: IslandSettingsStore? = null
}
