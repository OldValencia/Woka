package com.woka.unit

import com.woka.core.ui.image.SourceRange
import com.woka.feature.viewer.shell.slider.WHOLE_TRACK
import com.woka.feature.viewer.video.chapterSegmentsOnResult
import com.woka.feature.viewer.video.model.MediaChapter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Chapters on a cut film's slider (reported 2026-11-04: a two-split `mkv` lost both its
 * chapters and the bar itself).
 */
class ChapterSegmentsTest {

    private val chapters = listOf(MediaChapter(0L, 4_000L, "One"), MediaChapter(4_000L, 10_000L, "Two"))

    @Test
    fun `a cut film without chapters still has a whole track`() {
        assertEquals(WHOLE_TRACK, chapterSegmentsOnResult(emptyList(), listOf(SourceRange(0L, 5_000L))))
    }

    @Test
    fun `chapters are laid on the result, in the order the ranges play`() {
        // Seconds 6–10 first, then 2–6: Two's tail, then One's tail, then Two's head.
        val segments = chapterSegmentsOnResult(chapters, listOf(SourceRange(6_000L, 10_000L), SourceRange(2_000L, 6_000L)))
        assertEquals(listOf("Two", "One", "Two"), segments.map { it.label })
        assertEquals(listOf(0f to 0.5f, 0.5f to 0.75f, 0.75f to 1f), segments.map { it.start to it.end })
    }
}
