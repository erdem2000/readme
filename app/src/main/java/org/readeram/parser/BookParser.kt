package org.readeram.parser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

class BookParser(context: Context) {
    private val appContext = context.applicationContext
    private val epub = EpubParser(appContext)
    private val pdf = PdfParser(appContext)
    private val txt = TxtParser(appContext)

    fun formatOf(uri: Uri, mime: String? = null): BookFormat? {
        val name = queryName(uri)
        return BookFormat.fromName(name, mime ?: appContext.contentResolver.getType(uri))
    }

    fun parseMeta(uri: Uri, id: String): BookMeta {
        val name = queryName(uri)
        return when (formatOf(uri)) {
            BookFormat.Epub -> epub.parseMeta(uri, id)
            BookFormat.Pdf -> pdf.parseMeta(uri)
            BookFormat.Txt -> txt.parseMeta(uri, name)
            null -> error("Unsupported format")
        }
    }

    fun open(uri: Uri, id: String): OpenedBook {
        val name = queryName(uri)
        return when (formatOf(uri)) {
            BookFormat.Epub -> epub.open(uri, id)
            BookFormat.Pdf -> pdf.open(uri)
            BookFormat.Txt -> txt.open(uri, name)
            null -> error("Unsupported format")
        }
    }

    fun renderPdfPage(uri: Uri, pageIndex: Int, maxWidth: Int = 1200) =
        pdf.renderPage(uri, pageIndex, maxWidth)

    fun queryName(uri: Uri): String {
        val fromUri = uri.lastPathSegment?.substringAfterLast('/') ?: "book"
        return appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0) ?: fromUri
                } else {
                    fromUri
                }
            } ?: fromUri
    }

    fun querySize(uri: Uri): Long {
        return appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } ?: 0L
    }
}
