package com.woka.feature.editor.tools.markup

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * A point of a shape, in source pixels.
 *
 * Its own type rather than `Offset` for two reasons that both have to hold: `Offset` is
 * not serialisable, and it is a Compose value class over packed floats whose layout is
 * Compose's to change — which `code-style/model.md` keeps out of anything written to a
 * document. Float, not Int: a shape is dragged, and rounding every drag to whole pixels
 * makes a slow diagonal move in steps.
 *
 * Its own **file** since ME-146, which took `MarkupShape.kt` to the size mandate.
 */
@Immutable
@Serializable
data class MarkupPoint(val x: Float, val y: Float)
