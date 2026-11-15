package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ClounelDarkColorScheme = darkColorScheme(
    primary = PumpkinGold,
    onPrimary = Color(0xFF1A1002),
    primaryContainer = PumpkinContainerDark,
    onPrimaryContainer = PumpkinGoldSoft,
    secondary = StatusEmeraldDark,
    onSecondary = Color(0xFF04220E),
    secondaryContainer = Color(0xFF0A2916),
    onSecondaryContainer = Color(0xFF86EFAC),
    tertiary = PumpkinGoldSoft,
    onTertiary = Color(0xFF1A1002),
    tertiaryContainer = MatteBlackElevated,
    onTertiaryContainer = TextLight,
    background = MatteBlackBg,
    onBackground = TextLight,
    surface = MatteBlackSurface,
    onSurface = TextLight,
    surfaceVariant = MatteBlackElevated,
    onSurfaceVariant = TextMutedDark,
    surfaceContainerLow = MatteBlackSurface,
    surfaceContainer = MatteBlackElevated,
    surfaceContainerHigh = Color(0xFF22262F),
    outline = MatteBlackPumpkinBorder,
    outlineVariant = MatteBlackBorder,
    error = StatusErrorDark,
    onError = Color.White,
    errorContainer = Color(0xFF3B0D11),
    onErrorContainer = Color(0xFFFCA5A5)
)

private val ClounelLightColorScheme = lightColorScheme(
    primary = PumpkinGoldDeep,
    onPrimary = Color.White,
    primaryContainer = PumpkinContainerLight,
    onPrimaryContainer = Color(0xFF5C2D02),
    secondary = StatusEmerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCFCE7),
    onSecondaryContainer = Color(0xFF14532D),
    tertiary = PumpkinGold,
    onTertiary = Color.White,
    tertiaryContainer = SoftIvorySurface,
    onTertiaryContainer = TextDark,
    background = PureWhiteBg,
    onBackground = TextDark,
    surface = PureWhiteSurface,
    onSurface = TextDark,
    surfaceVariant = SoftIvorySurface,
    onSurfaceVariant = TextMutedLight,
    surfaceContainerLow = PureWhiteSurface,
    surfaceContainer = SoftIvorySurface,
    surfaceContainerHigh = Color(0xFFFFF3E0),
    outline = PumpkinGold,
    outlineVariant = Color(0xFFF3CFA0),
    error = StatusErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

data class PulseExtendedColors(
    val success: Color,
    val warning: Color,
    val cyanAccent: Color,
    val studioAccent: Color,
    val terminalBg: Color,
    val terminalBorder: Color,
    val cardBorder: Color
)

val LocalPulseColors = staticCompositionLocalOf {
    PulseExtendedColors(
        success = StatusEmeraldDark,
        warning = PumpkinGold,
        cyanAccent = PumpkinGold,
        studioAccent = PumpkinGoldSoft,
        terminalBg = Color(0xFF0E1014),
        terminalBorder = MatteBlackBorder,
        cardBorder = MatteBlackPumpkinBorder
    )
}

val PulseShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

@Composable
fun PulseProxyTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ClounelDarkColorScheme else ClounelLightColorScheme
    val extended = if (darkTheme) {
        PulseExtendedColors(
            success = StatusEmeraldDark,
            warning = PumpkinGold,
            cyanAccent = PumpkinGold,
            studioAccent = PumpkinGoldSoft,
            terminalBg = Color(0xFF0E1014),
            terminalBorder = MatteBlackBorder,
            cardBorder = MatteBlackPumpkinBorder
        )
    } else {
        PulseExtendedColors(
            success = StatusEmerald,
            warning = PumpkinGoldDeep,
            cyanAccent = PumpkinGoldDeep,
            studioAccent = PumpkinGoldDeep,
            terminalBg = Color(0xFF181A20),
            terminalBorder = PumpkinGold,
            cardBorder = PumpkinGold
        )
    }

    CompositionLocalProvider(LocalPulseColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PulseTypography,
            shapes = PulseShapes,
            content = content
        )
    }
}
