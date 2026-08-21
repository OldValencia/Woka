package com.woka.integration

import androidx.compose.ui.graphics.toPixelMap
import com.woka.feature.editor.engine.image.editorAnimationOf
import com.woka.feature.viewer.image.SkiaFrameDecoder
import com.woka.feature.viewer.image.forwardFrames
import com.woka.fixtures.ANIM_FRAME_COUNT
import com.woka.fixtures.ANIM_GIF
import com.woka.fixtures.ANIM_WEBP
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.MULTIPAGE_TIFF
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_JPG
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The forward walk, against the independent reads it exists to replace.
 *
 * **The risk it is here for is `priorFrame`.** Handing skia the previous frame is what
 * makes loading a whole animation linear instead of quadratic, and it is also how a
 * disposal method quietly produces the wrong picture — a frame composited onto one it
 * was not supposed to be built on looks plausible and is wrong. So every frame is
 * compared, pixel for pixel, against `SkiaFrameDecoder.frame`, which asks for each one
 * from scratch.
 */
class ForwardFramesTest {

    private fun walk(name: String): Map<Int, IntArray> {
        val file = SampleMedia.file(name)
        val frames = mutableMapOf<Int, IntArray>()
        forwardFrames(file) { index, bitmap -> frames[index] = bitmap.toPixelMap().buffer }
        return frames
    }

    @Test
    fun `every frame of a gif matches an independent read of it`() {
        val file = SampleMedia.file(ANIM_GIF)
        val walked = walk(ANIM_GIF)

        assertEquals(ANIM_FRAME_COUNT, walked.size, "the walk lost frames")
        walked.forEach { (index, pixels) ->
            val alone = SkiaFrameDecoder.frame(file, index).toPixelMap().buffer
            assertContentEquals(alone, pixels, "frame $index differs from an independent read")
        }
    }

    @Test
    fun `every frame of an animated webp matches an independent read of it`() {
        val file = SampleMedia.file(ANIM_WEBP)
        val walked = walk(ANIM_WEBP)

        assertEquals(ANIM_FRAME_COUNT, walked.size, "the walk lost frames")
        walked.forEach { (index, pixels) ->
            val alone = SkiaFrameDecoder.frame(file, index).toPixelMap().buffer
            assertContentEquals(alone, pixels, "frame $index differs from an independent read")
        }
    }

    /**
     * What the editing canvas holds. Only a file that actually plays gets frames; a
     * photograph, a burst and a page chain draw their one still, exactly as before.
     */
    @Test
    fun `the editor loads an animation and refuses everything else`() {
        val animation = assertNotNull(editorAnimationOf(SampleMedia.file(ANIM_GIF)))
        assertEquals(ANIM_FRAME_COUNT, animation.frames.size)
        assertNotNull(animation.bitmapAt(ANIM_FRAME_COUNT - 1), "the last frame was not held")

        listOf(STILL_JPG, BURST_HEIC, MULTIPAGE_TIFF).forEach { name ->
            assertNull(editorAnimationOf(SampleMedia.file(name)), "$name was taken for an animation")
        }
    }
}
