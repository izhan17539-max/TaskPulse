package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Immutable
data class TareasStatusColors(
    val synced: Color,
    val syncing: Color,
    val urgent: Color,
    val warning: Color
)

val LocalTareasStatusColors = staticCompositionLocalOf {
    TareasStatusColors(
        synced = EmeraldSyncLight,
        syncing = CobaltPrimary,
        urgent = CoralUrgentLight,
        warning = AmberWarningLight
    )
}

private val LightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = OnCobaltPrimary,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = OnCobaltContainer,
    secondary = SlateSecondary,
    onSecondary = OnSlateSecondary,
    secondaryContainer = SlateContainer,
    onSecondaryContainer = OnSlateContainer,
    tertiary = AmberTertiary,
    onTertiary = OnAmberTertiary,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    background = AlabasterBackground,
    onBackground = InkOnSurface,
    surface = PureSurface,
    onSurface = InkOnSurface,
    surfaceVariant = SoftSurfaceVariant,
    onSurfaceVariant = MutedOnSurfaceVariant,
    surfaceContainerLow = PureSurface,
    surfaceContainer = SoftSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE2E8F0),
    outline = SubtleOutline,
    outlineVariant = SubtleOutlineVariant,
    error = CoralUrgentLight,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = CobaltPrimaryDark,
    onPrimary = OnCobaltPrimaryDark,
    primaryContainer = CobaltContainerDark,
    onPrimaryContainer = OnCobaltContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = OnSlateSecondaryDark,
    secondaryContainer = SlateContainerDark,
    onSecondaryContainer = OnSlateContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = OnAmberTertiaryDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    background = ObsidianBackground,
    onBackground = InkOnSurfaceDark,
    surface = ObsidianSurface,
    onSurface = InkOnSurfaceDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = MutedOnSurfaceDark,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceVariant,
    surfaceContainerHigh = Color(0xFF273449),
    outline = SubtleOutlineDark,
    outlineVariant = SubtleOutlineVariantDark,
    error = CoralUrgentDark,
    onError = Color(0xFF4C0519)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TareasNubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val statusColors = if (darkTheme) {
        TareasStatusColors(
            synced = EmeraldSyncDark,
            syncing = CobaltPrimaryDark,
            urgent = CoralUrgentDark,
            warning = AmberWarningDark
        )
    } else {
        TareasStatusColors(
            synced = EmeraldSyncLight,
            syncing = CobaltPrimary,
            urgent = CoralUrgentLight,
            warning = AmberWarningLight
        )
    }

    CompositionLocalProvider(LocalTareasStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
