package com.woka.feature.editor.engine.video

import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.viewer.video.ASSUMED_WHITE_POINT
import com.woka.feature.viewer.video.HDR_EFFECT
import com.woka.feature.viewer.video.engine.probe.HdrInfo
import com.woka.feature.viewer.video.shaderCode

/**
 * A video frame's colour management, as the recipe's first stage (ME-205).
 *
 * `RasterSurface` does not go through `HdrImagePainter`, so an HDR video drawn through a
 * recipe arrives untone-mapped and blown out. The fix is not a second draw path: the
 * shader is already written, already compiled once per process, and its uniform block is
 * already in `RasterStage.Shader`'s convention — declaration order, little-endian, and no
 * appended `resolutionScale`, because it samples one pixel and a uniform an effect never
 * reads is one SkSL may drop from the layout.
 *
 * **`scale` is `(1, 1)` here, and this is the one line to get wrong.** In
 * `HdrImagePainter` that uniform is `intrinsicSize / drawSize`, because that painter's
 * image shader carries no local matrix and the effect is handed a *device* coordinate. In
 * the recipe chain `imageShaderFor` has already installed the source→bitmap matrix and
 * every stage evaluates its child in **source** coordinates — any other value draws a
 * corner of the frame blown up across the picture.
 *
 * **[ASSUMED_WHITE_POINT], not a measured peak.** ME-120 measures a still's own; a frame
 * that will be gone in 40 ms is the one thing that cannot be measured, and a running peak
 * would make the tone curve move while the picture plays. A stable per-file peak — read
 * from the container's content-light-level side data rather than from the pixels — is
 * ME-301's probe and not this.
 */
internal fun hdrStageFor(info: HdrInfo?): RasterStage.Shader? {
    // Null is both "no player yet" and "ordinary sRGB", and they want the same answer:
    // no stage, so the recipe keeps the fast `drawImage` path it has always taken.
    if (info == null || !info.needsColorManagement) return null
    return RasterStage.Shader(
        effect = HDR_EFFECT,
        uniforms = listOf(
            1f,
            1f,
            info.transfer.shaderCode(),
            if (info.isWideGamut) 1f else 0f,
            ASSUMED_WHITE_POINT
        ),
        resolutionScale = null
    )
}
