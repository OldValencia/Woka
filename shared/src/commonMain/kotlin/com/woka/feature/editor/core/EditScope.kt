package com.woka.feature.editor.core

/**
 * Who an edit is for, when the file holds more than one picture (ME-126).
 *
 * A frame set is several photographs in one container — a burst, a Live Photo's
 * still, a page chain — and one recipe covered all of them until this existed. That
 * was the honest MVP answer (§9, 2026-08-19) and it stays the **default**: an
 * un-keyed payload means [AllFrames], so every file written before this enum still
 * means what it meant.
 *
 * Declared here rather than beside the control that draws it (ME-156), because the
 * two words get their meaning from [EditPayload.with] — one of them *clears* the
 * other's work, and a switch cannot be the place that rule is written down.
 */
enum class EditScope {

    /**
     * Every frame draws this edit, and no frame carries one of its own.
     *
     * Saving in this scope **removes every per-frame override**: "all frames draw
     * this" is false wherever a key wins, so a payload that kept both would be
     * describing two different rules at once. Destructive by design; saying so
     * before the click is ME-156's obligation.
     */
    AllFrames,

    /**
     * This frame draws its own edit; the rest are left exactly as they were.
     *
     * The un-keyed [EditPayload.ops] survive untouched, so the other frames keep
     * whatever the file already said about them.
     */
    ThisFrame
}
