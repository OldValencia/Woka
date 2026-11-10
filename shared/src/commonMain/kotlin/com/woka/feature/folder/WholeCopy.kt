package com.woka.feature.folder

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * [source] copied to [target] so that **no one sees the copy before it is whole**.
 *
 * `copyTo` wrote straight under the final name, and the folder's watch put the tile up the
 * moment the file appeared. A 4 GB `mkv` duplicated and opened at once was read while still
 * short: its header declares the whole Segment, the file did not yet hold it, and the editor
 * refused it as a stream it cannot keep an edit in (reported 2026-11-10). The player would
 * have played it cut off the same way.
 *
 * So a file is written as `<name>.woka-tmp` beside the target — an extension no reader takes
 * for media, the one `replaceAtomically` uses — and moved onto its name when complete. A
 * folder is made and filled file by file the same way, so each file in it appears whole. The
 * temp is deleted on every failure, including the ones that throw. Never overwrites: the
 * caller has already found a free name.
 */
internal fun copyWhole(source: File, target: File) {
    if (source.isDirectory) {
        target.mkdirs()
        source.listFiles().orEmpty().forEach { child -> copyWhole(child, File(target, child.name)) }
        return
    }
    val temp = File(target.parentFile, "${target.name}.woka-tmp")
    try {
        source.copyTo(temp, overwrite = true)
        moveOnto(temp, target)
    } catch (e: Exception) {
        temp.delete()
        throw e
    }
}

/** One step where the filesystem allows it — it does within a folder — else a plain rename. */
private fun moveOnto(temp: File, target: File) {
    try {
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(temp.toPath(), target.toPath())
    }
}
