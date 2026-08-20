package com.hc.rzi.ui.reel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hc.rzi.domain.model.ReelFilter
import com.hc.rzi.domain.model.ReelMode
import com.hc.rzi.domain.repository.AdminRepository
import com.hc.rzi.domain.repository.QuoteRepository
import com.hc.rzi.domain.repository.ReelStateStore
import com.hc.rzi.domain.usecase.ObserveReelDeck
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
    private val adminRepository: AdminRepository,
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

        adminRepository.session
            .onEach { isAdmin -> _state.value = _state.value.copy(isAdmin = isAdmin) }
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

    fun openBookSheet() { _state.value = _state.value.copy(isBookSheetOpen = true) }

    fun closeBookSheet() { _state.value = _state.value.copy(isBookSheetOpen = false) }

    fun openTagSheet() { _state.value = _state.value.copy(isTagSheetOpen = true) }

    fun closeTagSheet() { _state.value = _state.value.copy(isTagSheetOpen = false) }

    fun onBookToggle(bookId: Long) {
        val current = _state.value.filter
        val newIds = if (bookId in current.bookIds) current.bookIds - bookId else current.bookIds + bookId
        val newFilter = current.copy(bookIds = newIds)
        viewModelScope.launch {
            store.update { it.copy(filter = newFilter, absoluteIndex = 0, currentQuoteId = null) }
        }
    }

    fun clearBookFilter() {
        viewModelScope.launch {
            store.update { it.copy(filter = it.filter.copy(bookIds = emptyList()), absoluteIndex = 0, currentQuoteId = null) }
        }
    }

    fun onTagToggle(tagId: Long) {
        val current = _state.value.filter
        val newIds = if (tagId in current.tagIds) current.tagIds - tagId else current.tagIds + tagId
        val newFilter = current.copy(tagIds = newIds)
        viewModelScope.launch {
            store.update { it.copy(filter = newFilter, absoluteIndex = 0, currentQuoteId = null) }
        }
    }

    fun clearTagFilter() {
        viewModelScope.launch {
            store.update { it.copy(filter = it.filter.copy(tagIds = emptyList()), absoluteIndex = 0, currentQuoteId = null) }
        }
    }

    fun applyFilter(filter: ReelFilter) {
        viewModelScope.launch {
            store.update { it.copy(filter = filter, absoluteIndex = 0, currentQuoteId = null) }
        }
        _state.value = _state.value.copy(isBookSheetOpen = false, isTagSheetOpen = false)
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
