import com.xuncorp.spw.workshop.gradle.PluginPermission
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java-library")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.spw.workshop)
}

group = "io.github.spw"
version = "3.5.1"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    jvmToolchain(25)

    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
    }
}

/**
 * Compose runtime / ui / foundation / material3 / ripple 均由宿主内置，
 * 全部 compileOnly，避免把第二份 Compose 运行时打进插件。
 */
private fun ModuleDependency.excludeHostProvided() {
    listOf(
        "org.jetbrains.compose.runtime",
        "org.jetbrains.compose.foundation",
        "org.jetbrains.compose.ui",
        "org.jetbrains.compose.animation",
        "org.jetbrains.compose.components",
        "org.jetbrains.compose.material",
        "androidx.annotation",
        "androidx.collection",
        "androidx.lifecycle",
        "org.jetbrains.kotlinx",
        "org.jetbrains.kotlin",
    ).forEach { exclude(group = it) }
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly(libs.spw.workshop.api)
    kapt(libs.spw.workshop.api)

    compileOnly(libs.compose.runtime.desktop)
    compileOnly(libs.compose.ui.desktop)
    compileOnly(libs.compose.foundation.desktop)
    compileOnly(libs.compose.material3.desktop) { excludeHostProvided() }
    compileOnly(libs.compose.material.ripple.desktop) { excludeHostProvided() }

    // 宿主内置 JNA（salt-ui 依赖），用于 Windows 全屏检测
    compileOnly(libs.jna)
    compileOnly(libs.jna.platform)
}

spmod {
    PluginClass = "io.github.spw.island.MainPlugin"
    PluginId = "io.github.spw.island"
    PluginVersion = project.version.toString()
    PluginProvider = "spw"
    PluginName = "音乐浮窗"
    PluginDescription = "Windows 风格桌面音乐浮窗"
    PluginOpenSourceUrl = ""
    PluginHasConfig = true
    PluginPermissions = listOf(PluginPermission.LIBRARY_READ, PluginPermission.KEY_BINDINGS)
}
