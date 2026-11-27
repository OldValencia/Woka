package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The editor's dialogs, measured off the prototype (RD-401, `editor.css` `dialog`,
 * `.dialog-compact`, `.dialog-actions`, `.output-size-*`, `.help-option`). Beside
 * `EditorTokens`, which is at its line ceiling.
 */
internal object EditorDialogTokens {
    /** `dialog`: min(720px, 90vw), padding 24 × 28, radius 18, a 21 px semibold title. */
    object Frame {
        val maxWidth = 720.dp
        val padding = PaddingValues(horizontal = 28.dp, vertical = 24.dp)
        val radius = 18.dp
        val titleSize = 21.sp
        val titleGap = 12.dp
        val bodySize = 14.sp
        val bodyLine = 24.sp // line-height 1.7
        val actionsTop = 22.dp
        val actionsGap = 8.dp
    }

    /** `.dialog-compact`: 460 wide, padding 16 / 20 / 18, radius 14, an 18 px title. */
    object Compact {
        val maxWidth = 460.dp
        val padding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp)
        val radius = 14.dp
        val titleSize = 18.sp
        val titleGap = 10.dp
        val bodySize = 13.sp
        val bodyLine = 19.sp // line-height 1.45
        val actionsTop = 14.dp
        val actionsGap = 6.dp
    }

    /** `button` and its kinds: 32 high, radius 7; `.primary` a pill; `.secondary` radius 10. */
    object Button {
        val height = 32.dp
        val radius = 7.dp
        val primaryRadius = 18.dp
        val secondaryRadius = 10.dp
        val padding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
        val primaryPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
        val compactPrimaryPadding = PaddingValues(horizontal = 15.dp, vertical = 7.dp)
        val labelSize = 14.sp
        /** `.wide`: the full width, 10 above. */
        val wideTop = 10.dp
    }

    /** `.dialog-group`: the size well — padding 9 / 10 / 10, radius 10, 1 px line. */
    object Group {
        val padding = PaddingValues(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 10.dp)
        val radius = 10.dp
        val legendSize = 13.sp
        val segmentHeight = 33.dp
        val segmentGap = 10.dp
        val fieldGap = 8.dp
        val fieldHeight = 36.dp
        val fieldRadius = 10.dp
        val fieldText = 16.sp
        val labelSize = 13.sp
        val lockTop = 9.dp
    }

    /** `.help-option`: a well card, padding 14, radius 12, 9 apart; its line 12 px at 1.6. */
    object Help {
        val padding = 14.dp
        val radius = 12.dp
        val gap = 9.dp
        val titleSize = 14.sp
        val lineSize = 12.sp
        val lineHeight = 19.sp
    }
}
