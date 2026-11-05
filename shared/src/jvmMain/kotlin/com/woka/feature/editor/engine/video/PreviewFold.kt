package com.woka.feature.editor.engine.video

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.editor.engine.image.fold
import com.woka.feature.editor.tools.resize.ResizeTool

/**
 * A film's picture as it is **shown** — the editor's canvas and the viewer — which is the
 * whole fold except Resize (ME-262, the author's choice 2026-11-02: Resize sizes the
 * render and nothing else).
 *
 * **Not only a matter of what is visible.** Folded in, a 25 % Resize shrank the frame the
 * canvas tools measure against, and Crop could no longer reach past the top-left quarter
 * of the picture (reported the same day, with a screenshot). The render folds the whole
 * document, Resize included; nothing else in the video path does.
 */
internal fun previewFold(document: EditDocument, registry: EditorToolRegistry, sourceSize: IntSize): RasterRecipe =
    fold(document.without(ResizeTool.manifest.id), registry, sourceSize)
