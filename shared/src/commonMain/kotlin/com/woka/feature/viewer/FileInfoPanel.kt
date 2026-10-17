package com.woka.feature.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.woka.core.data.MediaItem

/**
 * The right-hand Info panel's content for one item (SIDE-009): what the viewer's
 * File Information dialog says, laid out as a column instead of a dialog — a
 * preview, the name, path, size and date, and for a video its streams.
 *
 * `expect` here and `actual` in `jvmMain` beside the dialog it reuses, because the
 * stream probe is the player's machinery.
 */
@Composable
expect fun FileInfoPanel(item: MediaItem, modifier: Modifier = Modifier)
