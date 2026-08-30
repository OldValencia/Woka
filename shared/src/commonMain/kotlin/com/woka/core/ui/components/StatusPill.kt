package com.woka.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme

/**
 * How long a finished message stays up before it takes itself away.
 *
 * Written once, here, because two producers now settle their own messages (ME-130) —
 * an export and a save — and the same duration copied into each is the one that drifts.
 */
const val STATUS_SETTLE_MS = 2600L

/**
 * The pill a status message is drawn in, and nothing else.
 *
 * **It places itself nowhere.** The `Box(fillMaxSize)` this body used to carry aligned
 * it to the bottom centre of the window, which made a second copy of it a pill drawn
 * underneath the first: an export and a save can be live together, and both wanted that
 * one spot. Placement is `StatusBanners`', which stacks whatever is running.
 *
 * A null [message] is not "draw an empty pill" — it is the exit animation, which is why
 * the visibility is inside rather than around the call.
 */
@Composable
fun StatusPill(message: String?) {
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .shadow(12.dp, RoundedCornerShape(10.dp))
                .background(Theme.colors.surfaceElevated, RoundedCornerShape(10.dp))
                .border(1.dp, Theme.colors.border, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            BasicText(
                text = message.orEmpty(),
                style = TextStyle(
                    color = Theme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}
