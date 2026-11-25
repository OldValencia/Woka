package com.woka.unit

import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.CanvasMotion
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.tools.transform.TransformTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Each Transform press reports the movement the canvas plays for it (RD-203). */
class TransformMotionTest {

    private fun pressed(spec: ActionParam) =
        ToolOp(TransformTool.manifest.id, TransformTool.defaults().with(spec, true))

    @Test
    fun `a turn reports its direction and a flip its axis`() {
        assertEquals(CanvasMotion(degrees = -90f), TransformTool.canvasMotion(pressed(TransformTool.RotateLeft)))
        assertEquals(CanvasMotion(degrees = 90f), TransformTool.canvasMotion(pressed(TransformTool.RotateRight)))
        assertEquals(CanvasMotion(flipX = true), TransformTool.canvasMotion(pressed(TransformTool.FlipHorizontal)))
        assertEquals(CanvasMotion(flipY = true), TransformTool.canvasMotion(pressed(TransformTool.FlipVertical)))
    }

    @Test
    fun `an operation with no press moves nothing`() {
        assertNull(TransformTool.canvasMotion(ToolOp(TransformTool.manifest.id, TransformTool.defaults())))
    }
}
