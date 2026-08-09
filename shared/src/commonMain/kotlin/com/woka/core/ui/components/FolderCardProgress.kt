package com.woka.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.woka.core.data.MediaItem
import com.woka.core.data.ResumeMode
import com.woka.core.data.SettingsRepository
import com.woka.core.data.WatchRecord
import com.woka.core.ui.theme.Theme

/** Thin enough to read as part of the thumbnail rather than a control under it. */
private val BAR_HEIGHT = 3.dp

/**
 * The watched marks a grid draws, resolved once for the whole grid.
 *
 * Bundled because they are always read together and always from the same place: a
 * card that collected settings for itself would recompose every tile in the folder
 * on any settings change at all (§10).
 */
@Immutable
data class WatchMarks(
    val records: Map<String, WatchRecord> = emptyMap(),
    val dimStarted: Boolean = false,
    val enabled: Boolean = false,
    /** Below this length a file is not tracked, mirroring `resumeAppliesTo`. */
    val minDurationMs: Long = 0L
) {
    /**
     * The record to draw for [item] — videos only, and only one this setting would
     * still be recording.
     *
     * That last condition is why the length is kept in the record. Switching to
     * "Long videos" stops the player writing short files, but it does not *erase*
     * what an earlier setting wrote, so without this a clip watched under "Always"
     * kept its bar while refusing to resume — the card claiming something the
     * player no longer honoured. Hidden rather than deleted: changing a setting
     * should not destroy data, and switching back brings the marks with it.
     */
    fun forVideo(item: MediaItem): WatchRecord? =
        records[item.file.absolutePath]?.takeIf {
            enabled && item is MediaItem.Video && it.isTrackedAt(minDurationMs)
        }
}

/**
 * A record with no known length is never hidden: it was asserted by hand from the
 * context menu, and a claim the user made outright is not the settings' to overrule.
 */
private fun WatchRecord.isTrackedAt(minDurationMs: Long): Boolean =
    durationMs <= 0L || durationMs >= minDurationMs

/** Reads the fields the marks need, in one place. */
@Composable
fun rememberWatchMarks(repository: SettingsRepository): WatchMarks {
    val settings by repository.settings.collectAsState()
    return remember(settings) {
        WatchMarks(
            records = settings.watchRecords,
            dimStarted = settings.dimWatchedVideos,
            enabled = settings.resumeMode != ResumeMode.Off,
            minDurationMs = when (settings.resumeMode) {
                ResumeMode.LongOnly -> settings.resumeMinDurationMinutes * 60_000L
                else -> 0L
            }
        )
    }
}

/**
 * How far through a video the viewer got, along the bottom edge of its cover.
 *
 * Partly watched fills to its fraction; finished fills the width. That is the whole
 * vocabulary, and it is why the dimming setting is a separate question: dimming
 * says *touched*, this says *how far*, and a library where both are drawn separates
 * "not started" from "started" from "done" at a glance without a word of text.
 *
 * Drawn inside the cover's own clip, so it follows the card's rounded corners.
 * Nothing is drawn when [WatchRecord.progress] is null — a record with a position
 * but no known duration has no honest fraction, and inventing one would be worse
 * than staying quiet.
 */
@Composable
internal fun BoxScope.FolderCardProgress(record: WatchRecord?) {
    val progress = record?.progress ?: return
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .background(Color.Black.copy(alpha = 0.45f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(Theme.colors.accent)
        )
    }
}
