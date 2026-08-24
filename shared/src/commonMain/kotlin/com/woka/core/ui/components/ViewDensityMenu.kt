package com.woka.core.ui.components

import androidx.compose.runtime.Composable
import com.woka.core.data.ViewLayout

/**
 * "View ›  Bigger / Smaller" — the pointing-device path to grid density, for the
 * hand that is already right-clicking the background.
 *
 * The ends of the ladder are shown **disabled** rather than hidden: a menu whose
 * items move depending on state is one nobody builds muscle memory for, and greyed
 * out is also the answer to "why is nothing happening" when the grid is already as
 * big as it goes.
 */
@Composable
fun ViewDensityMenu(
    layout: ViewLayout,
    onSelect: (ViewLayout) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownSubmenu(text = "View") {
        DropdownItem("Bigger", enabled = layout.canZoomIn) {
            onSelect(layout.zoomedIn())
            onDismiss()
        }
        DropdownItem("Smaller", enabled = layout.canZoomOut) {
            onSelect(layout.zoomedOut())
            onDismiss()
        }
    }
}
