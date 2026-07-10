package io.iris.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class IrisColors(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color
)

val LightColors = IrisColors(
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0xFF8E8E93),
    border = Color(0xFFE5E5EA)
)

val DarkColors = IrisColors(
    background = Color(0xFF1C1C1E),
    surface = Color(0xFF2C2C2E),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8E8E93),
    border = Color(0xFF3A3A3C)
)

val LocalIrisColors = staticCompositionLocalOf { LightColors }

object IrisTheme {
    val colors: IrisColors
        @Composable
        @ReadOnlyComposable
        get() = LocalIrisColors.current
}

@Composable
fun IrisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val scrollbarStyle = defaultScrollbarStyle().copy(
        unhoverColor = if (darkTheme) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f),
        hoverColor = if (darkTheme) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f),
        thickness = 10.dp,
        shape = RoundedCornerShape(5.dp)
    )

    CompositionLocalProvider(
        LocalIrisColors provides colors,
        LocalScrollbarStyle provides scrollbarStyle,
        content = content
    )
}
