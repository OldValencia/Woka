package com.woka.feature.viewer.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme

/**
 * The two pieces of nothing that separate a pill's groups.
 *
 * Their own file since ME-179, and `internal` rather than private: the pill is built in
 * two places — the viewer's row and `CanvasZoomPill`, for an editor opened with no viewer
 * beneath it — and a second copy of an 8.dp spacer and a 1.dp rule is how two rows that
 * must look identical quietly stop doing so.
 */

/** The one gap width the pill uses, whether spaced by the row or by [PillGap]. */
internal val PILL_GAP = 8.dp

@Composable
internal fun PillGap() {
    Spacer(modifier = Modifier.width(PILL_GAP))
}

@Composable
internal fun PillDivider() {
    Box(modifier = Modifier.height(16.dp).width(1.dp).background(Theme.colors.border))
}
