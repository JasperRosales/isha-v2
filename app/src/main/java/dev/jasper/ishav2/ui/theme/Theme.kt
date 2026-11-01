package dev.jasper.ishav2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun IshaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalIshaPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.colorScheme(darkTheme),
            typography = IshaTypography,
            shapes = IshaShapes,
            content = content
        )
    }
}
