package com.woka.integration

import com.woka.core.data.MediaItem
import com.woka.feature.viewer.shell.CarouselChip
import com.woka.feature.viewer.shell.chipFor
import com.woka.feature.viewer.video.engine.probe.containerLengthMs
import com.woka.fixtures.ANIM_GIF
import com.woka.fixtures.ANIM_WEBP
import com.woka.fixtures.BURST_FRAME_COUNT
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.CHAPTERS
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.SampleMedia
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What each kind of file says on its carousel tile (RD-502, `carouselBadge`), read from the
 * fixtures themselves: an animation its format, a burst its count, a film its length, a track
 * its music, a single still nothing.
 */
class CarouselChipTest {

    private fun chip(item: MediaItem) = runBlocking { chipFor(item) }

    @Test
    fun `an animation is named by its format`() {
        assertEquals(CarouselChip.Animation("GIF"), chip(MediaItem.Image(SampleMedia.file(ANIM_GIF))))
        assertEquals(CarouselChip.Animation("WEBP"), chip(MediaItem.Image(SampleMedia.file(ANIM_WEBP))))
    }

    @Test
    fun `a burst shows its count and a single still nothing`() {
        assertEquals(CarouselChip.Frames(BURST_FRAME_COUNT), chip(MediaItem.Image(SampleMedia.file(BURST_HEIC))))
        assertEquals(CarouselChip.None, chip(MediaItem.Image(SampleMedia.file(STILL_JPG))))
    }

    @Test
    fun `a film shows its length and a track its music`() {
        val film = SampleMedia.file(CHAPTERS)
        val length = containerLengthMs(film)
        assertTrue(length > 0, "fixture: $CHAPTERS has no length")
        assertEquals(CarouselChip.Film(length), chip(MediaItem.Video(film)))
        assertEquals(CarouselChip.Music, chip(MediaItem.Audio(SampleMedia.file("audio/tone.aac"))))
    }
}
