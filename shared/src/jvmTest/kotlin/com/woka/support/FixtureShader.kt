package com.woka.support

import com.woka.feature.editor.engine.image.RasterStage
import org.jetbrains.skia.RuntimeEffect

/**
 * The one shader the shader-stage tests drive, with every knob they need (ME-165).
 *
 * **One effect, not four.** Each test sets the uniforms it cares about and leaves the
 * rest at their identity, so there is a single piece of SkSL to keep correct rather
 * than one per assertion.
 *
 * **Every uniform is genuinely read**, deliberately. An SkSL compiler is free to drop
 * a uniform nothing uses, and the packed data must match the layout Skia ends up with
 * exactly — a fixture that declared `resolutionScale` and ignored it could therefore
 * fail on a size mismatch, or worse, pass while proving nothing about the value.
 *
 * [tapX] is what makes this a stand-in for a real convolution: it samples that many
 * **source pixels** to the right, which is only the right distance if `resolutionScale`
 * reached the shader and `scaledBy` kept it honest.
 */
private const val FIXTURE_SHADER = """
    uniform float gain;
    uniform float offset;
    uniform float tapX;
    uniform float resolutionScale;
    uniform shader image;

    half4 main(float2 coord) {
        half4 c = image.eval(coord + float2(tapX * resolutionScale, 0.0));
        return half4(clamp(c.rgb * gain + offset, 0.0, 1.0), c.a);
    }
"""

/** Compiled once: parsing SkSL per assertion is slow and proves nothing extra. */
val FIXTURE_EFFECT: RuntimeEffect by lazy { RuntimeEffect.makeForShader(FIXTURE_SHADER) }

/**
 * A shader stage over [FIXTURE_EFFECT]. The defaults are the identity, so a test names
 * only what it is asserting about.
 *
 * `resolutionScale` is **not** a parameter here — it is the recipe's to set, and
 * `scaledBy` is the only thing allowed to change it.
 */
fun fixtureShader(
    gain: Float = 1f,
    offset: Float = 0f,
    tapX: Float = 0f
): RasterStage.Shader = RasterStage.Shader(
    effect = FIXTURE_EFFECT,
    uniforms = listOf(gain, offset, tapX)
)
