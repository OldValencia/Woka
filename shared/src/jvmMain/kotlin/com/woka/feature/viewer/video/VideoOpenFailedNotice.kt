package com.woka.feature.viewer.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Says that the file would not open, where a frame would be (VF-103). Measured
 * 2026-11-01 (`TruncatedFileProbe`): every cut MPEG, TS, MKV and DAV plays to its cut
 * and stops as at an end; the one that does not open at all is an `.mp4` whose
 * recording stopped before its index was written, and without the index there is
 * nothing to recover from. Before this the viewer showed the placeholder and 0:00,
 * which reads as a file still loading.
 */
@Composable
fun VideoOpenFailedNotice(isVisible: Boolean) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            NoticeCard(
                text = "This video can't be played. The file may be incomplete — a " +
                    "recording that stopped before it was finished — or damaged."
            )
        }
    }
}
