package com.woka.feature.editor.engine.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import java.io.File

/**
 * The one place the image engine turns a recipe into pixels on screen.
 *
 * Both of its drawing surfaces — the canvas and a preview-choice thumbnail (ME-112) —
 * are the same call with a different recipe and a different box, and they **must** share
 * `rememberEditorFrame`: it holds the session's proxy, and a second loader would mean a
 * decode per thumbnail per recomposition.
 *
 * A separate file because `ImageEditorEngine` is at the size mandate, and because the
 * sharing is the point worth stating once rather than twice.
 */
@Composable
internal fun ImageCanvas(
    file: File,
    frameIndex: Int,
    playback: EditorPlayback,
    sourceSize: IntSize,
    recipe: RasterRecipe,
    modifier: Modifier
) {
    RasterSurface(
        proxy = rememberEditorFrame(file, frameIndex, playback),
        sourceSize = sourceSize,
        recipe = recipe,
        modifier = modifier
    )
}
