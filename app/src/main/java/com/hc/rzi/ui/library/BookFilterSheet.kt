package com.hc.rzi.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hc.rzi.domain.model.Book

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookFilterSheet(
    books: List<Book>,
    selectedBookIds: List<Long>,
    onBookToggle: (Long) -> Unit,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var search by remember { mutableStateOf("") }
    val filtered = books.filter {
        search.isBlank() || it.name.contains(search, ignoreCase = true)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Filter by book",
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(
                    onClick = {
                        onClear()
                        onDismiss()
                    },
                    enabled = selectedBookIds.isNotEmpty(),
                ) {
                    Text("Clear")
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search books") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.height(360.dp)) {
                items(filtered, key = { it.id }) { book ->
                    val selected = book.id in selectedBookIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBookToggle(book.id) }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { onBookToggle(book.id) },
                        )
                        Text(
                            text = book.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
