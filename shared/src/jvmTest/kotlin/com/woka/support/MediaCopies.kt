package com.woka.support

import com.woka.core.data.MediaItem
import com.woka.fixtures.SampleMedia
import java.io.File
import java.nio.file.Files

/**
 * A private copy of a fixture in a directory of its own.
 *
 * Both caches are keyed by absolute path, and `ImageBitmapCache` is a
 * process-wide singleton with no eviction (F1) — so a test that wants a cold
 * cache has to bring its own file rather than reuse one another test decoded.
 */
fun videoCopy(name: String, as_: String = "clip.mkv"): MediaItem.Video {
    val dir = Files.createTempDirectory("woka-thumb").toFile()
    val target = File(dir, as_)
    SampleMedia.file(name).copyTo(target)
    return MediaItem.Video(target)
}
