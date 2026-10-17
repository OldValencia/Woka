package com.woka.feature.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.io.File

/**
 * A hovered video's five excerpts, drawn over its cover (SIDE-006): moving, muted,
 * tone-mapped when the file is HDR, with a five-segment bar across the top.
 *
 * Composed only while the pointer rests on the card, and nothing is decoded for the
 * first 400 ms, so a pointer crossing the grid costs nothing. Until the first frame
 * arrives it draws nothing at all, and the poster beneath it stays.
 *
 * `expect` here and `actual` in `jvmMain`, beside `NativeMediaViewer`: it is the
 * player's machinery, and the folder grid reaches it the way it reaches the viewer.
 */
@Composable
expect fun VideoHoverExcerpts(file: File, modifier: Modifier = Modifier)
