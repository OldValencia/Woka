package com.woka.support

import androidx.compose.ui.graphics.Matrix

/**
 * A [FixtureEngine] whose preview is **mirrored across x** — 640 px wide, so a source
 * point at `x` is drawn at `640 − x`.
 *
 * Every markup overlay test uses it, deliberately: Crop's overlay was written against an
 * identity preview matrix and landed on the far side of a flipped picture, and a test that
 * cannot see that bug cannot guard against it (ME-109, 2026-08-22). Shared between two
 * test classes rather than copied, so neither can quietly become the identity.
 */
fun flippedPreviewEngine() = FixtureEngine(
    previewMatrixOf = {
        Matrix().apply {
            translate(640f, 0f)
            scale(-1f, 1f)
        }
    }
)
