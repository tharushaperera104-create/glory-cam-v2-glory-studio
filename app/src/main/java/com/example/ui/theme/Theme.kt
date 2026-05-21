package com.glorycam.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GloryGold,
    secondary = GloryLightGold,
    tertiary = CyanAccent,
    background = DeepBackground,
    surface = DarkSurface,
    onPrimary = DeepBackground,
    onSecondary = DeepBackground,
    onTertiary = DeepBackground,
    onBackground = WhiteCream,
    onSurface = WhiteCream,
    surfaceVariant = DarkSurfaceOverlay,
    onSurfaceVariant = MutedSlate
)

// Standard camera interfaces are always sleek dark for accurate framing
private val LightColorScheme = DarkColorScheme

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme for professional camera look
    dynamicColor: Boolean = false, // Always use our signature Glory Cam branding
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
