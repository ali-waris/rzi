package com.hc.rzi.ui.reel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hc.rzi.domain.model.Quote
import com.hc.rzi.ui.components.TagChip
import com.hc.rzi.ui.theme.QUOTE_MAX_LINES
import com.hc.rzi.ui.theme.Spacing
import com.hc.rzi.ui.theme.quoteTextStyle

@Composable
fun ReelPage(
    quote: Quote?,
    onCopy: (Quote) -> Unit,
    onShare: (Quote) -> Unit,
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

    var isClamped by remember(quote.id) { mutableStateOf(false) }

    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to pageTopColor(quote.bookName),
                    1f to scheme.primaryContainer,
                )
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(start = Spacing.lg, end = Spacing.lg, top = 112.dp, bottom = 104.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "\u201C",
                style = MaterialTheme.typography.displayLarge,
                color = scheme.primary.copy(alpha = 0.4f),
            )
            Text(
                text = quote.text,
                style = quoteTextStyle(quote.text.length),
                textAlign = TextAlign.Center,
                maxLines = QUOTE_MAX_LINES,
                onTextLayout = { layout -> isClamped = layout.hasVisualOverflow },
                modifier = Modifier.padding(top = Spacing.md),
            )
            if (isClamped) {
                TextButton(
                    onClick = { onReadMore(quote) },
                    modifier = Modifier.padding(top = Spacing.sm),
                ) { Text("Read more") }
            }
            Text(
                text = buildString {
                    append("\u2014 ")
                    append(quote.bookName)
                    quote.pageNumber?.let { append(", p. $it") }
                },
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.lg),
            )
            if (quote.tags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.padding(top = Spacing.md),
                ) {
                    quote.tags.take(3).forEach { tag ->
                        TagChip(tag = tag, onClick = { onTagClick(tag) })
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FilledTonalButton(
                onClick = { onCopy(quote) },
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Copy")
            }
            FilledTonalButton(
                onClick = { onShare(quote) },
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Share")
            }
        }
    }
}

@Composable
private fun pageTopColor(bookName: String): Color {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.surfaceContainerLow,
        scheme.surfaceContainer,
        scheme.surfaceContainerHigh,
        scheme.surfaceContainerHighest,
        scheme.secondaryContainer,
        scheme.tertiaryContainer,
    )
    return palette[bookName.lowercase().hashCode().mod(palette.size)]
}
