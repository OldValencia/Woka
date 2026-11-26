package com.woka.unit

import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.shell.tabFor
import com.woka.feature.editor.shell.tabsOf
import com.woka.feature.editor.tools.adjust.AdjustTool
import com.woka.feature.editor.tools.crop.CropTool
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.sound.SoundTool
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.feature.editor.tools.trim.TrimTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which tab a click on the timeline opens (RD-301, as the prototype's `selectFromTimeline`): a
 * clip opens Clip from Transform or Markup only; its wave opens Sound from anywhere.
 */
class TimelineTabSwitchTest {

    private val tabs = tabsOf(listOf(TrimTool, AdjustTool, CropTool, TransformTool, MarkupTool, SoundTool))
    private val fromWholeFilm = setOf(ToolDomain.Geometry, ToolDomain.Annotate)

    private fun tab(id: String) = tabs.first { it.id == id }

    @Test
    fun `a clip click from Transform or Markup opens Clip`() {
        assertEquals("trim", tabFor(tabs, tab("transform"), TrimTool.manifest.id, fromWholeFilm)?.id)
        assertEquals("trim", tabFor(tabs, tab("markup"), TrimTool.manifest.id, fromWholeFilm)?.id)
    }

    @Test
    fun `a clip click from Adjust leaves the tab where it is`() {
        assertNull(tabFor(tabs, tab("adjust"), TrimTool.manifest.id, fromWholeFilm))
    }

    @Test
    fun `a wave click opens Sound from any tab, and a joining tool opens its owner's tab`() {
        assertEquals("sound", tabFor(tabs, tab("adjust"), SoundTool.manifest.id, null)?.id)
        assertEquals("transform", tabFor(tabs, tab("trim"), CropTool.manifest.id, null)?.id)
    }
}
