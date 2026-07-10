package com.androidkris.ide.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = KrisGreen,
    secondary = KrisBlue,
    tertiary = KrisAmber,
    background = DarkBackground,
    surface = DarkSurface,
    error = KrisRed,
)

private val LightColors = lightColorScheme(
    primary = KrisGreenDark,
    secondary = KrisBlue,
    tertiary = KrisAmber,
    background = LightBackground,
    surface = LightSurface,
    error = KrisRed,
)

@Composable
fun AndroidKrisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
