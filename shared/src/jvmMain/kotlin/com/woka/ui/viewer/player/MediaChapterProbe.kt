package com.woka.ui.viewer.player

import org.bytedeco.ffmpeg.avformat.AVChapter
import org.bytedeco.ffmpeg.global.avutil.av_dict_get
import org.bytedeco.javacv.FFmpegFrameGrabber

/**
 * Reads the container's chapter list (§4) off the **already-open** format
 * context the track probe uses, so this costs no extra I/O.
 *
 * Everything is normalised here so no consumer has to: ends that the container
 * left unstated are closed on the next chapter's start, titles are filled in,
 * both ends are clamped to the file's duration and empty entries are dropped.
 * Fewer than two chapters returns empty — one chapter is not a segmentation,
 * and the UI then behaves exactly as it does for a file with none.
 */
internal fun probeChapters(grabber: FFmpegFrameGrabber, durationMs: Long): List<MediaChapter> {
    val fc = grabber.formatContext ?: return emptyList()
    val count = fc.nb_chapters()
    if (count < 2) return emptyList()

    val raw = (0 until count).mapNotNull { fc.chapters(it)?.toRawChapter() }
    if (raw.size < 2) return emptyList()

    // durationMs is occasionally still 0 straight after start(); the chapters
    // themselves then define the ceiling, so the clamp cannot wipe them out.
    val limit = if (durationMs > 0L) durationMs else raw.maxOf { maxOf(it.start, it.end) }
    if (limit <= 0L) return emptyList()

    val chapters = raw.mapIndexed { i, chapter ->
        val end = if (chapter.end > chapter.start) chapter.end
        else raw.getOrNull(i + 1)?.start ?: limit
        MediaChapter(
            startMs = chapter.start.coerceIn(0L, limit),
            endMs = end.coerceIn(0L, limit),
            title = chapter.title ?: "Chapter ${i + 1}"
        )
    }.filter { it.endMs > it.startMs }

    return if (chapters.size < 2) emptyList() else chapters
}

private class RawChapter(val start: Long, val end: Long, val title: String?)

/**
 * Timestamps go through the chapter's own `time_base` rather than an assumed
 * unit — MKV reports `1/1000000000`, not milliseconds.
 */
private fun AVChapter.toRawChapter(): RawChapter? {
    val timeBase = time_base() ?: return null
    val num = timeBase.num().toLong()
    val den = timeBase.den().toLong()
    if (den <= 0L || num <= 0L) return null
    return RawChapter(
        start = start() * 1000L * num / den,
        end = end() * 1000L * num / den,
        title = chapterTitle()
    )
}

private fun AVChapter.chapterTitle(): String? =
    av_dict_get(metadata(), "title", null, 0)
        ?.value()
        ?.getString()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
