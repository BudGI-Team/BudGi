package com.app.presentation.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * BudGi Material3 Theme — Liquid Glass / Glassmorphic theme.
 * Dark theme is the primary/default — deep obsidian canvas with BudGi Cyan accents.
 * Pill-shaped, water-drop aesthetic with translucent glass surfaces.
 * Supports Material You dynamic colors on Android 12+.
 */

// Composition local to check dark theme state from anywhere in the tree
val LocalIsDarkTheme = staticCompositionLocalOf { true }

private val BudgiDarkScheme = darkColorScheme(
    primary = DarkColors.Accent,                // BudGi Sky/Cyan #38BDF8
    onPrimary = Color.White,                    // White on accent
    primaryContainer = DarkColors.AccentDim,
    onPrimaryContainer = DarkColors.AccentBright,
    secondary = DarkColors.Secondary,           // Teal #60BCD4
    onSecondary = Color.White,
    secondaryContainer = DarkColors.SecondaryDim,
    onSecondaryContainer = DarkColors.Secondary,
    tertiary = DarkColors.Green,                // Green #1DBF81
    onTertiary = Color.White,
    tertiaryContainer = DarkColors.GreenDim,
    onTertiaryContainer = DarkColors.Green,
    error = DarkColors.Red,                     // Red
    onError = Color.White,
    errorContainer = DarkColors.RedDim,
    onErrorContainer = DarkColors.Red,
    background = DarkColors.BgBase,             // Deep obsidian canvas #08090C
    onBackground = DarkColors.TextPrimary,      // Crisp near-white #F9FAFB
    surface = DarkColors.BgElevated,            // Elevated charcoal card surface #13161C
    onSurface = DarkColors.TextPrimary,         // Crisp near-white #F9FAFB
    surfaceVariant = DarkColors.SurfaceContainerHighest, // Highest elevation pills & squircles #1E232E
    onSurfaceVariant = DarkColors.TextSecondary, // Clean neutral slate gray #9CA3AF
    surfaceContainer = DarkColors.SurfaceContainer,      // #0E1015
    surfaceContainerHigh = DarkColors.SurfaceContainerHigh, // #181C24
    surfaceContainerHighest = DarkColors.SurfaceContainerHighest, // #1E232E
    outline = DarkColors.Border,                // Hairline crisp border #222630
    outlineVariant = DarkColors.BorderStrong,   // Subtle border #2B313E
    inverseSurface = DarkColors.TextPrimary,
    inverseOnSurface = DarkColors.BgBase,
    inversePrimary = DarkColors.AccentBright,
    surfaceTint = Color.Transparent,            // No tint
)

private val BudgiLightScheme = lightColorScheme(
    primary = LightColors.Accent,
    onPrimary = Color.White,
    primaryContainer = LightColors.AccentDim,
    onPrimaryContainer = Color(0xFF0C4A6E),     // Deep sky on light container
    secondary = LightColors.Blue,               // Teal
    onSecondary = Color.White,
    secondaryContainer = LightColors.GlassSurface,
    onSecondaryContainer = LightColors.TextPrimary,
    tertiary = LightColors.Green,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD4F5E4),      // Light green container
    onTertiaryContainer = Color(0xFF003D20),
    error = LightColors.Red,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = LightColors.BgBase,            // Light gray-blue
    onBackground = LightColors.TextPrimary,     // Dark navy
    surface = LightColors.BgSurface,            // White
    onSurface = LightColors.TextPrimary,
    surfaceVariant = LightColors.GlassSurface,
    onSurfaceVariant = LightColors.TextSecondary,
    surfaceContainer = LightColors.SurfaceContainer,
    surfaceContainerHigh = LightColors.SurfaceContainerHigh,
    surfaceContainerHighest = LightColors.SurfaceContainerHighest,
    outline = LightColors.BorderStrong,
    outlineVariant = LightColors.Border,
    inverseSurface = LightColors.TextPrimary,
    inverseOnSurface = LightColors.BgBase,
    inversePrimary = LightColors.Accent,
    surfaceTint = LightColors.Accent,
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false by default to preserve curated BudGi liquid glass palette
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> BudgiDarkScheme
        else -> BudgiLightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            activity?.window?.let { window ->
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BudgiTypography,
            shapes = BudgiShapes,
            content = content
        )
    }
}
