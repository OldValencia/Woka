package com.woka.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.woka.core.ui.theme.Theme

/**
 * The colours a menu card and its rows are drawn in (RD-301).
 *
 * **One card, two rooms.** Every menu in the app stood on `Theme.colors`; the video editor's
 * timeline menu stands in the editor's room (`Theme.room`, `base.css` `.context-menu`), whose
 * light theme is not the app's. A second `MenuSurface` in the editor would be the copy that
 * drifts which [MenuSurface] exists to prevent, so the card takes its colours instead.
 */
@Immutable
data class MenuColors(
    val surface: Color,
    val border: Color,
    val text: Color,
    val disabled: Color,
    val hover: Color
) {
    companion object {
        /** Every menu outside the editor — the values `MenuSurface` and `DropdownItem` always had. */
        val app: MenuColors
            @Composable get() = MenuColors(
                surface = Theme.colors.surfaceElevated,
                border = Theme.colors.border,
                text = Theme.colors.textPrimary,
                disabled = Theme.colors.textTertiary,
                hover = Theme.colors.surfaceHover
            )
    }
}
