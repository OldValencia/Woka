package com.woka.feature.editor.core

/**
 * One editable parameter: its key on the wire, its default, and the pair of
 * functions that move it between the typed call site and [ParamValue].
 *
 * **The schema is the UI** (§3.4). A tool declares its parameters and writes no
 * panel code at all — the shell picks a row renderer per spec type, so the label,
 * spacing, typography, commit granularity and test id are all derived here rather
 * than repeated per tool. Adding a tool therefore cannot produce an inspector that
 * looks unlike the others.
 *
 * [decode] returns null on a type mismatch rather than throwing. A document
 * written against an older schema — an `IntParam` that used to be a `FloatParam`,
 * a key whose meaning moved — must degrade to the default, because §3.4's promise
 * is that a document "never crashes and never silently loses work". Throwing here
 * would break the first half; substituting silently at the call site would break
 * the second, which is why the null is returned to [ParamSet] and handled once.
 *
 * Specs are not serialised. Only the [ParamValue]s they produce are, so a spec may
 * hold lambdas and Compose-facing types that a document never could.
 */
sealed interface ParamSpec<T> {
    val key: String
    val default: T
    fun encode(value: T): ParamValue
    fun decode(value: ParamValue): T?
}

/**
 * A slider row with a signed readout.
 *
 * [detent] is the value a double-click snaps back to, and it is not always the
 * same as [default] — a tool may open at one value and reset to another. [unit]
 * is the suffix drawn after the number (`%`, `px`, `°`); null draws none.
 */
data class FloatParam(
    override val key: String,
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    override val default: Float,
    val detent: Float = default,
    val unit: String? = null
) : ParamSpec<Float> {
    // Clamped on the way in and on the way out: a document written when the range
    // was wider must not hand a slider a value off its own track. A type mismatch
    // still returns null — clamping is about range, not about kind.
    override fun encode(value: Float) = FloatValue(value.coerceIn(range))
    override fun decode(value: ParamValue): Float? =
        (value as? FloatValue)?.value?.coerceIn(range)
}

/** A numeric field with steppers — pixel sizes, counts. */
data class IntParam(
    override val key: String,
    val label: String,
    val range: IntRange,
    override val default: Int
) : ParamSpec<Int> {
    override fun encode(value: Int) = IntValue(value.coerceIn(range))
    override fun decode(value: ParamValue): Int? = (value as? IntValue)?.value?.coerceIn(range)
}

/** A label and an `SliderSwitch`. */
data class BoolParam(
    override val key: String,
    val label: String,
    override val default: Boolean = false
) : ParamSpec<Boolean> {
    override fun encode(value: Boolean) = BoolValue(value)
    override fun decode(value: ParamValue): Boolean? = (value as? BoolValue)?.value
}
