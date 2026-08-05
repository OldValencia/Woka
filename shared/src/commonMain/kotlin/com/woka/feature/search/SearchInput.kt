package com.woka.feature.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

@Composable
fun SearchInput(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(Icons.Static.Search),
            contentDescription = "Search",
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(Theme.colors.textSecondary)
        )
        Spacer(Modifier.width(16.dp))
        
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = TextStyle(
                color = Theme.colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(Theme.colors.accentFolders),
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    BasicText(
                        text = "Search files and folders...",
                        style = TextStyle(
                            color = Theme.colors.textSecondary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                innerTextField()
            },
            modifier = Modifier.weight(1f)
        )
    }
}
