package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.BoolParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec

/**
 * Scrub rows grouped in a well (RD-202, `.well`): one rounded plate on the panel, a hairline
 * between rows. Every run of levels in the inspector is drawn as one — Adjust's sections,
 * Styles' intensity, a film's sound. A toggle in the run is a row of the same well (RD-302).
 */
@Composable
internal fun ScrubWell(specs: List<ParamSpec<*>>, params: ParamSet, writer: ParamWriter) {
    val rows = specs.flatMap { spec ->
        if (spec is BoolParam) {
            listOf<@Composable () -> Unit>({
                key(spec.key) {
                    WellToggle(spec.label, spec.icon, params[spec], "param-${spec.key}") { writer.edit(spec, it) }
                }
            })
        } else {
            // A pad is two rows over one stored point — see `ScrubLevel`.
            writer.levelsOf(spec, params).map { level -> @Composable { key(level.spec.key) { ScrubRow(level) } } }
        }
    }
    Well(rows)
}

/**
 * An action as a full-width well button (`.well-button`, `.auto-button`): Adjust's
 * Auto-enhance. The glyph takes the room's accent, the word its text.
 */
@Composable
internal fun WellButton(spec: ActionParam, writer: ParamWriter, modifier: Modifier = Modifier, accentGlyph: Boolean = false) =
    WellButton(spec.label, spec.icon, "param-${spec.key}", modifier, accentGlyph = accentGlyph) { writer.edit(spec, true) }

/**
 * An action as a quiet line under the rows (`.text-action`): Adjust's Reset adjustments —
 * something done rarely and on purpose, so it does not wear a plate.
 */
@Composable
internal fun TextAction(spec: ActionParam, writer: ParamWriter) =
    TextAction(spec.label, spec.icon, "param-${spec.key}") { writer.edit(spec, true) }

/** A group's name above its control (`.group-label`): Shape, Orientation, Sound's Fades. */
@Composable
internal fun GroupLabel(text: String) {
    BasicText(
        text = text,
        modifier = Modifier.padding(start = 4.dp),
        style = TextStyle(color = Theme.room.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    )
}

/** Actions side by side, two to a row (`.transform-actions`). */
@Composable
internal fun ActionGrid(actions: List<ActionParam>, writer: ParamWriter) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        actions.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { spec -> key(spec.key) { WellButton(spec, writer, Modifier.weight(1f)) } }
            }
        }
    }
}
