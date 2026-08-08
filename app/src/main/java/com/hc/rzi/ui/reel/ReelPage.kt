package com.hc.rzi.ui.reel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hc.rzi.domain.model.Quote
import com.hc.rzi.ui.theme.QUOTE_MAX_LINES
import com.hc.rzi.ui.theme.quoteTextStyle

@Composable
fun ReelPage(
    quote: Quote?,
    onCopy: (Quote) -> Unit,
    onShare: (Quote) -> Unit,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (quote == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var showFullText by remember(quote.id) { mutableStateOf(false) }
    var isClamped by remember(quote.id) { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(pageColor(quote.bookName))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = quote.text,
                style = quoteTextStyle(quote.text.length),
                textAlign = TextAlign.Center,
                maxLines = QUOTE_MAX_LINES,
                onTextLayout = { layout -> isClamped = layout.hasVisualOverflow },
            )
            if (isClamped) {
                TextButton(onClick = { showFullText = true }) { Text("Read more") }
            }
            Text(
                text = buildString {
                    append(quote.bookName)
                    quote.pageNumber?.let { append(" · p. $it") }
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
            if (quote.tags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    quote.tags.take(3).forEach { tag ->
                        AssistChip(onClick = { onTagClick(tag) }, label = { Text(tag) })
                    }
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = { onCopy(quote) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy quote")
            }
            IconButton(onClick = { onShare(quote) }) {
                Icon(Icons.Filled.Share, contentDescription = "Share quote")
            }
        }
    }

    if (showFullText) {
        AlertDialog(
            onDismissRequest = { showFullText = false },
            confirmButton = {
                TextButton(onClick = { showFullText = false }) { Text("Close") }
            },
            title = { Text(quote.bookName) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(quote.text)
                }
            },
        )
    }
}

@Composable
private fun pageColor(bookName: String): Color {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.surfaceContainerLowest,
        scheme.surfaceContainerLow,
        scheme.surfaceContainer,
        scheme.surfaceContainerHigh,
        scheme.surfaceContainerHighest,
        scheme.secondaryContainer,
    )
    return palette[bookName.lowercase().hashCode().mod(palette.size)]
}
