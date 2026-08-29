package com.woka.feature.editor.shell

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Every chrome measurement the editor draws with, mirroring
 * `ai/mockups/design/editor.html` — which is the spec, not a proposal. A
 * disagreement is settled by changing the mockup first.
 *
 * **No colour is declared here.** `Theme.viewer` (`ViewerChrome`) owns both halves
 * of the on-media pair — the dark scrims and the light `onScrim*` foregrounds — and
 * `Theme.colors` owns everything that is *not* over media: the overwrite
 * confirmation, the conversion sheet, the Settings rows. Re-declaring `popoverScrim`
 * or a text colour under an editor name would create a second value to keep in
 * step, and the second one is the one that drifts. This object holds only what is
 * genuinely new: the rail, the inspector and the editor's own top bar.
 *
 * The editor therefore **follows the theme** exactly as the rest of the app does.
 * What stays fixed is only the on-media layer, and that is a property of sitting on
 * a photograph rather than of the editor.
 */
object EditorTokens {

    /** The tool rail — the viewer's dock geometry, reused rather than re-invented. */
    object Rail {

        /** `DockItem`: 40.dp tall, 14.dp radius. */
        val itemRadius = 14.dp

        val iconSize = 16.dp

        /**
         * A rail button is an icon **and its name** (2026-08-21). It was the icon
         * alone, with the name in a tooltip — which meant two tools whose glyphs had
         * not been drawn yet were two identical squares, and finding a tool meant
         * hovering over each in turn.
         */
        val itemHeight = 40.dp
        val labelSize = 12.sp
        val labelGap = 7.dp
        val itemPadding = 12.dp

        val itemGap = 4.dp

        /** Container: `FloatingDock`'s 6.dp inset and 20.dp radius. */
        val containerPadding = 6.dp
        val containerRadius = 20.dp

        /** One 1×20 divider per `ToolDomain` boundary, 4.dp of air either side. */
        val dividerWidth = 1.dp
        val dividerHeight = 20.dp
        val dividerGap = 4.dp

        /** Clear of the inspector above it, which clears the canvas. */
        val bottomInset = 28.dp
    }

    /** The schema-driven inspector: one card, one row per `ParamSpec`. */
    object Inspector {

        /**
         * A range, not a fixed width — corrected 2026-08-18.
         *
         * It was fixed at 420 dp on the reasoning that a card growing per tool would
         * move the rail under the cursor. **That reasoning was wrong**: the inspector
         * and the rail are independent children of a `CenterHorizontally` column, so
         * the rail is centred whatever the card measures and does not move at all.
         *
         * What the fixed width actually bought was a Transform panel of four small
         * buttons floating in 420 dp of empty space, with `Reset` three buttons' worth
         * of nothing away from anything.
         */
        val minWidth = 240.dp
        val maxWidth = 420.dp

        val rowHeight = 36.dp
        val rowGap = 2.dp

        /** Labels share a column so sliders line up across rows. */
        val labelWidth = 104.dp

        /** Between the two halves of a paired line, and before the second one's name. */
        val pairGap = 8.dp

        /** The signed readout — right-aligned, monospaced digits. */
        val readoutWidth = 48.dp

        val padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)

        /** Gap to the rail below. */
        val bottomInset = 12.dp

        /** Preview strip: a filter picker shows the picture, not the name. */
        val previewSize = 64.dp
        val previewRadius = 8.dp
        val previewGap = 8.dp

        /** Colour swatches, plus the one that opens the custom popover. */
        val swatchSize = 24.dp
        val swatchGap = 8.dp
    }

    /** The accessory top bar: Cancel, title, HDR, undo/redo, Done. */
    object TopBar {

        /** The app shell's header height, so entering the editor shifts nothing. */
        val height = 60.dp
        val sidePadding = 24.dp

        val controlHeight = 34.dp
        val controlRadius = 8.dp
        val controlGap = 6.dp

        /** `Done` is a pill, not a rectangle — it is the one affirmative action. */
        val doneRadius = 17.dp
        val donePadding = PaddingValues(horizontal = 18.dp)

        /** The dirty mark after the filename: "photo.jpg · Edited". */
        val titleStyle = 15.sp
        val subtitleStyle = 13.sp
    }

    /** Motion, one curve for everything so nothing feels bolted on. */
    object Motion {

        /** Hover, press, selection — anything the pointer causes. */
        const val QUICK_MS = 150

        /** Panel and popover fades; the editor entering and leaving. */
        const val PANEL_MS = 200

        /** How long the save banner sits before it dismisses itself. */
        const val BANNER_MS = 2600

        /** The shell's easing, from the app mockup's `cubic-bezier`. */
        val easing: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    }
}
