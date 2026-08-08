package com.hc.rzi.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.hc.rzi.domain.model.ImportOutcome
import com.hc.rzi.domain.model.Quote
import com.hc.rzi.domain.model.QuoteDraft
import com.hc.rzi.domain.repository.AdminRepository
import com.hc.rzi.domain.repository.QuoteRepository
import com.hc.rzi.domain.usecase.DeleteQuote
import com.hc.rzi.domain.usecase.ExportDatabase
import com.hc.rzi.domain.usecase.ImportDatabase
import com.hc.rzi.domain.usecase.SaveQuote
import com.hc.rzi.domain.usecase.SearchQuotes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class SearchKey(val query: String, val tagIds: List<Long>)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: QuoteRepository,
    private val searchQuotes: SearchQuotes,
    private val deleteQuote: DeleteQuote,
    private val saveQuote: SaveQuote,
    private val importDatabaseUseCase: ImportDatabase,
    private val exportDatabaseUseCase: ExportDatabase,
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var lastDeleted: Quote? = null

    private val searchKey: Flow<SearchKey> = _state
        .map { SearchKey(it.query, it.selectedTagIds) }
        .distinctUntilChanged()
        .debounce(250)

    val quotes: Flow<PagingData<Quote>> = searchKey
        .flatMapLatest { key -> searchQuotes(key.query, key.tagIds) }
        .cachedIn(viewModelScope)

    init {
        repository.observeTagFilters()
            .onEach { filters -> _state.value = _state.value.copy(tagFilters = filters) }
            .launchIn(viewModelScope)

        repository.observeQuoteCount()
            .onEach { total -> _state.value = _state.value.copy(totalCount = total) }
            .launchIn(viewModelScope)

        searchKey
            .flatMapLatest { key -> repository.observeMatchCount(key.query, key.tagIds) }
            .onEach { count -> _state.value = _state.value.copy(matchCount = count) }
            .launchIn(viewModelScope)

        adminRepository.session
            .onEach { isAdmin -> _state.value = _state.value.copy(isAdmin = isAdmin) }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun onTagToggle(tagId: Long) {
        val selected = _state.value.selectedTagIds
        _state.value = _state.value.copy(
            selectedTagIds = if (tagId in selected) selected - tagId else selected + tagId,
        )
    }

    fun openEditor(quoteId: Long?) {
        _state.value = _state.value.copy(editorQuoteId = quoteId, isEditorOpen = true)
    }

    fun closeEditor() {
        _state.value = _state.value.copy(isEditorOpen = false, editorQuoteId = null)
    }

    fun lock() {
        adminRepository.lock()
    }

    fun openAdminDialog() {
        _state.update { it.copy(isPinDialogOpen = true) }
    }

    fun closeAdminDialog() {
        _state.update { it.copy(isPinDialogOpen = false) }
    }

    fun openChangePin() {
        _state.update { it.copy(isChangePinOpen = true) }
    }

    fun closeChangePin() {
        _state.update { it.copy(isChangePinOpen = false) }
    }

    fun delete(quote: Quote) {
        lastDeleted = quote
        viewModelScope.launch {
            deleteQuote(quote.id)
            _messages.send(DELETE_MESSAGE)
        }
    }

    fun undoDelete() {
        val quote = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            saveQuote(
                QuoteDraft(
                    text = quote.text,
                    bookName = quote.bookName,
                    pageNumber = quote.pageNumber,
                    tags = quote.tags,
                )
            )
        }
    }

    fun showMessage(text: String) {
        viewModelScope.launch { _messages.send(text) }
    }

    fun importDatabase(uriString: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isTransferInProgress = true)
            try {
                when (val outcome = importDatabaseUseCase(uriString)) {
                    is ImportOutcome.Success -> {
                        val r = outcome.result
                        _messages.send("Imported ${r.added} quotes (${r.skippedDuplicates} duplicates, ${r.skippedInvalid} invalid skipped)")
                    }
                    is ImportOutcome.Failure -> {
                        _messages.send("Import failed: ${outcome.reason.name}")
                    }
                }
            } finally {
                _state.value = _state.value.copy(isTransferInProgress = false)
            }
        }
    }

    fun exportDatabase(uriString: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isTransferInProgress = true)
            try {
                when (val outcome = exportDatabaseUseCase(uriString)) {
                    is com.hc.rzi.domain.model.ExportOutcome.Success -> {
                        _messages.send("Export complete")
                    }
                    is com.hc.rzi.domain.model.ExportOutcome.Failure -> {
                        _messages.send("Export failed: ${outcome.reason.name}")
                    }
                }
            } finally {
                _state.value = _state.value.copy(isTransferInProgress = false)
            }
        }
    }

    companion object {
        const val DELETE_MESSAGE = "Deleted"
    }
}
