package com.woka.integration

import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.video.render.saveFramePhoto
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.support.MainDispatcherRule
import com.woka.support.RENDER_SIZE
import com.woka.support.onClip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.jetbrains.skia.Image
import org.junit.Rule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * This frame as a photo (ME-294): the clip under the play head through its own look, at the
 * film's coded size, written beside it under the time it was taken — and never over another.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FramePhotoTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `a frame is drawn through its clip's look, at the coded size, beside the film`() = onClip { source, registry ->
        val clips = listOf(TrimClip("a", 0L, 1_000L), TrimClip("b", 2_000L, 3_000L, key = 1))
        val document = EditDocument(ops = listOf(TrimTool.opFor(clips)))
        val looks = { key: Int -> if (key == 1) listOf(ToolOp("tint")) else emptyList() }
        fun take(atMs: Long) = File(
            assertIs<EditSaveResult.Saved>(saveFramePhoto(source, document, looks, registry, RENDER_SIZE, atMs)).path
        )

        val plain = take(500L)
        val tinted = take(1_500L)
        assertEquals("clip 0m00s.jpg", plain.name)
        assertEquals("clip 0m01s.jpg", tinted.name)
        assertEquals(source.parentFile.absoluteFile, tinted.parentFile.absoluteFile)
        assertTrue(redOverGreen(tinted) > redOverGreen(plain) * 1.2, "the second clip's own look is missing")
        assertEquals("clip 0m01s 2.jpg", take(1_500L).name, "a second photo of the same moment overwrote the first")
    }

    /** Mean red over mean green of [file]'s pixels, checking it is the coded size on the way. */
    private fun redOverGreen(file: File): Double {
        val pixels = Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap().toPixelMap()
        assertEquals(RENDER_SIZE.width, pixels.width)
        assertEquals(RENDER_SIZE.height, pixels.height)
        var red = 0.0
        var green = 0.0
        for (y in 0 until pixels.height step 4) for (x in 0 until pixels.width step 4) {
            red += pixels[x, y].red
            green += pixels[x, y].green
        }
        return red / green.coerceAtLeast(1e-6)
    }
}
