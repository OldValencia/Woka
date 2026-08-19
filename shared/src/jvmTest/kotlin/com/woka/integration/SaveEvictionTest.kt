package com.woka.integration

import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.editor.save.evictCachesFor
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_JPG
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The grid's cache, evicted against a key **Coil actually produced**.
 *
 * This test exists because the alternative was a guess. `evictCachesFor` matches
 * memory keys by substring, and the shape of the string it is matching against was
 * read out of coil's bytecode rather than observed: `FileUriKeyer` declines when
 * `addLastModifiedToFileCacheKey` is off and `UriKeyer` answers `uri.toString()` — but
 * what a Windows path becomes inside a `coil3.Uri` was never seen. A filter that
 * silently matches nothing looks exactly like a filter that works, and the symptom
 * would be a grid tile still showing the pre-edit picture (§5.2).
 *
 * So the picture is put through the real singleton loader, the key is taken from the
 * real cache, and the assertion is that eviction removed it.
 *
 * **What it measured:** `file:C:\…\photo.jpg` — the raw path, no percent-encoding and
 * no `//`. Built from `File.path` by `FileMapper`, which is why `evictCachesFor`
 * matches that as well as `absolutePath`.
 */
class SaveEvictionTest {

    private fun onPhoto(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("woka-evict").toFile()
        try {
            val file = File(dir, "photo.jpg")
            file.writeBytes(SampleMedia.file(STILL_JPG).readBytes())
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun keysFor(file: File): List<String> {
        val memory = SingletonImageLoader.get(PlatformContext.INSTANCE).memoryCache
        return memory?.keys.orEmpty().map { it.key }.filter { it.contains("photo.jpg") }
    }

    @Test
    fun `a save drops the entry Coil made for that path`() {
        onPhoto { file ->
            val loader = SingletonImageLoader.get(PlatformContext.INSTANCE)
            val request = ImageRequest.Builder(PlatformContext.INSTANCE).data(file).build()
            val result = runBlocking { loader.execute(request) }
            assertTrue(result is SuccessResult, "Coil could not read the fixture: $result")

            // The measurement: what the key looks like, and that our filter sees it.
            val before = keysFor(file)
            assertTrue(before.isNotEmpty(), "Coil cached nothing — this test proves nothing")
            println("[coil] memory key for ${file.absolutePath} -> $before")

            evictCachesFor(file)

            assertEquals(emptyList(), keysFor(file), "the substring filter matched nothing")
        }
    }

    @Test
    fun `an unrelated Coil entry survives`() {
        onPhoto { file ->
            val loader = SingletonImageLoader.get(PlatformContext.INSTANCE)
            val other = File(file.parentFile, "other.jpg")
            other.writeBytes(SampleMedia.file(STILL_JPG).readBytes())
            runBlocking {
                loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(file).build())
                loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(other).build())
            }
            val memory = loader.memoryCache
            val otherKeys = { memory?.keys.orEmpty().count { it.key.contains("other.jpg") } }
            assertTrue(otherKeys() > 0, "Coil cached nothing for the second file")

            evictCachesFor(file)

            assertTrue(otherKeys() > 0, "an unrelated path was evicted")
            ImageBitmapCache.remove(other.absolutePath)
        }
    }
}
