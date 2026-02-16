package com.realdebrid.downloader.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF78C257),
    onPrimary = Color(0xFF003A00),
    primaryContainer = Color(0xFF005300),
    onPrimaryContainer = Color(0xFF94DF70),
    secondary = Color(0xFFB9CCB0),
    onSecondary = Color(0xFF253422),
    secondaryContainer = Color(0xFF3B4B37),
    onSecondaryContainer = Color(0xFFD5E8CB),
    tertiary = Color(0xFFA0D0CB),
    onTertiary = Color(0xFF013735),
    tertiaryContainer = Color(0xFF1F4E4C),
    onTertiaryContainer = Color(0xFFBCECE7),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF1A1C19),
    surface = Color(0xFF1A1C19),
    onBackground = Color(0xFFE2E3DE),
    onSurface = Color(0xFFE2E3DE)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006D2F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF94DF70),
    onPrimaryContainer = Color(0xFF002107),
    secondary = Color(0xFF526350),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8CB),
    onSecondaryContainer = Color(0xFF101F10),
    tertiary = Color(0xFF39656B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCECE7),
    onTertiaryContainer = Color(0xFF002022),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFFCFDF7),
    surface = Color(0xFFFCFDF7),
    onBackground = Color(0xFF1A1C19),
    onSurface = Color(0xFF1A1C19)
)

@Composable
fun RealDebridTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
