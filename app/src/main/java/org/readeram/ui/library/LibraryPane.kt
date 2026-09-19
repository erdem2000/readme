package org.readeram.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import org.readeram.R
import org.readeram.data.local.BookEntity
import org.readeram.data.local.CollectionEntity
import org.readeram.data.local.ReadStatus
import java.io.File

private val ShelfWood = Color(0xFF6D4C41)
private val ShelfWoodDark = Color(0xFF4E342E)
private val ShelfWoodLight = Color(0xFF8D6E63)
private val ShelfBack = Color(0xFF3E2723)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryPane(
    selectedId: String?,
    onOpen: (String) -> Unit,
    onRemoved: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(),
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val shelves by viewModel.collectionShelves.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val openDocs = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris)
    }
    val openTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri?.let { viewModel.importFolder(it) }
    }
    var actionBook by remember { mutableStateOf<BookEntity?>(null) }
    var actionCollection by remember { mutableStateOf<CollectionEntity?>(null) }
    var showCreateCollection by remember { mutableStateOf(false) }
    var showAddToCollection by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmDeleteCollection by remember { mutableStateOf<CollectionEntity?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selection_count, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showAddToCollection = true },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = stringResource(R.string.add_to_collection))
                        }
                        val shelfCollection = actionCollection
                        if (viewMode == LibraryViewMode.Collections && shelfCollection != null) {
                            TextButton(
                                onClick = {
                                    viewModel.removeSelectedFromCollection(shelfCollection.id)
                                },
                                enabled = selectedIds.isNotEmpty(),
                            ) {
                                Text(stringResource(R.string.remove_from_collection))
                            }
                        }
                        IconButton(
                            onClick = { confirmRemove = true },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.remove_from_library))
                        }
                    },
                )
            } else {
                TopAppBar(title = { Text(stringResource(R.string.library_title)) })
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = {
                    openDocs.launch(
                        arrayOf(
                            "application/epub+zip",
                            "application/pdf",
                            "text/plain",
                            "*/*",
                        ),
                    )
                }) {
                    Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_files))
                }
                FilledTonalButton(onClick = { openTree.launch(null) }) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_folder))
                }
            }
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                SegmentedButton(
                    selected = viewMode == LibraryViewMode.Shelves,
                    onClick = { viewModel.setViewMode(LibraryViewMode.Shelves) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = {},
                ) {
                    Text(stringResource(R.string.view_shelves))
                }
                SegmentedButton(
                    selected = viewMode == LibraryViewMode.Collections,
                    onClick = { viewModel.setViewMode(LibraryViewMode.Collections) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = {},
                ) {
                    Text(stringResource(R.string.view_collections))
                }
            }
            if (viewMode == LibraryViewMode.Collections) {
                TextButton(
                    onClick = { showCreateCollection = true },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Icon(Icons.Outlined.LibraryAdd, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.new_collection))
                }
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.importing),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (books.isEmpty() && !busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.library_empty),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                when (viewMode) {
                    LibraryViewMode.Shelves -> {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            item {
                                BookshelfRow(
                                    title = stringResource(R.string.shelf_all),
                                    books = books,
                                    selectedId = selectedId,
                                    selectionMode = selectionMode,
                                    selectedIds = selectedIds,
                                    onOpen = onOpen,
                                    onLongPress = { viewModel.startSelection(it.id) },
                                    onToggleSelect = { viewModel.toggleSelected(it) },
                                )
                            }
                            item {
                                BookshelfRow(
                                    title = stringResource(R.string.shelf_reading),
                                    books = books.filter { it.status == ReadStatus.Reading },
                                    selectedId = selectedId,
                                    selectionMode = selectionMode,
                                    selectedIds = selectedIds,
                                    onOpen = onOpen,
                                    onLongPress = { viewModel.startSelection(it.id) },
                                    onToggleSelect = { viewModel.toggleSelected(it) },
                                )
                            }
                            item {
                                BookshelfRow(
                                    title = stringResource(R.string.shelf_unread),
                                    books = books.filter { it.status == ReadStatus.Unread },
                                    selectedId = selectedId,
                                    selectionMode = selectionMode,
                                    selectedIds = selectedIds,
                                    onOpen = onOpen,
                                    onLongPress = { viewModel.startSelection(it.id) },
                                    onToggleSelect = { viewModel.toggleSelected(it) },
                                )
                            }
                            item {
                                BookshelfRow(
                                    title = stringResource(R.string.shelf_read),
                                    books = books.filter { it.status == ReadStatus.Read },
                                    selectedId = selectedId,
                                    selectionMode = selectionMode,
                                    selectedIds = selectedIds,
                                    onOpen = onOpen,
                                    onLongPress = { viewModel.startSelection(it.id) },
                                    onToggleSelect = { viewModel.toggleSelected(it) },
                                )
                            }
                        }
                    }
                    LibraryViewMode.Collections -> {
                        if (collections.isEmpty() && books.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.collections_empty))
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                items(shelves, key = { it.collection?.id ?: "uncategorized" }) { shelf ->
                                    BookshelfRow(
                                        title = shelf.collection?.name
                                            ?: stringResource(R.string.uncategorized),
                                        books = shelf.books,
                                        selectedId = selectedId,
                                    selectionMode = selectionMode,
                                    selectedIds = selectedIds,
                                        onOpen = onOpen,
                                        onLongPress = {
                                            actionCollection = shelf.collection
                                            viewModel.startSelection(it.id)
                                        },
                                        onToggleSelect = { viewModel.toggleSelected(it) },
                                        onTitleLongPress = shelf.collection?.let { collection ->
                                            { confirmDeleteCollection = collection }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val selectedBook = actionBook
    if (selectedBook != null && !selectionMode && !showAddToCollection && !confirmRemove) {
        ModalBottomSheet(onDismissRequest = {
            actionBook = null
            actionCollection = null
        }) {
            Text(
                selectedBook.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            TextButton(
                onClick = {
                    viewModel.setReadStatus(selectedBook.id, ReadStatus.Read)
                    actionBook = null
                    actionCollection = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.mark_read), modifier = Modifier.fillMaxWidth())
            }
            TextButton(
                onClick = {
                    viewModel.setReadStatus(selectedBook.id, ReadStatus.Unread)
                    actionBook = null
                    actionCollection = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.mark_unread), modifier = Modifier.fillMaxWidth())
            }
            TextButton(
                onClick = { showAddToCollection = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.add_to_collection), modifier = Modifier.fillMaxWidth())
            }
            val collection = actionCollection
            if (collection != null) {
                TextButton(
                    onClick = {
                        viewModel.removeFromCollection(selectedBook.id, collection.id)
                        actionBook = null
                        actionCollection = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.remove_from_collection),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            TextButton(
                onClick = { confirmRemove = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.remove_from_library),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddToCollection && (selectedBook != null || (selectionMode && selectedIds.isNotEmpty()))) {
        AlertDialog(
            onDismissRequest = { showAddToCollection = false },
            title = { Text(stringResource(R.string.add_to_collection)) },
            text = {
                Column {
                    if (collections.isEmpty()) {
                        Text(stringResource(R.string.no_collections))
                    } else {
                        collections.forEach { collection ->
                            TextButton(
                                onClick = {
                                    if (selectionMode && selectedIds.isNotEmpty()) {
                                        viewModel.addSelectedToCollection(collection.id)
                                    } else if (selectedBook != null) {
                                        viewModel.addToCollection(selectedBook.id, collection.id)
                                        actionBook = null
                                        actionCollection = null
                                    }
                                    showAddToCollection = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(collection.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    TextButton(onClick = {
                        showAddToCollection = false
                        showCreateCollection = true
                    }) {
                        Text(stringResource(R.string.new_collection))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddToCollection = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }

    if (confirmRemove && (selectedBook != null || (selectionMode && selectedIds.isNotEmpty()))) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.remove_from_library)) },
            text = {
                Text(
                    if (selectionMode && selectedIds.isNotEmpty()) {
                        stringResource(R.string.remove_books_confirm, selectedIds.size)
                    } else {
                        stringResource(R.string.remove_book_confirm)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (selectionMode && selectedIds.isNotEmpty()) {
                        viewModel.removeSelected { id -> onRemoved(id) }
                    } else if (selectedBook != null) {
                        val id = selectedBook.id
                        viewModel.removeBook(id)
                        actionBook = null
                        actionCollection = null
                        onRemoved(id)
                    }
                    confirmRemove = false
                }) {
                    Text(stringResource(R.string.remove_from_library))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showCreateCollection) {
        CollectionNameDialog(
            onDismiss = { showCreateCollection = false },
            onCreate = { name ->
                viewModel.createCollection(name)
                showCreateCollection = false
            },
        )
    }

    val collectionToDelete = confirmDeleteCollection
    if (collectionToDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteCollection = null },
            title = { Text(stringResource(R.string.delete_collection)) },
            text = { Text(collectionToDelete.name) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCollection(collectionToDelete.id)
                    confirmDeleteCollection = null
                }) {
                    Text(stringResource(R.string.delete_collection))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteCollection = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CollectionNameDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_collection)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.collection_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name) },
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookshelfRow(
    title: String,
    books: List<BookEntity>,
    selectedId: String?,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onOpen: (String) -> Unit,
    onLongPress: (BookEntity) -> Unit,
    onToggleSelect: (String) -> Unit,
    onTitleLongPress: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .then(
                    if (onTitleLongPress != null) {
                        Modifier.combinedClickable(onClick = {}, onLongClick = onTitleLongPress)
                    } else {
                        Modifier
                    },
                ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(ShelfBack.copy(alpha = 0.55f), ShelfWoodDark),
                    ),
                )
                .border(1.dp, ShelfWoodLight.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(top = 12.dp, bottom = 0.dp),
        ) {
            Column {
                if (books.isEmpty()) {
                    Text(
                        stringResource(R.string.shelf_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEFEBE9),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 28.dp),
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(books, key = { it.id }) { book ->
                            BookCoverCard(
                                book = book,
                                selected = book.id == selectedId,
                                checked = book.id in selectedIds,
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) onToggleSelect(book.id) else onOpen(book.id)
                                },
                                onLongClick = {
                                    if (selectionMode) onToggleSelect(book.id) else onLongPress(book)
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(ShelfWood, ShelfWoodDark),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(ShelfWoodDark.copy(alpha = 0.85f)),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCoverCard(
    book: BookEntity,
    selected: Boolean,
    checked: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val percent = (book.progress * 100).toInt().coerceIn(0, 100)
    Column(
        Modifier
            .width(108.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val cover = book.coverPath?.let { File(it) }
        Box(
            Modifier
                .width(96.dp)
                .height(140.dp)
                .shadow(6.dp, RoundedCornerShape(4.dp), clip = false)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFD7CCC8))
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null && cover.exists()) {
                AsyncImage(
                    model = cover,
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = ShelfWoodDark,
                    )
                    Text(
                        book.format.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = ShelfWoodDark,
                    )
                }
            }
            if (selectionMode) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (checked) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (checked) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            book.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xFFFFF8E1),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.progress_percent, percent),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFD7CCC8),
        )
    }
}
