package com.hc.rzi.ui.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.hc.rzi.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onQuoteClick: (Long?) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val quotes = viewModel.quotes.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    var overflowOpen by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = state.isSelectionMode) {
        viewModel.clearSelection()
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importDatabase(it.toString()) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportDatabase(it.toString()) }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val isDeleteMessage = message == LibraryViewModel.DELETE_MESSAGE || message.endsWith("deleted")
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (isDeleteMessage) "Undo" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${state.selectedIds.size} quotes?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteSelected()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.isSelectionMode) {
                TopAppBar(
                    title = { Text("${state.selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Library") },
                    actions = {
                        if (state.isAdmin) {
                            IconButton(onClick = viewModel::lock) {
                                Icon(Icons.Filled.LockOpen, contentDescription = "Lock admin")
                            }
                        } else {
                            IconButton(onClick = viewModel::openAdminDialog) {
                                Icon(Icons.Filled.Lock, contentDescription = "Admin")
                            }
                        }
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export database") },
                                onClick = {
                                    overflowOpen = false
                                    exportLauncher.launch("rzi-quotes.sqlite")
                                },
                            )
                            if (state.isAdmin) {
                                DropdownMenuItem(
                                    text = { Text("Import database") },
                                    onClick = {
                                        overflowOpen = false
                                        importLauncher.launch(arrayOf("application/octet-stream"))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Change PIN") },
                                    onClick = {
                                        overflowOpen = false
                                        viewModel.openChangePin()
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DockedSearchBar(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = {},
                active = false,
                onActiveChange = {},
                placeholder = { Text("Search quotes, books, tags") },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {}

            if (state.isTransferInProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.books.isNotEmpty() || state.tagFilters.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    if (state.books.isNotEmpty()) {
                        item {
                            FilterChip(
                                selected = state.selectedBookIds.isNotEmpty(),
                                onClick = viewModel::openBookSheet,
                                label = {
                                    Text(
                                        if (state.selectedBookIds.isEmpty()) "Books"
                                        else "Books (${state.selectedBookIds.size})"
                                    )
                                },
                            )
                        }
                    }
                    if (state.tagFilters.isNotEmpty()) {
                        item {
                            FilterChip(
                                selected = state.selectedTagIds.isNotEmpty(),
                                onClick = viewModel::openTagSheet,
                                label = {
                                    Text(
                                        if (state.selectedTagIds.isEmpty()) "Tags"
                                        else "Tags (${state.selectedTagIds.size})"
                                    )
                                },
                            )
                        }
                    }
                }
            }

            Text(
                text = state.countLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                state.totalCount == 0 && state.isAdmin -> EmptyState(
                    title = "Nothing here yet",
                    actionLabel = "Add a quote",
                    onAction = { onQuoteClick(null) },
                    secondaryLabel = "Import a database",
                    onSecondary = { importLauncher.launch(arrayOf("application/octet-stream")) },
                )

                state.totalCount == 0 -> EmptyState(title = "Nothing here yet")

                quotes.itemCount == 0 && state.isSearching ->
                    EmptyState(
                        title = if (state.query.isBlank()) {
                            "No matches for these filters"
                        } else {
                            "No matches for '${state.query}'"
                        }
                    )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        count = quotes.itemCount,
                        key = { index -> quotes[index]?.id ?: index },
                    ) { index ->
                        val quote = quotes[index] ?: return@items
                        val isSelected = quote.id in state.selectedIds
                        QuoteRowItem(
                            quote = quote,
                            query = state.query,
                            isSelected = isSelected,
                            isSelectionMode = state.isSelectionMode,
                            onClick = {
                                if (state.isSelectionMode) {
                                    viewModel.toggleSelection(quote.id)
                                } else {
                                    onQuoteClick(quote.id)
                                }
                            },
                            onLongClick = if (state.isAdmin) {
                                { viewModel.onQuoteLongPress(quote.id) }
                            } else null,
                        )
                    }
                }
            }
        }

        if (state.isBookSheetOpen) {
            BookFilterSheet(
                books = state.books,
                selectedBookIds = state.selectedBookIds,
                onBookToggle = viewModel::onBookToggle,
                onDismiss = viewModel::closeBookSheet,
                onClear = {
                    state.selectedBookIds.forEach { viewModel.onBookToggle(it) }
                },
            )
        }

        if (state.isTagSheetOpen) {
            TagFilterSheet(
                tagFilters = state.tagFilters,
                selectedTagIds = state.selectedTagIds,
                onTagToggle = viewModel::onTagToggle,
                onDismiss = viewModel::closeTagSheet,
                onClear = {
                    state.selectedTagIds.forEach { viewModel.onTagToggle(it) }
                },
            )
        }

        if (state.isPinDialogOpen) {
            AdminPinDialog(
                onDismiss = viewModel::closeAdminDialog,
                onSuccess = viewModel::closeAdminDialog,
            )
        }

        if (state.isChangePinOpen) {
            ChangePinDialog(
                onDismiss = viewModel::closeChangePin,
                onSuccess = {
                    viewModel.closeChangePin()
                    viewModel.showMessage("PIN updated")
                },
            )
        }
    }
}
