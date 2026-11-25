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
 * A run of levels as the prototype draws it (RD-202, `#editor/photo` → Adjust): a segmented
 * control over the tool's sections, when it declares more than one, and the open section's
 * levels as one well of scrub rows.
 *
 * **It replaces the chooser** (ME-161): chips over one slider, measured then against a card
 * that floated over the picture and never scrolled. The inspector is a docked, scrolling
 * panel now, and a section of six rows at 42 dp is shorter than that card was — so every
 * level is visible and none is a click away.
 */
@Composable
internal fun ParamStrip(
    run: List<ParamSpec<*>>,
    params: ParamSet,
    writer: ParamWriter
) {
    val all = run.filterIsInstance<FloatParam>()
    val sections = all.mapNotNull { it.section }.distinct()
    // Keyed on the run's own keys: a tool whose rows come and go would otherwise keep a
    // section naming specs that are no longer shown.
    var section by remember(all.map { it.key }) { mutableStateOf(sections.firstOrNull()) }
    val shown = all.filter { section == null || it.section == section }.ifEmpty { all }

    Column(verticalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
        // Only when there is something to choose between (ME-164).
        if (sections.size > 1) {
            SectionControl(sections, section ?: sections.first()) { section = it }
        }
        ScrubWell(shown, params, writer)
    }
}

/** Between the section control and the well under it. */
private val SECTION_GAP = 10.dp
