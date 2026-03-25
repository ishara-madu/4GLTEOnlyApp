package com.pixeleye.lteonly.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

var globalIsDarkTheme by mutableStateOf(false)

val NeumorphicBackground: Color
    get() = if (globalIsDarkTheme) Color(0xFF1B1B22) else Color(0xFFF0F4F8)

val NeumorphicLightShadow: Color
    get() = if (globalIsDarkTheme) Color(0xFF272730) else Color(0xFFFFFFFF)

val NeumorphicDarkShadow: Color
    get() = if (globalIsDarkTheme) Color(0xFF0F0F14) else Color(0xFFD1D9E6)

val TextPrimary: Color
    get() = if (globalIsDarkTheme) Color(0xFFE2E8F0) else Color(0xFF2D3748)

val TextSecondary: Color
    get() = if (globalIsDarkTheme) Color(0xFFA0AEC0) else Color(0xFF718096)

val TextTeal = Color(0xFF00BFA5)

val GradientStart = Color(0xFFB57BFF)
val GradientMiddle = Color(0xFF67B2FF)
val GradientEnd = Color(0xFF5AC8FA)

val GradientColors: List<Color>
    get() = listOf(GradientStart, GradientMiddle, GradientEnd)