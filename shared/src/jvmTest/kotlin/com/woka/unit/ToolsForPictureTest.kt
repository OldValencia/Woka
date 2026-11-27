package com.woka.unit

import com.woka.core.data.StillColorInfo
import com.woka.core.data.TransferKind
import com.woka.core.data.needsToneCurve
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.shell.forPicture
import com.woka.support.FixtureTool
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The third question the rail asks — not "does the medium support this tool" and not
 * "is it switched on", but "has it anything to say about *this file*" (ME-302).
 *
 * Reported against an SDR photograph: the HDR tool was in its rail and neither of its
 * two options could move anything. That tool left with RD-401; the question stays, asked
 * here of a stand-in that, like it, speaks only to high dynamic range.
 */
class ToolsForPictureTest {

    private object HdrOnly : EditorTool by FixtureTool(id = "hdr-only") {
        override fun appliesTo(color: StillColorInfo): Boolean = needsToneCurve(color.transfer)
    }

    /**
     * What proves the shell filters rather than naming one tool: an ordinary tool that
     * says nothing about colour survives the same pass that drops the HDR-only one.
     */
    @Test
    fun `the filter asks every tool and keeps the ones that answer`() {
        val tools = listOf(HdrOnly, FixtureTool(id = "adjust"))

        assertEquals(
            listOf("adjust"),
            tools.forPicture(StillColorInfo.Sdr).map { it.manifest.id }
        )
        assertEquals(
            listOf("hdr-only", "adjust"),
            tools.forPicture(StillColorInfo(TransferKind.PQ)).map { it.manifest.id }
        )
    }
}
