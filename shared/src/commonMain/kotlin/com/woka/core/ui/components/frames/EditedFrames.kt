package com.woka.core.ui.components.frames

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.core.ui.image.rememberReloadKey
import com.woka.core.ui.theme.Theme
import java.io.File

/** 5 dp: large enough to see beside a 48 dp thumbnail, small enough not to be a badge. */
private val DOT_SIZE = 5.dp

private val DOT_INSET = 5.dp

internal const val EDITED_DOT_TAG = "frame-edited"

/**
 * Which frames of the file at [path] carry an edit of their own (ME-157).
 *
 * Keyed on the path **and on `rememberReloadKey`**, the same signal `FrameThumbnail`
 * already re-reads its bitmap on (ME-135), so a save moves the marks instead of
 * leaving a stale one behind.
 *
 * Returns empty until the first read completes, which is the honest initial state: no
 * mark is better than a mark that may be wrong.
 */
@Composable
internal fun rememberEditedFrames(path: String): Set<Int> {
    var edited by remember(path) { mutableStateOf(emptySet<Int>()) }
    val reloadKey = rememberReloadKey(path)
    LaunchedEffect(path, reloadKey) {
        edited = AppliedStillEdits.editedFrames(File(path))
    }
    return edited
}

/**
 * The mark itself: a filled dot in the thumbnail's corner, `accentEditor` — the colour
 * the editor already uses for a selected tool and the `Done` pill, so "this is the
 * editor's doing" needs no legend.
 *
 * **Not a badge with a glyph.** `MotionBadge` beside it is already a glyph, and two
 * badges on a 48 dp square is a wall.
 */
@Composable
internal fun EditedDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(DOT_INSET)
            .size(DOT_SIZE)
            .clip(CircleShape)
            .background(Theme.colors.accentEditor)
            .testTag(EDITED_DOT_TAG)
    )
}
