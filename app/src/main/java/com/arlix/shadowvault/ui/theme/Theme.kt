package com.arlix.shadowvault.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = HotVaultAccent,
    secondary = ColdVaultAccent,
    tertiary = HotVaultAccent,
    background = HotVaultCanvas,
    surface = HotVaultCanvas,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = HotVaultBorder
)

/** The UI is designed on fixed light canvases, so the app always uses the light scheme. */
@Composable
fun ArlixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
