package com.ultimateiptv.sports.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Pitch = Color(0xFF00D47E)
val PitchDark = Color(0xFF00A862)
val Night = Color(0xFF0A0E17)
val Surface1 = Color(0xFF121826)
val Surface2 = Color(0xFF1A2132)
val Surface3 = Color(0xFF232C40)
val Electric = Color(0xFF3D8BFF)
val Amber = Color(0xFFFFB020)
val LiveRed = Color(0xFFFF3B30)
val TextHigh = Color(0xFFF2F5FA)
val TextMedium = Color(0xFFA9B3C7)

private val colors = darkColorScheme(
    primary = Pitch,
    onPrimary = Color(0xFF00170C),
    primaryContainer = Color(0xFF00381F),
    onPrimaryContainer = Color(0xFF9CF7C8),
    secondary = Electric,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF14305A),
    onSecondaryContainer = Color(0xFFD5E3FF),
    tertiary = Amber,
    onTertiary = Color(0xFF261900),
    background = Night,
    onBackground = TextHigh,
    surface = Surface1,
    onSurface = TextHigh,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMedium,
    surfaceContainerLowest = Night,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    outline = Color(0xFF3A4560),
    outlineVariant = Color(0xFF283149),
    error = Color(0xFFFF5A5F),
)

private val base = Typography()
private val typography = base.copy(
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
