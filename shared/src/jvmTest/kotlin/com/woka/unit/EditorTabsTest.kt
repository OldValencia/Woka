package com.woka.unit

import androidx.compose.runtime.mutableStateOf
import com.woka.feature.editor.shell.EditorTabState
import com.woka.feature.editor.shell.tabFor
import com.woka.feature.editor.shell.tabsOf
import com.woka.feature.editor.tools.adjust.AdjustTool
import com.woka.feature.editor.tools.cover.CoverTool
import com.woka.feature.editor.tools.crop.CropTool
import com.woka.feature.editor.tools.filter.FilterTool
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.resize.ResizeTool
import com.woka.feature.editor.tools.sound.SoundTool
import com.woka.feature.editor.tools.straighten.StraightenTool
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.feature.editor.tools.trim.TrimTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The inspector's tabs as the prototype names them (RD-201), from the real tools. */
class EditorTabsTest {

    private val photo = listOf(TransformTool, CropTool, StraightenTool, AdjustTool, FilterTool, MarkupTool, ResizeTool)

    @Test
    fun `a photo gets Styles, Adjust, Transform and Markup in that order`() {
        assertEquals(listOf("Styles", "Adjust", "Transform", "Markup"), tabsOf(photo).map { it.title })
    }

    @Test
    fun `a film puts Clip first and Sound last, and Cover has no tab`() {
        val film = photo + listOf(TrimTool, SoundTool, CoverTool)
        assertEquals(
            listOf("Clip", "Styles", "Adjust", "Transform", "Markup", "Sound"),
            tabsOf(film).map { it.title }
        )
    }

    @Test
    fun `Transform holds the crop and the dial and opens on the crop's frame`() {
        val transform = tabsOf(photo).single { it.id == TransformTool.manifest.id }
        assertEquals(listOf(CropTool, StraightenTool, TransformTool), transform.tools)
        assertSame(CropTool, transform.canvasTool)
    }

    @Test
    fun `the crop keeps a tab of its own when Transform is switched off`() {
        val tabs = tabsOf(photo - TransformTool)
        assertEquals(listOf(CropTool), tabs.single { it.id == CropTool.manifest.id }.tools)
    }

    @Test
    fun `Straighten keeps a tab of its own when Transform is switched off`() {
        val tabs = tabsOf(photo - TransformTool)
        assertEquals(listOf(StraightenTool), tabs.single { it.id == StraightenTool.manifest.id }.tools)
    }

    @Test
    fun `a hidden Clip is not shown, the first shown tab opens, and a clip click still finds it`() {
        val film = photo + listOf(TrimTool, SoundTool, CoverTool)
        val chosen = mutableStateOf<String?>(TrimTool.manifest.id)
        val state = EditorTabState(tabsOf(film), emptyMap(), chosen, hidden = setOf(TrimTool.manifest.id))
        assertTrue(state.shown.none { it.id == TrimTool.manifest.id }, "Clip is drawn with no clip selected")
        assertEquals("Styles", state.tab?.title)
        assertEquals(TrimTool.manifest.id, tabFor(state.tabs, state.tab, TrimTool.manifest.id, null)?.id)
    }

    @Test
    fun `only Clip comes and goes with the selection`() {
        val film = photo + listOf(TrimTool, SoundTool, CoverTool)
        assertEquals(listOf(TrimTool.manifest.id), film.filter { it.manifest.clipOnly }.map { it.manifest.id })
    }
}
