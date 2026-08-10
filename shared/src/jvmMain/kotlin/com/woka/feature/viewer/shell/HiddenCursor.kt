package com.woka.feature.viewer.shell

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage

/**
 * A fully transparent 1×1 cursor. AWT has no "hide the pointer" call, so an empty
 * custom cursor is how it is done; built once, because `createCustomCursor` talks
 * to the window system.
 */
private val HIDDEN_CURSOR: PointerIcon by lazy {
    PointerIcon(
        Toolkit.getDefaultToolkit().createCustomCursor(
            BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
            Point(0, 0),
            "hidden"
        )
    )
}

/**
 * Takes the pointer away while [hidden] — for a fullscreen video, where an arrow
 * parked over the picture is the one piece of chrome the idle timer used to leave
 * behind.
 *
 * `overrideDescendants` follows the same flag on purpose: while the controls are up
 * the buttons underneath must keep their own hand cursor, and only once everything
 * has gone does this layer win outright.
 */
internal fun Modifier.hideCursorWhen(hidden: Boolean): Modifier = this.pointerHoverIcon(
    icon = if (hidden) HIDDEN_CURSOR else PointerIcon.Default,
    overrideDescendants = hidden
)
