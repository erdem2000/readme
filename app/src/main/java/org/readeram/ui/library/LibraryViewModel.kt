package org.readeram.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.readeram.ReaderamApplication
import org.readeram.data.local.BookEntity
import org.readeram.data.local.CollectionEntity
import org.readeram.data.local.ReadStatus

enum class LibraryViewMode(val key: String) {
    Shelves("shelves"),
    Collections("collections");

    companion object {
        fun fromKey(key: String): LibraryViewMode = entries.firstOrNull { it.key == key } ?: Shelves
    }
}

data class CollectionShelf(
    val collection: CollectionEntity?,
    val books: List<BookEntity>,
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ReaderamApplication
    private val library = app.container.library
    private val prefs = app.container.preferences

    val books: StateFlow<List<BookEntity>> = library.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val collections: StateFlow<List<CollectionEntity>> = library.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val collectionShelves: StateFlow<List<CollectionShelf>> = combine(
        library.observeBooks(),
        library.observeCollections(),
        library.observeCollectionBooks(),
    ) { books, collections, refs ->
        val byCollection = refs.groupBy { it.collectionId }
        val assigned = refs.map { it.bookId }.toSet()
        val shelves = collections.map { collection ->
            val ids = byCollection[collection.id].orEmpty().map { it.bookId }.toSet()
            CollectionShelf(collection, books.filter { it.id in ids })
        }
        val uncategorized = books.filter { it.id !in assigned }
        if (uncategorized.isEmpty()) shelves else shelves + CollectionShelf(null, uncategorized)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val viewMode: StateFlow<LibraryViewMode> = prefs.libraryViewMode
        .map(LibraryViewMode::fromKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryViewMode.Shelves)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { library.repairContentTitles() }
        }
    }

    fun setViewMode(mode: LibraryViewMode) {
        viewModelScope.launch { prefs.setLibraryViewMode(mode.key) }
    }

    fun importFiles(uris: List<Uri>) {
        viewModelScope.launch {
            _busy.value = true
            try {
                library.importUris(uris)
            } finally {
                _busy.value = false
            }
        }
    }

    fun importFolder(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                library.importTree(uri)
            } finally {
                _busy.value = false
            }
        }
    }

    fun setReadStatus(id: String, status: ReadStatus) {
        viewModelScope.launch { library.setReadStatus(id, status) }
    }

    fun removeBook(id: String) {
        viewModelScope.launch { library.removeBook(id) }
    }

    fun createCollection(name: String) {
        viewModelScope.launch { library.createCollection(name) }
    }

    fun deleteCollection(id: String) {
        viewModelScope.launch { library.deleteCollection(id) }
    }

    fun addToCollection(bookId: String, collectionId: String) {
        viewModelScope.launch { library.addToCollection(bookId, collectionId) }
    }

    fun removeFromCollection(bookId: String, collectionId: String) {
        viewModelScope.launch { library.removeFromCollection(bookId, collectionId) }
    }

    fun startSelection(bookId: String) {
        _selectionMode.value = true
        _selectedIds.value = setOf(bookId)
    }

    fun clearSelection() {
        _selectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelected(bookId: String) {
        if (!_selectionMode.value) {
            startSelection(bookId)
            return
        }
        _selectedIds.update { cur ->
            if (bookId in cur) cur - bookId else cur + bookId
        }
        if (_selectedIds.value.isEmpty()) {
            _selectionMode.value = false
        }
    }

    fun removeSelected(onRemoved: (String) -> Unit = {}) {
        val ids = _selectedIds.value.toList()
        viewModelScope.launch {
            ids.forEach {
                library.removeBook(it)
                onRemoved(it)
            }
            clearSelection()
        }
    }

    fun addSelectedToCollection(collectionId: String) {
        val ids = _selectedIds.value.toList()
        viewModelScope.launch {
            ids.forEach { library.addToCollection(it, collectionId) }
            clearSelection()
        }
    }

    fun removeSelectedFromCollection(collectionId: String) {
        val ids = _selectedIds.value.toList()
        viewModelScope.launch {
            ids.forEach { library.removeFromCollection(it, collectionId) }
            clearSelection()
        }
    }
}
