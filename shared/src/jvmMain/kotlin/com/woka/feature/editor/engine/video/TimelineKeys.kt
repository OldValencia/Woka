package com.woka.feature.editor.engine.video

import androidx.compose.ui.input.key.Key
import com.woka.feature.editor.tools.trim.totalMs

/**
 * The timeline's keys (RD-301, the prototype's `S`, `Home`, `End` and the clip menu's `Del`),
 * answered on the clips the timeline draws — the strip it lends this holder while on screen.
 * Off screen, or before a player, nothing is used and the key goes back to the editor.
 *
 * `,` / `.` step one frame while the film is paused, and are left alone while it plays (RD-501,
 * the prototype's `stepFrame`); `Alt+←`/`→` step whether it plays or not.
 *
 * `Delete` arrives only once the canvas has declined it (`EditorKeys`): a selected Markup shape
 * goes before a selected clip. The last clip is never deleted, as the menu's row is disabled.
 */
internal fun VideoPlayerHolder.timelineKey(key: Key): Boolean {
    val strip = strip ?: return false
    val clips = strip.clips
    return when (key) {
        Key.S -> true.also { strip.splitAtPlayHead() }
        Key.MoveHome -> true.also { walk.seekTo(strip.player, clips, 0L) }
        Key.MoveEnd -> true.also { walk.seekTo(strip.player, clips, (clips.totalMs() - 1L).coerceAtLeast(0L)) }
        Key.Comma, Key.Period -> !strip.player.isPlaying &&
            true.also { walk.step(strip.player, if (key == Key.Period) 1 else -1, bySecond = false) }
        Key.Delete -> {
            val id = strip.edits.selectedId?.takeIf { selected -> clips.size > 1 && clips.any { it.id == selected } }
            id?.let { strip.deleteSelected(it) }
            id != null
        }
        else -> false
    }
}
