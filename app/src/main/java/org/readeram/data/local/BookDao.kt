package org.readeram.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC, addedAt DESC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeBook(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        "UPDATE books SET lastPosition = :position, progress = :progress, lastOpenedAt = :openedAt WHERE id = :id",
    )
    suspend fun updateProgress(id: String, position: String, progress: Float, openedAt: Long)

    @Query("UPDATE books SET coverPath = :coverPath WHERE id = :id")
    suspend fun updateCover(id: String, coverPath: String?)

    @Query("UPDATE books SET readStatus = :status WHERE id = :id")
    suspend fun updateReadStatus(id: String, status: String)

    @Insert
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeBookmarks(bookId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestBookmark(bookId: String): Flow<BookmarkEntity?>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestBookmark(bookId: String): BookmarkEntity?

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteBookmarks(bookId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollection(collection: CollectionEntity)

    @Query("SELECT * FROM collections ORDER BY sortOrder ASC, createdAt DESC")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getCollection(id: String): CollectionEntity?

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToCollection(ref: CollectionBookCrossRef)

    @Query("DELETE FROM collection_books WHERE collectionId = :collectionId AND bookId = :bookId")
    suspend fun removeFromCollection(collectionId: String, bookId: String)

    @Query("SELECT * FROM collection_books")
    fun observeCollectionBooks(): Flow<List<CollectionBookCrossRef>>
}
