package com.woka.feature.viewer.video.engine.decode

/**
 * Decoder threads for every grabber that reads one picture or a probe, not a film:
 * posters, still facts, cover art, the info panel.
 *
 * JavaCV opens each decoder with `thread_count(0)` — FFmpeg's `auto`, one thread per
 * core plus one — unless the caller says otherwise, and with frame threading every one
 * of those threads holds its own set of full-size pictures. Measured 2026-11-19 (ST-001):
 * opening a folder of sixteen 4K/8K clips with fresh posters peaked at 5 GB private /
 * 3.75 GB working set on a 16-thread machine. Playback keeps its own [DECODE_THREADS].
 */
internal const val LIGHT_DECODE_THREADS = "2"
