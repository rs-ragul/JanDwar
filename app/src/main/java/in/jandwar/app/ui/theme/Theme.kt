package `in`.jandwar.app.ui.theme

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

private val LightColorScheme = lightColorScheme(
    primary = BrandIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E0FF),
    onPrimaryContainer = BrandIndigo,
    secondary = BrandTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB0EDED),
    onSecondaryContainer = Color(0xFF0D4F4F),
    tertiary = BrandSaffron,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF4A2C0A),
    background = Paper,
    onBackground = Ink,
    surface = SurfaceLight,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE8ECF4),
    onSurfaceVariant = Muted,
    outline = BorderLight,
    error = Error
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0D4F4F), // dark teal container - premium
    onPrimaryContainer = Color(0xFFB0EDED),
    secondary = BrandIndigoLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E2F5C),
    onSecondaryContainer = Color(0xFFC2D0F0),
    tertiary = BrandSaffron,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF4A2C0A),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF0F131E),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF1A2030),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF2A344A),
    onSurfaceVariant = Color(0xFFBAC2D0),
    outline = Color(0xFF3E4A62),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF5A1A1A)
)

@Composable
fun JanDwarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
