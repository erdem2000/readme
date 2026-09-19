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
import org.readeram.data.local.CollectionBookCrossRef
import org.readeram.data.local.CollectionEntity
import org.readeram.data.local.ReadStatus
import org.readeram.parser.BookFormat
import org.readeram.parser.BookParser
import org.readeram.parser.OpenedBook
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class LibraryRepository(
    private val context: Context,
    private val dao: BookDao,
    private val parser: BookParser,
) {
    fun observeBooks(): Flow<List<BookEntity>> = dao.observeBooks()
    fun observeBook(id: String): Flow<BookEntity?> = dao.observeBook(id)
    fun observeLatestBookmark(bookId: String): Flow<BookmarkEntity?> = dao.observeLatestBookmark(bookId)
    fun observeCollections(): Flow<List<CollectionEntity>> = dao.observeCollections()
    fun observeCollectionBooks(): Flow<List<CollectionBookCrossRef>> = dao.observeCollectionBooks()
    suspend fun getBook(id: String): BookEntity? = dao.getBook(id)
    suspend fun getLatestBookmark(bookId: String): BookmarkEntity? = dao.getLatestBookmark(bookId)

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
        if (book.status == ReadStatus.Unread) {
            dao.updateReadStatus(id, ReadStatus.Reading.key)
        }
        return withContext(Dispatchers.IO) {
            parser.open(Uri.parse(book.uri), id)
        }
    }

    suspend fun saveProgress(id: String, position: String, progress: Float) {
        dao.updateProgress(id, position, progress.coerceIn(0f, 1f), System.currentTimeMillis())
    }

    suspend fun setReadStatus(id: String, status: ReadStatus) {
        dao.updateReadStatus(id, status.key)
    }

    suspend fun removeBook(id: String) {
        val book = dao.getBook(id)
        dao.deleteBookmarks(id)
        dao.delete(id)
        book?.coverPath?.let { path ->
            runCatching { File(path).delete() }
        }
    }

    suspend fun saveBookmark(id: String, position: String, label: String) {
        dao.deleteBookmarks(id)
        dao.insertBookmark(
            BookmarkEntity(
                bookId = id,
                position = position,
                label = label,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun createCollection(name: String): CollectionEntity {
        val collection = CollectionEntity(
            id = UUID.randomUUID().toString().take(16),
            name = name.trim(),
            createdAt = System.currentTimeMillis(),
        )
        dao.upsertCollection(collection)
        return collection
    }

    suspend fun renameCollection(id: String, name: String) {
        val current = dao.getCollection(id) ?: return
        dao.upsertCollection(current.copy(name = name.trim()))
    }

    suspend fun deleteCollection(id: String) {
        dao.deleteCollection(id)
    }

    suspend fun addToCollection(bookId: String, collectionId: String) {
        dao.addToCollection(CollectionBookCrossRef(collectionId = collectionId, bookId = bookId))
    }

    suspend fun removeFromCollection(bookId: String, collectionId: String) {
        dao.removeFromCollection(collectionId, bookId)
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
                title = resolveTitle(meta.title, name),
                author = meta.author,
                format = meta.format.key,
                coverPath = coverPath ?: existing?.coverPath,
                lastPosition = existing?.lastPosition,
                progress = existing?.progress ?: 0f,
                pageCount = meta.pageCount,
                fileSize = parser.querySize(uri),
                addedAt = existing?.addedAt ?: now,
                lastOpenedAt = existing?.lastOpenedAt ?: 0L,
                readStatus = existing?.readStatus ?: ReadStatus.Unread.key,
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

    private fun resolveTitle(metaTitle: String?, fileName: String): String {
        val stem = fileName.substringBeforeLast('.').ifBlank { fileName }
        val cleanedMeta = metaTitle?.trim()?.takeUnless {
            it.isBlank() || it.equals("content", ignoreCase = true)
        }
        val cleanedStem = stem.takeUnless { it.equals("content", ignoreCase = true) } ?: fileName
        return cleanedMeta ?: cleanedStem
    }

    /** Fix existing library rows whose title was wrongly saved as "content". */
    suspend fun repairContentTitles() = withContext(Dispatchers.IO) {
        dao.getAllBooks()
            .filter { it.title.equals("content", ignoreCase = true) }
            .forEach { book ->
                val uri = Uri.parse(book.uri)
                val name = runCatching { parser.queryName(uri) }.getOrDefault(book.displayName)
                val metaTitle = runCatching { parser.parseMeta(uri, book.id).title }.getOrNull()
                val fixed = resolveTitle(metaTitle, name)
                if (!fixed.equals("content", ignoreCase = true) && fixed != book.title) {
                    dao.updateTitle(book.id, fixed, name)
                }
            }
    }

}
