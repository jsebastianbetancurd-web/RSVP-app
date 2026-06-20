package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StrictHighContrastColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color.White,
    onSecondary = Color.Black,
    tertiary = Color(0xFFFF0000), // Red accent
    onTertiary = Color.White,
    background = Color(0xFF000000), // Pure Black Background
    onBackground = Color(0xFFFFFFFF), // Pure White Text
    surface = Color(0xFF000000), // Pure Black Surface
    onSurface = Color(0xFFFFFFFF), // Pure White On Surface
    surfaceVariant = Color(0xFF000000),
    onSurfaceVariant = Color(0xFFFFFFFF),
    outline = Color(0xFF222222)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Force off dynamic colors to preserve pure high-contrast
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StrictHighContrastColorScheme,
        typography = Typography,
        content = content
    )
}
