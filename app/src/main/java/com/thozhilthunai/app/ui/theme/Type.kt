package com.thozhilthunai.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.thozhilthunai.app.R

val NotoSansTamil = FontFamily(
    Font(R.font.noto_sans_tamil_regular, FontWeight.Normal),
    Font(R.font.noto_sans_tamil_medium, FontWeight.Medium),
    Font(R.font.noto_sans_tamil_bold, FontWeight.Bold)
)

// Text size multipliers: 0=small (0.85x), 1=medium (1.0x), 2=large (1.2x)
fun scaledTypography(scale: Float = 1f) = Typography(
    displayLarge = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.Bold,
        fontSize = (57 * scale).sp,
        lineHeight = (64 * scale).sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.Bold,
        fontSize = (45 * scale).sp,
        lineHeight = (52 * scale).sp
    ),
    displaySmall = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.Bold,
        fontSize = (36 * scale).sp,
        lineHeight = (44 * scale).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.Bold,
        fontSize = (32 * scale).sp,
        lineHeight = (40 * scale).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.SemiBold,
        fontSize = (28 * scale).sp,
        lineHeight = (36 * scale).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.SemiBold,
        fontSize = (24 * scale).sp,
        lineHeight = (32 * scale).sp
    ),
    titleLarge = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.SemiBold,
        fontSize = (22 * scale).sp,
        lineHeight = (28 * scale).sp
    ),
    titleMedium = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.Medium,
        fontSize = (16 * scale).sp,
        lineHeight = (24 * scale).sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.Medium,
        fontSize = (14 * scale).sp,
        lineHeight = (20 * scale).sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.Normal,
        fontSize = (16 * scale).sp,
        lineHeight = (24 * scale).sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.Normal,
        fontSize = (14 * scale).sp,
        lineHeight = (20 * scale).sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.Normal,
        fontSize = (12 * scale).sp,
        lineHeight = (16 * scale).sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.Medium,
        fontSize = (14 * scale).sp,
        lineHeight = (20 * scale).sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.Medium,
        fontSize = (12 * scale).sp,
        lineHeight = (16 * scale).sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = NotoSansTamil,
        fontWeight = FontWeight.Medium,
        fontSize = (11 * scale).sp,
        lineHeight = (16 * scale).sp,
        letterSpacing = 0.5.sp
    )
)

val Typography = scaledTypography(1f)
