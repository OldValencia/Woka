package com.woka.unit

import com.woka.core.ui.image.SoundMix
import com.woka.core.ui.image.soundGain
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.tools.sound.SoundTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The edit's sound (ME-240): mute, volume, two linear fades, the film's times the clip's. */
class SoundMixTest {

    @Test
    fun `fades are linear at either end, and mute is silence`() {
        val mix = SoundMix(fadeInMs = 1_000L, fadeOutMs = 2_000L)
        assertEquals(0f, mix.gainAt(0L, 10_000L))
        assertEquals(0.5f, mix.gainAt(500L, 10_000L))
        assertEquals(1f, mix.gainAt(5_000L, 10_000L))
        assertEquals(0.25f, mix.gainAt(9_500L, 10_000L))
        assertEquals(0f, SoundMix(mute = true).gainAt(5_000L, 10_000L))
    }

    @Test
    fun `the film's sound and a clip's multiply`() {
        val film = SoundMix(volume = 0.5f)
        val clip = SoundMix(volume = 1.5f, fadeInMs = 1_000L)
        assertEquals(0.375f, soundGain(film, clip, 20_000L, 60_000L, 500L, 4_000L))
        assertEquals(1f, soundGain(null, null, 0L, 1L, 0L, 1L))
    }

    @Test
    fun `the tool at its defaults is no edit, and its percent is a gain`() {
        assertNull(SoundTool.mixIn(listOf(ToolOp(SoundTool.manifest.id, ParamSet()))))
        val louder = ToolOp(SoundTool.manifest.id, ParamSet().with(SoundTool.Volume, 150f))
        assertEquals(SoundMix(volume = 1.5f), SoundTool.mixIn(listOf(louder)))
    }
}
