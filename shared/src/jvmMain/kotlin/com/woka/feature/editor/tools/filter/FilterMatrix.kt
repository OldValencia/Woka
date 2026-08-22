package com.woka.feature.editor.tools.filter

import androidx.compose.ui.graphics.ColorMatrix
import com.woka.feature.editor.engine.image.isIdentity

/** Luminance weights, the same ones Compose's own `setToSaturation` uses. */
private const val LUMA_R = 0.213f
private const val LUMA_G = 0.715f
private const val LUMA_B = 0.072f

/** Offsets are added in 0..255, the scale the channels are sampled in (ME-111). */
private const val MID = 127.5f

/**
 * Eight looks, each a colour matrix (ME-112).
 *
 * **Constants, not recipes.** A filter is one matrix; the whole point of §3.2's three
 * primitives is that a look costs exactly what an adjustment does — a filter rebuild and
 * no pixel work. Anything that cannot be written as a matrix is not a filter here.
 *
 * Named after iOS Photos (§9), because these are the words people already have for them.
 */
enum class PhotoFilter(val label: String) {
    Original("Original"),
    Vivid("Vivid"),
    Dramatic("Dramatic"),
    Warm("Warm"),
    Cool("Cool"),
    Mono("Mono"),
    Noir("Noir"),
    Silvertone("Silvertone")
}

/** Rows of a 4×5: three scales and a shared offset. */
private fun channels(r: Float, g: Float, b: Float, offset: Float = 0f) = ColorMatrix(
    floatArrayOf(
        r, 0f, 0f, 0f, offset,
        0f, g, 0f, 0f, offset,
        0f, 0f, b, 0f, offset,
        0f, 0f, 0f, 1f, 0f
    )
)

/** Every channel becomes the same weighted grey, then optionally tinted per channel. */
private fun greyscale(r: Float = 1f, g: Float = 1f, b: Float = 1f, offset: Float = 0f) = ColorMatrix(
    floatArrayOf(
        LUMA_R * r, LUMA_G * r, LUMA_B * r, 0f, offset,
        LUMA_R * g, LUMA_G * g, LUMA_B * g, 0f, offset,
        LUMA_R * b, LUMA_G * b, LUMA_B * b, 0f, offset,
        0f, 0f, 0f, 1f, 0f
    )
)

/** Saturation and contrast in one matrix — what most of these looks are made of. */
private fun look(saturation: Float, contrast: Float, r: Float = 1f, g: Float = 1f, b: Float = 1f) =
    ColorMatrix().apply {
        setToSaturation(saturation)
        timesAssign(channels(r * contrast, g * contrast, b * contrast, (1f - contrast) * MID))
    }

/**
 * The matrix for [filter] at full strength, or null for [PhotoFilter.Original] — which
 * is the absence of a look rather than a look of its own.
 */
fun filterMatrix(filter: PhotoFilter): ColorMatrix? = when (filter) {
    PhotoFilter.Original -> null
    PhotoFilter.Vivid -> look(saturation = 1.35f, contrast = 1.1f)
    PhotoFilter.Dramatic -> look(saturation = 0.85f, contrast = 1.35f)
    PhotoFilter.Warm -> look(saturation = 1.15f, contrast = 1.05f, r = 1.08f, b = 0.94f)
    PhotoFilter.Cool -> look(saturation = 1.05f, contrast = 1.05f, r = 0.94f, b = 1.08f)
    PhotoFilter.Mono -> greyscale()
    PhotoFilter.Noir -> greyscale().apply {
        timesAssign(channels(1.3f, 1.3f, 1.3f, (1f - 1.3f) * MID))
    }
    PhotoFilter.Silvertone -> greyscale(r = 1.06f, g = 1.0f, b = 0.94f)
}

/**
 * [filter] at [intensity] percent, lerped toward the identity — and **null when it would
 * change nothing**, so the fold's `colorFilter` stays null for a picture nobody filtered.
 *
 * Lerping the matrix rather than blending two renders is what keeps an intensity slider
 * as cheap as any other: one matrix, rebuilt when the number moves.
 */
fun filterMatrix(filter: PhotoFilter, intensity: Float): ColorMatrix? {
    val full = filterMatrix(filter) ?: return null
    val amount = (intensity / 100f).coerceIn(0f, 1f)
    if (amount == 0f) return null

    val identity = ColorMatrix().values
    val blended = FloatArray(full.values.size) { at ->
        identity[at] + (full.values[at] - identity[at]) * amount
    }
    return ColorMatrix(blended).takeUnless { it.isIdentity() }
}
