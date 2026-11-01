package com.woka.feature.editor.shell

import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSpec

/**
 * Writes [spec]'s own default, whatever its type.
 *
 * The star projection is captured **here and nowhere else**: `writer.edit(spec, …)` on a
 * `ParamSpec<*>` does not typecheck, and every control that offers a reset would
 * otherwise spell out the same generic helper. The same trick `EditorTool.defaults()`
 * uses from the other side.
 *
 * **A `FloatParam` returns to its detent**, not its default. The two are the same for
 * every tool today, but the spec's own doc says a tool may open at one value and reset to
 * another — and the detent is the mark already drawn on the slider, so a reset that
 * landed anywhere else would visibly disagree with the track.
 */
internal fun ParamWriter.resetTo(spec: ParamSpec<*>) {
    when (spec) {
        is FloatParam -> edit(spec, spec.detent)
        else -> writeDefault(this, spec)
    }
}

private fun <T> writeDefault(writer: ParamWriter, spec: ParamSpec<T>) =
    writer.edit(spec, spec.default)
