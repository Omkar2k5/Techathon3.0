package com.example.edunet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Always dark — pure B&W professional theme
private val BwColorScheme = darkColorScheme(
    primary              = White,
    onPrimary            = Black,
    primaryContainer     = Gray800,
    onPrimaryContainer   = White,

    secondary            = Gray300,
    onSecondary          = Black,
    secondaryContainer   = Gray700,
    onSecondaryContainer = White,

    tertiary             = Gray200,
    onTertiary           = Black,

    background           = Gray900,
    onBackground         = White,

    surface              = Gray800,
    onSurface            = White,
    surfaceVariant       = Gray700,
    onSurfaceVariant     = Gray300,

    outline              = Gray600,
    outlineVariant       = Gray700,

    error                = Color(0xFFFF4444),
    onError              = Black,
)

@Composable
fun EduNetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BwColorScheme,
        typography  = Typography,
        content     = content
    )
}