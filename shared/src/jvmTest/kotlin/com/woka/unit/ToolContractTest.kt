package com.woka.unit

import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.BoolParam
import com.woka.feature.editor.core.ChoiceParam
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.IntParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolManifest
import com.woka.feature.editor.core.ToolStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private enum class FakeFit { Contain, Cover }

private object FakeTool : EditorTool {

    val Amount = FloatParam("amount", "Amount", -1f..1f, default = 0.25f)
    val Size = IntParam("size", "Size", 1..100, default = 42)
    val Enabled = BoolParam("enabled", "Enabled", default = true)
    val Mode = ChoiceParam("mode", "Mode", FakeFit.entries.toList(), default = FakeFit.Cover)

    override val manifest = ToolManifest(
        id = "fake",
        title = "Fake",
        summary = "Exists only to exercise the contract",
        icon = Icons.Static.Close,
        domain = ToolDomain.Color,
        order = 0
    )

    override val params: List<ParamSpec<*>> = listOf(Amount, Size, Enabled, Mode)

    override fun stage(params: ParamSet): ToolStage? = null
}

class ToolContractTest {

    @Test
    fun `defaults holds every spec's default and nothing else`() {
        val defaults = FakeTool.defaults()

        assertEquals(
            FakeTool.params.map { it.key }.toSet(),
            defaults.values.keys,
            "defaults must cover exactly the declared params"
        )
        assertEquals(0.25f, defaults[FakeTool.Amount])
        assertEquals(42, defaults[FakeTool.Size])
        assertTrue(defaults[FakeTool.Enabled])
        assertEquals(FakeFit.Cover, defaults[FakeTool.Mode])
    }

    @Test
    fun `defaults round-trips as a real document value, not just as a default fallback`() {
        val defaults = FakeTool.defaults()
        // Every key is genuinely written — otherwise `defaults()` would be
        // indistinguishable from an empty set and would prove nothing.
        FakeTool.params.forEach { spec ->
            assertTrue(defaults.isSet(spec), "${spec.key} was not written")
        }
    }

    @Test
    fun `ToolDomain is declared in application order`() {
        // Geometry before colour before annotation before output is the only
        // correct order: a crop must still clip an arrow drawn before it, and a
        // resize must be the last thing before encoding (§3.4). Reordering this
        // enum for a rail layout changes what images come out — hence the test.
        assertEquals(
            listOf(
                ToolDomain.Geometry,
                ToolDomain.Color,
                ToolDomain.Annotate,
                ToolDomain.Output
            ),
            ToolDomain.entries.toList()
        )
    }
}
