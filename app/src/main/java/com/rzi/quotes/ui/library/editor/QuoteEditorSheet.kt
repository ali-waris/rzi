package com.rzi.quotes.ui.library.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteEditorSheet(
    quoteId: Long?,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: QuoteEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(quoteId) { viewModel.load(quoteId) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                EditorEvent.Saved, EditorEvent.Deleted -> onDismiss()
                is EditorEvent.Message -> onMessage(event.text)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel")
                }
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.isEditing) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete quote")
                        }
                    }
                    TextButton(onClick = viewModel::save, enabled = state.canSave) { Text("Save") }
                }
            }

            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::onTextChange,
                label = { Text("Quote") },
                minLines = 5,
                isError = state.errors.text != null,
                supportingText = state.errors.text?.let { message -> { Text(message) } },
                modifier = Modifier.fillMaxWidth(),
            )

            BookNameField(state = state, onValueChange = viewModel::onBookNameChange)

            OutlinedTextField(
                value = state.pageText,
                onValueChange = viewModel::onPageChange,
                label = { Text("Page number (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.errors.pageNumber != null,
                supportingText = state.errors.pageNumber?.let { message -> { Text(message) } },
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.tags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.tags.size) { index ->
                        val tag = state.tags[index]
                        InputChip(
                            selected = false,
                            onClick = { viewModel.removeTag(tag) },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(Icons.Filled.Close, contentDescription = "Remove $tag")
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.tagInput,
                onValueChange = viewModel::onTagInputChange,
                label = { Text("Tags (optional)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.commitTag() }),
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.tagSuggestions.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.tagSuggestions.size) { index ->
                        val suggestion = state.tagSuggestions[index]
                        SuggestionChip(
                            onClick = { viewModel.commitTag(suggestion) },
                            label = { Text(suggestion) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookNameField(state: QuoteEditorUiState, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val matches = state.bookSuggestions.filter {
        state.bookName.isBlank() || it.contains(state.bookName, ignoreCase = true)
    }
    val showMenu = expanded && matches.isNotEmpty()

    ExposedDropdownMenuBox(expanded = showMenu, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = state.bookName,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text("Book") },
            isError = state.errors.bookName != null,
            supportingText = state.errors.bookName?.let { message -> { Text(message) } },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = showMenu, onDismissRequest = { expanded = false }) {
            matches.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onValueChange(name); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
