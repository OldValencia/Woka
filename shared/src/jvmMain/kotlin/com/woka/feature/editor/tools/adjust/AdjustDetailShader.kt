package com.woka.feature.editor.tools.adjust

import com.woka.feature.editor.engine.image.RasterStage
import org.jetbrains.skia.RuntimeEffect

/**
 * The Sharpness slider (ME-162).
 *
 * **Order matters and it is `AdjustStage`'s to keep**: denoise runs before this, because
 * sharpening noise amplifies it into the texture the sharpening was meant to reveal.
 *
 * `resolutionScale` is declared **last** and read, so the same recipe sharpens by the
 * same *picture* distance whatever size bitmap it is folded against — without it a
 * 400 px tile is sharpened as though the tile were the whole photograph.
 */
/** An unsharp mask: the picture, plus what it loses to a small blur. */
private const val SHARPEN_SHADER = """
    uniform float amount;
    uniform float resolutionScale;
    uniform shader image;

    half4 main(float2 coord) {
        float r = resolutionScale;
        half4 c = image.eval(coord);

        // A 3×3 tent, weights 1-2-1: the cheapest blur that is not a box, and a box
        // leaves the square artefacts an unsharp mask then sharpens into visibility.
        half3 sum = c.rgb * 4.0;
        sum += image.eval(coord + float2(r, 0.0)).rgb * 2.0;
        sum += image.eval(coord - float2(r, 0.0)).rgb * 2.0;
        sum += image.eval(coord + float2(0.0, r)).rgb * 2.0;
        sum += image.eval(coord - float2(0.0, r)).rgb * 2.0;
        sum += image.eval(coord + float2(r, r)).rgb;
        sum += image.eval(coord + float2(r, -r)).rgb;
        sum += image.eval(coord + float2(-r, r)).rgb;
        sum += image.eval(coord + float2(-r, -r)).rgb;
        half3 blurred = sum / 16.0;

        return half4(clamp(c.rgb + (c.rgb - blurred) * amount, 0.0, 1.0), c.a);
    }
"""

private val SHARPEN_EFFECT: RuntimeEffect by lazy { RuntimeEffect.makeForShader(SHARPEN_SHADER) }

/** ±100 doubles the difference the blur found, or removes it entirely at −100. */
private const val SHARPEN_RANGE = 1.5f

/** Negative softens, positive sharpens, zero produces no stage at all. */
fun sharpenShaderStage(amount: Float): RasterStage.Shader? {
    if (amount == 0f) return null
    return RasterStage.Shader(
        effect = SHARPEN_EFFECT,
        uniforms = listOf(amount / 100f * SHARPEN_RANGE),
        resolutionScale = 1f
    )
}
