package com.woka.feature.editor.core

import androidx.compose.ui.input.key.Key

/**
 * What kind of thing a tool does — and, because of that, **two** separate jobs.
 *
 * **Placement.** The rail draws the groups in declaration order with a 1.dp
 * divider between them, so reordering the rail is reordering this enum and
 * nothing else.
 *
 * **Application order.** The fold sorts operations by domain rank, *not* by the
 * order the user clicked (§3.4). This is the only semantically correct order: a
 * crop performed after an arrow was drawn must still clip that arrow, and a
 * resize must be the last thing before encoding. Without it the result depends on
 * what the user happened to click first, which is a whole class of bug rather
 * than one.
 *
 * The two jobs coincide today and the ticket says they may. If they ever diverge,
 * the fold gets its own rank and this keeps placement — do not silently reorder
 * the enum to fix a rail layout, because that changes what images come out.
 */
enum class ToolDomain { Geometry, Color, Annotate, Output }

/**
 * How a tool's controls turn into history (§3.4).
 *
 * This lives in the manifest rather than in a tool's code so that a slider cannot
 * accidentally produce two hundred undo steps — the shell reads it and decides
 * whether a change calls `preview` or `commit`, and the tool never chooses.
 */
enum class ToolInteraction {
    /** Commit on change: a button, a switch, a segmented control. */
    Instant,

    /** Preview while dragging, commit on release: a slider. */
    Continuous,

    /** Hand over to the tool's `Overlay`, which decides — crop, markup, trim. */
    Canvas
}

/**
 * A tool's keyboard shortcut, in the vocabulary the viewer's key handling already
 * speaks (`MediaViewerKeyHandler` matches `Key` plus modifier flags).
 *
 * [label] is what the `?` popover prints. It is stored rather than derived because
 * the printed form differs per platform — `Ctrl` against `⌘` — and a table built
 * by string-munging a `Key` name reads like a debugger, not like a help screen.
 */
data class EditorShortcut(
    val key: Key,
    val label: String,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false
)
