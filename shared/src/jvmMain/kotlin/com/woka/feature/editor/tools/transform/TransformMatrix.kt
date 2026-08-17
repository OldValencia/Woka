package com.woka.feature.editor.tools.transform

import androidx.compose.ui.graphics.Matrix
import com.woka.feature.editor.core.ParamSet

/**
 * One operation, one matrix — and **no size anywhere**.
 *
 * A quarter turn about the origin sends the picture into negative coordinates, and a
 * flip sends it into negative x or y. Neither is compensated here, because
 * compensating means knowing the frame and `stage()` is not told it. `derivedFrame`
 * normalises both, which is what `RasterStage.Geometry.outputSize` being nullable is
 * for.
 *
 * Null when no flag is set: the "no-op configuration" the fold already drops, rather
 * than an identity matrix it would then have to recognise.
 *
 * Two flags in one operation is unreachable through `ParamWriter` — an action op is
 * built from defaults — but the order below defines what would happen anyway, so the
 * behaviour is stated rather than emergent.
 */
internal fun matrixFor(params: ParamSet): Matrix? = when {
    params[TransformTool.RotateRight] -> Matrix().apply { rotateZ(90f) }
    params[TransformTool.RotateLeft] -> Matrix().apply { rotateZ(-90f) }
    params[TransformTool.FlipHorizontal] -> Matrix().apply { scale(x = -1f, y = 1f) }
    params[TransformTool.FlipVertical] -> Matrix().apply { scale(x = 1f, y = -1f) }
    else -> null
}
