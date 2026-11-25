package com.woka.feature.editor.shell

import androidx.compose.runtime.Immutable
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.PadParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec

/**
 * One level a scrub row edits: what it is called and holds, and how a value is written,
 * settled and reset (RD-203).
 *
 * A row is no longer always one `FloatParam`: the look's shape is a `PadParam` — one stored
 * point — drawn as two rows, Vividness and Brightness. Each axis is a level that writes the
 * point with its own coordinate moved, so the document keeps the shape it has always had.
 */
@Immutable
internal class ScrubLevel(
    /** Name, range, default, unit, glyph — and the key the row's tags are built from. */
    val spec: FloatParam,
    val value: Float,
    private val onWrite: (Float, Boolean) -> Unit,
    val settle: () -> Unit,
    val reset: () -> Unit
) {
    /** [settled] false while a drag or a typed number is still moving. */
    fun write(value: Float, settled: Boolean) = onWrite(value, settled)
}

/** The rows [spec] is drawn as: one for a level, two for a pad, none for anything else. */
internal fun ParamWriter.levelsOf(spec: ParamSpec<*>, params: ParamSet): List<ScrubLevel> = when (spec) {
    is FloatParam -> listOf(
        ScrubLevel(spec, params[spec], { value, settled -> edit(spec, value, settled) }, ::settle) { resetTo(spec) }
    )
    is PadParam -> padLevels(spec, params)
    else -> emptyList()
}

private fun ParamWriter.padLevels(pad: PadParam, params: ParamSet): List<ScrubLevel> {
    val point = params[pad]
    val x = FloatParam("${pad.key}-x", pad.xLabel, pad.xRange, pad.default.x, icon = pad.xIcon)
    val y = FloatParam("${pad.key}-y", pad.yLabel, pad.yRange, pad.default.y, icon = pad.yIcon)
    return listOf(
        ScrubLevel(x, point.x, { value, settled -> edit(pad, point.copy(x = value), settled) }, ::settle) {
            edit(pad, point.copy(x = pad.default.x))
        },
        ScrubLevel(y, point.y, { value, settled -> edit(pad, point.copy(y = value), settled) }, ::settle) {
            edit(pad, point.copy(y = pad.default.y))
        }
    )
}
