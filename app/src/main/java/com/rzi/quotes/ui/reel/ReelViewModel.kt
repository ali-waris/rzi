package com.rzi.quotes.ui.reel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzi.quotes.domain.model.ReelFilter
import com.rzi.quotes.domain.model.ReelMode
import com.rzi.quotes.domain.repository.QuoteRepository
import com.rzi.quotes.domain.repository.ReelStateStore
import com.rzi.quotes.domain.usecase.ObserveReelDeck
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReelViewModel @Inject constructor(
    private val repository: QuoteRepository,
    private val store: ReelStateStore,
    observeReelDeck: ObserveReelDeck,
) : ViewModel() {

    private val _state = MutableStateFlow(ReelUiState())
    val state: StateFlow<ReelUiState> = _state.asStateFlow()

    init {
        observeReelDeck()
            .onEach { deckState ->
                _state.value = _state.value.copy(
                    deck = deckState.deck,
                    initialPage = startPageFor(deckState.deck.size, deckState.index),
                    mode = deckState.mode,
                    filter = deckState.filter,
                    isLoading = false,
                )
                prefetchAround(startPageFor(deckState.deck.size, deckState.index))
            }
            .launchIn(viewModelScope)

        repository.observeBooks()
            .onEach { books -> _state.value = _state.value.copy(books = books) }
            .launchIn(viewModelScope)

        repository.observeTagFilters()
            .onEach { tags -> _state.value = _state.value.copy(tagFilters = tags) }
            .launchIn(viewModelScope)
    }

    fun onPageSettled(page: Int) {
        val deck = _state.value.deck
        if (deck.size == 0) return
        val quoteId = deck.idAt(page)
        viewModelScope.launch {
            store.update { it.copy(absoluteIndex = page, currentQuoteId = quoteId) }
        }
        prefetchAround(page)
    }

    fun toggleMode() {
        val nextMode =
            if (_state.value.mode == ReelMode.SHUFFLE) ReelMode.LINEAR else ReelMode.SHUFFLE
        viewModelScope.launch { store.update { it.copy(mode = nextMode) } }
    }

    fun openFilterSheet() { _state.value = _state.value.copy(isFilterSheetOpen = true) }

    fun closeFilterSheet() { _state.value = _state.value.copy(isFilterSheetOpen = false) }

    fun applyFilter(filter: ReelFilter) {
        viewModelScope.launch {
            store.update { it.copy(filter = filter, absoluteIndex = 0, currentQuoteId = null) }
        }
        closeFilterSheet()
    }

    fun clearFilter() = applyFilter(ReelFilter())

    fun filterByTag(tagName: String) {
        val tagId = _state.value.tagFilters.firstOrNull { it.name == tagName }?.id ?: return
        applyFilter(ReelFilter(tagIds = listOf(tagId)))
    }

    private fun prefetchAround(page: Int) {
        val deck = _state.value.deck
        if (deck.size == 0) return
        viewModelScope.launch {
            val wanted = (page - PREFETCH..page + PREFETCH).map { deck.idAt(it) }.distinct()
            val known = _state.value.quotes
            val fetched = wanted.filter { it !in known }.mapNotNull { repository.quoteById(it) }
            if (fetched.isEmpty()) return@launch
            val merged = known + fetched.associateBy { it.id }
            val retained = if (merged.size <= CACHE_LIMIT) merged else {
                merged.filterKeys { it in wanted } +
                    merged.entries.take(CACHE_LIMIT - wanted.size)
                        .associate { it.key to it.value }
            }
            _state.value = _state.value.copy(quotes = retained)
        }
    }

    private fun startPageFor(size: Int, index: Int): Int {
        if (size == 0) return 0
        val initialCycle = minOf(1_000, (Int.MAX_VALUE / 2) / size)
        return initialCycle * size + index.mod(size)
    }

    private companion object {
        const val PREFETCH = 3
        const val CACHE_LIMIT = 60
    }
}
