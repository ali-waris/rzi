package com.hc.rzi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hc.rzi.R

private val Serif = FontFamily(Font(R.font.noto_nastaliq_urdu))
private val Sans = FontFamily.SansSerif

val RziTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.Normal,
        fontSize = 57.sp, lineHeight = 104.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.Normal,
        fontSize = 45.sp, lineHeight = 86.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.Normal,
        fontSize = 36.sp, lineHeight = 70.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp, lineHeight = 64.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 58.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 50.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.Medium,
        fontSize = 22.sp, lineHeight = 46.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 36.sp, letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
)

const val QUOTE_MAX_LINES = 14

@Composable
@ReadOnlyComposable
fun quoteTextStyle(charCount: Int): TextStyle {
    val base = when {
        charCount <= 120 -> MaterialTheme.typography.displaySmall
        charCount <= 300 -> MaterialTheme.typography.headlineSmall
        charCount <= 600 -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.bodyLarge
    }
    return base.copy(fontFamily = Serif)
}
