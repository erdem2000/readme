package org.readeram.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File

class PdfParser(private val context: Context) {
    fun parseMeta(uri: Uri): BookMeta {
        val pageCount = PdfRenderer(openPfd(uri)).use { it.pageCount }
        val cover = renderCover(uri)
        val name = displayName(uri)
        return BookMeta(
            title = name.substringBeforeLast('.'),
            author = null,
            format = BookFormat.Pdf,
            pageCount = pageCount,
            coverBytes = cover,
        )
    }

    fun open(uri: Uri): OpenedBook.Pdf {
        val pageCount = PdfRenderer(openPfd(uri)).use { renderer -> renderer.pageCount }
        val pageTexts = extractPageTexts(uri, pageCount)
        val paragraphsByPage = pageTexts.map { page ->
            page.split(Regex("\\n{2,}")).map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }
                .ifEmpty { if (page.isNotBlank()) listOf(page.trim()) else emptyList() }
        }
        val sentences = mutableListOf<Sentence>()
        paragraphsByPage.forEachIndexed { pageIndex, paragraphs ->
            sentences += TextSplitter.paragraphsToSentences(paragraphs, pageIndex, sentences.size)
        }
        val name = displayName(uri)
        return OpenedBook.Pdf(
            meta = BookMeta(
                title = name.substringBeforeLast('.'),
                author = null,
                format = BookFormat.Pdf,
                pageCount = pageCount,
            ),
            pageCount = pageCount,
            pageTexts = pageTexts,
            sentences = sentences,
        )
    }

    fun renderPage(uri: Uri, pageIndex: Int, maxWidth: Int = 1080): Bitmap? {
        return PdfRenderer(openPfd(uri)).use { renderer ->
            if (pageIndex !in 0 until renderer.pageCount) return@use null
            renderer.openPage(pageIndex).use { page ->
                val scale = maxWidth.toFloat() / page.width.coerceAtLeast(1)
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

    private fun extractPageTexts(uri: Uri, pageCount: Int): List<String> {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { document ->
                val stripper = PDFTextStripper()
                (1..pageCount).map { page ->
                    stripper.startPage = page
                    stripper.endPage = page
                    stripper.getText(document).trim()
                }
            }
        } ?: List(pageCount) { "" }
    }

    private fun renderCover(uri: Uri): ByteArray? {
        val bitmap = renderPage(uri, 0, 400) ?: return null
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun openPfd(uri: Uri): ParcelFileDescriptor {
        return context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot open PDF descriptor")
    }

    private fun displayName(uri: Uri): String {
        return File(uri.lastPathSegment ?: "document.pdf").name
    }
}
