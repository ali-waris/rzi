package com.hc.rzi.ui.reel

import com.hc.rzi.domain.model.Book
import com.hc.rzi.domain.model.Quote
import com.hc.rzi.domain.model.ReelFilter
import com.hc.rzi.domain.model.ReelMode
import com.hc.rzi.domain.model.TagFilter
import com.hc.rzi.domain.reel.Deck
import com.hc.rzi.domain.reel.LinearDeck

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
    val isAdmin: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && deck.size == 0
    val deckKey: String get() = "$mode-${filter.bookId}-${filter.tagIds}-${deck.size}"
}
