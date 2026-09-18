package org.readeram.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.readeram.ReaderamApplication
import org.readeram.data.local.BookEntity

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val library = (application as ReaderamApplication).container.library

    val books: StateFlow<List<BookEntity>> = library.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

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
}
