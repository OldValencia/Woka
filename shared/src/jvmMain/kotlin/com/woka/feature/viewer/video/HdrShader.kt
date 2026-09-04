package com.woka.feature.viewer.video

import com.woka.core.data.TransferKind
import org.jetbrains.skia.RuntimeEffect

/**
 * The colour-management shader, in three branches because three physically
 * different signals reach it.
 *
 * It used to be one chain — PQ EOTF, BT.2020→709, Hable — applied to everything
 * `needsToneMap` selected, which meant HLG was decoded by the wrong curve and
 * wide-gamut SDR was tone mapped despite having no extra range to compress.
 *
 * Everything is carried into a common space first: **linear light in units of
 * the 100-nit SDR reference white**, so 1.0 is SDR white whichever transfer it
 * arrived on. That is what lets one tone curve serve both HDR transfers, and
 * what lets the SDR branch skip it without the rest of the chain changing.
 *
 * `whitePoint` is in those same units, which is why it is a uniform rather than a
 * constant: it is a fact about the picture, and only the caller has seen the pixels.
 */
internal const val HDR_SHADER = """
    uniform vec2 scale;
    uniform float transfer;   // 0 = SDR, 1 = PQ, 2 = HLG
    uniform float wideGamut;  // 1 when the primaries are BT.2020
    uniform float whitePoint;  // the picture's own peak, in units of SDR white
    uniform shader image;

    const float m1 = 0.1593017578125, m2 = 78.84375;
    const float c1 = 0.8359375, c2 = 18.8515625, c3 = 18.6875;

    // SMPTE ST 2084. Absolute: the result is nits/10000.
    vec3 pqEotf(vec3 e) {
        vec3 p = pow(e, vec3(1.0/m2));
        vec3 num = max(p - c1, 0.0);
        vec3 den = c2 - c3 * p;
        return pow(num / den, vec3(1.0/m1));
    }

    // ARIB STD-B67 inverse OETF: scene light in [0,1]. Two pieces, and the
    // lower one is a square — nothing like PQ's, which is why feeding HLG to
    // the function above crushed everything below half signal.
    const float hlgA = 0.17883277, hlgB = 0.28466892, hlgC = 0.55991073;
    vec3 hlgInverseOetf(vec3 e) {
        vec3 lo = (e * e) / 3.0;
        vec3 hi = (exp((e - hlgC) / hlgA) + hlgB) / 12.0;
        return mix(lo, hi, step(vec3(0.5), e));
    }

    const mat3 bt2020to709 = mat3(
         1.6605, -0.1246, -0.0182,
        -0.5876,  1.1329, -0.1006,
        -0.0728, -0.0083,  1.1187);

    vec3 hable(vec3 x) {
        const float A=0.15,B=0.50,C=0.10,D=0.20,E=0.02,F=0.30;
        return ((x*(A*x+C*B)+D*E)/(x*(A*x+B)+D*F))-E/F;
    }

    half4 main(float2 coord) {
        vec3 e = image.eval(coord * scale).rgb;
        bool isPq = transfer > 0.5 && transfer < 1.5;
        bool isHlg = transfer > 1.5;

        vec3 lin;
        if (isPq) {
            // PQ is absolute: 10000 nits full scale, over 100-nit reference.
            lin = pqEotf(e) * (10000.0 / 100.0);
        } else if (isHlg) {
            // Scene light, then the OOTF's system gamma, then HLG's nominal
            // 1000-nit peak over the same 100-nit reference.
            lin = pow(hlgInverseOetf(e), vec3(1.2)) * (1000.0 / 100.0);
        } else {
            // Ordinary SDR: 1.0 already *is* reference white.
            lin = pow(e, vec3(2.2));
        }

        // The tone curve belongs to the HDR transfers alone. A wide-gamut SDR
        // picture has no extra range to compress and must come out with its
        // luminance untouched — only its primaries converted.
        // **The curve is normalised by what this picture actually reaches** (ME-120).
        // It was the literal 11.2 — 1120 nits — asserted of every HDR source, so a frame
        // peaking at 174 was compressed as though it had eleven times the range and SDR
        // reference white came out at 148/255. Normalised by a real peak of ~2.3 it comes
        // out near 213 instead: 255 belongs to the picture's own brightest tone, which is
        // what `hable(x) / hable(W)` reaching 1.0 at x = W means.
        vec3 mapped = lin;
        if (isPq || isHlg) {
            mapped = hable(lin) / hable(vec3(whitePoint));
        }
        mapped = clamp(mapped, 0.0, 1.0);

        // Gamut conversion goes *after* the curve, never before. `hable` is a
        // rational function defined on non-negative light, and the matrix is
        // what first produces negatives: every saturated BT.2020 colour falls
        // outside BT.709. Fed one, the curve's denominator changes sign and it
        // returns a large positive. Measured on the PQ bars: BT.2020 red
        // reached the curve as g = -12.5 and left it as 1.73, so red drew
        // yellow, blue drew magenta, and green, yellow, magenta and cyan all
        // drew white. Clipping the converted value is the right answer
        // instead — a colour the display cannot make becomes the nearest one
        // it can, which is what leaves red red.
        if (wideGamut > 0.5) mapped = clamp(bt2020to709 * mapped, 0.0, 1.0);

        return half4(pow(mapped, vec3(1.0/2.2)), 1.0);
    }
"""

/**
 * Compiled **once per process**, and shared by everything that runs the chain.
 *
 * It began as a `by lazy` on `HdrImagePainter`, which `MediaViewerFrame` mints per
 * presented frame — so the shader was parsed and compiled 24 times a second, on the UI
 * thread, inside `onDraw`. ME-205 moved it here rather than giving the editor a second
 * private copy, which would have given half of that back.
 */
internal val HDR_EFFECT: RuntimeEffect by lazy { RuntimeEffect.makeForShader(HDR_SHADER) }

/** Floats rather than an int uniform: every Skia build takes these. */
internal fun TransferKind.shaderCode(): Float = when (this) {
    TransferKind.SDR -> 0f
    TransferKind.PQ -> 1f
    TransferKind.HLG -> 2f
}
