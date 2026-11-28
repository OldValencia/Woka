package com.woka.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme

/** The label's fade, `.original-label`'s `hint-in` 120 ms. */
private const val LABEL_FADE_MS = 120

internal const val ORIGINAL_LABEL_TAG = "original-label"

/**
 * `Original`, top-centre over the picture while [visible] (`.original-label`). The fill is
 * `tooltipScrim`: the prototype's `#151515c9` was a near-twin of it and moved to it (§6).
 */
@Composable
fun OriginalLabel(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(LABEL_FADE_MS)),
        exit = fadeOut(tween(LABEL_FADE_MS))
    ) {
        BasicText(
            text = "Original",
            style = TextStyle(color = Theme.viewer.onScrim, fontSize = 13.sp),
            modifier = Modifier
                .padding(top = 20.dp)
                .background(Theme.viewer.tooltipScrim, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp)
                .testTag(ORIGINAL_LABEL_TAG)
        )
    }
}
