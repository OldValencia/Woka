package com.woka.integration

import com.woka.core.ui.image.SourceRange
import com.woka.feature.viewer.video.engine.preview.hoverExcerpts
import com.woka.probe.CLIP_FPS
import com.woka.probe.generateClip
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The hover reel (SIDE-006) on the two things found by hand 2026-11-04: an `avi` whose
 * container is longer than its picture, and a cut film (ME-284).
 */
class HoverExcerptsTest {

    private fun onClip(format: String, block: (File) -> Unit) {
        val dir = Files.createTempDirectory("woka-hover").toFile()
        try {
            val clip = File(dir, "clip.${if (format == "matroska") "mkv" else format}")
            generateClip(clip, "libopenh264", seconds = 10, gop = CLIP_FPS, format = format)
            block(clip)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun excerpts(clip: File, kept: List<SourceRange> = emptyList()) = runBlocking {
        withTimeout(60_000) { hoverExcerpts(clip, kept).take(5).toList() }
    }

    /**
     * Muxed with AAC, the `avi` declares 26.4 s against 10 s of video — measured — and the
     * reel used to place excerpts three to five past the last frame and stop after two.
     */
    @Test
    fun `an avi longer than its picture still plays all five excerpts`() = onClip("avi") { clip ->
        val got = excerpts(clip)
        assertEquals(listOf(0, 1, 2, 3, 4), got.map { it.index })
        assertTrue(got.all { it.frames.isNotEmpty() }, "an excerpt came back empty")
    }

    /**
     * Two kept seconds out of ten: every excerpt lands inside them and none runs past the
     * cut, so no excerpt is the full 2.5 s.
     */
    @Test
    fun `a cut film samples only what was kept`() = onClip("mp4") { clip ->
        val got = excerpts(clip, listOf(SourceRange(4_000, 6_000)))
        assertEquals(5, got.size)
        val longest = got.maxOf { it.frames.size }
        assertTrue(longest <= 2 * CLIP_FPS, "an excerpt ran past the kept range: $longest frames")
    }
}
