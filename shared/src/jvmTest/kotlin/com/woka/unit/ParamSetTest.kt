package com.woka.unit

import com.woka.feature.editor.core.BoolParam
import com.woka.feature.editor.core.ChoiceParam
import com.woka.feature.editor.core.ColorParam
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.FloatValue
import com.woka.feature.editor.core.IntParam
import com.woka.feature.editor.core.ParamSet
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private enum class Fit { Original, Square, Wide }

private val Brightness = FloatParam("brightness", "Brightness", -1f..1f, default = 0f)
private val Width = IntParam("width", "Width", 1..8000, default = 1920)
private val Mute = BoolParam("mute", "Mute")
private val Aspect = ChoiceParam("aspect", "Aspect", Fit.entries.toList(), default = Fit.Original)
private val Stroke = ColorParam("stroke", "Stroke", swatches = listOf(0xFFFFFFFF), default = 0xFF0000FF)

/** The schema's contract: typed at the call site, tolerant on the wire (§3.4). */
class ParamSetTest {

    @Test
    fun `a stored value comes back typed`() {
        val set = ParamSet().with(Brightness, 0.5f)
        // The point of the assertion is the static type as much as the value:
        // this compiles only because `get` returns Float, not ParamValue.
        val brightness: Float = set[Brightness]
        assertEquals(0.5f, brightness)
    }

    @Test
    fun `an absent key reads as the spec default`() {
        val set = ParamSet()
        assertEquals(0f, set[Brightness])
        assertEquals(1920, set[Width])
        assertEquals(false, set[Mute])
        assertEquals(Fit.Original, set[Aspect])
        assertFalse(set.isSet(Brightness))
    }

    @Test
    fun `a value of the wrong kind reads as the default rather than throwing`() {
        // What a document written when `width` was a slider looks like today.
        val set = ParamSet(mapOf(Width.key to FloatValue(3.5f)))
        assertEquals(1920, set[Width])
        // It is still present — degrading is not the same as forgetting.
        assertTrue(set.isSet(Width))
    }

    @Test
    fun `a retired enum constant falls back instead of resolving to a neighbour`() {
        val set = ParamSet().with(Aspect, Fit.Wide)
        val narrowed = Aspect.copy(options = listOf(Fit.Original, Fit.Square))
        assertEquals(Fit.Original, set[narrowed])
    }

    @Test
    fun `with does not mutate the receiver`() {
        val original = ParamSet().with(Brightness, 0.2f)
        val changed = original.with(Brightness, 0.9f)
        assertEquals(0.2f, original[Brightness])
        assertEquals(0.9f, changed[Brightness])
    }

    @Test
    fun `a set holding one of every value round-trips through JSON`() {
        val set = ParamSet()
            .with(Brightness, 0.25f)
            .with(Width, 640)
            .with(Mute, true)
            .with(Aspect, Fit.Square)
            .with(Stroke, 0xFF00FF00)
        val restored = Json.decodeFromString<ParamSet>(Json.encodeToString(set))
        assertEquals(set, restored)
        assertEquals(0.25f, restored[Brightness])
        assertEquals(640, restored[Width])
        assertTrue(restored[Mute])
        assertEquals(Fit.Square, restored[Aspect])
        assertEquals(0xFF00FF00, restored[Stroke])
    }
}
