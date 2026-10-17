package com.thiago.listamercado.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7EFB2),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFFE65100),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBC8),
    onSecondaryContainer = Color(0xFF341100),
    tertiary = Color(0xFF00658F),
    tertiaryContainer = Color(0xFFC7E7FF),
    onTertiaryContainer = Color(0xFF001E2E),
    background = Color(0xFFF7FBF2),
    surface = Color(0xFFF7FBF2),
    surfaceVariant = Color(0xFFDEE5D8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CD67D),
    onPrimary = Color(0xFF0E3900),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFB7EFB2),
    secondary = Color(0xFFFFB68E),
    onSecondary = Color(0xFF552000),
    secondaryContainer = Color(0xFF793000),
    onSecondaryContainer = Color(0xFFFFDBC8),
    tertiary = Color(0xFF85CFFF),
    tertiaryContainer = Color(0xFF004C6C),
    onTertiaryContainer = Color(0xFFC7E7FF),
)

/** Cor usada para destacar preços em promoção. */
val PromoColor = Color(0xFFD84315)

@Composable
fun ListaMercadoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
