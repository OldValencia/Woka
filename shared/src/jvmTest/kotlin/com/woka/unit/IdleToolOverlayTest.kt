package com.woka.unit

import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.Composable
import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.CanvasTool
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolScope
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolManifest
import com.woka.feature.editor.core.ToolStage
import com.woka.feature.editor.shell.idleCanvasTool
import com.woka.feature.editor.tools.crop.CropTool
import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.markupOpWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Which tool is mounted with **no** tool open (ME-149).
 *
 * The rule is one function precisely so it can be asserted without a window: "the first
 * canvas tool in rail order that claims the document". Its two halves fail differently —
 * a tool that claims a document it has nothing in takes the pointer off the picture for
 * no reason, and mounting more than one puts two overlays on the same press.
 */
class IdleToolOverlayTest {

    private val marked = EditDocument(
        ops = listOf(
            EditDocument().markupOpWith(
                listOf(
                    MarkupShape.Arrow(
                        id = "a",
                        color = 0xFFFF9F0AL,
                        width = 6f,
                        from = MarkupPoint(0f, 0f),
                        to = MarkupPoint(50f, 50f)
                    )
                ),
                // Any picture: this fixture is about a document *holding* a shape, not
                // about the size that shape was placed at.
                IntSize.Zero
            )
        )
    )

    @Test
    fun `markup claims a document holding a shape`() {
        assertSame(MarkupTool, idleCanvasTool(listOf(CropTool, MarkupTool), marked))
    }

    /** A picture nobody has drawn on behaves exactly as it did before this existed. */
    @Test
    fun `nothing is mounted for a document with no marks`() {
        assertNull(idleCanvasTool(listOf(CropTool, MarkupTool), EditDocument()))
    }

    /** A crop rect is not an object on the picture, so Crop claims nothing, ever. */
    @Test
    fun `crop alone claims nothing`() {
        assertNull(idleCanvasTool(listOf(CropTool), marked))
    }

    /**
     * **One, not all** — the objection the whole rule exists to answer. Two overlays would
     * contest the same press, so rail order decides and the second claimant is not mounted.
     */
    @Test
    fun `the first claimant in rail order wins`() {
        val first = Claimant("first")
        val second = Claimant("second")
        assertEquals(first, idleCanvasTool(listOf(first, second), EditDocument()))
    }
}

/** A canvas tool that always has something to select — the second one is the point. */
private class Claimant(id: String) : CanvasTool {

    override val manifest = ToolManifest(
        id = id,
        title = id,
        summary = "Claims every document",
        icon = Icons.Static.Close,
        domain = ToolDomain.Annotate,
        order = 0
    )

    override val params: List<ParamSpec<*>> = emptyList()

    override fun stage(params: ParamSet): ToolStage? = null

    override fun selectableIn(document: EditDocument): Boolean = true

    @Composable
    override fun Overlay(scope: EditorToolScope) = Unit
}
