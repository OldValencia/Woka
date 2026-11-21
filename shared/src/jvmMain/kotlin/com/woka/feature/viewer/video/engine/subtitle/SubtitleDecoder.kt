package com.woka.feature.viewer.video.engine.subtitle

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.sync.subtitleTimeMs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import com.woka.feature.viewer.video.model.Cue
import com.woka.feature.viewer.video.model.SubtitleSelection

/**
 * Keeps VideoPlayerTracks.activeCue in step with the play head (§2.2). An embedded track
 * is opened when it is first chosen and read around the play head ([SubtitleWindow]);
 * sidecar files are opened on first selection too, since the picker (Step 9) can add one
 * at any time, and read whole — they are small.
 *
 * **No pass over the container on open** (reported 2026-11-20). One used to read every
 * embedded track before any cue existed, which on a 63.6 GB film was 32.7 s of nothing on
 * screen — and that whole file read again on every open, whatever was selected.
 *
 * Runs under [decoder], the video decoder's job, so `dispose()` ends it with the rest.
 * It ran on the player's long-lived scope until 2026-11-19 (ST-001): this loop never
 * stopped, and its capture kept every closed video's player, 26 retained frames and the
 * full channel alive for the session. Supervised, so a subtitle failure stays its own.
 */
internal fun VideoPlayerState.startSubtitleDecoderLoop(decoder: Job) {
    scope.launch(Dispatchers.IO + SupervisorJob(decoder)) {
        val bitmapTracks = tracks.mediaTracks.subtitle.filter { it.isTextBased == false }.map { it.index }.toSet()
        val embedded = mutableMapOf<Int, SubtitleWindow?>()
        val externalText = mutableMapOf<String, List<Cue>>()
        val externalBitmap = mutableMapOf<String, BitmapSubtitleSource?>()
        var shownSelection: SubtitleSelection? = null
        var shownCue: BitmapCue? = null

        // Pictures are decoded only as the play head reaches them, and only once per cue —
        // an index holds compressed packets, not rasters (see BitmapSubtitleSource).
        fun showBitmap(cue: BitmapCue?, decode: (BitmapCue) -> Cue?) {
            if (cue === shownCue) return
            shownCue = cue
            tracks.activeCue = cue?.let(decode)
        }

        try {
            while (isActive) {
                val selection = tracks.subtitleSelection
                if (selection != shownSelection) {
                    shownSelection = selection
                    shownCue = null
                    tracks.activeCue = null
                }
                val window = (selection as? SubtitleSelection.Embedded)?.let { chosen ->
                    embedded.getOrPut(chosen.index) {
                        openSubtitleWindow(file.absolutePath, chosen.index, chosen.index in bitmapTracks)
                    }
                }
                // subtitleTimeMs, not currentPositionMs: the sync offset (§3) is applied
                // here at lookup, so nothing has to be re-read.
                val atMs = subtitleTimeMs
                val sidecar = externalBitmapFor(selection, externalBitmap)
                when {
                    window != null && window.isBitmap -> showBitmap(window.bitmapAt(atMs)) { cue ->
                        Cue(startMs = cue.startMs, endMs = cue.endMs, image = window.decode(cue))
                    }
                    window != null -> tracks.activeCue = window.textAt(atMs)
                    sidecar != null -> showBitmap(sidecar.cueAt(atMs)) { cue ->
                        Cue(startMs = cue.startMs, endMs = cue.endMs, image = sidecar.decode(cue))
                    }
                    else -> tracks.activeCue = externalTextFor(selection, externalText)?.let { findActiveCue(it, atMs) }
                }
                delay(50.milliseconds)
            }
        } finally {
            embedded.values.forEach { it?.close() }
            externalBitmap.values.forEach { it?.close() }
        }
    }
}

private fun externalBitmapFor(
    selection: SubtitleSelection,
    external: MutableMap<String, BitmapSubtitleSource?>
): BitmapSubtitleSource? {
    if (selection !is SubtitleSelection.External || !isBitmapSubtitleFile(selection.path)) return null
    return external.getOrPut(selection.path) { openBitmapSubtitleFile(selection.path) }
}

private fun externalTextFor(
    selection: SubtitleSelection,
    external: MutableMap<String, List<Cue>>
): List<Cue>? {
    if (selection !is SubtitleSelection.External || isBitmapSubtitleFile(selection.path)) return null
    return external.getOrPut(selection.path) { parseSubtitleFile(File(selection.path)) }
}

private fun findActiveCue(cues: List<Cue>, positionMs: Long): Cue? =
    cues.firstOrNull { positionMs in it.startMs..it.endMs }
