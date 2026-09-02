package com.woka.feature.editor.tools.adjust

import com.woka.feature.editor.engine.image.RasterStage
import org.jetbrains.skia.RuntimeEffect

/**
 * The Noise slider, which is **two** effects because it points both ways (ME-162,
 * 2026-09-02).
 *
 * Its own file rather than more of `AdjustDetailShader`: one slider, one file, and that
 * file was past the size mandate holding three shaders for two controls.
 *
 * Both declare `resolutionScale` **last** and both read it — it is one source pixel in
 * coordinate units, so the same recipe grains and denoises by the same *picture* distance
 * against the 2560 px proxy, the native bitmap on save, and a 400 px grid tile.
 */
private const val DENOISE_SHADER = """
    uniform float strength;
    uniform float resolutionScale;
    uniform shader image;

    // A bilateral filter, not a gaussian. A gaussian of this radius is simply a blur:
    // it removes the noise and the edges together, and reads as mush. Weighting each
    // neighbour by how *different* it is keeps an edge sharp while averaging the flat
    // areas where noise actually lives.
    half4 main(float2 coord) {
        half4 c = image.eval(coord);
        if (strength <= 0.0) return c;
        // Wider range sigma at full strength: more neighbours count as "similar", so
        // more of them are averaged in.
        float sigma = mix(0.03, 0.30, strength);
        float twoSigmaSq = 2.0 * sigma * sigma;

        half3 acc = half3(0.0);
        float total = 0.0;
        for (int y = -1; y <= 1; ++y) {
            for (int x = -1; x <= 1; ++x) {
                float2 at = coord + float2(float(x), float(y)) * resolutionScale;
                half3 s = image.eval(at).rgb;
                float d = distance(float3(s), float3(c.rgb));
                float w = exp(-(d * d) / twoSigmaSq);
                acc += s * half(w);
                total += w;
            }
        }
        half3 smoothed = acc / half(total);
        // Mixed by strength as well, so the slider fades the effect in rather than
        // switching between "off" and "fully bilateral".
        return half4(mix(c.rgb, smoothed, half(strength)), c.a);
    }
"""

/**
 * Film grain — the other half of the same slider.
 *
 * **Hashed from the coordinate, never from a clock.** Grain that is re-rolled per frame
 * is grain that crawls: the picture would shimmer while the user is looking at it and
 * the save would not match the preview it was judged by. A hash of the position is the
 * same value every time the same pixel is drawn.
 *
 * The coordinate is divided by `resolutionScale` first, so the grain is one *source*
 * pixel wherever the recipe is folded. It is finer than the proxy can show at Fit and
 * resolves fully at native scale — inherent to sampling, and the reason a grain
 * judgement wants 100 % zoom.
 *
 * Monochrome rather than per-channel: coloured noise reads as a broken sensor, and film
 * grain is a density variation.
 */
private const val GRAIN_SHADER = """
    uniform float amount;
    uniform float resolutionScale;
    uniform shader image;

    float hash(float2 p) {
        return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    }

    half4 main(float2 coord) {
        half4 c = image.eval(coord);
        float2 at = floor(coord / max(resolutionScale, 0.0001));
        // Centred on zero, so grain darkens as often as it brightens and the picture
        // keeps the exposure it had.
        float g = (hash(at) - 0.5) * amount;
        return half4(clamp(c.rgb + half(g), 0.0, 1.0), c.a);
    }
"""

private val DENOISE_EFFECT: RuntimeEffect by lazy { RuntimeEffect.makeForShader(DENOISE_SHADER) }
private val GRAIN_EFFECT: RuntimeEffect by lazy { RuntimeEffect.makeForShader(GRAIN_SHADER) }

/** ±100 moves a pixel by a third of the range at most: visible, not a sandstorm. */
private const val GRAIN_RANGE = 0.34f

/**
 * **One slider, two directions** (2026-09-02). Positive **adds** grain, negative removes
 * noise — the direction the name demands, and the whole travel means something, which the
 * first cut of this did not: it denoised on the right and did nothing at all on the left.
 *
 * The span has to be the shared one — `ParamRunGrouping` joins `FloatParam`s of the
 * *same range* into a run, and a 0..100 range here would drop this control out of the
 * chooser strip into a stack of its own — so the choice was a dead half or a second
 * meaning for it. Grain is a real adjustment and it is the natural opposite.
 *
 * Named **Noise** rather than Noise Reduction for the same reason: a slider labelled for
 * one of its directions is a slider that lies in the other.
 */
fun noiseShaderStage(amount: Float): RasterStage.Shader? = when {
    // A control called Noise must make more of it when pushed up. Getting this the
    // other way round was caught by hand before it shipped.
    amount > 0f -> RasterStage.Shader(
        effect = GRAIN_EFFECT,
        uniforms = listOf(amount / 100f * GRAIN_RANGE),
        resolutionScale = 1f
    )
    amount < 0f -> RasterStage.Shader(
        effect = DENOISE_EFFECT,
        uniforms = listOf(-amount / 100f),
        resolutionScale = 1f
    )
    else -> null
}
