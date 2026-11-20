package com.example.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CacauLightColorScheme = lightColorScheme(
    primary = CacauVerdeEscuro,
    onPrimary = Color.White,
    primaryContainer = CacauVerdeClaro,
    onPrimaryContainer = CacauVerdeEscuro,
    secondary = CacauVerdeBotanico,
    onSecondary = Color.White,
    secondaryContainer = CacauDouradoClaro,
    onSecondaryContainer = Color(0xFF4A3200),
    tertiary = CacauDourado,
    onTertiary = Color.White,
    background = CacauFundoSolar,
    onBackground = CacauTextoPrincipal,
    surface = CacauSuperficie,
    onSurface = CacauTextoPrincipal,
    surfaceVariant = CacauSuperficieVariant,
    onSurfaceVariant = CacauTextoSecundario,
    outline = CacauBordaSuave,
    error = CacauPerigo,
    onError = Color.White,
    errorContainer = CacauPerigoClaro,
    onErrorContainer = CacauPerigo
)

/**
 * Tema botânico de alto contraste solar projetado para legibilidade sob luz forte no campo.
 */
@Composable
fun CacauTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CacauLightColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    CacauTheme(darkTheme = darkTheme, content = content)
}
