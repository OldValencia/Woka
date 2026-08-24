package com.woka.feature.editor.shell

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Packed ARGB to hue/saturation/brightness and back — the arithmetic the custom colour
 * picker is drawn from (ME-114).
 *
 * **A document stores ARGB** (`ParamValue.ColorValue`), and a picker is arranged in HSV
 * because those are the axes a person adjusts: "the same red, less bright" is one number
 * here and three there. So the conversion happens at the panel's edge, in this file, and
 * nothing downstream of it ever sees an HSV triple.
 *
 * Pure, and separate from the composable for the same reason `MarkupGeometry` is separate
 * from the overlay: it can be asserted without a window, and is.
 */
internal data class Hsv(val hue: Float, val saturation: Float, val value: Float)

/**
 * [argb]'s hue, saturation and brightness.
 *
 * A grey has **no** hue — every channel is equal, so the formula's denominator is zero —
 * and returns 0f, which is red at zero saturation. That is why the picker's strip does
 * not jump when black is chosen: the hue it reports was never meaningful, and holding it
 * would mean storing a fourth number the document has no field for.
 */
internal fun hsvOf(argb: Long): Hsv {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f

    val top = max(r, max(g, b))
    val bottom = min(r, min(g, b))
    val span = top - bottom
    if (span == 0f) return Hsv(hue = 0f, saturation = 0f, value = top)

    val hue = when (top) {
        r -> 60f * (((g - b) / span) % 6f)
        g -> 60f * ((b - r) / span + 2f)
        else -> 60f * ((r - g) / span + 4f)
    }
    // The formula's red branch goes negative below the axis; a hue is a bearing.
    return Hsv(hue = (hue + 360f) % 360f, saturation = span / top, value = top)
}

/**
 * The reverse, as the `Long` a document stores — **opaque**, always.
 *
 * A markup colour has no alpha channel of its own: transparency would be a second
 * property with its own control, and a half-transparent arrow over a busy photograph is
 * unreadable rather than subtle. `Color.hsv` does the conversion so this file carries one
 * direction of the arithmetic rather than two.
 */
internal fun packHsv(hue: Float, saturation: Float, value: Float): Long {
    val color = Color.hsv(
        hue = ((hue % 360f) + 360f) % 360f,
        saturation = saturation.coerceIn(0f, 1f),
        value = value.coerceIn(0f, 1f)
    )
    return 0xFF000000L or
        (color.red.toByteValue() shl 16) or
        (color.green.toByteValue() shl 8) or
        color.blue.toByteValue()
}

private fun Float.toByteValue(): Long = (this * 255f).roundToInt().coerceIn(0, 255).toLong()

/**
 * `#RRGGBB` — the picker's readout, and the one form of a colour that can be written down.
 *
 * The alpha is dropped rather than printed: a markup colour is always opaque
 * ([packHsv]), so `#FFFF9F0A` would be eight characters of which two never change.
 */
internal fun hexOf(argb: Long): String = "#%06X".format(argb and 0xFFFFFF)

/**
 * Whether [argb] is one of [swatches] — what decides between the ring on a swatch and the
 * ring on the custom circle.
 *
 * Compared on the low 24 bits, because the offered constants are opaque and so is
 * everything the picker packs: an alpha that differed only in how it was written would
 * otherwise show no ring anywhere and leave the row looking unselected.
 */
internal fun Long.isSwatch(swatches: List<Long>): Boolean =
    swatches.any { abs((it and 0xFFFFFF) - (this and 0xFFFFFF)) == 0L }
