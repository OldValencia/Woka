package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec

/**
 * A long run of like sliders as **one chooser and one control** — the iOS Photos model
 * the feature doc names as this editor's UX reference (ME-161).
 *
 * **Why not a stack, and why not the compact bar.** Fifteen labelled rows measure 613 dp
 * against a window whose minimum height is 580; the panel would be the thing you cannot
 * see past, and the inspector has never scrolled. ME-155's bar wraps instead of growing,
 * but it collapses every control to a labelless button, which works because a swatch is a
 * colour and `64px` is a size — and fails here, because `+12` names nothing and an
 * adjustment is only told apart by its name. A named button is ~215 dp wide and fits one
 * per line, which is worse than the stack it replaces. All three were measured before
 * this shape was chosen.
 *
 * **This one does not grow.** Chooser plus control is about 115 dp whether the run holds
 * six adjustments or fifteen, which is what makes ME-162's six more cost nothing.
 *
 * The strip scrolls with the same chevrons and the same wheel handling as the preview
 * strip (`ParamRowStripScroll`), because a desktop wheel reports on `y` and a
 * `horizontalScroll` reads `x`.
 */
@Composable
internal fun ParamStrip(run: List<ParamSpec<*>>, params: ParamSet, writer: ParamWriter) {
    val all = run.filterIsInstance<FloatParam>()
    val sections = all.mapNotNull { it.section }.distinct()

    // Keyed on the run's own keys: a tool whose rows come and go (Markup) would
    // otherwise keep a selection naming a spec that is no longer shown.
    var section by remember(all.map { it.key }) { mutableStateOf(sections.firstOrNull()) }
    val specs = all.filter { section == null || it.section == section }.ifEmpty { all }
    var selectedKey by remember(all.map { it.key }) { mutableStateOf(specs.first().key) }
    val selected = specs.firstOrNull { it.key == selectedKey } ?: specs.first()

    Column(verticalArrangement = Arrangement.spacedBy(STRIP_CHIP_GAP)) {
        // Only when there is something to choose between: one section, or none at all,
        // draws no control and leaves every other tool exactly as it was (ME-164).
        if (sections.size > 1) {
            SectionControl(sections, section ?: sections.first()) { picked ->
                section = picked
                // **The selection follows the section**, or the control below goes on
                // setting a parameter from a tab nobody is looking at.
                selectedKey = all.first { it.section == picked }.key
            }
        }
        StripChooser(specs, params, writer, selected.key) { selectedKey = it }
        ParamValueControls(selected, params, writer)
    }
}

/** Air between two chips, and between the chooser and the control under it. */
internal val STRIP_CHIP_GAP = 6.dp
