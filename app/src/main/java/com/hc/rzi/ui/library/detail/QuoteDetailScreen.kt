package com.hc.rzi.ui.library.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hc.rzi.domain.model.Quote
import com.hc.rzi.ui.components.EmptyState
import com.hc.rzi.ui.components.TagChip
import com.hc.rzi.ui.theme.Spacing
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
    var showInfoSheet by remember { mutableStateOf(false) }
    var textAlign by remember { mutableStateOf(TextAlign.Center) }

    LaunchedEffect(quoteId) { viewModel.load(quoteId) }

    val quote = state.quote
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                actions = {
                    IconButton(onClick = { textAlign = TextAlign.Start }) {
                        Icon(
                            Icons.AutoMirrored.Filled.FormatAlignLeft,
                            contentDescription = "Align left",
                            tint = if (textAlign == TextAlign.Start)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { textAlign = TextAlign.Center }) {
                        Icon(
                            Icons.Filled.FormatAlignCenter,
                            contentDescription = "Align center",
                            tint = if (textAlign == TextAlign.Center)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { textAlign = TextAlign.End }) {
                        Icon(
                            Icons.AutoMirrored.Filled.FormatAlignRight,
                            contentDescription = "Align right",
                            tint = if (textAlign == TextAlign.End)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (quote != null) {
                QuoteBottomBar(quote = quote, onExpand = { showInfoSheet = true })
            }
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

                quote != null -> QuoteContent(quote = quote, textAlign = textAlign)
            }
        }
    }

    if (showInfoSheet && quote != null) {
        QuoteInfoSheet(
            quote = quote,
            sheetState = sheetState,
            onDismiss = { showInfoSheet = false },
            onCopy = { copyQuote(context, it) },
            onShare = { shareQuote(context, it) },
        )
    }
}

@Composable
private fun QuoteContent(quote: Quote, textAlign: TextAlign = TextAlign.Start) {
    val scheme = MaterialTheme.colorScheme
    var fontScale by remember { mutableFloatStateOf(1f) }
    val baseStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pinchToZoom(
                onZoom = { scale ->
                    fontScale = (fontScale * scale).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
                },
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = quote.text,
                style = baseStyle.copy(
                    fontSize = baseStyle.fontSize * fontScale,
                    lineHeight = baseStyle.lineHeight * fontScale,
                ),
                textAlign = textAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md),
            )
        }
    }
}

private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) {
                onZoom(event.calculateZoom())
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

private const val MIN_FONT_SCALE = 0.5f
private const val MAX_FONT_SCALE = 3f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuoteBottomBar(quote: Quote, onExpand: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onExpand,
        modifier = Modifier.fillMaxWidth(),
        color = scheme.surfaceContainerLow,
        contentColor = scheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        append(quote.bookName)
                        quote.pageNumber?.let { append(", p. $it") }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (quote.tags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.padding(top = Spacing.xs),
                    ) {
                        quote.tags.take(3).forEach { tag ->
                            TagChip(tag = tag)
                        }
                    }
                }
            }
            Icon(
                Icons.Filled.ExpandLess,
                contentDescription = "Show details",
                tint = scheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuoteInfoSheet(
    quote: Quote,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    onCopy: (Quote) -> Unit,
    onShare: (Quote) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
        ) {
            Text(
                text = quote.bookName,
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
            )
            quote.pageNumber?.let {
                Text(
                    text = "Page $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            if (quote.tags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.padding(top = Spacing.md),
                ) {
                    quote.tags.forEach { tag -> TagChip(tag = tag) }
                }
            }

            Spacer(Modifier.height(Spacing.lg))

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

            Spacer(Modifier.height(Spacing.lg))

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
        }
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
