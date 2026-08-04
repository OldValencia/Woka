package com.woka.ui.viewer

import java.io.File

/**
 * Where [ThumbnailLoader] keeps the poster frame for [videoFile] — named by the
 * hash of the file name, which is the convention the loader has always used.
 *
 * Split out of the loader while the OS media panel needed the same path. That
 * panel is gone; the rule stays here because it is a naming convention, not
 * loader logic, and the loader reads better without it.
 */
internal fun thumbnailCacheFile(videoFile: File): File {
    val dir = File(System.getProperty("user.home"), "Documents/OldValencia/Woka/.thumbnails")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "${videoFile.name.hashCode()}.png")
}
