package com.woka.probe

import com.woka.core.data.WokaStorage
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.image.ImageLoader
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.STILL_PNG
import com.woka.fixtures.SampleMedia
import com.woka.support.withTempStorageRoot
import java.io.File
import kotlin.test.Test

/**
 * Which caches keep serving the old picture after a file is overwritten in place.
 *
 * This is the exact invalidation list ME-104's overwrite path has to implement —
 * a grid still showing the pre-edit tile is the bug that ticket exists to prevent.
 *
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.EditorCacheProbe" -i
 */
class EditorCacheProbe {

    @Test
    fun `what survives an overwrite`() = withTempStorageRoot { root ->
        val target = File(root, "photo.png")
        SampleMedia.file(STILL_PNG).copyTo(target, overwrite = true)

        val before = ImageLoader.load(target)
        val nativeKey = ImageBitmapCache.key(target.absolutePath, 0)
        val previewKey = ImageBitmapCache.previewKey(target.absolutePath)
        ImageBitmapCache[nativeKey] = before
        ImageBitmapCache[previewKey] = before
        val posterBefore = WokaStorage.thumbnailFile(target)

        // Overwrite in place with a different picture, the way `Overwrite` will.
        Thread.sleep(1_100) // so `lastModified` is observably different
        SampleMedia.file(STILL_JPG).copyTo(target, overwrite = true)

        val posterAfter = WokaStorage.thumbnailFile(target)
        val nativeStale = ImageBitmapCache[nativeKey] != null
        val previewStale = ImageBitmapCache[previewKey] != null

        println("Editor cache probe — after overwriting a file in place")
        println(
            renderTable(
                listOf("cache", "key", "still serves the old picture"),
                listOf(
                    listOf(
                        "`ImageBitmapCache` native",
                        "the absolute path alone",
                        presenceCell(nativeStale)
                    ),
                    listOf(
                        "`ImageBitmapCache` preview",
                        "`path#preview`",
                        presenceCell(previewStale)
                    ),
                    listOf(
                        "`.thumbnails` poster",
                        "path + length + mtime",
                        presenceCell(posterAfter == posterBefore)
                    )
                )
            )
        )
        println()
        println("poster before: `${posterBefore.name}`")
        println("poster after:  `${posterAfter.name}`")
        println()
        println("`ImageBitmapCache` has **no removal API at all** — only `get` and `set`.")
        println("Whatever this table says must be added there before ME-104 can invalidate it.")
        println("Coil is not measured here: the grid builds it inside a composition, so its")
        println("staleness is a question for the UI ticket, not for a headless probe.")

        // Leave nothing behind: the cache is a process-wide object.
        ImageBitmapCache[nativeKey] = before
    }
}
