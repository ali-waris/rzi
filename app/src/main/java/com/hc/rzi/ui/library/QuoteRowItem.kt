package com.hc.rzi.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hc.rzi.domain.model.Quote
import com.hc.rzi.ui.theme.Spacing

@Composable
fun QuoteRowItem(
    quote: Quote,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("\u201C", style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.width(Spacing.sm + 4.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = highlight(quote.text, query),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(quote.bookName)
                        quote.pageNumber?.let { append(" \u00B7 p. $it") }
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
                if (quote.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        quote.tags.take(3).forEach { tag ->
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = scheme.surfaceContainerHighest,
                                contentColor = scheme.onSurfaceVariant,
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun highlight(text: String, query: String): AnnotatedString {
    val tokens = query.trim().split(' ').filter { it.length > 1 }
    if (tokens.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        tokens.forEach { token ->
            val start = text.indexOf(token, ignoreCase = true)
            if (start >= 0) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + token.length)
            }
        }
    }
}
