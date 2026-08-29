package com.woka.unit

import com.woka.feature.editor.core.FloatValue
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.TextValue
import com.woka.feature.editor.core.ToggleSetParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `ToggleSetParam`'s wire format (ME-147) — the one spec whose value is a *set*, and
 * therefore the one whose encoding has an order question to answer.
 */
class ParamToggleSetTest {

    private enum class Face { Bold, Italic, Underline }

    private val spec = ToggleSetParam("faces", "Style", Face.entries.toList(), emptySet())

    @Test
    fun `a set encodes in options order however it was built`() {
        // Built back to front, and built as a LinkedHashSet that remembers that order —
        // which is exactly what `on + option` produces when a user lights the last chip
        // first. The stored string must not depend on it, or two documents holding the
        // same faces would differ on disk.
        val built = linkedSetOf(Face.Underline, Face.Bold)

        assertEquals(TextValue("Bold,Underline"), spec.encode(built))
        assertEquals(spec.encode(built), spec.encode(setOf(Face.Bold, Face.Underline)))
    }

    @Test
    fun `it round-trips through a ParamSet`() {
        val set = ParamSet().with(spec, setOf(Face.Bold, Face.Underline))
        assertEquals(setOf(Face.Bold, Face.Underline), set[spec])
    }

    @Test
    fun `an unknown name is dropped and the rest of the set survives`() {
        // What retiring an option looks like from an older document's side. `ChoiceParam`
        // answers the whole value with its default; a set can answer per element, and
        // must — losing two faces because a third was renamed is losing work.
        assertEquals(setOf(Face.Bold, Face.Italic), spec.decode(TextValue("Bold,Sideways,Italic")))
        assertEquals(emptySet(), spec.decode(TextValue("Sideways")))
    }

    @Test
    fun `the empty set is a value, not an absence`() {
        assertEquals(TextValue(""), spec.encode(emptySet()))
        assertEquals(emptySet(), spec.decode(TextValue("")))
        // And it is genuinely written, so a document carrying "no faces" is
        // distinguishable from one that never heard of the row.
        assertTrue(ParamSet().with(spec, emptySet()).isSet(spec))
    }

    @Test
    fun `a value of the wrong kind decodes to null and the set falls back to the default`() {
        assertNull(spec.decode(FloatValue(1f)))

        val defaulted = ToggleSetParam("faces", "Style", Face.entries.toList(), setOf(Face.Bold))
        val set = ParamSet(mapOf("faces" to FloatValue(1f)))
        assertEquals(setOf(Face.Bold), set[defaulted])
    }
}
