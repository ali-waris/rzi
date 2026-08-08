package com.hc.rzi.ui.library

import com.hc.rzi.domain.model.TagFilter

data class LibraryUiState(
    val query: String = "",
    val tagFilters: List<TagFilter> = emptyList(),
    val selectedTagIds: List<Long> = emptyList(),
    val matchCount: Int = 0,
    val totalCount: Int = 0,
    val editorQuoteId: Long? = null,
    val isEditorOpen: Boolean = false,
    val isTransferInProgress: Boolean = false,
    val isAdmin: Boolean = false,
    val isPinDialogOpen: Boolean = false,
    val isChangePinOpen: Boolean = false,
) {
    val isSearching: Boolean get() = query.isNotBlank() || selectedTagIds.isNotEmpty()

    val countLabel: String get() = if (isSearching) {
        "$matchCount ${if (matchCount == 1) "result" else "results"}"
    } else {
        "$totalCount ${if (totalCount == 1) "quote" else "quotes"}"
    }
}
