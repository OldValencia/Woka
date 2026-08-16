package com.woka.feature.editor.core

private const val SUFFIX = "-edited"

/**
 * The name a **Save a Copy** writes to: `photo.jpg` → `photo-edited.jpg`, and
 * `-2`, `-3`… when that is taken (§5).
 *
 * Reads as what it is, sorts next to the original, and contains no space to break
 * a shell command — the three reasons this shape was chosen over `photo (edited)`
 * or `edited_photo`.
 *
 * Takes [isTaken] rather than a directory so it is a pure function with no
 * filesystem behind it; the caller supplies existence. [baseName] is the stem
 * **without** an extension and [ext] is supplied separately, which is what keeps a
 * converted HEIC becoming `photo-edited.jpg` rather than `photo.heic-edited.jpg`.
 *
 * A source already ending in `-edited` does not gain a second one: re-editing a
 * copy produces `photo-edited-2.jpg`, not `photo-edited-edited.jpg`. Dots inside
 * the stem are left alone — `my.photo.v2` is a name, not an extension.
 */
fun copyName(baseName: String, ext: String, isTaken: (String) -> Boolean): String {
    val stem = if (baseName.endsWith(SUFFIX)) baseName else baseName + SUFFIX
    val suffix = if (ext.isEmpty()) "" else ".$ext"

    val first = "$stem$suffix"
    if (!isTaken(first)) return first

    // From 2 upward: `-edited` is the first copy, so the second is `-edited-2`.
    var index = 2
    while (isTaken("$stem-$index$suffix")) {
        index++
    }
    return "$stem-$index$suffix"
}
