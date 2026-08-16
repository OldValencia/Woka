package com.woka.feature.editor.core

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * One applied edit: which tool, and what it was set to.
 *
 * **The only operation type in the app** (§3.1) — there is no `CropOp`, no
 * `AdjustOp`, no hierarchy at all. One type is what lets the document serialise
 * without a polymorphic serialiser, lets history be a list copy, and lets the
 * shell render a tool's panel without knowing the tool. The cost is that
 * [toolId] is a `String` the registry has to resolve; the benefit is that an
 * operation from a tool that is disabled or gone still round-trips instead of
 * failing to parse (§3.4).
 */
@Immutable
@Serializable
data class ToolOp(
    val toolId: String,
    val params: ParamSet = ParamSet()
)
