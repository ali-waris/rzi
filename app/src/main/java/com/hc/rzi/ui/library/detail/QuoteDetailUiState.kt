package com.hc.rzi.ui.library.detail

import com.hc.rzi.domain.model.Quote
import com.hc.rzi.domain.model.ValidationErrors

data class QuoteDetailUiState(
    val quote: Quote? = null,
    val isLoading: Boolean = true,
    val isNotFound: Boolean = false,
    val isAdmin: Boolean = false,
    val isEditing: Boolean = false,
    val text: String = "",
    val bookName: String = "",
    val pageText: String = "",
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val bookSuggestions: List<String> = emptyList(),
    val tagSuggestions: List<String> = emptyList(),
    val allKnownTags: List<String> = emptyList(),
    val errors: ValidationErrors = ValidationErrors(),
    val isSaving: Boolean = false,
) {
    val canSave: Boolean get() = text.isNotBlank() && bookName.isNotBlank() && !isSaving
}

sealed interface QuoteDetailEvent {
    data class Saved(val id: Long) : QuoteDetailEvent
    data object Deleted : QuoteDetailEvent
    data class Message(val text: String) : QuoteDetailEvent
}
