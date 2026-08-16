package com.woka.feature.editor.core

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The closed set of values a tool parameter can hold inside an `EditDocument`.
 *
 * **Closed, rather than `Map<String, Any>`** (§3.1, feature doc §8 hole 1). The
 * whole argument for one `ToolOp` type was that a document is serialisable by
 * construction, and `Any` cannot round-trip through `kotlinx.serialization` — it
 * has no serialiser and no way to acquire one. A sealed hierarchy costs one
 * `when` at the two encode/decode sites and buys a document that can be written
 * to disk, diffed and restored without a custom polymorphic serialiser.
 *
 * Call sites never name these types: [ParamSpec] encodes and decodes, so a tool
 * reads a `Float` and this stays on the wire.
 */
@Serializable
sealed interface ParamValue

/** Sliders — brightness, contrast, angle. */
@Immutable
@Serializable
data class FloatValue(val value: Float) : ParamValue

/** Counted things — pixel dimensions, corner radius, stroke width. */
@Immutable
@Serializable
data class IntValue(val value: Int) : ParamValue

@Immutable
@Serializable
data class BoolValue(val value: Boolean) : ParamValue

/**
 * Free text, and the encoding [ChoiceParam] uses for an enum — the constant's
 * `name`, never its ordinal. An ordinal turns reordering an enum into silent
 * corruption of every document already written.
 */
@Immutable
@Serializable
data class TextValue(val value: String) : ParamValue

/**
 * A colour as packed ARGB.
 *
 * `Long`, never `androidx.compose.ui.graphics.Color` (`code-style/model.md`): a
 * serialised model carries no platform type, and `Color` is a value class over a
 * `ULong` whose bit layout is Compose's to change. Converted at the UI boundary.
 */
@Immutable
@Serializable
data class ColorValue(val argb: Long) : ParamValue

/**
 * The escape hatch for structured payloads a `CanvasTool` owns — the shape list
 * markup builds, where every entry has its own fields (§3.1).
 *
 * It is deliberately the only opaque member: everything the schema-driven
 * inspector renders is a scalar above, so a value landing here is a statement
 * that no generic row can draw it and [ObjectParam] plus an overlay must.
 */
@Immutable
@Serializable
data class JsonValue(val element: JsonElement) : ParamValue
