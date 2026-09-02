package com.woka.feature.editor.tools.adjust

import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.FloatParam

/** Every adjustment runs the same span, so a slider means the same thing in each row. */
private val SPAN = -100f..100f

/**
 * The three groups the panel shows one at a time (ME-164), Photos' own split.
 *
 * Reported by hand at eight: what was wrong was not the length of the strip but that
 * Exposure sat beside Warmth for no reason a reader could see. **Detail** arrived with
 * ME-162. `ParamRowStrip` derives its segmented control from the sections the specs
 * declare, so a third group is a third constant here and no UI change anywhere.
 */
private const val LIGHT = "Light"
private const val COLOUR = "Colour"
private const val DETAIL = "Detail"

/**
 * The twelve specs and the press, and nothing else (ME-167).
 *
 * **An abstract base rather than a second object**, so `AdjustTool.Exposure` goes on
 * meaning what it always did — every reference in the matrices, the stage and the tests
 * is untouched, because these are inherited members and not a second namespace.
 *
 * Split out when the twelfth slider and Auto took `AdjustTool` past the size mandate. It
 * is also the honest seam: this file says *what can be adjusted*, that one says what the
 * tool is, how it is placed, and what it performs.
 */
abstract class AdjustParams {

    /**
     * Not a slider — a press, rendered as its own button above the chooser (ME-167).
     * What it computes and what it deliberately leaves alone is `AdjustAuto`'s.
     */
    val Auto = ActionParam("auto", "Auto")

    val Exposure =
        FloatParam(
            "exposure", "Exposure", SPAN,
            default = 0f, section = LIGHT, icon = Icons.Static.Exposure
        )
    val Brightness =
        FloatParam(
            "brightness", "Brightness", SPAN,
            default = 0f, section = LIGHT, icon = Icons.Static.Brightness
        )
    val Contrast =
        FloatParam(
            "contrast", "Contrast", SPAN,
            default = 0f, section = LIGHT, icon = Icons.Static.Contrast
        )
    val BlackPoint =
        FloatParam(
            "blackPoint", "Black Point", SPAN,
            default = 0f, section = LIGHT, icon = Icons.Static.BlackPoint
        )
    val Highlights =
        FloatParam(
            "highlights", "Highlights", SPAN,
            default = 0f, section = LIGHT, icon = Icons.Static.Highlights
        )
    val Shadows =
        FloatParam(
            "shadows", "Shadows", SPAN,
            default = 0f, section = LIGHT, icon = Icons.Static.Shadows
        )
    val Saturation =
        FloatParam(
            "saturation", "Saturation", SPAN,
            default = 0f, section = COLOUR, icon = Icons.Static.Saturation
        )
    val Vibrance =
        FloatParam(
            "vibrance", "Vibrance", SPAN,
            default = 0f, section = COLOUR, icon = Icons.Static.Vibrance
        )
    val Warmth =
        FloatParam(
            "warmth", "Warmth", SPAN,
            default = 0f, section = COLOUR, icon = Icons.Static.Warmth
        )
    val Tint =
        FloatParam(
            "tint", "Tint", SPAN,
            default = 0f, section = COLOUR, icon = Icons.Static.Tint
        )
    val Noise =
        FloatParam(
            "noise", "Noise", SPAN,
            default = 0f, section = DETAIL, icon = Icons.Static.Noise
        )
    val Sharpness =
        FloatParam(
            "sharpness", "Sharpness", SPAN,
            default = 0f, section = DETAIL, icon = Icons.Static.Sharpness
        )
}
