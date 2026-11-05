package com.woka.unit

import com.woka.core.data.HdrViewMode
import com.woka.core.data.StillColorInfo
import com.woka.core.data.TransferKind
import com.woka.core.data.applyTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** ME-304: the HDR mode over a video frame's colour. */
class HdrModeOnVideoTest {

    private val sdr = StillColorInfo.Sdr
    private val film = StillColorInfo(TransferKind.PQ, isWideGamut = true, whitePoint = 7.17f)
    private val hlg = StillColorInfo(TransferKind.HLG, isWideGamut = true)

    @Test
    fun `Auto leaves every frame as it is`() {
        listOf(sdr, film, hlg).forEach { assertSame(it, HdrViewMode.Auto.applyTo(it)) }
    }

    @Test
    fun `Off never tone maps`() {
        listOf(sdr, film, hlg).forEach { assertEquals(StillColorInfo.Sdr, HdrViewMode.ForceSdr.applyTo(it)) }
    }

    @Test
    fun `On keeps an HDR film whole and reads an untagged one as PQ`() {
        assertSame(film, HdrViewMode.ForceHdr.applyTo(film), "the film's own white point was dropped")
        assertSame(hlg, HdrViewMode.ForceHdr.applyTo(hlg), "an HLG film was re-read as PQ")
        val forced = HdrViewMode.ForceHdr.applyTo(sdr)
        assertEquals(TransferKind.PQ, forced.transfer)
        assertEquals(true, forced.isWideGamut)
        assertNull(forced.whitePoint, "an untagged film declared a white point")
    }
}
