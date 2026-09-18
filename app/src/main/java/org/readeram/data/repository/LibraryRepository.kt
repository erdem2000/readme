package org.readeram.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.readeram.data.local.BookDao
import org.readeram.data.local.BookEntity
import org.readeram.data.local.BookmarkEntity
import org.readeram.parser.BookFormat
import org.readeram.parser.BookParser
import org.readeram.parser.OpenedBook
import java.io.File
import java.security.MessageDigest

class LibraryRepository(
    private val context: Context,
    private val dao: BookDao,
    private val parser: BookParser,
) {
    fun observeBooks(): Flow<List<BookEntity>> = dao.observeBooks()
    fun observeBook(id: String): Flow<BookEntity?> = dao.observeBook(id)
    suspend fun getBook(id: String): BookEntity? = dao.getBook(id)

    suspend fun importUris(uris: List<Uri>) {
        uris.forEach { importUri(it) }
    }

    suspend fun importTree(treeUri: Uri) {
        persist(treeUri, readWrite = true)
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return
        walk(root)
    }

    suspend fun open(id: String): OpenedBook {
        val book = dao.getBook(id) ?: error("Book not found")
        return withContext(Dispatchers.IO) {
            parser.open(Uri.parse(book.uri), id)
        }
    }

    suspend fun saveProgress(id: String, position: String, progress: Float) {
        dao.updateProgress(id, position, progress.coerceIn(0f, 1f), System.currentTimeMillis())
    }

    suspend fun addBookmark(id: String, position: String, label: String) {
        dao.insertBookmark(
            BookmarkEntity(
                bookId = id,
                position = position,
                label = label,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    fun renderPdfPage(uri: Uri, page: Int) = parser.renderPdfPage(uri, page)

    private suspend fun walk(dir: DocumentFile) {
        dir.listFiles().forEach { child ->
            if (child.isDirectory) {
                walk(child)
            } else {
                val name = child.name ?: return@forEach
                if (BookFormat.fromName(name, child.type) != null) {
                    importUri(child.uri)
                }
            }
        }
    }

    private suspend fun importUri(uri: Uri) = withContext(Dispatchers.IO) {
        persist(uri)
        val existing = dao.getByUri(uri.toString())
        val id = existing?.id ?: hashUri(uri)
        val name = parser.queryName(uri)
        val meta = try {
            parser.parseMeta(uri, id)
        } catch (_: Exception) {
            return@withContext
        }
        val coverPath = meta.coverBytes?.let { bytes ->
            val covers = File(context.filesDir, "covers")
            covers.mkdirs()
            val file = File(covers, "$id.jpg")
            file.writeBytes(bytes)
            file.absolutePath
        }
        val now = System.currentTimeMillis()
        dao.upsert(
            BookEntity(
                id = id,
                uri = uri.toString(),
                displayName = name,
                title = meta.title.ifBlank { name },
                author = meta.author,
                format = meta.format.key,
                coverPath = coverPath ?: existing?.coverPath,
                lastPosition = existing?.lastPosition,
                progress = existing?.progress ?: 0f,
                pageCount = meta.pageCount,
                fileSize = parser.querySize(uri),
                addedAt = existing?.addedAt ?: now,
                lastOpenedAt = existing?.lastOpenedAt ?: 0L,
            ),
        )
    }

    private fun persist(uri: Uri, readWrite: Boolean = false) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            if (readWrite) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // Single-file pickers may not always grant persistable permission.
        }
    }

    private fun hashUri(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
