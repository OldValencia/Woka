package com.woka.core.utils

import java.io.File
import java.nio.file.Files

/** Copy files outlive the process: Windows stores their paths for a later paste. */
object ClipboardCopies {
    private val root = File(System.getProperty("java.io.tmpdir"), "Woka/copy")
    private var last: File? = null
    private var started = false
    private val created = mutableSetOf<File>()

    @Synchronized
    fun start() {
        if (started) return
        started = true
        runCatching { cleanExcept(created) }
        Runtime.getRuntime().addShutdownHook(Thread({ cleanAtExit() }, "Woka-Copy-Cleanup"))
    }

    @Synchronized
    internal fun keep(file: File) {
        last = file.parentFile.takeIf { it.parentFile == root }
    }

    @Synchronized
    fun newDirectory(): File {
        root.mkdirs()
        return Files.createTempDirectory(root.toPath(), "photo-").toFile().also { created.add(it) }
    }

    @Synchronized
    private fun cleanAtExit() {
        runCatching { cleanExcept(setOfNotNull(last)) }
    }

    private fun cleanExcept(keep: Set<File>) {
        if (Files.isSymbolicLink(root.toPath())) return
        root.listFiles()?.filter { it.name.startsWith("photo-") && it !in keep }?.forEach { dir ->
            if (dir.isDirectory && !Files.isSymbolicLink(dir.toPath()) && dir.canonicalFile.parentFile == root.canonicalFile) {
                dir.listFiles()?.filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }?.forEach { it.delete() }
                dir.delete()
            }
        }
    }
}
