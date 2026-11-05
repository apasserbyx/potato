package com.potato.focus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PotatoPalette(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val focus: Color,
    val focusSoft: Color,
    val breakColor: Color,
    val breakSoft: Color,
    val accent: Color,
    val accentSoft: Color,
    val danger: Color
)

val LightPalette = PotatoPalette(
    background = Color(0xFFF6F0E4),
    surface = Color(0xFFFFFBF3),
    surfaceRaised = Color(0xFFF1E8D9),
    ink = Color(0xFF241F19),
    muted = Color(0xFF776B5C),
    line = Color(0xFFE6D9C5),
    focus = Color(0xFF96592F),
    focusSoft = Color(0xFFEFDCC4),
    breakColor = Color(0xFF5F7550),
    breakSoft = Color(0xFFE1E8D8),
    accent = Color(0xFF8F6224),
    accentSoft = Color(0xFFF1E3C8),
    danger = Color(0xFFA84E38)
)

val DarkPalette = PotatoPalette(
    background = Color(0xFF151310),
    surface = Color(0xFF201C16),
    surfaceRaised = Color(0xFF29231C),
    ink = Color(0xFFF3EBDD),
    muted = Color(0xFFB5A792),
    line = Color(0xFF342D23),
    focus = Color(0xFFD69A63),
    focusSoft = Color(0xFF3A2A1C),
    breakColor = Color(0xFF9BB087),
    breakSoft = Color(0xFF253021),
    accent = Color(0xFFE3B26A),
    accentSoft = Color(0xFF3B2F1E),
    danger = Color(0xFFE07B68)
)

val LocalPotatoPalette = staticCompositionLocalOf { LightPalette }

private val PotatoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 76.sp,
        lineHeight = 84.sp,
        letterSpacing = (-2.5).sp,
        fontFeatureSettings = "tnum"
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 58.sp,
        lineHeight = 66.sp,
        letterSpacing = (-1.5).sp,
        fontFeatureSettings = "tnum"
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp
    )
)

private val PotatoShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PotatoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) DarkPalette else LightPalette
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = palette.focus,
            onPrimary = Color(0xFF2A1511),
            secondary = palette.breakColor,
            onSecondary = Color(0xFF122018),
            tertiary = palette.accent,
            onTertiary = Color(0xFF2A1D08),
            background = palette.background,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceRaised,
            onSurfaceVariant = palette.muted,
            outline = palette.line,
            error = palette.danger
        )
    } else {
        lightColorScheme(
            primary = palette.focus,
            onPrimary = palette.surface,
            secondary = palette.breakColor,
            onSecondary = palette.surface,
            tertiary = palette.accent,
            onTertiary = palette.surface,
            background = palette.background,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceRaised,
            onSurfaceVariant = palette.muted,
            outline = palette.line,
            error = palette.danger
        )
    }

    CompositionLocalProvider(LocalPotatoPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PotatoTypography,
            shapes = PotatoShapes,
            content = content
        )
    }
}

object Potato {
    val palette: PotatoPalette
        @Composable get() = LocalPotatoPalette.current
}
