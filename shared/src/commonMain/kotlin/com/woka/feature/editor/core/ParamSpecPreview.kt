package com.woka.feature.editor.core

/**
 * A [ChoiceParam] drawn as a thumbnail strip instead of a control — each option
 * rendered through its own stage, which is what makes a filter picker show the
 * picture rather than the filter's name. Same wire format; only the renderer the
 * shell picks differs, so a filter set can change presentation without touching
 * a document.
 *
 * Its own file since ME-141: it is the one choice spec that carries a layout of its
 * own, and it grew two fields the others have no use for while `ParamSpecChoice.kt`
 * was already at the size mandate.
 */
data class PreviewChoiceParam<E : Enum<E>>(
    override val key: String,
    val options: List<E>,
    override val default: E,
    /** What the option is called under its thumbnail — see [ChoiceParam.labelOf]. */
    val labelOf: (E) -> String = { it.name },
    /**
     * The group's name, drawn **at the head of its options** inside the strip, or null
     * for none (ME-141).
     *
     * It exists because Styles has two of these specs and they are independent
     * selections. Consecutive preview specs are laid out as one scrolling line, so
     * without a name at each group's head the boundary between "which undertone" and
     * "which look" is only a divider. Null is every other tool's case — one spec, one
     * strip, nothing to distinguish.
     */
    val section: String? = null,
    /**
     * How strongly this option is applied, drawn **inside the selected square** rather
     * than as a row of its own (asked for 2026-08-28).
     *
     * A strength with nothing selected means nothing, and a strength on the option that
     * *is* the absence — `Neutral`, `Original` — means nothing either; both used to be
     * full-width rows sitting under the strip doing nothing, which is the defect
     * `rows()` was added to end. The value is still declared in the tool's `params`,
     * still written and still folded: only its row is gone.
     */
    val strength: FloatParam? = null
) : ParamSpec<E> {
    override fun encode(value: E) = TextValue(value.name)
    override fun decode(value: ParamValue): E? {
        val name = (value as? TextValue)?.value ?: return null
        return options.firstOrNull { it.name == name }
    }
}
