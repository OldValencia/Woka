package com.woka.unit

import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.BoolParam
import com.woka.feature.editor.core.ChoiceParam
import com.woka.feature.editor.core.ColorParam
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.IntParam
import com.woka.feature.editor.core.ObjectParam
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.PreviewChoiceParam
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolInteraction
import com.woka.support.FixtureChoice
import com.woka.support.FixtureParams
import com.woka.support.FixturePayload
import com.woka.support.FixtureTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The fixture tool's own contract (ME-011): that it really does declare one of
 * every spec type, that all eight survive a document round trip, and that its six
 * variants come from constructor parameters rather than from subclasses.
 *
 * What the shell will be *driven* through lives in `EditorHarnessTest`.
 */
class EditorFixtureTest {

    @Test
    fun `the fixture declares one spec of every ParamSpec type`() {
        val covered = FixtureParams.all.map { kindOf(it) }

        assertEquals(EVERY_SPEC_KIND, covered.toSet(), "a spec type has no fixture")
        assertEquals(covered.size, covered.distinct().size, "a spec type is declared twice")
    }

    @Test
    fun `every declared spec round-trips through a document value`() {
        val set = FixtureTool().defaults()
            .with(FixtureParams.Amount, 0.5f)
            .with(FixtureParams.Count, 7)
            .with(FixtureParams.Flag, true)
            .with(FixtureParams.Choice, FixtureChoice.Third)
            .with(FixtureParams.Preview, FixtureChoice.First)
            .with(FixtureParams.Tint, 0xFF123456L)
            .with(FixtureParams.Press, true)
            .with(FixtureParams.Payload, FixturePayload("arrow", 2))

        assertEquals(0.5f, set[FixtureParams.Amount])
        assertEquals(7, set[FixtureParams.Count])
        assertTrue(set[FixtureParams.Flag])
        assertEquals(FixtureChoice.Third, set[FixtureParams.Choice])
        assertEquals(FixtureChoice.First, set[FixtureParams.Preview])
        assertEquals(0xFF123456L, set[FixtureParams.Tint])
        assertTrue(set[FixtureParams.Press])
        assertEquals(FixturePayload("arrow", 2), set[FixtureParams.Payload])
    }

    @Test
    fun `defaults covers exactly the declared params`() {
        val tool = FixtureTool()
        assertEquals(tool.params.map { it.key }.toSet(), tool.defaults().values.keys)
        // Genuinely written, not merely reading as a default — otherwise
        // `defaults()` would be indistinguishable from an empty set.
        tool.params.forEach { spec ->
            assertTrue(tool.defaults().isSet(spec), "${spec.key} was not written")
        }
    }

    @Test
    fun `cardinality and interaction come from the constructor, not from a subclass`() {
        val repeating = FixtureTool(
            cardinality = ToolCardinality.Repeating,
            interaction = ToolInteraction.Instant
        )
        assertEquals(ToolCardinality.Repeating, repeating.manifest.cardinality)
        assertEquals(ToolInteraction.Instant, repeating.manifest.interaction)
        assertEquals(ToolCardinality.Single, FixtureTool().manifest.cardinality)
    }

    @Test
    fun `stage is not consulted until something folds`() {
        // `staged` is what separates "the fold skipped this tool" from "the fold
        // never reached it" — a distinction a null stage alone cannot make.
        assertTrue(FixtureTool().staged.isEmpty())
    }
}

/**
 * Every kind of [ParamSpec] there is, named.
 *
 * Reflection would read this off the sealed hierarchy, but `kotlin-reflect` is not
 * on the test classpath and adding it to guard one assertion is the wrong trade.
 * The `when` in [kindOf] is exhaustive, so a ninth `ParamSpec` subtype stops this
 * file **compiling** — a harder guarantee than a failing test. Adding the branch
 * then forces a name in here, and that is what fails until the fixture declares
 * one, and so until every row renderer built against the fixture has one too.
 */
private val EVERY_SPEC_KIND =
    setOf("float", "int", "bool", "choice", "previewChoice", "color", "action", "object")

private fun kindOf(spec: ParamSpec<*>): String = when (spec) {
    is FloatParam -> "float"
    is IntParam -> "int"
    is BoolParam -> "bool"
    is ChoiceParam<*> -> "choice"
    is PreviewChoiceParam<*> -> "previewChoice"
    is ColorParam -> "color"
    is ActionParam -> "action"
    is ObjectParam<*> -> "object"
}
