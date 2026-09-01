package com.woka.feature.editor.tools.crop

import androidx.compose.ui.unit.IntSize

/**
 * The ratios a crop can be locked to (ME-109).
 *
 * Six, which is what makes the inspector draw a chip row rather than a segmented
 * control — the schema decides that by counting options, and no code here says so.
 *
 * Stored by `name` through `ChoiceParam`, so these constants are part of the wire
 * format: renaming one is a rewrite of every document that used it, while adding one
 * is safe and retiring one degrades to [Free].
 */
enum class CropAspect(val label: String) {
    Free("Free"),
    Original("Original"),
    Square("1:1"),
    FourThree("4:3"),
    ThreeTwo("3:2"),
    SixteenNine("16:9");

    /**
     * Width over height, or **null** for a ratio that does not constrain anything.
     *
     * [Original] is a function of the picture rather than a constant, which is the
     * whole reason this takes a frame: locking to "the shape it already is" has to
     * follow a quarter turn, and a stored 1.333 would not.
     *
     * A frame with no area answers null rather than dividing by zero — that is the
     * canvas before its first measurement, where nothing is drawn anyway.
     */
    fun ratioIn(frame: IntSize): Float? = when (this) {
        Free -> null
        Original -> if (frame.height > 0) frame.width.toFloat() / frame.height else null
        Square -> 1f
        FourThree -> 4f / 3f
        ThreeTwo -> 3f / 2f
        SixteenNine -> 16f / 9f
    }

    /** The same ratio the other way up — what `Flip ratio` turns 16:9 into. */
    fun flippedIn(frame: IntSize): Float? = ratioIn(frame)?.let { 1f / it }
}

/**
 * Which way round the chosen ratio is held (ME-144).
 *
 * **Was a `BoolParam` until the chips gained glyphs.** A switch labelled "Portrait" is a
 * two-state choice wearing a boolean, and only a choice can carry an icon per option —
 * a tall frame and a wide one, which is the clearest glyph pair in the editor. The key
 * on the wire is unchanged, and `cropIsPortrait` still reads what an older document
 * wrote there.
 */
enum class CropOrientation(val label: String) {
    Landscape("Landscape"),
    Portrait("Portrait")
}
