package com.woka.integration

import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.FrameKind
import com.woka.core.data.ImageFrameSet
import com.woka.core.data.MediaItem
import com.woka.feature.viewer.image.ImageLoader
import com.woka.feature.viewer.shell.MediaThumbnails
import com.woka.fixtures.ANIM_AVIF
import com.woka.fixtures.ANIM_FRAME_COUNT
import com.woka.fixtures.ANIM_GIF
import com.woka.fixtures.ANIM_WEBP
import com.woka.fixtures.BURST_FRAME_COUNT
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.LIVE_PHOTO_STILL
import com.woka.fixtures.MULTIPAGE_TIFF
import com.woka.fixtures.STILL_AVIF
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.STILL_PNG
import com.woka.fixtures.STILL_WEBP
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.TIFF_PAGE_COUNT
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What is inside a file, and whether the pictures that come out of it are
 * actually different pictures.
 *
 * The counts are not guesses: every one is what `DecoderCapabilityProbe`
 * measured against the same fixture, so a decoder that regresses to
 * "one frame, the first one" — the failure this whole feature exists to remove
 * — fails here rather than looking like a file with nothing in it.
 */
class FrameSetDecodeTest {

    private fun frameSetOf(name: String): ImageFrameSet = runBlocking {
        MediaThumbnails.frameSet(MediaItem.Image(SampleMedia.file(name)))
    }

    /** Every frame of a set, as raw pixels, for comparing one against another. */
    private fun framePixels(name: String): List<List<Int>> {
        val file = SampleMedia.file(name)
        return frameSetOf(name).frames.map { frame ->
            ImageLoader.load(file, frame.index).toPixelMap().buffer.toList()
        }
    }

    @Test
    fun `every fixture reports the frame count the probe recorded`() {
        val expected = mapOf(
            BURST_HEIC to BURST_FRAME_COUNT,
            ANIM_AVIF to ANIM_FRAME_COUNT,
            ANIM_GIF to ANIM_FRAME_COUNT,
            ANIM_WEBP to ANIM_FRAME_COUNT,
            MULTIPAGE_TIFF to TIFF_PAGE_COUNT,
            LIVE_PHOTO_STILL to 1,
            STILL_AVIF to 1,
            STILL_PNG to 1,
            STILL_JPG to 1,
            STILL_WEBP to 1
        )
        expected.forEach { (name, count) ->
            assertEquals(count, frameSetOf(name).frames.size, name)
        }
    }

    @Test
    fun `an ordinary photograph is a single frame and stays free`() {
        listOf(STILL_PNG, STILL_JPG, STILL_WEBP, STILL_AVIF).forEach { name ->
            assertTrue(frameSetOf(name).isSingle, name)
        }
    }

    /**
     * The finding Phase 0 exists for: HEIF stores each image as **its own video
     * stream**, so a grabber that loops `grabImage()` reports one frame for a
     * five-shot burst. If this drops to 1, the stream walk has been lost.
     */
    @Test
    fun `a HEIC burst is read as five streams, not one frame`() {
        val set = frameSetOf(BURST_HEIC)
        assertFalse(set.isSingle)
        assertEquals(BURST_FRAME_COUNT, set.frames.size)
        assertEquals(FrameKind.Primary, set.frames.first().kind)
        assertTrue(set.frames.drop(1).all { it.kind == FrameKind.Sequence })
        val indices = set.frames.map { it.index }
        assertEquals(indices.distinct(), indices, "two frames point at one stream")
    }

    /** A Live Photo's motion is the sibling `.mov`; the still itself is one image. */
    @Test
    fun `a Live Photo still is a single frame on its own`() {
        assertTrue(frameSetOf(LIVE_PHOTO_STILL).isSingle)
    }

    /**
     * Counting frames is worth nothing if every index decodes the same picture,
     * which is exactly what a decoder that ignores its frame argument does.
     * Each fixture plate carries its own number painted across it.
     */
    @Test
    fun `every frame of a set decodes to a different picture`() {
        listOf(
            BURST_HEIC to BURST_FRAME_COUNT,
            ANIM_AVIF to ANIM_FRAME_COUNT,
            ANIM_GIF to ANIM_FRAME_COUNT,
            ANIM_WEBP to ANIM_FRAME_COUNT,
            MULTIPAGE_TIFF to TIFF_PAGE_COUNT
        ).forEach { (name, count) ->
            val frames = framePixels(name)
            assertEquals(count, frames.size, name)
            assertEquals(count, frames.distinct().size, "$name repeats a frame")
        }
    }

    /** Skia's animation frames carry timings; a burst's streams have none. */
    @Test
    fun `only animation carries per-frame durations`() {
        assertTrue(frameSetOf(ANIM_GIF).frames.all { it.durationMs != null })
        assertTrue(frameSetOf(ANIM_AVIF).frames.all { it.durationMs != null })
        assertTrue(frameSetOf(BURST_HEIC).frames.all { it.durationMs == null })
        assertTrue(frameSetOf(MULTIPAGE_TIFF).frames.all { it.durationMs == null })
    }

    /**
     * An animated AVIF is a **one-frame cover stream plus an eight-frame
     * animation**, and both are tagged `default`. Counting streams gives 2 and
     * counting the first stream's frames gives 1; only picking the stream that
     * has more than one frame gives 8. If this reads 2, the layout has gone
     * back to counting streams.
     */
    @Test
    fun `an animated AVIF reports its frames, not its two streams`() {
        val set = frameSetOf(ANIM_AVIF)
        assertEquals(ANIM_FRAME_COUNT, set.frames.size)
        assertEquals(125, set.frames.first().durationMs, "8 fps is 125 ms a frame")
    }

    /**
     * What the clock reads (ME-140): an animation declares durations and how often it
     * repeats; a burst and a page chain declare neither and must stay unplayable.
     */
    @Test
    fun `only an animation reports timings and a repetition count`() {
        listOf(ANIM_GIF, ANIM_WEBP).forEach { name ->
            val set = frameSetOf(name)
            assertTrue(set.isAnimated, "$name is not animated")
            assertTrue(set.frames.all { (it.durationMs ?: 0) > 0 }, "$name has a frame with no delay")
            assertNotNull(set.repeatCount, "$name never said how often it loops")
        }
        listOf(BURST_HEIC, MULTIPAGE_TIFF).forEach { name ->
            val set = frameSetOf(name)
            assertFalse(set.isAnimated, "$name was taken for an animation")
        }
    }
}
