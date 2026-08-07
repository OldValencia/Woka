package com.woka.core.data

import androidx.compose.runtime.Immutable

/**
 * A still's colour description — the still-side twin of `HdrInfo`.
 *
 * It exists because colour was a video-only concept: `HdrInfo` is derived from
 * FFmpeg stream ids inside the player, and `MediaViewerFrame` picks its painter
 * from `video.player.currentFrameHdrInfo`. A photograph had no path to that
 * decision at all, so a PQ still rendered as SDR.
 *
 * **The defaults are the common case.** The overwhelming majority of stills are
 * SDR sRGB, so every existing construction site that does not say otherwise
 * keeps exactly the behaviour it has.
 *
 * Filled from the FFmpeg path and **never** from skia: measured 2026-08-07,
 * skia describes a PNG carrying a valid PQ `cICP` chunk as "gamma close to
 * sRGB". `ColorSpace` exposes three booleans and no transfer function, so the
 * question cannot be asked there — see Phase 0 of
 * `ai/docs/features/pending/rare-image-formats-and-frame-sets.md`.
 */
@Immutable
data class StillColorInfo(
    val transfer: TransferKind = TransferKind.SDR,
    val isWideGamut: Boolean = false
) {
    val needsToneMap: Boolean
        get() = needsToneMap(transfer, isWideGamut)

    companion object {
        /** What a file that never said anything about its colour is taken to be. */
        val Sdr = StillColorInfo()
    }
}
