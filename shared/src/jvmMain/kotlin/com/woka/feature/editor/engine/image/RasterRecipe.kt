package com.woka.feature.editor.engine.image

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.ToolOp

/**
 * A whole document collapsed into what one draw call needs (§3.2).
 *
 * The canvas draws: source → [matrix] → [colorFilter] → [paints]. The same recipe
 * runs at screen scale for the preview and at scale 1.0 against the native bitmap
 * on save, which is what makes "markup rasterised at source resolution" fall out
 * of the design instead of being a special case.
 *
 * [colorFilter] is **null** when no colour operation is present, so a document
 * that only crops costs nothing at draw time — an identity filter would still be
 * a filter the GPU applies to every pixel.
 *
 * [skipped] carries operations whose tool is missing or switched off. They are
 * reported in the inspector rather than dropped silently or thrown on: §3.4's
 * promise is that a document never crashes and never silently loses work, and a
 * disabled tool must be re-enableable with its operations intact.
 */
@Immutable
data class RasterRecipe(
    val matrix: Matrix,
    val outputSize: IntSize,
    val colorFilter: ColorFilter?,
    val paints: List<RasterStage.Paint>,
    val skipped: List<ToolOp>
)
