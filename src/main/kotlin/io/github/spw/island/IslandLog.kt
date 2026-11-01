package io.github.spw.island

import java.io.File
import java.time.LocalTime

/** 插件日志：桌面版看不到控制台，同时写文件方便排查。 */
object IslandLog {

    private var logFile: File? = null

    fun attach(directory: File?) {
        logFile = directory?.let { dir ->
            runCatching { dir.mkdirs() }
            File(dir, "island.log")
        }
    }

    fun info(message: String) = write("I", message)

    fun warn(message: String) = write("W", message)

    fun error(message: String) = write("E", message)

    private fun write(level: String, message: String) {
        val line = "${LocalTime.now()} $level $message"
        println("[SPW/ISLAND] $line")
        val file = logFile ?: return
        runCatching { file.appendText(line + System.lineSeparator()) }
    }
}
