package org.readeram.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ReadStatus(val key: String) {
    Unread("unread"),
    Reading("reading"),
    Read("read");

    companion object {
        fun fromKey(key: String): ReadStatus = entries.firstOrNull { it.key == key } ?: Unread
    }
}

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val displayName: String,
    val title: String,
    val author: String?,
    val format: String,
    val coverPath: String?,
    val lastPosition: String?,
    val progress: Float,
    val pageCount: Int,
    val fileSize: Long,
    val addedAt: Long,
    val lastOpenedAt: Long,
    val readStatus: String = ReadStatus.Unread.key,
) {
    val status: ReadStatus get() = ReadStatus.fromKey(readStatus)
}

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val position: String,
    val label: String,
    val createdAt: Long,
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "collection_books",
    primaryKeys = ["collectionId", "bookId"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId"), Index("collectionId")],
)
data class CollectionBookCrossRef(
    val collectionId: String,
    val bookId: String,
)
