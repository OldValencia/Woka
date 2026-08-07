package com.woka.integration

import com.woka.fixtures.ANIM_GIF
import com.woka.fixtures.ANIM_WEBP
import com.woka.fixtures.BITMAP_SUBS
import com.woka.fixtures.BROKEN
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.CHAPTERS
import com.woka.fixtures.DUAL_VIDEO
import com.woka.fixtures.EXIF_ROTATED
import com.woka.fixtures.HDR_HLG_AVIF
import com.woka.fixtures.HDR_PQ_AVIF
import com.woka.fixtures.HDR_PQ_PNG
import com.woka.fixtures.LIVE_PHOTO_MOTION
import com.woka.fixtures.LIVE_PHOTO_STILL
import com.woka.fixtures.MULTIPAGE_TIFF
import com.woka.fixtures.NO_AUDIO
import com.woka.fixtures.STILL_AVIF
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.STILL_PNG
import com.woka.fixtures.STILL_WEBP
import com.woka.fixtures.TEXT_SUBS
import com.woka.fixtures.SampleMedia
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the corpus itself. Everything here is committed, so a missing or empty
 * file means a broken checkout — the tests that depend on it would otherwise fail
 * in ways that point at the player instead of at the fixtures.
 */
class SampleMediaTest {

    private val declared = listOf(
        BITMAP_SUBS, DUAL_VIDEO, TEXT_SUBS, CHAPTERS, NO_AUDIO, BROKEN,
        STILL_PNG, STILL_JPG, STILL_WEBP, EXIF_ROTATED,
        BURST_HEIC, LIVE_PHOTO_STILL, LIVE_PHOTO_MOTION, STILL_AVIF,
        ANIM_GIF, ANIM_WEBP, MULTIPAGE_TIFF,
        HDR_PQ_AVIF, HDR_HLG_AVIF, HDR_PQ_PNG
    )

    @Test
    fun `every declared fixture resolves to a non-empty file`() {
        declared.forEach { name ->
            val file = SampleMedia.file(name)
            assertTrue(file.length() > 0, "$name is empty")
        }
    }

    @Test
    fun `sidecar subtitles sit beside the episode sharing their stem`() {
        listOf(".mkv", ".srt", ".ru.srt", ".rus.srt").forEach { suffix ->
            val name = SampleMedia.episode(1, suffix)
            assertTrue(SampleMedia.file(name).length() > 0, "$name is empty")
        }
        listOf(".mkv", ".en.srt", ".ass").forEach { suffix ->
            val name = SampleMedia.episode(2, suffix)
            assertTrue(SampleMedia.file(name).length() > 0, "$name is empty")
        }
    }

    /** The negative case the scan has to get right: two stems in one directory. */
    @Test
    fun `the two episode stems differ`() {
        val first = SampleMedia.episode(1, "")
        val second = SampleMedia.episode(2, "")
        assertTrue(first != second, "both episodes resolved to the same stem")
        assertTrue(
            SampleMedia.root.listFiles()?.any { it.name.startsWith(second) } == true,
            "no file carries the second stem"
        )
    }
}
