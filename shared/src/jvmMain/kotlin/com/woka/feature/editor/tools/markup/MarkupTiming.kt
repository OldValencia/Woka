package com.woka.feature.editor.tools.markup

import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ObjectParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.TimeWindow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * When a shape on a film is on screen (ME-263): a [TimeWindow] per shape id, beside the shape
 * list rather than inside it, so no `MarkupShape` and no wire format changes (`picture.md`
 * §3.3). A shape with no entry is on for the whole film — a watermark — and a still never
 * writes the map.
 */
val MarkupWindowsParam = ObjectParam(
    "windows",
    MapSerializer(String.serializer(), TimeWindow.serializer()),
    emptyMap()
)

/**
 * The four rows, in seconds. Declared over a day so a stored value is never clamped by the
 * declaration; the panel draws them ranged to the film (`timingRows`).
 */
private const val DAY_S = 86_400f

/** A fade's reach, the Audio tool's (`SoundTool`). */
private const val FADE_S = 10f

val MarkupAppearsParam = FloatParam("appearsAt", "Appears at", 0f..DAY_S, default = 0f, unit = "s", step = 0.1f)
val MarkupFromPlayHeadParam = ActionParam("appearsHere", "Appear at play head")
val MarkupHoldsParam = FloatParam("holdsFor", "Holds for", 0f..DAY_S, default = DAY_S, unit = "s", step = 0.1f)
val MarkupFadeInParam = FloatParam("fadeIn", "Fade in", 0f..FADE_S, default = 0f, unit = "s", step = 0.1f)
val MarkupFadeOutParam = FloatParam("fadeOut", "Fade out", 0f..FADE_S, default = 0f, unit = "s", step = 0.1f)

/** The timing rows in panel order. */
internal val TIMING = listOf(MarkupAppearsParam, MarkupFromPlayHeadParam, MarkupHoldsParam, MarkupFadeInParam, MarkupFadeOutParam)

private val TIMING_KEYS = TIMING.map { it.key }.toSet()

internal fun ParamSpec<*>.isTiming(): Boolean = key in TIMING_KEYS

/** The timing rows for a film [timelineMs] long: a start and a hold within it, fades to ten seconds. */
internal fun timingRows(timelineMs: Long): List<ParamSpec<*>> {
    val length = timelineMs / 1000f
    return listOf(
        MarkupAppearsParam.copy(range = 0f..length),
        MarkupFromPlayHeadParam,
        MarkupHoldsParam.copy(range = 0f..length, default = length, detent = length),
        MarkupFadeInParam.copy(range = 0f..minOf(FADE_S, length)),
        MarkupFadeOutParam.copy(range = 0f..minOf(FADE_S, length))
    )
}

/**
 * A timing row's write, into the selected shape's window — the timing half of
 * `restyleSelected`. A shape with no window yet starts from the whole film, so the one number
 * changed is the only one that moves. Null when there is nothing timed to write.
 */
internal fun restyleTiming(spec: ParamSpec<*>, params: ParamSet, selectedId: String?, timelineMs: Long?): ParamSet? {
    if (selectedId == null || timelineMs == null || !spec.isTiming()) return null
    if (params[MarkupTool.Shapes].none { it.id == selectedId }) return null
    val windows = params[MarkupWindowsParam]
    val was = windows[selectedId] ?: TimeWindow(0L, timelineMs)
    val now = when (spec.key) {
        MarkupAppearsParam.key, MarkupFromPlayHeadParam.key -> was.copy(atMs = params[MarkupAppearsParam].ms())
        MarkupHoldsParam.key -> was.copy(holdMs = params[MarkupHoldsParam].ms())
        MarkupFadeInParam.key -> was.copy(fadeInMs = params[MarkupFadeInParam].ms())
        else -> was.copy(fadeOutMs = params[MarkupFadeOutParam].ms())
    }
    return params.with(MarkupWindowsParam, windows + (selectedId to now))
}

/**
 * What the timing rows show for the selected shape: its window, or — with none — no timing
 * key at all, so each row reads its film-ranged default (on from the start, for the whole film).
 */
internal fun displayTiming(params: ParamSet, selectedId: String?): ParamSet {
    val window = selectedId?.let { params[MarkupWindowsParam][it] }
    val bare = ParamSet(params.values - TIMING_KEYS)
    if (window == null) return bare
    return bare
        .with(MarkupAppearsParam, window.atMs / 1000f)
        .with(MarkupHoldsParam, window.holdMs / 1000f)
        .with(MarkupFadeInParam, window.fadeInMs / 1000f)
        .with(MarkupFadeOutParam, window.fadeOutMs / 1000f)
}

/**
 * `Appear at play head`: the shapes as the document holds them, the start moved to [playHeadMs].
 * An action's write starts from the tool's defaults — an empty shape list — so the document is
 * where the list has to come from, or the press would erase every shape.
 */
internal fun appearAtPlayHead(spec: ParamSpec<*>, playHeadMs: Long, document: EditDocument): ParamSet? {
    if (spec.key != MarkupFromPlayHeadParam.key) return null
    val held = document.opsOf(MarkupTool.manifest.id).lastOrNull()?.params ?: return null
    return held.with(MarkupAppearsParam, playHeadMs / 1000f)
}

/** The window [document] gives shape [selectedId], or null — untimed, or nothing selected. */
internal fun windowOf(document: EditDocument, selectedId: String?): TimeWindow? {
    val id = selectedId ?: return null
    return document.opsOf(MarkupTool.manifest.id).lastOrNull()?.params?.get(MarkupWindowsParam)?.get(id)
}

private fun Float.ms(): Long = (this * 1000f).toLong()
