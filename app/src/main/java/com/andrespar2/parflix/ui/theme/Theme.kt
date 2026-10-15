package com.andrespar2.parflix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Identidad visual: Black Modern AMOLED Red.
val Black = Color(0xFF000000)
val SurfaceDark = Color(0xFF0C0C0C)
val SurfaceVariantDark = Color(0xFF161616)
val RedAccent = Color(0xFFE50914)
val RedDim = Color(0xFF8E0A12)
val TextPrimary = Color(0xFFF5F5F5)
val TextSecondary = Color(0xFF9E9E9E)

private val ParFlixColors = darkColorScheme(
    primary = RedAccent,
    onPrimary = Color.White,
    primaryContainer = RedDim,
    onPrimaryContainer = Color.White,
    secondary = RedAccent,
    onSecondary = Color.White,
    background = Black,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF2A2A2A),
    error = Color(0xFFCF6679),
)

/**
 * Tema oscuro exclusivo (AMOLED). ParFlix es una app de reproducción
 * nocturna: el negro puro maximiza ahorro de batería en pantallas AMOLED.
 */
@Composable
fun ParFlixTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ParFlixColors,
        content = content,
    )
}
