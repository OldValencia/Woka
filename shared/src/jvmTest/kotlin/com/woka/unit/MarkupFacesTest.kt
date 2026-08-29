package com.woka.unit

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.TextFace
import com.woka.feature.editor.tools.markup.markupTextStyle
import com.woka.feature.editor.tools.markup.textBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The four faces reaching the layout (ME-151).
 *
 * **Two different claims, and they need different assertions.** A weight and a slant change
 * how wide the glyphs are, so they are measured; a decoration draws a line and leaves the
 * advance alone, so it is asserted on the style rather than on the box. Testing both the
 * same way would either miss the decoration or invent a width change it must not have.
 */
class MarkupFacesTest {

    private fun text(vararg faces: TextFace) = MarkupShape.Text(
        id = "t",
        color = 0xFFFF9F0AL,
        width = 64f,
        at = MarkupPoint(120f, 80f),
        text = "Woka",
        faces = faces.toSet()
    )

    @Test
    fun `bold measures wider than regular at the same size`() {
        val regular = textBounds(text()).width
        val bold = textBounds(text(TextFace.Bold)).width

        assertTrue(bold > regular, "bold measured $bold against regular $regular")
    }

    /**
     * A line under the glyphs is not a wider glyph. If this starts failing, the decoration
     * has stopped being a decoration and is being resolved as a different face.
     */
    @Test
    fun `underline and strikethrough leave the measured width alone`() {
        val regular = textBounds(text()).width

        assertEquals(regular, textBounds(text(TextFace.Underline)).width)
        assertEquals(regular, textBounds(text(TextFace.Strikethrough)).width)
    }

    @Test
    fun `each face reaches its own property of the style`() {
        assertEquals(FontWeight.Bold, markupTextStyle(text(TextFace.Bold), 64f.sp).fontWeight)
        assertEquals(FontStyle.Italic, markupTextStyle(text(TextFace.Italic), 64f.sp).fontStyle)
        assertEquals(
            TextDecoration.Underline,
            markupTextStyle(text(TextFace.Underline), 64f.sp).textDecoration
        )
    }

    /**
     * The reason `TextStyle` gets `combine` rather than a `when`: it has one slot for both
     * lines, and a caption may wear both.
     */
    @Test
    fun `underline and strikethrough combine into one decoration`() {
        val both = markupTextStyle(text(TextFace.Underline, TextFace.Strikethrough), 64f.sp)

        assertEquals(
            TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough)),
            both.textDecoration
        )
    }

    /**
     * No faces leaves every property unset rather than naming a default — a weight named
     * here would pick a face for a font that may not carry it.
     */
    @Test
    fun `a caption with no faces names no weight, slant or decoration`() {
        val plain = markupTextStyle(text(), 64f.sp)

        assertNull(plain.fontWeight)
        assertNull(plain.fontStyle)
        assertNull(plain.textDecoration)
    }
}
