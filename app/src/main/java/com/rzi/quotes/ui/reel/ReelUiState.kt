package com.rzi.quotes.ui.reel

import com.rzi.quotes.domain.model.Book
import com.rzi.quotes.domain.model.Quote
import com.rzi.quotes.domain.model.ReelFilter
import com.rzi.quotes.domain.model.ReelMode
import com.rzi.quotes.domain.model.TagFilter
import com.rzi.quotes.domain.reel.Deck
import com.rzi.quotes.domain.reel.LinearDeck

data class ReelUiState(
    val deck: Deck = LinearDeck(emptyList()),
    val initialPage: Int = 0,
    val mode: ReelMode = ReelMode.SHUFFLE,
    val filter: ReelFilter = ReelFilter(),
    val quotes: Map<Long, Quote> = emptyMap(),
    val books: List<Book> = emptyList(),
    val tagFilters: List<TagFilter> = emptyList(),
    val isFilterSheetOpen: Boolean = false,
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && deck.size == 0
    val deckKey: String get() = "$mode-${filter.bookId}-${filter.tagIds}-${deck.size}"
}
