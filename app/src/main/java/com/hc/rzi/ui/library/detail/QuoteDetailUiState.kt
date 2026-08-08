package com.hc.rzi.ui.library.detail

import com.hc.rzi.domain.model.Quote

data class QuoteDetailUiState(
    val quote: Quote? = null,
    val isLoading: Boolean = true,
    val isNotFound: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && !isNotFound && quote == null
}
