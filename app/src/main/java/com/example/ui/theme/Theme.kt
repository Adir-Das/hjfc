package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = NavyLight,
    onPrimaryContainer = Color.White,
    secondary = AthleticGreen,
    onSecondary = Color.White,
    secondaryContainer = AthleticGreenContainer,
    onSecondaryContainer = AthleticGreenDark,
    tertiary = GoldAccent,
    onTertiary = NavyDark,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = NavyDark,
    background = SurfaceOffWhite,
    onBackground = TextPrimary,
    surface = SurfaceCardWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = StatusError,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = AthleticGreen,
    onPrimary = Color.White,
    primaryContainer = NavyLight,
    onPrimaryContainer = DarkTextPrimary,
    secondary = GoldAccent,
    onSecondary = NavyDark,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = AthleticGreenContainer,
    tertiary = GoldAccent,
    onTertiary = NavyDark,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0xFF284B6E),
    error = Color(0xFFF87171),
    onError = NavyDark
)

val JscShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JournalistSportsClubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JscTypography,
        shapes = JscShapes,
        content = content
    )
}
