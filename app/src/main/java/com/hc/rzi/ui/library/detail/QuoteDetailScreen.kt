package com.hc.rzi.ui.library.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hc.rzi.domain.model.Quote
import com.hc.rzi.ui.components.EmptyState
import com.hc.rzi.ui.components.TagChip
import com.hc.rzi.ui.theme.Spacing
import com.hc.rzi.ui.theme.quoteTextStyle
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteDetailScreen(
    quoteId: Long,
    onBack: () -> Unit,
    viewModel: QuoteDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(quoteId) { viewModel.load(quoteId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.quote?.bookName ?: "Quote", maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                state.isNotFound -> EmptyState(
                    title = "Quote not found",
                    body = "This quote may have been removed from your library.",
                    actionLabel = "Back to library",
                    onAction = onBack,
                )

                state.quote != null -> QuoteDetailContent(
                    quote = state.quote!!,
                    onCopy = { copyQuote(context, it) },
                    onShare = { shareQuote(context, it) },
                )
            }
        }
    }
}

@Composable
private fun QuoteDetailContent(
    quote: Quote,
    onCopy: (Quote) -> Unit,
    onShare: (Quote) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "\u201C",
            style = MaterialTheme.typography.displayLarge,
            color = scheme.primary.copy(alpha = 0.4f),
        )
        Text(
            text = quote.text,
            style = quoteTextStyle(quote.text.length),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.md),
        )
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
                quote.tags.forEach { tag -> TagChip(tag = tag) }
            }
        }

        Spacer(Modifier.height(Spacing.xl))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text("Details", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.sm))
                DetailRow(label = "Book", value = quote.bookName)
                quote.pageNumber?.let {
                    DetailRow(label = "Page", value = it.toString())
                }
                if (quote.tags.isNotEmpty()) {
                    DetailRow(label = "Tags", value = quote.tags.joinToString(", "))
                }
                DetailRow(label = "Added", value = formatDate(quote.createdAt))
                DetailRow(label = "Last edited", value = formatDate(quote.updatedAt))
            }
        }

        Spacer(Modifier.height(Spacing.xl))

        Row(
            modifier = Modifier.fillMaxWidth(),
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

        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

private fun quoteAsText(quote: Quote): String = buildString {
    append(quote.text)
    append("\n— ")
    append(quote.bookName)
    quote.pageNumber?.let { append(", p. $it") }
}

private fun copyQuote(context: Context, quote: Quote) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Quote", quoteAsText(quote)))
}

private fun shareQuote(context: Context, quote: Quote) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, quoteAsText(quote))
    }
    context.startActivity(Intent.createChooser(intent, null))
}
