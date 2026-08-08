package com.hc.rzi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

val RziTypography = Typography()

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
    return base.copy(fontFamily = FontFamily.Serif)
}
