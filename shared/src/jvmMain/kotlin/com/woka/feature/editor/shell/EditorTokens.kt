package com.woka.feature.editor.shell

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Every chrome measurement the editor draws with, mirroring
 * `ai/mockups/design/woka.html` (its `#editor` scenes) — which is the spec, not a proposal. A
 * disagreement is settled by changing the mockup first.
 *
 * **No colour is declared here.** The room the editor stands in — header, inspector,
 * tabs — is `Theme.room` (`RoomColors`), which follows the theme; what still sits over
 * the picture (the notices, the video band) keeps `Theme.viewer` (`ViewerChrome`).
 */
object EditorTokens {

    /**
     * The video band's frame — the old tool rail's geometry, which the band borrowed. The rail
     * itself became the inspector's tabs (RD-201); [Panel] holds theirs.
     */
    object Band {
        val itemRadius = 14.dp
        val containerPadding = 6.dp
        val containerRadius = 20.dp

        /** Clear of the window's bottom edge. */
        val bottomInset = 28.dp
    }

    /** The right-hand inspector (RD-201, `editor.css` `.inspector`, `.tools`, `.panel`). */
    object Panel {
        val width = 294.dp

        /** The tab well: inset from the panel, padded, its tabs spaced. */
        val tabsMargin = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp)
        val tabsPadding = 4.dp
        val tabsGap = 2.dp
        val tabsRadius = 12.dp
        val tabHeight = 54.dp
        val tabRadius = 9.dp
        val tabGlyph = 20.dp
        val tabLabel = 11.sp
        val tabLabelGap = 5.dp

        val bodyPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 22.dp)

        /** Between two tools sharing a tab — Transform's crop and rotate/flip. */
        val sectionGap = 18.dp

        val footPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        val footLabel = 12.sp

        /** The side panel a row opens, floated left of the inspector. */
        val accessoryRadius = 12.dp
        val accessoryInset = 12.dp
    }

    /** The schema-driven rows inside the panel: one row per `ParamSpec`. */
    object Inspector {

        /** The notices still float over the canvas and keep the card's old range. */
        val minWidth = 200.dp
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

        /** Between the notices and the band over the canvas's bottom edge. */
        val bottomInset = 12.dp

        /** Between the rows of a panel opened beside the inspector. */
        val accessoryGap = 10.dp

        /** Above and below a compact line: [rowGap] is for rows that carry their own height. */
        val barGap = 5.dp

        /** Preview strip: a filter picker shows the picture, not the name. */
        val previewSize = 64.dp
        val previewRadius = 8.dp
        val previewGap = 8.dp

        /** Colour swatches, plus the one that opens the custom popover. */
        val swatchSize = 24.dp
        val swatchGap = 8.dp
    }

    /** The picture in the room (RD-201, `.stage`, `.picture`). */
    object Canvas {
        val sideInset = 34.dp
        val topInset = 26.dp
        val pictureShape = RoundedCornerShape(4.dp)
        val shadow = 28.dp
    }

    /** The header (RD-201, `editor.css` `.edit-history`, `.header-actions`, `#done`). */
    object TopBar {
        val controlHeight = 32.dp
        val controlRadius = 8.dp
        val controlGap = 4.dp
        val glyphSize = 18.dp
        val textPadding = 10.dp
        val labelSize = 13.sp

        /** `Original`: icon and word, 11.dp aside, 7.dp between. */
        val originalPadding = 11.dp
        val originalGap = 7.dp

        /** `Done` is a pill, not a rectangle — it is the one affirmative action. */
        val doneRadius = 16.dp
        val donePadding = PaddingValues(horizontal = 18.dp)

        /** The hairline between the view controls and the ways out. */
        val dividerHeight = 18.dp
        val dividerGap = 6.dp
    }

    /** Motion, one curve for everything so nothing feels bolted on. */
    object Motion {

        /** Hover, press, selection — anything the pointer causes. */
        const val QUICK_MS = 150

        /** Panel and popover fades; the editor entering and leaving. */
        const val PANEL_MS = 200

        /** The shell's easing, from the app mockup's `cubic-bezier`. */
        val easing: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    }
}
