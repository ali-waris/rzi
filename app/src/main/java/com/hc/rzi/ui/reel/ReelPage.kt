package com.hc.rzi.ui.reel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hc.rzi.domain.model.Quote
import com.hc.rzi.ui.components.TagChip
import com.hc.rzi.ui.theme.Spacing
import com.hc.rzi.ui.theme.quoteTextStyle
import kotlin.random.Random

private const val MAX_REEL_CHARS = 500

@Composable
fun ReelPage(
    quote: Quote?,
    onTagClick: (String) -> Unit,
    onReadMore: (Quote) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (quote == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val isDark = isSystemInDarkTheme()
    val colors = remember(quote.id, isDark) {
        val palettes = if (isDark) darkPalettes else lightPalettes
        palettes[Random.nextInt(palettes.size)]
    }

    val displayText = if (quote.text.length > MAX_REEL_CHARS) {
        quote.text.take(MAX_REEL_CHARS).trimEnd() + "\u2026"
    } else {
        quote.text
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to colors.top,
                    0.5f to colors.middle,
                    1f to colors.bottom,
                ),
                shape = RoundedCornerShape(Spacing.lg)
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = 112.dp,
                        bottom = Spacing.lg
                    )
                ),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { onReadMore(quote) },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = displayText,
                    style = quoteTextStyle(displayText.length),
                    color = colors.onBackground,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = Int.MAX_VALUE,
                    modifier = Modifier
                        .fillMaxWidth(),
                )
            }
            Text(
                text = buildString {
                    append("\u2014 ")
                    append(quote.bookName)
                    quote.pageNumber?.let { append(", p. $it") }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onBackgroundMuted,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lg),
            )
            if (quote.tags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = Spacing.sm),
                ) {
                    quote.tags.forEach { tag ->
                        TagChip(tag = tag, onClick = { onTagClick(tag) })
                    }
                }
            }
        }
    }
}
