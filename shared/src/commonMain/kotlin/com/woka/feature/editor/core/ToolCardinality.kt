package com.woka.feature.editor.core

/**
 * Whether committing a tool's operation replaces its previous one or adds another.
 *
 * `Single` is the answer for anything with a settled value — brightness has one
 * amount, a crop has one rectangle — so re-adjusting it must not leave a trail of
 * superseded operations in the document or a trail of undo steps behind them.
 * `Repeating` is for operations that compose: two rotations are 180°, and three
 * arrows are three arrows.
 *
 * Declared here rather than beside `ToolManifest`, which is where it is read from
 * (§3.4), because [EditDocument.commitPending] needs it and ME-004 lands before
 * ME-005. The manifest will name this type, not redeclare it.
 */
enum class ToolCardinality { Single, Repeating }
