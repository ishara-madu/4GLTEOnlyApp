package com.pixeleye.lteonly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = GradientStart,
    secondary = GradientMiddle,
    tertiary = GradientEnd,
    background = NeumorphicBackground,
    surface = NeumorphicBackground,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

private val DarkColorScheme = darkColorScheme(
    primary = GradientStart,
    secondary = GradientMiddle,
    tertiary = GradientEnd,
    background = Color(0xFF1A1A2E),
    surface = Color(0xFF16213E),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun ForceLTEOnlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
