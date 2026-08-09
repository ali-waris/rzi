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

private val Nastaleeq = FontFamily(Font(R.font.jameel_noori_nastaleeq))
private val Sans = FontFamily.SansSerif

val RziTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Nastaleeq, fontWeight = FontWeight.Normal,
        fontSize = 57.sp, lineHeight = 48.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Nastaleeq, fontWeight = FontWeight.Normal,
        fontSize = 45.sp, lineHeight = 48.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Nastaleeq, fontWeight = FontWeight.Normal,
        fontSize = 36.sp, lineHeight = 48.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Nastaleeq, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 48.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Nastaleeq, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 48.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Nastaleeq, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 48.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Nastaleeq, fontWeight = FontWeight.Normal,
        fontSize = 22.sp, lineHeight = 48.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Nastaleeq, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 44.sp, letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp,
    ),
)

@Composable
@ReadOnlyComposable
fun quoteTextStyle(charCount: Int): TextStyle {
    val base = when {
        charCount <= 80 -> MaterialTheme.typography.displayLarge
        charCount <= 160 -> MaterialTheme.typography.displayMedium
        charCount <= 300 -> MaterialTheme.typography.displaySmall
        charCount <= 500 -> MaterialTheme.typography.headlineLarge
        else -> MaterialTheme.typography.headlineMedium
    }
    return base.copy(fontFamily = Nastaleeq)
}
