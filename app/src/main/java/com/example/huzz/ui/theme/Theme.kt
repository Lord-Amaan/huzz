package com.example.huzz.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
    primary = HuzzGreen,
    onPrimary = Color.Black,
    primaryContainer = HuzzGreenDark,
    onPrimaryContainer = HuzzGreenLight,
    secondary = HuzzBlue,
    onSecondary = Color.Black,
    secondaryContainer = HuzzBlueDark,
    onSecondaryContainer = HuzzBlueLight,
    tertiary = HuzzOrange,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = NothingTextDisplay,
    surface = DarkSurface,
    onSurface = NothingTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = NothingTextSecondary,
    outline = DarkOutline,
    outlineVariant = NothingBorderSubtle,
    error = HuzzRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E2E0),
    onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFF666666),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBEBE9),
    onSecondaryContainer = Color(0xFF111111),
    tertiary = HuzzOrange,
    onTertiary = Color.Black,
    background = LightBackground,
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF666666),
    outline = LightOutline,
    outlineVariant = Color(0xFFE0E0DE),
    error = HuzzRed,
    onError = Color.White
)

@Composable
fun HuzzTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val huzzPalette = if (darkTheme) DarkHuzzPalette else LightHuzzPalette
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalHuzzPalette provides huzzPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
