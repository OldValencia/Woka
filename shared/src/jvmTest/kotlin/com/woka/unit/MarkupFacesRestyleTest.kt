package com.woka.unit

import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.TextFace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Where the `Style` row is allowed to land (ME-151) — the twin of `MarkupFillRestyleTest`,
 * and its own file for the same reason: `MarkupRestyleTest` is at the size mandate, and a
 * row with exactly one class it applies to is a different question from the two size rows
 * routing by the selected shape's class.
 */
class MarkupFacesRestyleTest {

    private fun arrow(id: String) =
        MarkupShape.Arrow(id, 0xFFFF9F0AL, 6f, MarkupPoint(0f, 0f), MarkupPoint(50f, 50f))

    private fun text(id: String, faces: Set<TextFace> = emptySet()) =
        MarkupShape.Text(id, 0xFFFF9F0AL, 64f, MarkupPoint(10f, 10f), "Woka", faces = faces)

    private fun params(vararg shapes: MarkupShape) =
        MarkupTool.defaults().with(MarkupTool.Shapes, shapes.toList())

    @Test
    fun `choosing a face with a caption selected styles that caption alone`() {
        val set = params(text("t"), text("u")).with(MarkupTool.Faces, setOf(TextFace.Bold))

        val shapes =
            requireNotNull(MarkupTool.restyled(MarkupTool.Faces, set, "t"))[MarkupTool.Shapes]

        assertEquals(
            setOf(TextFace.Bold),
            assertIs<MarkupShape.Text>(shapes.first { it.id == "t" }).faces
        )
        assertEquals(emptySet(), assertIs<MarkupShape.Text>(shapes.first { it.id == "u" }).faces)
    }

    /**
     * The row writes the **whole set**, so adding a second face must keep the first — the
     * chip row is what decides which are on, and this only applies its answer.
     */
    @Test
    fun `a second face joins the first rather than replacing it`() {
        val set = params(text("t", setOf(TextFace.Bold)))
            .with(MarkupTool.Faces, setOf(TextFace.Bold, TextFace.Italic))

        val shape = assertIs<MarkupShape.Text>(
            requireNotNull(MarkupTool.restyled(MarkupTool.Faces, set, "t"))[MarkupTool.Shapes]
                .single()
        )

        assertEquals(setOf(TextFace.Bold, TextFace.Italic), shape.faces)
    }

    @Test
    fun `styling a caption leaves its colour, its size and its plate alone`() {
        val set = params(text("t")).with(MarkupTool.Faces, setOf(TextFace.Strikethrough))

        val shape = assertIs<MarkupShape.Text>(
            requireNotNull(MarkupTool.restyled(MarkupTool.Faces, set, "t"))[MarkupTool.Shapes]
                .single()
        )

        assertEquals(0xFFFF9F0AL, shape.color)
        assertEquals(64f, shape.width)
        assertEquals(0L, shape.fill)
    }

    /** An arrow has no face to wear, and giving it one would be a history entry for nothing. */
    @Test
    fun `choosing a face with a stroke selected restyles nothing`() {
        val set = params(arrow("a")).with(MarkupTool.Faces, setOf(TextFace.Bold))

        assertNull(MarkupTool.restyled(MarkupTool.Faces, set, "a"), "an arrow was made bold")
    }

    /** Clearing the row has to *remove* the faces rather than be treated as "nothing to do". */
    @Test
    fun `an empty set clears the faces a caption already had`() {
        val set = params(text("t", setOf(TextFace.Bold, TextFace.Underline)))
            .with(MarkupTool.Faces, emptySet())

        val shape = assertIs<MarkupShape.Text>(
            requireNotNull(MarkupTool.restyled(MarkupTool.Faces, set, "t"))[MarkupTool.Shapes]
                .single()
        )

        assertEquals(emptySet(), shape.faces)
    }
}
