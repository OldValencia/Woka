package com.woka.app.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import java.io.File

/**
 * The open folder's name, as the toolbar's title (`.title h2`): one line, cut with
 * an ellipsis when the controls beside it need the room. A drive root has no name
 * of its own, so it shows its path.
 */
@Composable
fun ToolbarTitle(path: String) {
    val name = remember(path) { File(path).name.ifEmpty { path } }
    BasicText(
        name,
        modifier = Modifier.padding(start = 6.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = Theme.colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    )
}
