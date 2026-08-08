package com.hc.rzi.ui.library.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hc.rzi.domain.model.QuoteDraft
import com.hc.rzi.domain.model.SaveQuoteResult
import com.hc.rzi.domain.repository.QuoteRepository
import com.hc.rzi.domain.usecase.BookSuggestions
import com.hc.rzi.domain.usecase.DeleteQuote
import com.hc.rzi.domain.usecase.SaveQuote
import com.hc.rzi.domain.usecase.TagSuggestions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuoteEditorViewModel @Inject constructor(
    private val repository: QuoteRepository,
    private val saveQuote: SaveQuote,
    private val deleteQuote: DeleteQuote,
    private val bookSuggestions: BookSuggestions,
    private val tagSuggestions: TagSuggestions,
) : ViewModel() {

    private val _state = MutableStateFlow(QuoteEditorUiState())
    val state: StateFlow<QuoteEditorUiState> = _state.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var loadedId: Long? = Long.MIN_VALUE

    fun load(quoteId: Long?) {
        if (loadedId == quoteId) return
        loadedId = quoteId
        viewModelScope.launch {
            val quote = quoteId?.let { repository.quoteById(it) }
            _state.value = QuoteEditorUiState(
                quoteId = quoteId,
                text = quote?.text.orEmpty(),
                bookName = quote?.bookName.orEmpty(),
                pageText = quote?.pageNumber?.toString().orEmpty(),
                tags = quote?.tags.orEmpty(),
            )
            refreshBookSuggestions("")
            refreshTagSuggestions("")
        }
    }

    fun onTextChange(value: String) {
        _state.value = _state.value.copy(
            text = value,
            errors = _state.value.errors.copy(text = null),
        )
    }

    fun onBookNameChange(value: String) {
        _state.value = _state.value.copy(
            bookName = value,
            errors = _state.value.errors.copy(bookName = null),
        )
        viewModelScope.launch { refreshBookSuggestions(value) }
    }

    fun onPageChange(value: String) {
        if (value.any { !it.isDigit() }) return
        _state.value = _state.value.copy(
            pageText = value,
            errors = _state.value.errors.copy(pageNumber = null),
        )
    }

    fun onTagInputChange(value: String) {
        if (value.endsWith(",")) {
            commitTag(value.dropLast(1))
            return
        }
        _state.value = _state.value.copy(tagInput = value)
        viewModelScope.launch { refreshTagSuggestions(value) }
    }

    fun commitTag(raw: String = _state.value.tagInput) {
        val name = raw.replace(",", "").trim()
        if (name.isEmpty()) {
            _state.value = _state.value.copy(tagInput = "")
            return
        }
        val existing = _state.value.tags
        val next = if (existing.any { it.equals(name, ignoreCase = true) }) existing else existing + name
        _state.value = _state.value.copy(tags = next, tagInput = "")
        viewModelScope.launch { refreshTagSuggestions("") }
    }

    fun removeTag(name: String) {
        _state.value = _state.value.copy(tags = _state.value.tags - name)
    }

    fun save() {
        val current = _state.value
        _state.value = current.copy(isSaving = true)
        viewModelScope.launch {
            val result = saveQuote(
                QuoteDraft(
                    id = current.quoteId,
                    text = current.text,
                    bookName = current.bookName,
                    pageNumber = current.pageText.toIntOrNull(),
                    tags = current.tags,
                )
            )
            _state.value = _state.value.copy(isSaving = false)
            when (result) {
                is SaveQuoteResult.Saved -> _events.send(EditorEvent.Saved)
                is SaveQuoteResult.Duplicate ->
                    _events.send(EditorEvent.Message("This quote already exists"))
                is SaveQuoteResult.Invalid ->
                    _state.value = _state.value.copy(errors = result.errors)
            }
        }
    }

    fun delete() {
        val id = _state.value.quoteId ?: return
        viewModelScope.launch {
            deleteQuote(id)
            _events.send(EditorEvent.Deleted)
        }
    }

    private suspend fun refreshBookSuggestions(prefix: String) {
        _state.value = _state.value.copy(bookSuggestions = bookSuggestions(prefix).first())
    }

    private suspend fun refreshTagSuggestions(prefix: String) {
        val already = _state.value.tags.map { it.lowercase() }.toSet()
        _state.value = _state.value.copy(
            tagSuggestions = tagSuggestions(prefix).first().filter { it.lowercase() !in already },
        )
    }
}
