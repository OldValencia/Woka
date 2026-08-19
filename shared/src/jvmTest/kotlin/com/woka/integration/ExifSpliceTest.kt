package com.woka.integration

import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.ImageEditorEngine
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.viewer.image.ImageLoader
import com.woka.fixtures.EXIF_ROTATED
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.STILL_PNG
import com.woka.support.MainDispatcherRule
import com.woka.support.StageTool
import com.woka.support.scaleRed
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EXIF across a save, and the one thing that would silently ruin it.
 *
 * ME-010 measured both halves: a Skia re-encode drops `APP1` entirely, **and** the
 * decode path has already applied `Orientation`. So the segment has to be carried
 * over by hand, and the orientation inside it has to be neutralised — put back
 * unchanged, it rotates a picture that is already rotated.
 *
 * **Driven through the re-encode branch deliberately** (ME-132): a JPEG is bucket A
 * now, so nothing the app can do reaches the splice. It is kept because a re-encode is
 * what every bucket B format gets, and `webp` carries no EXIF to prove it with.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExifSpliceTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val redder = StageTool("tint", ToolDomain.Color, RasterStage.Pixels(scaleRed(2f)))

    /** [block] takes the file as it was before the re-encode, and the file after it. */
    private fun onReEncodeOf(fixture: String, name: String, block: (ByteArray, File) -> Unit) =
        withTempStorageRoot {
            val dir = Files.createTempDirectory("woka-exif").toFile()
            try {
                val file = File(dir, name)
                file.writeBytes(SampleMedia.file(fixture).readBytes())
                val before = file.readBytes()
                val engine = ImageEditorEngine(
                    file,
                    0,
                    EditorToolRegistry(listOf(redder), SettingsRepositoryImpl()),
                    EditCapability.Destructive
                )
                val result = runBlocking {
                    engine.save(EditDocument(ops = listOf(ToolOp("tint", redder.defaults()))))
                }
                assertTrue(result is EditSaveResult.Saved, "save failed: $result")
                block(before, file)
            } finally {
                dir.deleteRecursively()
            }
        }

    @Test
    fun `the APP1 segment survives a JPEG save`() {
        onReEncodeOf(EXIF_ROTATED, "rotated.jpg") { before, saved ->
            assertTrue(before.hasApp1(), "the fixture lost its own EXIF")
            assertTrue(saved.readBytes().hasApp1(), "APP1 was dropped — the splice did nothing")
        }
    }

    @Test
    fun `orientation comes out as 1, because the pixels are already rotated`() {
        onReEncodeOf(EXIF_ROTATED, "rotated.jpg") { before, saved ->
            assertEquals(6, before.orientation(), "the fixture is not tagged 6")
            assertEquals(1, saved.readBytes().orientation())
        }
    }

    @Test
    fun `the saved picture keeps the dimensions the decoder produced`() {
        onReEncodeOf(EXIF_ROTATED, "rotated.jpg") { _, saved ->
            // 640x480 tagged 6 decodes to 480x640; with orientation neutralised, the
            // next reader must see exactly that and not rotate it again.
            val bitmap = ImageLoader.load(saved)
            assertEquals(480, bitmap.width)
            assertEquals(640, bitmap.height)
        }
    }

    @Test
    fun `a PNG target is left alone`() {
        onReEncodeOf(STILL_PNG, "plain.png") { _, saved ->
            val bytes = saved.readBytes()
            assertTrue(bytes.size > 8, "nothing was written")
            // PNG's own magic, intact — the splice must not have prefixed a JPEG
            // segment onto a file that is not a JPEG.
            assertEquals(0x89.toByte(), bytes[0])
            assertEquals('P'.code.toByte(), bytes[1])
        }
    }

    @Test
    fun `a JPEG source with no EXIF still produces a readable file`() {
        onReEncodeOf(STILL_JPG, "plain.jpg") { before, saved ->
            assertTrue(!before.hasApp1(), "the fixture unexpectedly carries EXIF")
            // The real assertion: it decodes. A splice that prefixed a malformed
            // segment would produce bytes that are not a JPEG at all.
            assertEquals(640, ImageLoader.load(saved).width)
        }
    }
}

/** Walks the marker segments looking for `APP1`, stopping at the scan header. */
private fun ByteArray.app1Range(): IntRange? {
    if (size < 4 || this[0] != 0xFF.toByte() || this[1] != 0xD8.toByte()) return null
    var at = 2
    while (at + 4 <= size && this[at] == 0xFF.toByte()) {
        val marker = this[at + 1]
        val length = ((this[at + 2].toInt() and 0xFF) shl 8) or (this[at + 3].toInt() and 0xFF)
        if (length < 2 || at + 2 + length > size) return null
        if (marker == 0xE1.toByte()) return at until (at + 2 + length)
        if (marker == 0xDA.toByte()) return null
        at += 2 + length
    }
    return null
}

private fun ByteArray.hasApp1(): Boolean = app1Range() != null

/** IFD0's `Orientation`, read straight out of the segment. Big-endian fixtures only. */
private fun ByteArray.orientation(): Int? {
    val range = app1Range() ?: return null
    val tiff = range.first + 10
    if (this[tiff] != 0x4D.toByte()) return null

    fun u16(at: Int) = ((this[at].toInt() and 0xFF) shl 8) or (this[at + 1].toInt() and 0xFF)
    fun u32(at: Int) = (u16(at) shl 16) or u16(at + 2)

    val ifd0 = tiff + u32(tiff + 4)
    repeat(u16(ifd0)) { index ->
        val entry = ifd0 + 2 + index * 12
        if (u16(entry) == 0x0112) return u16(entry + 8)
    }
    return null
}
