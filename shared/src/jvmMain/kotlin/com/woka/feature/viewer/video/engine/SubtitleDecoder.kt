package com.woka.feature.viewer.video.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import com.woka.feature.viewer.video.model.Cue
import com.woka.feature.viewer.video.model.SubtitleSelection

/**
 * Keeps [VideoPlayerTracks.activeCue] in step with the play head (§2.2). Embedded
 * tracks are prepared in one container pass on open — text parsed into [Cue]
 * lists, bitmap tracks indexed — while sidecar files are opened on first
 * selection, since the picker (Step 9) can add one at any time.
 */
internal fun VideoPlayerState.startSubtitleDecoderLoop() {
    scope.launch(Dispatchers.IO) {
        val subtitleTracks = tracks.mediaTracks.subtitle
        val streams = decodeEmbeddedSubtitles(
            filepath = file.absolutePath,
            textIndices = subtitleTracks.filter { it.isTextBased != false }.map { it.index }.toSet(),
            bitmapIndices = subtitleTracks.filter { it.isTextBased == false }.map { it.index }.toSet()
        )
        val externalText = mutableMapOf<String, List<Cue>>()
        val externalBitmap = mutableMapOf<String, BitmapSubtitleSource?>()
        var shownSelection: SubtitleSelection? = null
        var shownCue: BitmapCue? = null

        try {
            while (isActive) {
                val selection = tracks.subtitleSelection
                if (selection != shownSelection) {
                    shownSelection = selection
                    shownCue = null
                    tracks.activeCue = null
                }
                val source = bitmapSourceFor(selection, streams, externalBitmap)
                if (source != null) {
                    // Pictures are decoded only as the play head reaches them, and
                    // only once per cue — the index holds compressed packets, not
                    // rasters (see BitmapSubtitleSource).
                    val cue = source.cueAt(subtitleTimeMs)
                    if (cue !== shownCue) {
                        shownCue = cue
                        tracks.activeCue = cue?.let {
                            Cue(startMs = it.startMs, endMs = it.endMs, image = source.decode(it))
                        }
                    }
                } else {
                    // subtitleTimeMs, not currentPositionMs: the sync offset (§3) is
                    // applied here at lookup, so nothing has to be re-parsed.
                    val cues = textCuesFor(selection, streams, externalText)
                    tracks.activeCue = if (cues == null) null else findActiveCue(cues, subtitleTimeMs)
                }
                delay(50.milliseconds)
            }
        } finally {
            streams.close()
            externalBitmap.values.forEach { it?.close() }
        }
    }
}

private fun bitmapSourceFor(
    selection: SubtitleSelection,
    streams: SubtitleStreams,
    external: MutableMap<String, BitmapSubtitleSource?>
): BitmapSubtitleSource? = when (selection) {
    is SubtitleSelection.Off -> null
    is SubtitleSelection.Embedded -> streams.bitmap[selection.index]
    is SubtitleSelection.External -> if (isBitmapSubtitleFile(selection.path)) {
        external.getOrPut(selection.path) { openBitmapSubtitleFile(selection.path) }
    } else {
        null
    }
}

private fun textCuesFor(
    selection: SubtitleSelection,
    streams: SubtitleStreams,
    external: MutableMap<String, List<Cue>>
): List<Cue>? = when (selection) {
    is SubtitleSelection.Off -> null
    is SubtitleSelection.Embedded -> streams.text[selection.index]
    is SubtitleSelection.External -> if (isBitmapSubtitleFile(selection.path)) {
        null
    } else {
        external.getOrPut(selection.path) { parseSubtitleFile(File(selection.path)) }
    }
}

private fun findActiveCue(cues: List<Cue>?, positionMs: Long): Cue? {
    if (cues.isNullOrEmpty()) return null
    for (cue in cues) {
        if (positionMs in cue.startMs..cue.endMs) {
            return cue
        }
    }
    return null
}
