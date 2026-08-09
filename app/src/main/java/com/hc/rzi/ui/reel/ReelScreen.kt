package com.hc.rzi.ui.reel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hc.rzi.domain.model.Quote
import com.hc.rzi.domain.model.ReelMode
import com.hc.rzi.ui.components.EmptyState
import com.hc.rzi.ui.theme.Elevation
import com.hc.rzi.ui.theme.RziPill
import com.hc.rzi.ui.theme.Spacing
import kotlin.math.absoluteValue

@Composable
fun ReelScreen(
    onAddQuote: () -> Unit,
    onReadMore: (Long) -> Unit,
    viewModel: ReelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (state.isEmpty) {
        if (state.isAdmin) {
            EmptyState(
                title = "No quotes yet",
                body = "Start your collection and watch your reel come to life.",
                actionLabel = "Add your first quote",
                onAction = onAddQuote,
            )
        } else {
            EmptyState(title = "No quotes yet")
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        key(state.deckKey) {
            val pagerState = rememberPagerState(
                initialPage = state.initialPage,
                pageCount = { if (state.deck.size == 0) 0 else Int.MAX_VALUE },
            )

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect(viewModel::onPageSettled)
            }

            VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val quoteId = state.deck.idAt(page)
                val offset = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction)
                    .absoluteValue
                    .coerceIn(0f, 1f)
                ReelPage(
                    quote = state.quotes[quoteId],
                    onCopy = { copyQuote(context, it) },
                    onShare = { shareQuote(context, it) },
                    onTagClick = viewModel::filterByTag,
                    onReadMore = { quote -> onReadMore(quote.id) },
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - offset * 0.5f
                        val scale = 1f - offset * 0.05f
                        scaleX = scale
                        scaleY = scale
                    },
                )
            }
        }

        ReelToolbar(
            mode = state.mode,
            isFiltered = state.filter.isActive,
            onOpenFilter = viewModel::openFilterSheet,
            onToggleMode = viewModel::toggleMode,
            onClearFilter = viewModel::clearFilter,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(Spacing.sm),
        )
    }

    if (state.isFilterSheetOpen) {
        ReelFilterSheet(
            books = state.books,
            tagFilters = state.tagFilters,
            current = state.filter,
            onApply = viewModel::applyFilter,
            onDismiss = viewModel::closeFilterSheet,
        )
    }
}

@Composable
private fun ReelToolbar(
    mode: ReelMode,
    isFiltered: Boolean,
    onOpenFilter: () -> Unit,
    onToggleMode: () -> Unit,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RziPill.full,
        color = scheme.surfaceContainer,
        tonalElevation = Elevation.level1,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
        ) {
            IconButton(onClick = onOpenFilter) {
                Icon(
                    Icons.Filled.FilterList,
                    contentDescription = "Filter the reel",
                    tint = if (isFiltered) scheme.primary else LocalContentColor.current,
                )
            }
            IconToggleButton(checked = mode == ReelMode.SHUFFLE, onCheckedChange = { onToggleMode() }) {
                if (mode == ReelMode.SHUFFLE) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffled order",
                        tint = scheme.primary,
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Book order")
                }
            }
            if (isFiltered) {
                Surface(
                    onClick = onClearFilter,
                    shape = RziPill.full,
                    color = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    ) {
                        Text("Filtered", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear filter",
                            modifier = Modifier.width(16.dp),
                        )
                    }
                }
            }
        }
    }
}

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
