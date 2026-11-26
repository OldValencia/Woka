package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.shell.groupRuns
import com.woka.feature.editor.shell.wellBlocks
import com.woka.feature.editor.tools.adjust.AdjustTool
import com.woka.feature.editor.tools.sound.SoundTool
import kotlin.test.Test
import kotlin.test.assertEquals

/** How a run of rows is cut into wells (RD-302): Sound as the prototype's three. */
class ParamWellBlocksTest {

    private val sound = SoundTool.rows(SoundTool.defaults(IntSize.Zero), IntSize.Zero)

    @Test
    fun `Sound is one run, cut into Mute and Volume, the Fades, and Even loudness`() {
        val runs = groupRuns(sound)
        assertEquals(1, runs.size, "a toggle did not join the levels' run")
        val blocks = wellBlocks(runs.single()).map { (name, specs) -> name to specs.map { it.key } }
        assertEquals(
            listOf(null to listOf("mute", "volume"), "Fades" to listOf("fadeIn", "fadeOut"), null to listOf("even")),
            blocks
        )
    }

    @Test
    fun `a clip's Sound loses Even loudness and keeps the rest`() {
        val clip = sound.filterNot { it.key in SoundTool.manifest.filmOnly }
        val blocks = wellBlocks(groupRuns(clip).single()).map { (name, specs) -> name to specs.map { it.key } }
        assertEquals(listOf(null to listOf("mute", "volume"), "Fades" to listOf("fadeIn", "fadeOut")), blocks)
    }

    @Test
    fun `Adjust's levels stay one run with their three sections`() {
        val levels = AdjustTool.params.filter { it is FloatParam }
        val run = groupRuns(levels).single()
        assertEquals(setOf("Light", "Colour", "Detail"), run.mapNotNull { (it as FloatParam).section }.toSet())
    }
}
