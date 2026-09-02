package com.woka.unit

import com.woka.core.data.HdrView
import com.woka.core.data.HdrViewMode
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.image.displayable
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.fixtures.HDR_REAL_PQ_AVIF
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_JPG
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * The switch overrides what Woka *believes* about a file (ME-119).
 *
 * Each mode is asserted against the file that contradicts it, because that is the only
 * way to tell an override from agreement: `ForceSdr` is checked on the one genuine HDR
 * still, and `ForceHdr` on an ordinary JPEG. Checking either on a file it already agreed
 * with would pass without the feature existing.
 */
class HdrViewModeTest {

    @AfterTest
    fun restore() = HdrView.install { HdrViewMode.Auto }

    private fun decoded(name: String) =
        SampleMedia.file(name).let { it to stillDecoderFor(it).frame(it, 0) }

    @Test
    fun `Force SDR leaves a genuine HDR still exactly as it was decoded`() {
        HdrView.install { HdrViewMode.ForceSdr }
        val (file, bitmap) = decoded(HDR_REAL_PQ_AVIF)
        assertSame(bitmap, displayable(file, bitmap), "Force SDR still tone mapped")
    }

    @Test
    fun `Force HDR maps a file whose tags say SDR`() {
        HdrView.install { HdrViewMode.ForceHdr }
        val (file, bitmap) = decoded(STILL_JPG)
        assertNotSame(bitmap, displayable(file, bitmap), "Force HDR believed the tags")
    }

    @Test
    fun `Auto is the file's own answer, both ways`() {
        HdrView.install { HdrViewMode.Auto }
        val (hdr, hdrBitmap) = decoded(HDR_REAL_PQ_AVIF)
        val (sdr, sdrBitmap) = decoded(STILL_JPG)
        assertNotSame(hdrBitmap, displayable(hdr, hdrBitmap))
        assertSame(sdrBitmap, displayable(sdr, sdrBitmap))
    }

    @Test
    fun `Auto keeps the cache keys it already had`() {
        // The reason the suffix is empty on the default rather than "|auto": a mode that
        // renamed every key would rebuild every thumbnail in the library the first time
        // this feature shipped, for people who never touch it.
        HdrView.install { HdrViewMode.Auto }
        assertEquals("C:/pics/a.jpg", ImageBitmapCache.key("C:/pics/a.jpg", 0))
        assertEquals("C:/pics/a.jpg#preview", ImageBitmapCache.previewKey("C:/pics/a.jpg", 0))
    }

    @Test
    fun `a switched mode is a different key, and switching back is the old one`() {
        val path = "C:/pics/a.jpg"
        HdrView.install { HdrViewMode.Auto }
        val auto = ImageBitmapCache.key(path, 0)

        HdrView.install { HdrViewMode.ForceSdr }
        val forced = ImageBitmapCache.key(path, 0)
        assertNotSame(auto, forced)

        // The point of putting the mode in the key rather than clearing the cache: going
        // back is a hit, not a rebuild.
        HdrView.install { HdrViewMode.Auto }
        assertEquals(auto, ImageBitmapCache.key(path, 0))
    }
}
