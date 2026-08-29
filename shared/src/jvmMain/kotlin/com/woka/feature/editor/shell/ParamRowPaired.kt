package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.woka.feature.editor.core.ColorParam
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.ToggleSetParam

/**
 * A line the tool asked for: two named controls side by side (ME-153).
 *
 * **Buttons first, then the slider** (2026-08-29). Chips and swatches measure what they
 * measure; a slider is the one control that can use whatever room is left, so it goes
 * last and takes the remainder. A tool that declares them the other way round gets them
 * that way round — the order is the tool's, this only lays it out.
 *
 * The `when` here is deliberately **not** exhaustive, unlike `ParamRow`'s. That one asks
 * "can this spec be edited at all", and a missing branch is a value the user could never
 * change; this asks "can this spec sit half a line", and the honest answer for a preview
 * strip or a pad is no. Anything unpairable falls back to a row each, so a tool declaring
 * a pair the shell cannot draw loses the line and keeps the controls.
 */
@Composable
internal fun PairedRow(run: List<ParamSpec<*>>, params: ParamSet, writer: ParamWriter) {
    val left = run.first()
    val right = run.last()
    if (!left.pairable || !right.pairable) {
        Column(verticalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.rowGap)) {
            run.forEach { spec -> key(spec.key) { ParamRow(spec, params, writer) } }
        }
        return
    }

    PairedRowFrame(
        leftLabel = left.rowLabel,
        rightLabel = right.rowLabel,
        // **The left half is never weighted** (measured 2026-08-29). Weighted, it is the
        // half that gets compressed when the line is tight — and a compressed swatch row
        // does not scroll or wrap, it lays its swatches out at zero width and silently
        // loses them. Buttons take the room they need; the slider takes what is left.
        left = { ParamControl(left, params, writer, Modifier) },
        right = { ParamControl(right, params, writer, Modifier.weight(1f)) }
    )
}

/** The three specs whose control can stand without a row of its own. */
private val ParamSpec<*>.pairable: Boolean
    get() = this is FloatParam || this is ColorParam || this is ToggleSetParam<*>

/** A pairable spec's own name, which `ParamSpec` does not carry in one place. */
private val ParamSpec<*>.rowLabel: String
    get() = when (this) {
        is FloatParam -> label
        is ColorParam -> label
        is ToggleSetParam<*> -> label
        else -> key
    }

/** The control alone, for the three kinds a paired line can hold. */
@Composable
private fun RowScope.ParamControl(
    spec: ParamSpec<*>,
    params: ParamSet,
    writer: ParamWriter,
    modifier: Modifier
) {
    when (spec) {
        is FloatParam -> FloatControl(spec, params, writer, modifier)
        is ColorParam -> ColorControl(spec, params, writer, modifier)
        is ToggleSetParam<*> -> ToggleSetControl(spec, params, writer, modifier)
        else -> Unit
    }
}
