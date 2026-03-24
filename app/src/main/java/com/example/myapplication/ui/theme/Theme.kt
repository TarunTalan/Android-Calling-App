package com.example.myapplication.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CallBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1A2A5E),
    onPrimaryContainer = Color(0xFFD0E4FF),

    secondary = CallGreen,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF00391A),
    onSecondaryContainer = Color(0xFFB5F0CC),

    tertiary = CallRed,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF4A0014),
    onTertiaryContainer = Color(0xFFFFD9DF),

    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,

    outline = OutlineColor,
    outlineVariant = DividerColor,

    error = CallRed,
    onError = Color.White,

    inverseSurface = Color(0xFFE8E8F0),
    inverseOnSurface = Color(0xFF1A1A28),
    inversePrimary = Color(0xFF0040CC),
)

@Composable
fun CallingAppTheme(content: @Composable () -> Unit) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BackgroundDark.toArgb()
            window.navigationBarColor = BackgroundDark.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme, typography = Typography, content = content
    )
}
