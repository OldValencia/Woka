package com.woka.unit

import com.woka.core.data.StillColorInfo
import com.woka.core.data.TransferKind
import com.woka.feature.editor.shell.forPicture
import com.woka.feature.editor.tools.hdr.HdrTool
import com.woka.support.FixtureTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The third question the rail asks — not "does the medium support this tool" and not
 * "is it switched on", but "has it anything to say about *this file*" (ME-302).
 *
 * Reported against an SDR photograph: the HDR tool was in its rail and neither of its
 * two options could move anything.
 */
class ToolsForPictureTest {

    @Test
    fun `HDR has nothing to say about an SDR picture`() {
        assertFalse(HdrTool.appliesTo(StillColorInfo.Sdr))
    }

    @Test
    fun `nor about a wide-gamut SDR one, which is not HDR`() {
        // `TransferKind`'s own KDoc: BT.2020 primaries with an ordinary transfer want
        // the gamut matrix and nothing this tool decides. The predicate is the transfer
        // alone, deliberately.
        assertFalse(HdrTool.appliesTo(StillColorInfo(TransferKind.SDR, isWideGamut = true)))
    }

    @Test
    fun `and it applies to both high-dynamic-range transfers`() {
        assertTrue(HdrTool.appliesTo(StillColorInfo(TransferKind.PQ, isWideGamut = true)))
        // HLG is not PQ and is still HDR — the case `needsToneCurve` exists to catch.
        assertTrue(HdrTool.appliesTo(StillColorInfo(TransferKind.HLG)))
    }

    /**
     * What proves the shell filters rather than naming one tool: an ordinary tool that
     * says nothing about colour survives the same pass that drops HDR.
     */
    @Test
    fun `the filter asks every tool and keeps the ones that answer`() {
        val tools = listOf(HdrTool, FixtureTool(id = "adjust"))

        assertEquals(
            listOf("adjust"),
            tools.forPicture(StillColorInfo.Sdr).map { it.manifest.id }
        )
        assertEquals(
            listOf("hdr", "adjust"),
            tools.forPicture(StillColorInfo(TransferKind.PQ)).map { it.manifest.id }
        )
    }
}
