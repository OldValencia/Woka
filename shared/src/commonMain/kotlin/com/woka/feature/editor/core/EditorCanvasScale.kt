package com.woka.feature.editor.core

import androidx.compose.runtime.compositionLocalOf

/**
 * Canvas pixels per source pixel, published by the editing canvas — **`0f` means
 * "not the editing canvas"** (ME-116).
 *
 * **A `CompositionLocal` because the decision has to arrive from above and the seam
 * refuses to carry it.** `EditorEngine.Surface(document, modifier)` takes no zoom by
 * design (§3.3): the shell must not learn that one engine draws bitmaps. Yet the same
 * `ImageCanvas` draws both the editing canvas and the filter strip's thumbnails, and
 * only the first of those may ever ask for a native-resolution decode.
 *
 * The default is what makes that safe without naming anybody. `EditorBottomPanel` is a
 * **sibling** of `EditorCanvasHost` in `EditorScreen`, so a thumbnail reads `0f` and
 * declines by arithmetic — no exclusion list to keep in step with the panels, and a
 * surface added tomorrow is refused before it is written.
 *
 * `compositionLocalOf`, not `staticCompositionLocalOf`: this changes on every wheel
 * notch, and the static form invalidates the whole subtree rather than its readers.
 *
 * In `core/` rather than `shell/` because it is media-agnostic — the video canvas will
 * publish the same number for the same reason.
 */
internal val LocalCanvasScale = compositionLocalOf { 0f }
