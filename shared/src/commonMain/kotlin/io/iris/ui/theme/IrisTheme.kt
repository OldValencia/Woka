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
    val surfaceElevated: Color,
    val surfaceHover: Color,
    val surfacePressed: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color,
    val borderGlow: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val danger: Color,
    val dangerSoft: Color,
    val onDanger: Color,
    val accentHome: Color,
    val accentFolders: Color,
    val accentEditor: Color,
    val accentSearch: Color
)

val LightColors = IrisColors(
    background = Color(0xFFF2F2F7),
    surface = Color(0xCCFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceHover = Color(0x0A000000),
    surfacePressed = Color(0x14000000),
    textPrimary = Color(0xFF1C1C1E),
    textSecondary = Color(0xFF6E6E76),
    textTertiary = Color(0xFF9A9AA1),
    border = Color(0x14000000),
    borderGlow = Color(0x26000000),
    accent = Color(0xFF4C6FE0),
    accentSoft = Color(0x1F4C6FE0),
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFFE5484D),
    dangerSoft = Color(0x1AE5484D),
    onDanger = Color(0xFFFFFFFF),
    accentHome = Color(0xFF0A84FF),
    accentFolders = Color(0xFFFF9F0A),
    accentEditor = Color(0xFFBF5AF2),
    accentSearch = Color(0xFF30D158)
)

val DarkColors = IrisColors(
    background = Color(0xFF0E1015),
    surface = Color(0xCC161A22), // rgba(22, 26, 34, 0.8)
    surfaceElevated = Color(0xFF161920), // card bg
    surfaceHover = Color(0x05FFFFFF),
    surfacePressed = Color(0x08FFFFFF),
    textPrimary = Color(0xFFF4F4F6),
    textSecondary = Color(0xFF515764),
    textTertiary = Color(0xFF838893), // dock item text
    border = Color(0x0AFFFFFF), // rgba(255, 255, 255, 0.04)
    borderGlow = Color(0x14FFFFFF), // rgba(255, 255, 255, 0.08)
    accent = Color(0xFF7B93FF),
    accentSoft = Color(0x2B7B93FF),
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFFFF5C5C),
    dangerSoft = Color(0x24FF5C5C),
    onDanger = Color(0xFFFFFFFF),
    accentHome = Color(0xFF0A84FF),
    accentFolders = Color(0xFFFF9F0A),
    accentEditor = Color(0xFFBF5AF2),
    accentSearch = Color(0xFF30D158)
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
