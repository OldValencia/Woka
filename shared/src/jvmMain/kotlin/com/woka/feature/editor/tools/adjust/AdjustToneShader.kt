package com.woka.feature.editor.tools.adjust

import com.woka.feature.editor.engine.image.RasterStage
import org.jetbrains.skia.RuntimeEffect

/**
 * Highlights and Shadows, in **one** pass (ME-162).
 *
 * One effect and not two, because both are the same operation on the same number: a
 * shift of luminance weighted by where in the range the pixel already sits. Splitting
 * them would sample and reassemble the picture twice to compute one sum.
 *
 * **Why this cannot be a colour matrix.** A `ColorMatrix` is affine — every pixel gets
 * the same map. These two apply their strength *as a function of the pixel's own
 * luminance*, which is precisely the thing an affine map cannot express, and is why
 * `AdjustMatrixTone` has said since ME-142 that the real ones wait for a shader stage.
 *
 * **The shift is additive, and that is a decision.** The alternative is multiplying by
 * `L'/L`, which preserves saturation better in the midtones but cannot lift a pixel that
 * is already black — `rgb × anything` is still zero — so Shadows would do nothing to the
 * darkest part of the picture, which is the part it exists for. Additive lifts it, at
 * the cost of some desaturation at the very ends of the range. That trade is what wants
 * looking at on screen rather than asserting.
 *
 * No `resolutionScale`: this reads one pixel, so it declares no such uniform and none is
 * packed for it (`RasterStage.Shader`).
 */
private const val TONE_SHADER = """
    uniform float highlights;
    uniform float shadows;
    uniform shader image;

    // Rec. 709 luma, the same weights the saturation matrix uses, so the two
    // adjustments agree about which pixels are bright.
    const half3 LUMA = half3(0.2126, 0.7152, 0.0722);

    half4 main(float2 coord) {
        half4 c = image.eval(coord);
        half l = dot(c.rgb, LUMA);

        // Two masks that meet at mid-grey: each adjustment owns its half of the
        // range and fades out before reaching the other's, so moving Highlights
        // leaves a shadow alone rather than tilting the whole picture.
        half high = smoothstep(0.5, 1.0, l);
        half low = 1.0 - smoothstep(0.0, 0.5, l);

        half delta = highlights * high + shadows * low;
        return half4(clamp(c.rgb + delta, 0.0, 1.0), c.a);
    }
"""

/** Compiled once per process, for the reason `HdrImagePainter` states: not per frame. */
private val TONE_EFFECT: RuntimeEffect by lazy { RuntimeEffect.makeForShader(TONE_SHADER) }

/**
 * How far ±100 moves a pixel that the mask fully owns.
 *
 * 0.35 rather than 1.0: at full travel this is a strong correction and not a wipe to
 * white, which is what a tone slider that reaches the ends of its own range would be.
 */
private const val TONE_RANGE = 0.35f

/**
 * The stage for both sliders, or **null when neither is doing anything** — the same
 * rule `adjustmentMatrix` follows, and for the same reason: an effect that computes
 * two masks and adds zero is still an effect run over every pixel of every frame.
 */
fun toneShaderStage(highlights: Float, shadows: Float): RasterStage.Shader? {
    if (highlights == 0f && shadows == 0f) return null
    return RasterStage.Shader(
        effect = TONE_EFFECT,
        uniforms = listOf(
            highlights / 100f * TONE_RANGE,
            shadows / 100f * TONE_RANGE
        )
    )
}
