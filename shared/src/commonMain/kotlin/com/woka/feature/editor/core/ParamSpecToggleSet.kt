package com.woka.feature.editor.core

import org.jetbrains.compose.resources.DrawableResource

/**
 * **Any number** of a fixed set, including none — the tenth `ParamSpec` (ME-147).
 *
 * Its own file beside `ParamSpecChoice.kt`, `ParamSpecPad.kt` and `ParamSpecPreview.kt`,
 * which is the shape this schema already has: one file per spec that is not a bare
 * scalar.
 *
 * **[ChoiceParam] cannot express this and is not a near miss.** That one picks *one* of a
 * set, and markup's four faces — bold, italic, underline, strikethrough — are independent:
 * a caption may be bold *and* underlined. The alternative the schema already had is four
 * [BoolParam]s, which the shell draws as four full-width switch rows; under `Shape`,
 * `Color`, `Fill` and `Text size` that is a wall, and the row iOS Photos draws is a strip
 * of chips (decision log, 2026-08-27).
 *
 * [labelOf] and [iconOf] mean exactly what they mean on [ChoiceParam], because the two are
 * drawn by the same chip: a glyph never costs an option its name, it moves the name into a
 * tooltip.
 */
data class ToggleSetParam<E : Enum<E>>(
    override val key: String,
    val label: String,
    val options: List<E>,
    override val default: Set<E> = emptySet(),
    val labelOf: (E) -> String = { it.name },
    val iconOf: (E) -> DrawableResource? = { null }
) : ParamSpec<Set<E>> {

    /**
     * The chosen constants' **names**, joined — a [TextValue], so **no new [ParamValue]
     * subtype exists for this**. `ChoiceParam` already stores one name that way and the
     * argument is the same one: an ordinal makes reordering the enum a silent rewrite of
     * every document already saved.
     *
     * Written in [options] order rather than in the set's own, which is unspecified for a
     * `Set` and is whatever the caller's `+`/`-` happened to produce. Without that, the
     * same three faces could be written three ways and a document would appear to change
     * when nothing had.
     *
     * The empty set is the empty string, which is a **value** and not an absent key —
     * `defaults()` writes it and `isSet` reports true, like every other spec.
     */
    override fun encode(value: Set<E>) =
        TextValue(options.filter { it in value }.joinToString(SEPARATOR) { it.name })

    /**
     * Back to the set, **dropping every name that no longer resolves** and keeping the
     * rest — `ChoiceParam`'s rule for a retired option, applied per element rather than to
     * the whole value. A document that named four faces and now knows three is a document
     * with three, not one that falls back to the default.
     *
     * Null is therefore reserved for a genuine type mismatch, which is what [ParamSpec]
     * promises and what [ParamSet] turns into the default.
     */
    override fun decode(value: ParamValue): Set<E>? {
        val stored = (value as? TextValue)?.value ?: return null
        return stored.split(SEPARATOR)
            .mapNotNull { name -> options.firstOrNull { it.name == name } }
            .toSet()
    }
}

/** Not a character an enum constant can contain, so no name needs escaping. */
private const val SEPARATOR = ","
