package com.woka.feature.editor.core

/**
 * The single answer to "can this file be edited, and by what".
 *
 * **Null means not editable**, and that null is what greys out both Edit entry
 * points — the viewer's control pill and the grid's context menu (§4.1). Having
 * one function decide is the point: a second list of extensions somewhere else is
 * how the two entry points start disagreeing about the same file.
 *
 * [frameIndex] selects the picture inside a file that holds more than one — a
 * burst, an animation, a multi-page TIFF. It is the decoder's own index, so it
 * only means anything paired with the file it came from.
 *
 * Editability here is a question about the **format**, not about the bytes: a
 * `.png` that turns out to be truncated still yields an engine, and fails when it
 * is read. Deciding otherwise would mean decoding every file in a grid to know
 * whether to grey out a menu item.
 */
interface EditorEngineFactory {
    /**
     * A plain interface rather than a `fun interface`: Kotlin forbids a default
     * parameter on a functional interface's single method, and defaulting
     * [frameIndex] to the first picture is worth more than SAM conversion for a
     * type whose only implementation is a class.
     */
    fun create(path: String, frameIndex: Int = 0): EditorEngine?
}
