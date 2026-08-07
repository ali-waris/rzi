package com.rzi.quotes.ui.library.editor

import com.rzi.quotes.domain.model.ValidationErrors

data class QuoteEditorUiState(
    val quoteId: Long? = null,
    val text: String = "",
    val bookName: String = "",
    val pageText: String = "",
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val bookSuggestions: List<String> = emptyList(),
    val tagSuggestions: List<String> = emptyList(),
    val errors: ValidationErrors = ValidationErrors(),
    val isSaving: Boolean = false,
) {
    val isEditing: Boolean get() = quoteId != null
    val title: String get() = if (isEditing) "Edit quote" else "New quote"
    val canSave: Boolean get() = text.isNotBlank() && bookName.isNotBlank() && !isSaving
}

sealed interface EditorEvent {
    data object Saved : EditorEvent
    data object Deleted : EditorEvent
    data class Message(val text: String) : EditorEvent
}
