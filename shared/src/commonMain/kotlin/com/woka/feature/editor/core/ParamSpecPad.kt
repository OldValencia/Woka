package com.woka.feature.editor.core

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A point on a two-axis pad, in the units the pad's own ranges are expressed in.
 *
 * Its own type rather than a `Pair<Float, Float>`: it is written to disk, and a pair
 * serialises as `first`/`second`, which says nothing about which axis moved.
 */
@Immutable
@Serializable
data class ParamPoint(val x: Float, val y: Float)

/**
 * A square pad dragged in two axes at once — Styles' Tone and Colour (ME-141).
 *
 * **One spec, not two driven together, and that is the whole reason it exists.**
 * `ParamWriter.edit` takes one spec and one value, so a pad built from two
 * `FloatParam`s would put two operations in flight per pointer event: the second
 * would rebuild its parameters from a document the first had already changed, and
 * one gesture would land as two history entries. A value that is a point keeps the
 * manifest's promise of one undo step per gesture.
 *
 * Carried as [JsonValue], the same wire form [ObjectParam] uses — and the whole
 * difference between the two is that this one **has** a renderer. `ObjectParam`
 * draws nothing by design because no generic row can; a pad is generic.
 *
 * Both axes are clamped on the way in and on the way out, as [FloatParam] clamps
 * its range: a document written when the pad was wider must not hand the renderer a
 * handle off its own square. A type mismatch still answers null — clamping is about
 * range, not about kind.
 */
data class PadParam(
    override val key: String,
    val label: String,
    /** What the horizontal axis is called; the vertical one is [yLabel]. */
    val xLabel: String,
    val yLabel: String,
    val xRange: ClosedFloatingPointRange<Float>,
    val yRange: ClosedFloatingPointRange<Float>,
    override val default: ParamPoint
) : ParamSpec<ParamPoint> {

    override fun encode(value: ParamPoint): ParamValue =
        JsonValue(editorJson.encodeToJsonElement(ParamPoint.serializer(), clamped(value)))

    override fun decode(value: ParamValue): ParamPoint? {
        val element: JsonElement = (value as? JsonValue)?.element ?: return null
        return runCatching { editorJson.decodeFromJsonElement(ParamPoint.serializer(), element) }
            .getOrNull()
            ?.let(::clamped)
    }

    fun clamped(point: ParamPoint): ParamPoint =
        ParamPoint(point.x.coerceIn(xRange), point.y.coerceIn(yRange))
}
