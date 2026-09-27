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
    primaryContainer = BrandIndigoLight,
    onPrimaryContainer = Color.White,
    secondary = BrandTeal,
    onSecondary = Color.White,
    tertiary = BrandSaffron,
    onTertiary = Color.White,
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
    primaryContainer = BrandIndigo,
    onPrimaryContainer = Color.White,
    secondary = BrandIndigoLight,
    onSecondary = Color.White,
    tertiary = BrandSaffron,
    onTertiary = Color.White,
    background = Color(0xFF10141F),
    onBackground = Color(0xFFE8ECF4),
    surface = Color(0xFF1A1F2E),
    onSurface = Color(0xFFE8ECF4),
    surfaceVariant = Color(0xFF2A3245),
    onSurfaceVariant = Color(0xFFA0AEC0),
    outline = Color(0xFF3A455C),
    error = Color(0xFFFF6B6B)
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
