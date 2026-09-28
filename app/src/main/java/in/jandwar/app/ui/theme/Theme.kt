package `in`.jandwar.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = BrandIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FA),
    onPrimaryContainer = BrandIndigoDeep,
    secondary = BrandTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCBF0F0),
    onSecondaryContainer = Color(0xFF075959),
    tertiary = BrandSaffron,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE6CC),
    onTertiaryContainer = Color(0xFF6B3908),
    background = Paper,
    onBackground = Ink,
    surface = SurfaceLight,
    onSurface = Ink,
    surfaceVariant = SurfaceLightAlt,
    onSurfaceVariant = Muted,
    surfaceContainerHighest = SurfaceLightAlt,
    outline = BorderLight,
    outlineVariant = Color(0xFFE8EDF6),
    error = Error,
    onError = Color.White,
    errorContainer = ErrorSoft,
    onErrorContainer = Color(0xFF7A1414),
    scrim = Color(0x99000000)
)

private val DarkColors = darkColorScheme(
    primary = BrandTealLight,
    onPrimary = Color(0xFF00302F),
    primaryContainer = Color(0xFF0B4F4F),
    onPrimaryContainer = Color(0xFFB6F0F0),
    secondary = BrandIndigoLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E2F5C),
    onSecondaryContainer = Color(0xFFC9D6F5),
    tertiary = BrandSaffronSoft,
    onTertiary = Color(0xFF452200),
    tertiaryContainer = Color(0xFF5A3410),
    onTertiaryContainer = Color(0xFFFFE0BE),
    background = InkDark,
    onBackground = OnDark,
    surface = SurfaceDark,
    onSurface = OnDark,
    surfaceVariant = SurfaceDarkAlt,
    onSurfaceVariant = MutedDark,
    surfaceContainerHighest = SurfaceDarkAlt,
    outline = BorderDark,
    outlineVariant = Color(0xFF232E3E),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0A0A),
    errorContainer = Color(0xFF5A1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xCC000000)
)

/**
 * Exposes whether the app is currently in dark mode to composables that need
 * to pick a hand-tuned colour (e.g. soft success/warning fills).
 */
@Composable
fun JanDwarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
