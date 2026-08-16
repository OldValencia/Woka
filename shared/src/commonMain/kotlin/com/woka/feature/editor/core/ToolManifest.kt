package com.woka.feature.editor.core

import androidx.compose.runtime.Immutable
import com.woka.core.data.MediaKind
import org.jetbrains.compose.resources.DrawableResource

/**
 * Everything the shell needs to place, draw, label, order and bind a tool —
 * declared once, read by six different consumers (§3.4).
 *
 * **Nothing in a tool knows where it sits.** The rail button, its group and
 * position, the inspector header, the commit granularity, the keyboard table, the
 * Settings row and the "operations from a disabled tool were not applied" notice
 * are all derived from this. That is what makes adding a tool one directory and
 * one Koin line, and what stops the twentieth tool from looking unlike the first.
 *
 * | Field | Read by |
 * |---|---|
 * | [id] | operation ownership, settings key, test ids |
 * | [title] | rail tooltip, inspector header, settings row, disabled-tool warning |
 * | [summary] | rail tooltip second line, settings row subtitle |
 * | [icon] | rail button, settings row |
 * | [domain] | rail group **and** application order |
 * | [order] | position within the group |
 * | [supports] | whether the tool appears for this media at all |
 * | [cardinality] | whether a new operation replaces or appends |
 * | [interaction] | commit on change, on release, or hand to the overlay |
 * | [shortcut] | the keyboard table and the `?` popover |
 */
@Immutable
data class ToolManifest(
    val id: String,
    val title: String,
    val summary: String,
    val icon: DrawableResource,
    val domain: ToolDomain,
    val order: Int,
    // `MediaKind` from `core/data`, never a private extension list — the app has
    // one answer to "what kind of file is this" and six disagreeing copies of it
    // is the bug that type was extracted to end.
    val supports: Set<MediaKind> = setOf(MediaKind.Image, MediaKind.Video),
    val cardinality: ToolCardinality = ToolCardinality.Single,
    val interaction: ToolInteraction = ToolInteraction.Continuous,
    val shortcut: EditorShortcut? = null
)
