package org.readeram.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import org.readeram.R
import org.readeram.data.local.BookEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryPane(
    selectedId: String?,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(),
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.library_title)) })
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = {
                    openDocs.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain", "*/*"))
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
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(books, key = { it.id }) { book ->
                        BookRow(
                            book = book,
                            selected = book.id == selectedId,
                            onClick = { onOpen(book.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookRow(book: BookEntity, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cover = book.coverPath?.let { File(it) }
        if (cover != null && cover.exists()) {
            AsyncImage(
                model = cover,
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 52.dp, height = 72.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                Modifier
                    .size(width = 52.dp, height = 72.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                book.author ?: stringResource(R.string.author_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                "${book.format} · " + context.getString(R.string.progress_percent, (book.progress * 100).toInt()),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
