package com.woka.feature.viewer.video

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
