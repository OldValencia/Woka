package com.woka.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.app.APP_VERSION
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

/**
 * About panel: app brand, version, repository and support/donation links.
 */
@Composable
fun AboutSettingsPanel() {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(Icons.Static.Logo),
                contentDescription = "Woka",
                modifier = Modifier.height(22.dp)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Theme.colors.surfaceHover)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                BasicText(
                    text = "v$APP_VERSION",
                    style = TextStyle(
                        color = Theme.colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        BasicText(
            text = "Fast, minimalist media viewer and editor for desktop. Local-first, private by design, built with passion.",
            style = TextStyle(
                color = Theme.colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        )
        Spacer(Modifier.height(16.dp))
        SectionLabel("Project & Community")
        Spacer(Modifier.height(6.dp))
        AboutLinkRow(
            title = "GitHub Repository",
            subtitle = "Source code, release notes and issue tracker",
            url = "https://github.com/OldValencia/Woka"
        )
        Spacer(Modifier.height(14.dp))
        SectionLabel("Support Development")
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = "Woka is free and open-source. If you enjoy using it, your support makes a huge difference:",
            style = TextStyle(color = Theme.colors.textSecondary, fontSize = 11.sp)
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DonateButton("GitHub Sponsors", "https://github.com/sponsors/OldValencia", Modifier.weight(1f))
            DonateButton("Ko-fi", "https://ko-fi.com/oldvalencia", Modifier.weight(1f))
            DonateButton("Buy Me a Coffee", "https://buymeacoffee.com/oldvalencia", Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel("Credits")
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = "Compose Multiplatform · Skia · FFmpeg · JavaCV\nCreated by OldValencia",
            style = TextStyle(
                color = Theme.colors.textTertiary,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        )
    }
}
