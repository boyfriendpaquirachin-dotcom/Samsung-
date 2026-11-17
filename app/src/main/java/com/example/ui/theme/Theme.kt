package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = ShizukuCyan,
    onPrimary = Color(0xFF001F29),
    primaryContainer = ShizukuCyanContainer,
    onPrimaryContainer = ShizukuCyan,
    secondary = TermuxEmerald,
    onSecondary = Color(0xFF002110),
    secondaryContainer = TermuxEmeraldContainer,
    onSecondaryContainer = TermuxEmerald,
    tertiary = BatteryAmber,
    onTertiary = Color(0xFF281B00),
    tertiaryContainer = BatteryAmberContainer,
    onTertiaryContainer = BatteryAmber,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = SlateSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateContainer,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHigh = SlateElevated,
    outline = SlateOutline,
    error = AlertRed,
    onError = Color.White,
    errorContainer = AlertRedContainer,
    onErrorContainer = AlertRed
)

private val LightColorScheme = lightColorScheme(
    primary = ShizukuCyanDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F3FF),
    onPrimaryContainer = Color(0xFF003547),
    secondary = TermuxEmeraldDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F9E4),
    onSecondaryContainer = Color(0xFF00391C),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightContainer,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightOutline,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val TermuZukuShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun TermuZukuTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = TermuZukuShapes,
        content = content
    )
}
