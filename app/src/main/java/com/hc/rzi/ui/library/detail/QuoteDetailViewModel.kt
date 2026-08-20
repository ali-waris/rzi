package com.hc.rzi.ui.library.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hc.rzi.domain.model.QuoteDraft
import com.hc.rzi.domain.model.SaveQuoteResult
import com.hc.rzi.domain.model.ValidationErrors
import com.hc.rzi.domain.repository.AdminRepository
import com.hc.rzi.domain.repository.QuoteRepository
import com.hc.rzi.domain.usecase.AllTagNames
import com.hc.rzi.domain.usecase.BookSuggestions
import com.hc.rzi.domain.usecase.DeleteQuote
import com.hc.rzi.domain.usecase.SaveQuote
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuoteDetailViewModel @Inject constructor(
    private val repository: QuoteRepository,
    private val saveQuote: SaveQuote,
    private val deleteQuote: DeleteQuote,
    private val bookSuggestions: BookSuggestions,
    private val allTagNames: AllTagNames,
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(QuoteDetailUiState())
    val state: StateFlow<QuoteDetailUiState> = _state.asStateFlow()

    private val _events = Channel<QuoteDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var bookSuggestionsJob: Job? = null
    private var lastBookNameChangeTime: Long = 0

    private var loadedId: Long? = Long.MIN_VALUE

    init {
        adminRepository.session
            .onEach { isAdmin -> _state.value = _state.value.copy(isAdmin = isAdmin) }
            .launchIn(viewModelScope)
    }

    fun load(quoteId: Long?) {
        if (loadedId == quoteId) return
        loadedId = quoteId
        viewModelScope.launch {
            if (quoteId == null) {
                _state.value = _state.value.copy(isLoading = false, isEditing = true)
                refreshBookSuggestions("")
                refreshAllKnownTags()
                return@launch
            }
            val quote = repository.quoteById(quoteId)
            _state.value = if (quote == null) {
                _state.value.copy(quote = null, isLoading = false, isNotFound = true)
            } else {
                _state.value.copy(
                    quote = quote,
                    isLoading = false,
                    isEditing = false,
                    text = quote.text,
                    bookName = quote.bookName,
                    pageText = quote.pageNumber?.toString().orEmpty(),
                    tags = quote.tags,
                )
            }
            refreshBookSuggestions("")
            refreshAllKnownTags()
        }
    }

    fun startEdit() {
        val quote = _state.value.quote ?: return
        _state.value = _state.value.copy(
            isEditing = true,
            text = quote.text,
            bookName = quote.bookName,
            pageText = quote.pageNumber?.toString().orEmpty(),
            tags = quote.tags,
            errors = ValidationErrors(),
        )
        viewModelScope.launch { refreshAllKnownTags() }
    }

    fun cancelEdit() {
        val quote = _state.value.quote
        _state.value = _state.value.copy(
            isEditing = false,
            text = quote?.text.orEmpty(),
            bookName = quote?.bookName.orEmpty(),
            pageText = quote?.pageNumber?.toString().orEmpty(),
            tags = quote?.tags.orEmpty(),
            tagInput = "",
            errors = ValidationErrors(),
        )
        viewModelScope.launch { refreshAllKnownTags() }
    }

    fun onTextChange(value: String) {
        _state.value = _state.value.copy(text = value, errors = _state.value.errors.copy(text = null))
    }

    fun onBookNameChange(value: String) {
        _state.value = _state.value.copy(bookName = value, errors = _state.value.errors.copy(bookName = null))
        bookSuggestionsJob?.cancel()
        lastBookNameChangeTime = System.currentTimeMillis()
        bookSuggestionsJob = viewModelScope.launch {
            delay(400)
            if (System.currentTimeMillis() - lastBookNameChangeTime >= 400) {
                refreshBookSuggestions(value)
            }
        }
    }

    fun onPageChange(value: String) {
        if (value.any { !it.isDigit() }) return
        _state.value = _state.value.copy(pageText = value, errors = _state.value.errors.copy(pageNumber = null))
    }

    fun onTagInputChange(value: String) {
        if (value.endsWith(",")) {
            commitTag(value.dropLast(1))
            return
        }
        _state.value = _state.value.copy(tagInput = value)
    }

    fun commitTag(raw: String = _state.value.tagInput) {
        val name = raw.replace(",", "").trim()
        if (name.isEmpty()) {
            _state.value = _state.value.copy(tagInput = "")
            return
        }
        val existing = _state.value.tags
        val next = if (existing.any { it.equals(name, ignoreCase = true) }) existing else existing + name
        val currentAllKnown = _state.value.allKnownTags
        val updatedAllKnown = if (currentAllKnown.any { it.equals(name, ignoreCase = true) }) {
            currentAllKnown
        } else {
            currentAllKnown + name
        }
        _state.value = _state.value.copy(
            tags = next,
            tagInput = "",
            allKnownTags = updatedAllKnown,
        )
        viewModelScope.launch { refreshAllKnownTags() }
    }

    fun removeTag(name: String) {
        _state.value = _state.value.copy(tags = _state.value.tags - name)
    }

    fun toggleTag(name: String) {
        val current = _state.value.tags
        val next = if (current.any { it.equals(name, ignoreCase = true) }) {
            current.filter { !it.equals(name, ignoreCase = true) }
        } else {
            current + name
        }
        _state.value = _state.value.copy(tags = next)
    }

    fun save() {
        val current = _state.value

        if (current.text.isBlank()) {
            val existingId = current.quote?.id
            viewModelScope.launch {
                if (existingId != null) {
                    deleteQuote(existingId)
                }
                _events.send(QuoteDetailEvent.Deleted)
            }
            return
        }

        if (current.bookName.isBlank()) {
            _state.value = current.copy(errors = ValidationErrors(bookName = "Book name is required"))
            return
        }

        _state.value = current.copy(isSaving = true)
        viewModelScope.launch {
            val result = saveQuote(
                QuoteDraft(
                    id = current.quote?.id,
                    text = current.text,
                    bookName = current.bookName,
                    pageNumber = current.pageText.toIntOrNull(),
                    tags = current.tags,
                ),
            )
            _state.value = _state.value.copy(isSaving = false)
            when (result) {
                is SaveQuoteResult.Saved -> {
                    val saved = repository.quoteById(result.id)
                    _state.value = _state.value.copy(
                        quote = saved,
                        isEditing = false,
                        text = saved?.text.orEmpty(),
                        bookName = saved?.bookName.orEmpty(),
                        pageText = saved?.pageNumber?.toString().orEmpty(),
                        tags = saved?.tags.orEmpty(),
                        tagInput = "",
                        errors = ValidationErrors(),
                    )
                    _events.send(QuoteDetailEvent.Saved(result.id))
                }
                is SaveQuoteResult.Duplicate ->
                    _events.send(QuoteDetailEvent.Message("This quote already exists"))
                is SaveQuoteResult.Invalid ->
                    _state.value = _state.value.copy(errors = result.errors)
            }
        }
    }

    fun delete() {
        val id = _state.value.quote?.id ?: return
        viewModelScope.launch {
            deleteQuote(id)
            _events.send(QuoteDetailEvent.Deleted)
        }
    }

    private suspend fun refreshBookSuggestions(prefix: String) {
        _state.value = _state.value.copy(bookSuggestions = bookSuggestions(prefix).first())
    }

    private suspend fun refreshAllKnownTags() {
        val dbTags = allTagNames().first()
        val currentAllKnown = _state.value.allKnownTags
        val merged = (currentAllKnown + dbTags).distinctBy { it.lowercase() }.sorted()
        _state.value = _state.value.copy(allKnownTags = merged)
    }
}
