package org.readeram.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        BookmarkEntity::class,
        CollectionEntity::class,
        CollectionBookCrossRef::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN readStatus TEXT NOT NULL DEFAULT 'unread'")
                db.execSQL("UPDATE books SET readStatus = 'reading' WHERE lastOpenedAt > 0 AND progress < 0.98")
                db.execSQL("UPDATE books SET readStatus = 'read' WHERE progress >= 0.98")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS collections (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS collection_books (
                        collectionId TEXT NOT NULL,
                        bookId TEXT NOT NULL,
                        PRIMARY KEY(collectionId, bookId),
                        FOREIGN KEY(collectionId) REFERENCES collections(id) ON DELETE CASCADE,
                        FOREIGN KEY(bookId) REFERENCES books(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_books_bookId ON collection_books(bookId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_books_collectionId ON collection_books(collectionId)")
            }
        }

        fun create(context: Context): BookDatabase {
            return Room.databaseBuilder(context, BookDatabase::class.java, "readeram.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
