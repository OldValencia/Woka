package com.woka.integration

import com.woka.core.data.mayHoldFrames
import com.woka.core.data.needsWokaCover
import com.woka.core.utils.countFrames
import com.woka.core.utils.loadStillCover
import com.woka.fixtures.ANIM_FRAME_COUNT
import com.woka.fixtures.ANIM_GIF
import com.woka.fixtures.BURST_FRAME_COUNT
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.MULTIPAGE_TIFF
import com.woka.fixtures.STILL_AVIF
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.STILL_PNG
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.TIFF_PAGE_COUNT
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a grid tile shows for a file Coil cannot read.
 *
 * The cover and the badge are the two halves of it, and both come from the same
 * decoders the viewer uses — the grid used to draw a grey rectangle and the
 * extension instead.
 */
class GridCoverTest {

    @Test
    fun `the formats Coil is blind to still produce a cover`() = withTempStorageRoot {
        listOf(BURST_HEIC, STILL_AVIF, MULTIPAGE_TIFF).forEach { name ->
            val file = SampleMedia.file(name)
            assertTrue(file.needsWokaCover, name)
            val cover = assertNotNull(runBlocking { loadStillCover(file) }, name)
            assertTrue(cover.width in 1..400 && cover.height in 1..400, "$name is ${cover.width}px")
        }
    }

    /** The tile is a preview, not the picture: a 36-megapixel HEIC costs the same as a JPEG. */
    @Test
    fun `a cover is never larger than a preview`() = withTempStorageRoot {
        val cover = assertNotNull(runBlocking { loadStillCover(SampleMedia.file(STILL_PNG)) })
        assertTrue(cover.width <= 400 && cover.height <= 400, "${cover.width}x${cover.height}")
    }

    @Test
    fun `the badge counts what is inside a frame set`() = withTempStorageRoot {
        mapOf(
            BURST_HEIC to BURST_FRAME_COUNT,
            ANIM_GIF to ANIM_FRAME_COUNT,
            MULTIPAGE_TIFF to TIFF_PAGE_COUNT
        ).forEach { (name, expected) ->
            assertEquals(expected, runBlocking { countFrames(SampleMedia.file(name)) }, name)
        }
    }

    /** One picture keeps the extension badge — "1 FRAMES" says nothing. */
    @Test
    fun `an ordinary photograph counts one`() = withTempStorageRoot {
        listOf(STILL_JPG, STILL_PNG, STILL_AVIF).forEach { name ->
            assertEquals(1, runBlocking { countFrames(SampleMedia.file(name)) }, name)
        }
    }

    /**
     * The card only opens a file whose name says it might hold a set. Being
     * wrong here means every JPEG in a folder is opened to draw a badge.
     */
    @Test
    fun `only frame-capable names are opened for a count`() {
        assertTrue(SampleMedia.file(BURST_HEIC).mayHoldFrames)
        assertTrue(!SampleMedia.file(STILL_JPG).mayHoldFrames)
        assertTrue(!SampleMedia.file(STILL_PNG).mayHoldFrames)
    }
}
