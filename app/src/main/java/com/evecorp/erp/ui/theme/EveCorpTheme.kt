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

// 浅蓝主色 + 浅粉副色配色方案
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1565C0),
    onPrimaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFFF48FB1),
    onSecondary = Color(0xFF880E4F),
    secondaryContainer = Color(0xFFAD1457),
    onSecondaryContainer = Color(0xFFF8BBD0),
    tertiary = Color(0xFFFFCC80),
    onTertiary = Color(0xFFE65100),
    background = Color(0xFF0F0F14),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1A1A22),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF252530),
    onSurfaceVariant = Color(0xFFBDBDBD),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFFB71C1C)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF42A5F5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = Color(0xFFF48FB1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCE4EC),
    onSecondaryContainer = Color(0xFF880E4F),
    tertiary = Color(0xFFFFCC80),
    onTertiary = Color.White,
    background = Color(0xFFF8FAFE),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFEEF2F7),
    onSurfaceVariant = Color(0xFF44474F),
    error = Color(0xFFD32F2F),
    onError = Color.White
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
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
