package com.evecorp.erp.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════════════
//  Soft Structuralism — Sky Blue + Sakura Pink
//  Airy, floating components with ambient light aesthetics
// ═══════════════════════════════════════════════════════════════

private val DarkColorScheme = darkColorScheme(
    primary = Sky300,
    onPrimary = Sky900,
    primaryContainer = Sky700,
    onPrimaryContainer = Sky100,
    secondary = Sakura300,
    onSecondary = Sakura800,
    secondaryContainer = Sakura700,
    onSecondaryContainer = Sakura100,
    tertiary = Lavender300,
    onTertiary = Gray900,
    tertiaryContainer = Lavender400.copy(alpha = 0.3f),
    background = Gray900,
    onBackground = Gray100,
    surface = Gray800,
    onSurface = Gray100,
    surfaceVariant = Gray700,
    onSurfaceVariant = Gray400,
    surfaceTint = Sky300,
    outline = Gray500,
    outlineVariant = Gray700,
    error = Error.copy(alpha = 0.9f),
    onError = Color.White,
    errorContainer = Error.copy(alpha = 0.2f),
    onErrorContainer = ErrorLight,
    inverseSurface = Gray100,
    inverseOnSurface = Gray800,
    inversePrimary = Sky600,
    scrim = Color.Black.copy(alpha = 0.6f)
)

private val LightColorScheme = lightColorScheme(
    primary = Sky500,
    onPrimary = Color.White,
    primaryContainer = Sky100,
    onPrimaryContainer = Sky800,
    secondary = Sakura400,
    onSecondary = Color.White,
    secondaryContainer = Sakura50,
    onSecondaryContainer = Sakura700,
    tertiary = Lavender300,
    onTertiary = Color.White,
    tertiaryContainer = Lavender50,
    onTertiaryContainer = Lavender400,
    background = Gray50,
    onBackground = Gray800,
    surface = Color.White,
    onSurface = Gray800,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray500,
    surfaceTint = Sky500,
    outline = Gray300,
    outlineVariant = Gray200,
    error = Error,
    onError = Color.White,
    errorContainer = ErrorLight,
    onErrorContainer = Error,
    inverseSurface = Gray800,
    inverseOnSurface = Gray50,
    inversePrimary = Sky300,
    scrim = Color.Black.copy(alpha = 0.4f)
)

@Composable
fun EveCorpTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val isDark = darkTheme ?: isSystemInDarkTheme()
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
