package com.woka.feature.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.app.AppDependencies
import com.woka.app.navigation.Screen
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.core.utils.openDirectoryPicker
import org.jetbrains.compose.resources.painterResource

/**
 * The first run: nothing is linked, so the window has no folder to show (SIDE-002).
 * The mockup's `.empty .drop` card, with one sentence and one button.
 *
 * **It asks for a drop** since SIDE-010 made one work: a folder dragged from Explorer
 * anywhere on the window links it (`WindowDrops`). The button is the same act for a
 * person who would rather pick than drag.
 */
@Composable
fun WelcomeScreen() {
    val border = Theme.colors.border
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .drawBehind {
                    val width = 1.5.dp.toPx()
                    drawRoundRect(
                        color = border,
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                    )
                }
                .padding(horizontal = 30.dp, vertical = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(Icons.Static.Folder),
                contentDescription = null,
                modifier = Modifier.size(46.dp),
                colorFilter = ColorFilter.tint(Theme.colors.accentFolders)
            )
            Spacer(Modifier.height(14.dp))
            BasicText(
                "Drop a folder here",
                style = TextStyle(
                    color = Theme.colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                "Woka shows what is inside and keeps the folder in the sidebar. " +
                    "Nothing is copied or moved.",
                style = TextStyle(
                    color = Theme.colors.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            )
            Spacer(Modifier.height(18.dp))
            LinkButton()
        }
    }
}

/** `.btn`: the accent button that runs the same picker as the sidebar's foot. */
@Composable
private fun LinkButton() {
    val router = remember { AppDependencies.router }
    val settingsRepo = remember { AppDependencies.settingsRepository }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Theme.colors.accent)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable {
                val path = openDirectoryPicker("Link folder")
                if (path != null) {
                    settingsRepo.addLinkedFolder(path)
                    router.start(listOf(Screen.Folder(path)))
                }
            }
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        BasicText(
            "Link Folder…",
            style = TextStyle(
                color = Theme.colors.onAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
