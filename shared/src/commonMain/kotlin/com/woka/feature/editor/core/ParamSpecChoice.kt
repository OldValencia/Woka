package com.woka.feature.editor.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement

/**
 * The specs whose values are not a bare scalar: an enum, a colour, a press, a
 * structured payload. Split from `ParamSpec.kt` at the seam ME-003 names, because
 * the two halves are read at different times — the scalars while writing a tool,
 * these while writing a row renderer.
 */

/**
 * One of a fixed set, drawn as a `SegmentedControl` at four options or fewer and
 * a chip row above that.
 *
 * Stored as the constant's `name` ([TextValue]), never its ordinal: an ordinal
 * makes reordering the enum a silent rewrite of every document already saved. A
 * name that no longer resolves decodes to null and falls back to the default,
 * which is what retiring an option looks like from an old document's side.
 */
data class ChoiceParam<E : Enum<E>>(
    override val key: String,
    val label: String,
    val options: List<E>,
    override val default: E,
    /**
     * What an option is **called on screen**, which is not its constant's name.
     * `SixteenNine` is a Kotlin identifier; `16:9` is what a person reads. The
     * default keeps the name, so a tool whose constants already read well says
     * nothing (2026-08-22).
     */
    val labelOf: (E) -> String = { it.name }
) : ParamSpec<E> {
    override fun encode(value: E) = TextValue(value.name)
    override fun decode(value: ParamValue): E? {
        val name = (value as? TextValue)?.value ?: return null
        return options.firstOrNull { it.name == name }
    }
}

/**
 * A [ChoiceParam] drawn as a thumbnail strip instead of a control — each option
 * rendered through its own stage, which is what makes a filter picker show the
 * picture rather than the filter's name. Same wire format; only the renderer the
 * shell picks differs, so a filter set can change presentation without touching
 * a document.
 */
data class PreviewChoiceParam<E : Enum<E>>(
    override val key: String,
    val options: List<E>,
    override val default: E,
    /** What the option is called under its thumbnail — see [ChoiceParam.labelOf]. */
    val labelOf: (E) -> String = { it.name }
) : ParamSpec<E> {
    override fun encode(value: E) = TextValue(value.name)
    override fun decode(value: ParamValue): E? {
        val name = (value as? TextValue)?.value ?: return null
        return options.firstOrNull { it.name == name }
    }
}

/**
 * A swatch row plus a custom-colour popover. [swatches] are the offered ARGB
 * values; the stored value is not restricted to them, because the popover exists
 * precisely to leave the set.
 */
data class ColorParam(
    override val key: String,
    val label: String,
    val swatches: List<Long>,
    override val default: Long
) : ParamSpec<Long> {
    override fun encode(value: Long) = ColorValue(value)
    override fun decode(value: ParamValue): Long? = (value as? ColorValue)?.argb
}

/**
 * An icon button — rotate 90°, flip. Always `Instant`: there is nothing to drag,
 * so the press is the commit.
 *
 * **Typed `Boolean`, meaning "this op exists", not "the flag is on."** An action
 * carries no user-set value, and the alternative — a spec with no value at all —
 * would need `ParamSpec<Unit>` and a row renderer that special-cases it. Two
 * presses are two ops rather than one toggled flag; what makes that true is the
 * manifest's `Repeating` cardinality (§3.4), not anything here. If ME-005 finds a
 * tool that needs a press *count*, this is the spec that changes.
 */
data class ActionParam(
    override val key: String,
    val label: String,
    override val default: Boolean = false
) : ParamSpec<Boolean> {
    override fun encode(value: Boolean) = BoolValue(value)
    override fun decode(value: ParamValue): Boolean? = (value as? BoolValue)?.value
}

/**
 * A payload with its own schema, owned by a `CanvasTool`'s overlay — markup's
 * shape list is the case this exists for.
 *
 * The shell renders **nothing** for it (§3.4). That is the point: a value that
 * cannot be drawn by a generic row must say so in its type, rather than arriving
 * at a row renderer that then has to guess.
 */
data class ObjectParam<T>(
    override val key: String,
    val serializer: KSerializer<T>,
    override val default: T
) : ParamSpec<T> {
    override fun encode(value: T) = JsonValue(editorJson.encodeToJsonElement(serializer, value))

    override fun decode(value: ParamValue): T? {
        val element: JsonElement = (value as? JsonValue)?.element ?: return null
        // A payload whose own schema moved decodes to null like any other
        // mismatch, so one changed shape does not take the document with it.
        return runCatching { editorJson.decodeFromJsonElement(serializer, element) }.getOrNull()
    }
}
