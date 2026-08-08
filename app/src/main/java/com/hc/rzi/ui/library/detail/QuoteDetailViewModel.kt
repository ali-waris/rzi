package com.hc.rzi.ui.library.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hc.rzi.domain.repository.QuoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuoteDetailViewModel @Inject constructor(
    private val repository: QuoteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(QuoteDetailUiState())
    val state: StateFlow<QuoteDetailUiState> = _state.asStateFlow()

    private var loadedId: Long? = Long.MIN_VALUE

    fun load(quoteId: Long) {
        if (loadedId == quoteId) return
        loadedId = quoteId
        viewModelScope.launch {
            val quote = repository.quoteById(quoteId)
            _state.value = if (quote == null) {
                QuoteDetailUiState(quote = null, isLoading = false, isNotFound = true)
            } else {
                QuoteDetailUiState(quote = quote, isLoading = false)
            }
        }
    }
}
