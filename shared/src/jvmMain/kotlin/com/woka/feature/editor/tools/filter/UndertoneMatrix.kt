package com.woka.feature.editor.tools.filter

import androidx.compose.ui.graphics.ColorMatrix
import com.woka.feature.editor.engine.image.isIdentity

/**
 * Six undertones, each a colour matrix (ME-141).
 *
 * **An undertone is not a look, and the two are set together.** A look is the whole
 * treatment of a picture — saturation, contrast, a cast; an undertone is the cast
 * alone, the warmth a skin or a paper carries under whatever else is done to it. So
 * they are two parameters whose matrices multiply, exactly as an adjustment and a
 * filter already do in the fold, and not one strip a user has to choose between.
 *
 * Built the way `FilterMatrix` builds a look and lerped the way it lerps one, so the
 * two are the same kind of thing to read and to tune. [PhotoUndertone.Neutral] is the
 * default and is the *absence* of an undertone, the way `Original` is the absence of
 * a look — it answers null rather than the identity, so a panel opened and closed
 * leaves the document untouched.
 *
 * The constants are a starting point, to be tuned by hand against the corpus.
 */
enum class PhotoUndertone(val label: String) {
    Neutral("Neutral"),
    Amber("Amber"),
    Gold("Gold"),
    RoseGold("Rose Gold"),
    Bright("Bright"),
    CoolRose("Cool Rose")
}

/** Rows of a 4×5: three scales and a shared offset. */
private fun tone(r: Float, g: Float, b: Float, offset: Float = 0f) = ColorMatrix(
    floatArrayOf(
        r, 0f, 0f, 0f, offset,
        0f, g, 0f, 0f, offset,
        0f, 0f, b, 0f, offset,
        0f, 0f, 0f, 1f, 0f
    )
)

/** The matrix for [undertone] at full palette, or null for [PhotoUndertone.Neutral]. */
fun undertoneMatrix(undertone: PhotoUndertone): ColorMatrix? = when (undertone) {
    PhotoUndertone.Neutral -> null
    PhotoUndertone.Amber -> tone(1.10f, 1.02f, 0.90f)
    PhotoUndertone.Gold -> tone(1.08f, 1.05f, 0.86f)
    PhotoUndertone.RoseGold -> tone(1.10f, 0.98f, 0.96f)
    // The one that lifts rather than tints: an even scale plus an offset, which is
    // why it needs the fifth column the other five leave at zero.
    PhotoUndertone.Bright -> tone(1.06f, 1.06f, 1.06f, offset = 6f)
    PhotoUndertone.CoolRose -> tone(1.04f, 0.98f, 1.06f)
}

/**
 * [undertone] at [palette] percent, lerped toward the identity — and **null when it
 * would change nothing**, so a picture with no undertone contributes no matrix to the
 * product at all.
 *
 * `palette`, not `intensity`: this is the undertone's own strength and the look keeps
 * its own. They were nearly made one number, and that would have meant pulling a look
 * back necessarily pulled the skin tone back with it (settled 2026-08-26).
 */
fun undertoneMatrix(undertone: PhotoUndertone, palette: Float): ColorMatrix? {
    val full = undertoneMatrix(undertone) ?: return null
    val amount = (palette / 100f).coerceIn(0f, 1f)
    if (amount == 0f) return null

    val identity = ColorMatrix().values
    val blended = FloatArray(full.values.size) { at ->
        identity[at] + (full.values[at] - identity[at]) * amount
    }
    return ColorMatrix(blended).takeUnless { it.isIdentity() }
}
