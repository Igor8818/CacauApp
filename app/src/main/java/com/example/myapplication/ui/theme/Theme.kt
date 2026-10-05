package com.example.myapplication.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CacauGreenLight,
    onPrimary = CacauBrownDark,
    primaryContainer = CacauGreenDark,
    onPrimaryContainer = CacauGreenContainer,
    secondary = CacauAmberLight,
    onSecondary = CacauBrownDark,
    secondaryContainer = CacauBrownMedium,
    tertiary = CacauAmberAccent,
    background = CacauBrownDark,
    surface = Color(0xFF24160F),
    surfaceVariant = Color(0xFF332016),
    onBackground = Color(0xFFEDE0D4),
    onSurface = Color(0xFFEDE0D4)
)

private val LightColorScheme = lightColorScheme(
    primary = CacauGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = CacauGreenContainer,
    onPrimaryContainer = CacauGreenDark,
    secondary = CacauBrownMedium,
    onSecondary = Color.White,
    secondaryContainer = CacauBrownContainer,
    onSecondaryContainer = CacauBrownDark,
    tertiary = CacauAmberAccent,
    background = CacauBackgroundLight,
    surface = CacauSurfaceLight,
    surfaceVariant = CacauCardLight,
    onBackground = CacauTextPrimary,
    onSurface = CacauTextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}