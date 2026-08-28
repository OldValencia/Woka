package com.woka.support

import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.BoolParam
import com.woka.feature.editor.core.ChoiceParam
import com.woka.feature.editor.core.ColorParam
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.IntParam
import com.woka.feature.editor.core.ObjectParam
import com.woka.feature.editor.core.PadParam
import com.woka.feature.editor.core.ParamPoint
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.PreviewChoiceParam
import kotlinx.serialization.Serializable

/** Three options, so a choice can be moved off its default in both directions. */
enum class FixtureChoice { First, Second, Third }

/**
 * The structured value [ObjectParam] needs a serializer for — markup's shape list
 * in miniature. Every property is defaulted so the spec's own default is a literal.
 */
@Serializable
data class FixturePayload(val label: String = "none", val count: Int = 0)

/**
 * One spec of **every** type the schema has, declared once and shared by every
 * [FixtureTool].
 *
 * A test names the same object the tool declares, so `set[FixtureParams.Amount]`
 * cannot drift from what the tool listed — the failure a per-test copy of a spec
 * produces is a silent fall back to the default, which reads as a passing
 * assertion about the wrong value.
 *
 * Completeness is not maintained by eye: `ParamSpec` is sealed, and
 * `EditorFixtureTest` asserts that [all] covers exactly its subclasses. Adding a
 * ninth spec type to the schema therefore fails that test until the fixture — and
 * so every row renderer built against it — has one.
 */
object FixtureParams {

    val Amount = FloatParam("amount", "Amount", -1f..1f, default = 0f, unit = "%")

    val Count = IntParam("count", "Count", 0..10, default = 1)

    val Flag = BoolParam("flag", "Flag")

    val Choice =
        ChoiceParam("choice", "Choice", FixtureChoice.entries.toList(), FixtureChoice.First)

    val Preview =
        PreviewChoiceParam("preview", FixtureChoice.entries.toList(), FixtureChoice.Second)

    val Tint = ColorParam("tint", "Tint", listOf(0xFFFF0000L, 0xFF00FF00L), default = 0xFF000000L)

    val Press = ActionParam("press", "Press")

    val Payload = ObjectParam("payload", FixturePayload.serializer(), FixturePayload())

    /** Bipolar on both axes, so a drag can be shown to move each one in either direction. */
    val Pad = PadParam(
        key = "pad",
        label = "Pad",
        xLabel = "Across",
        yLabel = "Up",
        xRange = -100f..100f,
        yRange = -100f..100f,
        default = ParamPoint(0f, 0f)
    )

    /** In the order an inspector would draw them: scalars, then the rest. */
    val all: List<ParamSpec<*>> =
        listOf(Amount, Count, Flag, Choice, Preview, Tint, Press, Payload, Pad)
}
