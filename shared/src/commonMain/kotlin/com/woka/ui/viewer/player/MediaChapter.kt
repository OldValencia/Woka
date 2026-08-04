package com.woka.ui.viewer.player

/**
 * One container chapter (§4), normalised by the probe: [endMs] is always greater
 * than [startMs], both sit inside the file's duration, and [title] is never
 * blank. Gaps between neighbours are real — the file claims no chapter there.
 */
data class MediaChapter(
    val startMs: Long,
    val endMs: Long,
    val title: String
)
