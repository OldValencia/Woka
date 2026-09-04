package com.woka.unit

import com.woka.core.data.HdrViewMode
import com.woka.core.data.StillColorInfo
import com.woka.core.data.TransferKind
import com.woka.core.data.hdrControlApplies
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether the top bar's `HDR ▾` belongs on screen (ME-303) — the whole rule, in the one
 * place both pills read it from.
 *
 * Reported against an SDR photograph, where all three menu entries mean "leave it alone".
 */
class HdrControlAppliesTest {

    @Test
    fun `an SDR picture on the default mode has nothing to say`() {
        assertFalse(hdrControlApplies(StillColorInfo.Sdr, HdrViewMode.Auto))
    }

    @Test
    fun `nor with Force SDR, which changes nothing about it`() {
        // No clause of its own: a picture that was never going to be tone mapped is not
        // tone mapped either way, so the control would still move nothing.
        assertFalse(hdrControlApplies(StillColorInfo.Sdr, HdrViewMode.ForceSdr))
    }

    @Test
    fun `but Force HDR keeps it, because that is the only way back`() {
        // The mode outlives the file it was set on: chosen on an HDR photograph it is
        // still in force on the next SDR one, which `colourToBelieve` then tone maps as
        // PQ. Hidden here, the picture would be visibly wrong with no control to fix it.
        assertTrue(hdrControlApplies(StillColorInfo.Sdr, HdrViewMode.ForceHdr))
    }

    @Test
    fun `wide-gamut SDR is not HDR, so it hides too`() {
        // `TransferKind`'s own KDoc: BT.2020 primaries with an ordinary transfer want the
        // gamut matrix and nothing this control decides. The cost is accepted — such a
        // file *is* gamut mapped, and Force SDR is no longer reachable to stop that. It is
        // asserted because the predicate takes the whole colour and ignores one half of it.
        val wide = StillColorInfo(TransferKind.SDR, isWideGamut = true)

        assertFalse(hdrControlApplies(wide, HdrViewMode.Auto))
        assertFalse(hdrControlApplies(wide, HdrViewMode.ForceSdr))
    }

    @Test
    fun `both high-dynamic-range transfers keep it on every mode`() {
        HdrViewMode.entries.forEach { mode ->
            val pq = StillColorInfo(TransferKind.PQ, isWideGamut = true)
            assertTrue(hdrControlApplies(pq, mode), "PQ hid the control on $mode")
            // HLG is not PQ and is still HDR — the case `needsToneCurve` exists to catch.
            assertTrue(hdrControlApplies(StillColorInfo(TransferKind.HLG), mode), "HLG hid it on $mode")
        }
    }
}
