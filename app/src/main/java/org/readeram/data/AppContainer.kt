package org.readeram.data

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.readeram.data.local.BookDatabase
import org.readeram.data.prefs.UserPreferencesRepository
import org.readeram.data.repository.LibraryRepository
import org.readeram.parser.BookParser
import org.readeram.tts.TtsCatalog

class AppContainer(context: Context) {
    val database: BookDatabase = BookDatabase.create(context)
    val preferences: UserPreferencesRepository = UserPreferencesRepository(context)
    val parser: BookParser = BookParser(context)
    val library: LibraryRepository = LibraryRepository(
        context = context.applicationContext,
        dao = database.bookDao(),
        parser = parser,
    )
    val ttsCatalog: TtsCatalog = TtsCatalog(context.applicationContext)

    init {
        PDFBoxResourceLoader.init(context)
    }
}
