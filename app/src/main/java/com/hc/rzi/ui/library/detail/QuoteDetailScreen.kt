package com.hc.rzi.ui.library.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hc.rzi.domain.model.Quote
import com.hc.rzi.ui.components.EmptyState
import com.hc.rzi.ui.components.TagChip
import com.hc.rzi.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteDetailScreen(
    quoteId: Long?,
    onBack: () -> Unit,
    viewModel: QuoteDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var textAlign by remember { mutableStateOf(TextAlign.Center) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showTagSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = state.isEditing) {
        if (state.text.isBlank()) {
            showDiscardDialog = true
        } else {
            viewModel.save()
        }
    }

    LaunchedEffect(quoteId) { viewModel.load(quoteId) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is QuoteDetailEvent.Saved -> { /* ViewModel handles state */ }
                QuoteDetailEvent.Deleted -> onBack()
                is QuoteDetailEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard quote") },
            text = { Text("Empty quote will be discarded") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        viewModel.save()
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete quote") },
            text = { Text("Are you sure you want to delete?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    val quote = state.quote

    val showTagPicker = showTagSheet

    if (showTagPicker) {
        TagPickerScreen(
            selectedTags = state.tags,
            allTags = state.allKnownTags,
            onToggleTag = { viewModel.toggleTag(it) },
            onCreateTag = { viewModel.commitTag(it) },
            onDone = { showTagSheet = false },
        )
    } else {
        Scaffold(
            topBar = {
            if (state.isEditing) {
                EditModeTopBar(
                    onBack = { viewModel.save() },
                    textAlign = textAlign,
                    onAlignChange = { textAlign = it },
                )
            } else {
                    ReadingModeTopBar(
                        onBack = onBack,
                        textAlign = textAlign,
                        onAlignChange = { textAlign = it },
                        bookName = state.bookName,
                        pageText = state.pageText,
                    )
                }
            },
            bottomBar = {
            if (!state.isEditing && quote != null) {
                QuoteBottomBar(
                    isAdmin = state.isAdmin,
                    onCopy = { copyQuote(context, quote) },
                    onShare = { shareQuote(context, quote) },
                    onDelete = { showDeleteDialog = true },
                )
            }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(padding)) {
                when {
                    state.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )

                    state.isNotFound -> EmptyState(
                        title = "Quote not found",
                        body = "This quote may have been removed from your library.",
                        actionLabel = "Back to library",
                        onAction = onBack,
                    )

                    state.isEditing -> QuoteEditor(
                        state = state,
                        textAlign = textAlign,
                        onTextChange = viewModel::onTextChange,
                        onBookNameChange = viewModel::onBookNameChange,
                        onPageChange = viewModel::onPageChange,
                        onTagCount = state.tags.size,
                        onOpenTags = { showTagSheet = true },
                    )

                    quote != null -> QuoteReader(
                        quote = quote,
                        textAlign = textAlign,
                        isAdmin = state.isAdmin,
                        onTapToEdit = viewModel::startEdit,
                        tags = state.tags,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingModeTopBar(
    onBack: () -> Unit,
    textAlign: TextAlign,
    onAlignChange: (TextAlign) -> Unit,
    bookName: String,
    pageText: String,
) {
    val scheme = MaterialTheme.colorScheme
    TopAppBar(
        title = {
            Column {
                Text(
                    text = bookName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (pageText.isNotBlank()) {
                    Text(
                        text = "p. $pageText",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        actions = {
            IconButton(onClick = { onAlignChange(TextAlign.Start) }) {
                Icon(
                    Icons.AutoMirrored.Filled.FormatAlignLeft,
                    contentDescription = "Align left",
                    tint = if (textAlign == TextAlign.Start) scheme.primary else scheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onAlignChange(TextAlign.Center) }) {
                Icon(
                    Icons.Filled.FormatAlignCenter,
                    contentDescription = "Align center",
                    tint = if (textAlign == TextAlign.Center) scheme.primary else scheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onAlignChange(TextAlign.End) }) {
                Icon(
                    Icons.AutoMirrored.Filled.FormatAlignRight,
                    contentDescription = "Align right",
                    tint = if (textAlign == TextAlign.End) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditModeTopBar(
    onBack: () -> Unit,
    textAlign: TextAlign,
    onAlignChange: (TextAlign) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    TopAppBar(
        title = { Text("Edit quote") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        actions = {
            IconButton(onClick = { onAlignChange(TextAlign.Start) }) {
                Icon(
                    Icons.AutoMirrored.Filled.FormatAlignLeft,
                    contentDescription = "Align left",
                    tint = if (textAlign == TextAlign.Start) scheme.primary else scheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onAlignChange(TextAlign.Center) }) {
                Icon(
                    Icons.Filled.FormatAlignCenter,
                    contentDescription = "Align center",
                    tint = if (textAlign == TextAlign.Center) scheme.primary else scheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onAlignChange(TextAlign.End) }) {
                Icon(
                    Icons.AutoMirrored.Filled.FormatAlignRight,
                    contentDescription = "Align right",
                    tint = if (textAlign == TextAlign.End) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun QuoteBottomBar(
    isAdmin: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Copy quote",
                    tint = scheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = "Share quote",
                    tint = scheme.onSurfaceVariant,
                )
            }
            if (isAdmin) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete quote",
                        tint = scheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuoteReader(
    quote: Quote,
    textAlign: TextAlign,
    isAdmin: Boolean,
    onTapToEdit: () -> Unit,
    tags: List<String>,
) {
    val scheme = MaterialTheme.colorScheme
    var fontScale by remember { mutableFloatStateOf(1f) }
    val baseStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal)

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(Spacing.sm))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pinchToZoom(
                    onZoom = { scale ->
                        fontScale = (fontScale * scale).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
                    },
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .pointerInput(isAdmin) {
                        if (isAdmin) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downPos = down.position
                                var dragged = false
                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.any { it.pressed }) {
                                        val currentPos = event.changes.first().position
                                        val dx = currentPos.x - downPos.x
                                        val dy = currentPos.y - downPos.y
                                        if (dx * dx + dy * dy > 100) {
                                            dragged = true
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                                if (!dragged) {
                                    onTapToEdit()
                                }
                            }
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = quote.text,
                    style = baseStyle.copy(
                        fontSize = baseStyle.fontSize * fontScale,
                        lineHeight = baseStyle.lineHeight * fontScale,
                    ),
                    textAlign = textAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                )

                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg),
                    ) {
                        tags.forEach { tag -> TagChip(tag = tag) }
                    }
                    Spacer(modifier = Modifier.height(Spacing.lg))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuoteEditor(
    state: QuoteDetailUiState,
    textAlign: TextAlign,
    onTextChange: (String) -> Unit,
    onBookNameChange: (String) -> Unit,
    onPageChange: (String) -> Unit,
    onTagCount: Int,
    onOpenTags: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var fontScale by remember { mutableFloatStateOf(1f) }
    val baseStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookNameField(
                value = state.bookName,
                suggestions = state.bookSuggestions,
                error = state.errors.bookName,
                onValueChange = onBookNameChange,
                tagCount = onTagCount,
                onOpenTags = onOpenTags,
                modifier = Modifier.weight(1f),
            )

            OutlinedTextField(
                value = state.pageText,
                onValueChange = onPageChange,
                label = { Text("Page") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.errors.pageNumber != null,
                supportingText = state.errors.pageNumber?.let { message -> { Text(message) } },
                modifier = Modifier.width(72.dp),
                singleLine = true,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pinchToZoom(
                    onZoom = { scale ->
                        fontScale = (fontScale * scale).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
                    },
                ),
        ) {
            TextField(
                value = state.text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxSize(),
                textStyle = baseStyle.copy(
                    fontSize = baseStyle.fontSize * fontScale,
                    lineHeight = baseStyle.lineHeight * fontScale,
                    textAlign = textAlign,
                    color = scheme.onSurface,
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = scheme.surface,
                    unfocusedContainerColor = scheme.surface,
                    focusedIndicatorColor = scheme.surface,
                    unfocusedIndicatorColor = scheme.surface,
                ),
                placeholder = {
                    Text(
                        text = "Enter your quote...",
                        style = baseStyle.copy(
                            fontSize = baseStyle.fontSize * fontScale,
                            lineHeight = baseStyle.lineHeight * fontScale,
                            textAlign = textAlign,
                        ),
                        color = scheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                },
            )
        }
    }
}

@Composable
private fun BadgeBox(count: Int, content: @Composable () -> Unit) {
    BadgedBox(
        badge = {
            if (count > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                        ),
                    )
                }
            }
        },
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagPickerScreen(
    selectedTags: List<String>,
    allTags: List<String>,
    onToggleTag: (String) -> Unit,
    onCreateTag: (String) -> Unit,
    onDone: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    val trimmedQuery = query.trim()
    val filtered = allTags.filter {
        trimmedQuery.isBlank() || it.contains(trimmedQuery, ignoreCase = true)
    }
    val exactMatch = trimmedQuery.isNotEmpty() && allTags.any {
        it.equals(trimmedQuery, ignoreCase = true)
    }
    val showCreate = trimmedQuery.isNotEmpty() && !exactMatch

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tags") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Done",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.lg),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search or add tags...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            if (showCreate) {
                Surface(
                    onClick = {
                        onCreateTag(trimmedQuery)
                        query = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(Spacing.md))
                        Text(
                            text = trimmedQuery,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = "(create new)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filtered) { tag ->
                    val isSelected = selectedTags.any { it.equals(tag, ignoreCase = true) }
                    Surface(
                        onClick = { onToggleTag(tag) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleTag(tag) },
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookNameField(
    value: String,
    suggestions: List<String>,
    error: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    tagCount: Int? = null,
    onOpenTags: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var debouncedValue by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        delay(1.seconds)
        debouncedValue = value
    }
    val matches = suggestions.filter {
        debouncedValue.isBlank() || it.contains(debouncedValue, ignoreCase = true)
    }
    val showMenu = expanded && debouncedValue == value && matches.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = showMenu,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text("Book") },
            isError = error != null,
            supportingText = error?.let { message -> { Text(message) } },
            singleLine = true,
            trailingIcon = if (onOpenTags != null && tagCount != null) {
                {
                    IconButton(onClick = onOpenTags) {
                        BadgeBox(count = tagCount) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Label,
                                contentDescription = "Tags",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
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

private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) {
                onZoom(event.calculateZoom())
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

private const val MIN_FONT_SCALE = 0.5f
private const val MAX_FONT_SCALE = 3f

private fun quoteAsText(quote: Quote): String = buildString {
    append(quote.text)
    append("\n— ")
    append(quote.bookName)
    quote.pageNumber?.let { append(", p. $it") }
}

private fun copyQuote(context: Context, quote: Quote) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Quote", quoteAsText(quote)))
}

private fun shareQuote(context: Context, quote: Quote) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, quoteAsText(quote))
    }
    context.startActivity(Intent.createChooser(intent, null))
}
