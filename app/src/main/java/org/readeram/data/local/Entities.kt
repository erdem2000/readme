package org.readeram.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

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
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val position: String,
    val label: String,
    val createdAt: Long,
)
