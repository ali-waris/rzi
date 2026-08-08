package com.hc.rzi.ui.reel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hc.rzi.domain.model.Book
import com.hc.rzi.domain.model.ReelFilter
import com.hc.rzi.domain.model.TagFilter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReelFilterSheet(
    books: List<Book>,
    tagFilters: List<TagFilter>,
    current: ReelFilter,
    onApply: (ReelFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var bookId by remember { mutableStateOf(current.bookId) }
    var tagIds by remember { mutableStateOf(current.tagIds) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Filter the reel", style = MaterialTheme.typography.titleMedium)

            Text("Book", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = bookId == null,
                    onClick = { bookId = null },
                    label = { Text("All books") },
                )
                books.forEach { book ->
                    FilterChip(
                        selected = bookId == book.id,
                        onClick = { bookId = if (bookId == book.id) null else book.id },
                        label = { Text(book.name) },
                    )
                }
            }

            Text("Tags", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tagFilters.forEach { tag ->
                    FilterChip(
                        selected = tag.id in tagIds,
                        onClick = {
                            tagIds = if (tag.id in tagIds) tagIds - tag.id else tagIds + tag.id
                        },
                        label = { Text("${tag.name} (${tag.usageCount})") },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { onApply(ReelFilter()) }) { Text("Clear") }
                TextButton(onClick = { onApply(ReelFilter(bookId, tagIds)) }) { Text("Apply") }
            }
        }
    }
}
