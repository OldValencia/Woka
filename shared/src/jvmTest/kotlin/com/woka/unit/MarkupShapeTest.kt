package com.woka.unit

import com.woka.feature.editor.core.JsonValue
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.TextValue
import com.woka.feature.editor.tools.markup.MarkupKind
import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.styled
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The shape schema on the wire (ME-113).
 *
 * A document is the only place a shape survives a `Done`, so the round trip *is* the
 * feature: a shape that encodes and decodes to something else is a picture whose markup
 * moved between two openings of the same file.
 */
class MarkupShapeTest {

    private val shapes = listOf(
        MarkupShape.Arrow("a", 0xFFFF9F0AL, 6f, MarkupPoint(10f, 20f), MarkupPoint(300f, 240f)),
        MarkupShape.Rectangle("b", 0xFF0A84FFL, 3f, MarkupPoint(0f, 0f), MarkupPoint(120.5f, 80.25f)),
        MarkupShape.Ellipse("c", 0xFF30D158L, 12f, MarkupPoint(5f, 5f), MarkupPoint(65f, 45f)),
        MarkupShape.Freehand(
            id = "d",
            color = 0xFFFFFFFFL,
            width = 1f,
            points = listOf(MarkupPoint(1f, 1f), MarkupPoint(2f, 4f), MarkupPoint(9f, 3f))
        ),
        // `width` here is the font size, which is the one thing about this variant a
        // reader has to know and the reason it is asserted beside the other four.
        MarkupShape.Text("e", 0xFFFF3B30L, 72f, MarkupPoint(40f, 90f), "Kondakov")
    )

    @Test
    fun `every shape round-trips through the document unchanged`() {
        val encoded = MarkupTool.Shapes.encode(shapes)

        assertEquals(shapes, MarkupTool.Shapes.decode(encoded))
    }

    /**
     * The discriminators are the **wire format**, not an implementation detail: the one
     * kotlinx would derive is the fully qualified class name, so without these five
     * moving the file would rewrite the meaning of every document already saved.
     */
    @Test
    fun `each shape is named on the wire by its own stable discriminator`() {
        val encoded = MarkupTool.Shapes.encode(shapes)

        assertEquals(
            listOf("arrow", "rectangle", "ellipse", "freehand", "text"),
            encoded.element.jsonArray.map { it.jsonObject.getValue("type").jsonPrimitive.content }
        )
    }

    /**
     * The degradation `ObjectParam` has promised since ME-003, asserted for the first
     * payload that actually uses it: a shape type this build does not know decodes to
     * null, the list falls back to its default, and **the rest of the operation still
     * reads** — a document must never crash and never silently lose the work it can keep.
     */
    @Test
    fun `a shape type that does not exist decodes to null and leaves the rest readable`() {
        val payload = Json.parseToJsonElement(
            """[{"type":"hologram","id":"x","color":255,"width":4.0}]"""
        )
        val params = ParamSet(
            mapOf(
                MarkupTool.Shapes.key to JsonValue(payload),
                MarkupTool.Kind.key to TextValue("Ellipse")
            )
        )

        assertNull(MarkupTool.Shapes.decode(JsonValue(payload)), "an unknown shape decoded")
        assertEquals(emptyList(), params[MarkupTool.Shapes])
        assertEquals(MarkupKind.Ellipse, params[MarkupTool.Kind], "the rest of the op was lost")
    }

    /**
     * `styled` on the variant whose `width` means something else (ME-122): it is the font
     * size, so restyling changes it, and the string and the point it was placed at are not
     * style and must survive untouched.
     */
    @Test
    fun `restyling a text shape moves its colour and its size and nothing else`() {
        val text = MarkupShape.Text("e", 0xFFFF3B30L, 72f, MarkupPoint(40f, 90f), "Kondakov")

        val restyled = text.styled(color = 0xFF0A84FFL, width = 120f)

        assertEquals(
            MarkupShape.Text("e", 0xFF0A84FFL, 120f, MarkupPoint(40f, 90f), "Kondakov"),
            restyled
        )
    }
}
