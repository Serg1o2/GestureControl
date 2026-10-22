package com.gesturecontrol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Brand palette: indigo (primary), teal (secondary, "on / ready"), amber (tertiary, "needs attention").
private val Indigo10 = Color(0xFF0F0A3C)
private val Indigo40 = Color(0xFF4F46E5)
private val Indigo80 = Color(0xFFB4B0FF)
private val Indigo90 = Color(0xFFE2DFFF)
private val Teal40 = Color(0xFF00897B)
private val Teal80 = Color(0xFF6FD8C8)
private val Teal90 = Color(0xFFCCF2EC)
private val Amber40 = Color(0xFFB26A00)
private val Amber80 = Color(0xFFFFC56B)
private val Amber90 = Color(0xFFFFE8C7)

private val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFF7F6FC),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFF7F6FC),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F1F8),
    surfaceContainer = Color(0xFFECEBF3),
    surfaceContainerHigh = Color(0xFFE6E5ED),
    outline = Color(0xFF787680),
    outlineVariant = Color(0xFFC8C5D0),
)

private val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = Color(0xFF1D1677),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Indigo90,
    secondary = Teal80,
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Teal90,
    tertiary = Amber80,
    onTertiary = Color(0xFF462A00),
    tertiaryContainer = Color(0xFF643F00),
    onTertiaryContainer = Amber90,
    background = Color(0xFF121218),
    onBackground = Color(0xFFE5E1EA),
    surface = Color(0xFF121218),
    onSurface = Color(0xFFE5E1EA),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    surfaceContainerLowest = Color(0xFF0D0D12),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F26),
    surfaceContainerHigh = Color(0xFF2A2930),
    outline = Color(0xFF928F9A),
    outlineVariant = Color(0xFF47464F),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Colours outside the Material scheme: the status card gradients and the status accents. */
@Immutable
data class BrandColors(
    val heroOn: Brush,
    val heroOff: Brush,
    val onHero: Color,
    val ok: Color,
    val warn: Color,
    /** Section cards: lighter than the background in both themes. */
    val card: Color,
)

private val LightBrand = BrandColors(
    heroOn = Brush.linearGradient(listOf(Indigo40, Teal40)),
    heroOff = Brush.linearGradient(listOf(Color(0xFF5B5A6B), Color(0xFF3C3B4A))),
    onHero = Color.White,
    ok = Teal40,
    warn = Amber40,
    card = Color.White,
)

private val DarkBrand = BrandColors(
    heroOn = Brush.linearGradient(listOf(Color(0xFF3730A3), Color(0xFF00695C))),
    heroOff = Brush.linearGradient(listOf(Color(0xFF2A2930), Color(0xFF1F1F26))),
    onHero = Color.White,
    ok = Teal80,
    warn = Amber80,
    card = Color(0xFF1F1F27),
)

val LocalBrand = staticCompositionLocalOf { LightBrand }

@Composable
fun GestureControlTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalBrand provides if (dark) DarkBrand else LightBrand) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}
