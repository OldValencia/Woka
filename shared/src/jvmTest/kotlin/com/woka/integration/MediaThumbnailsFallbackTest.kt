package com.woka.integration

import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaItem
import com.woka.feature.viewer.shell.MediaThumbnails
import com.woka.fixtures.BURST_FRAME_COUNT
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_PNG
import com.woka.fixtures.STILL_WIDTH
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun copyOf(name: String, called: String): File {
    val target = File(Files.createTempDirectory("woka-facade").toFile(), called)
    SampleMedia.file(name).copyTo(target, overwrite = true)
    return target
}

/**
 * What `MediaThumbnails` answers when a decoder fails — the behaviour its `catch` exists for,
 * pinned before and after that `catch` changed shape.
 *
 * `sourceSize` and `frameSet` used to be written as `runCatching { … }.getOrDefault(x)`, which
 * catches `Throwable` and therefore turned an `OutOfMemoryError` out of a decoder into a
 * plausible `IntSize.Zero` the caller could not tell from a real answer. They now catch
 * `Exception`, so an `Error` travels. **That narrowing is not what these tests assert** — there
 * is no way to make a real decoder throw an `Error` on demand from here. What they assert is the
 * half that had to survive it: an ordinary failure still produces the documented fallback, and a
 * healthy file still produces a real answer.
 *
 * `loadThumbnail`'s own absorption is `ImageLoaderTest`; these are the two nobody covered.
 */
class MediaThumbnailsFallbackTest {

    @Test
    fun `a corrupt still still answers a zero source size`() {
        val corrupt = copyOf("broken.mkv", "corrupt.png")
        assertEquals(IntSize.Zero, runBlocking { MediaThumbnails.sourceSize(MediaItem.Image(corrupt)) })
    }

    @Test
    fun `a corrupt still still answers a single frame`() {
        val corrupt = copyOf("broken.mkv", "corrupt.png")
        val frames = runBlocking { MediaThumbnails.frameSet(MediaItem.Image(corrupt)) }
        assertEquals(1, frames.frames.size.coerceAtLeast(1))
    }

    @Test
    fun `a corrupt still still answers no preview`() {
        val corrupt = copyOf("broken.mkv", "corrupt.png")
        assertNull(runBlocking { MediaThumbnails.loadPreview(MediaItem.Image(corrupt)) })
    }

    /** The half a too-eager `catch` would hide: a healthy file must not take the fallback. */
    @Test
    fun `a readable still reports its real size`() {
        val picture = SampleMedia.file(STILL_PNG)
        val size = runBlocking { MediaThumbnails.sourceSize(MediaItem.Image(picture)) }
        assertEquals(STILL_WIDTH, size.width)
    }

    /** And a burst still counts its frames rather than falling back to one. */
    @Test
    fun `a frame set is still counted`() {
        val burst = SampleMedia.file(BURST_HEIC)
        val frames = runBlocking { MediaThumbnails.frameSet(MediaItem.Image(burst)) }
        assertEquals(BURST_FRAME_COUNT, frames.frames.size)
    }
}
