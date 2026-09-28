package `in`.jandwar.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Typography tuned for low-literacy users: larger base sizes, generous line
 * height and heavier weights so Indic scripts (Tamil, Telugu, Malayalam) stay
 * legible on low-cost devices.
 */
private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Double = 0.0
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    lineHeightStyle = lineHeightStyle
)

val Typography = Typography(
    displaySmall = style(34, 42, FontWeight.Bold, (-0.5)),
    headlineLarge = style(30, 38, FontWeight.Bold, (-0.4)),
    headlineMedium = style(25, 33, FontWeight.Bold, (-0.3)),
    headlineSmall = style(21, 29, FontWeight.Bold),
    titleLarge = style(19, 27, FontWeight.Bold),
    titleMedium = style(17, 24, FontWeight.SemiBold),
    titleSmall = style(15, 21, FontWeight.SemiBold),
    bodyLarge = style(16, 25, FontWeight.Normal, 0.1),
    bodyMedium = style(15, 23, FontWeight.Normal, 0.1),
    bodySmall = style(13, 19, FontWeight.Normal, 0.1),
    labelLarge = style(15, 20, FontWeight.SemiBold, 0.1),
    labelMedium = style(13, 17, FontWeight.SemiBold, 0.2),
    labelSmall = style(11, 15, FontWeight.SemiBold, 0.3)
)
