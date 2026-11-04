package com.woka.feature.viewer.video

import com.woka.core.ui.image.SourceRange
import com.woka.feature.viewer.shell.slider.SliderSegment
import com.woka.feature.viewer.shell.slider.WHOLE_TRACK
import com.woka.feature.viewer.video.model.MediaChapter

/**
 * Chapters as track segments (§4). The only place that knows a chapter is a time
 * range: the shared slider is handed plain fractions, which is what stopped it
 * from having to import the video package to draw its own track.
 */
internal fun chapterSegments(
    chapters: List<MediaChapter>,
    durationMs: Long
): List<SliderSegment> {
    if (chapters.isEmpty() || durationMs <= 0L) return WHOLE_TRACK
    return chapters.map { chapter ->
        SliderSegment(
            start = chapter.startMs.toFloat() / durationMs,
            end = chapter.endMs.toFloat() / durationMs,
            label = chapter.title
        )
    }
}

/**
 * The chapters of a film `Done` cut (ME-284), laid on the **result**: each kept range
 * shows the pieces of the chapters it holds, in the order the ranges play. A cut film used
 * to draw no track at all — an empty list here meant no segment, so the bar vanished
 * (reported 2026-11-04 on a two-split `mkv`).
 */
internal fun chapterSegmentsOnResult(chapters: List<MediaChapter>, ranges: List<SourceRange>): List<SliderSegment> {
    val total = ranges.sumOf { it.lengthMs }
    if (chapters.isEmpty() || total <= 0L) return WHOLE_TRACK
    var offset = 0L
    val pieces = ranges.flatMap { range ->
        val start = offset
        offset += range.lengthMs
        chapters.mapNotNull { chapter ->
            val from = maxOf(chapter.startMs, range.fromMs)
            val to = minOf(chapter.endMs, range.toMs)
            if (to <= from) return@mapNotNull null
            SliderSegment(
                start = (start + (from - range.fromMs) / range.speed).toFloat() / total,
                end = (start + (to - range.fromMs) / range.speed).toFloat() / total,
                label = chapter.title
            )
        }
    }
    return pieces.ifEmpty { WHOLE_TRACK }
}
