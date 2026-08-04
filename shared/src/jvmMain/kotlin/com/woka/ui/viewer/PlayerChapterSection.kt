package com.woka.ui.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.woka.ui.viewer.player.VideoPlayerState
import com.woka.ui.viewer.player.seek

/**
 * Chapter list in the Settings ⚙ popover (§4). Secondary navigation belongs in
 * the settings drawer, same reasoning as Video Track (§2.3); hidden entirely
 * when the file has no chapters.
 */
@Composable
internal fun PlayerChapterSection(videoState: VideoPlayerState) {
    val chapters = videoState.tracks.chapters
    if (chapters.isEmpty()) return

    // currentPositionMs changes at the presenter's rate; derived so the rows only
    // recompose when the play head actually crosses a boundary.
    val currentIndex by remember(chapters) {
        derivedStateOf {
            val position = videoState.currentPositionMs
            chapters.indexOfFirst { position >= it.startMs && position < it.endMs }
        }
    }

    PopoverSectionLabel("Chapters")
    chapters.forEachIndexed { index, chapter ->
        PopoverListRow(
            label = chapter.title,
            selected = index == currentIndex,
            onClick = { videoState.seek(chapter.startMs) }
        )
    }
}
