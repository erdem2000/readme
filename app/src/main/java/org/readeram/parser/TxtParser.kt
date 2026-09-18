package org.readeram.parser

import android.content.Context
import android.net.Uri
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class TxtParser(private val context: Context) {
    fun parseMeta(uri: Uri, fallbackName: String): BookMeta {
        val title = fallbackName.substringBeforeLast('.')
        return BookMeta(title = title, author = null, format = BookFormat.Txt, pageCount = 1)
    }

    fun open(uri: Uri, fallbackName: String): OpenedBook.Reflow {
        val text = readText(uri)
        val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
            .ifEmpty { listOf(text.trim()).filter { it.isNotEmpty() } }
        val sentences = TextSplitter.paragraphsToSentences(paragraphs, 0)
        val title = fallbackName.substringBeforeLast('.')
        return OpenedBook.Reflow(
            meta = BookMeta(title = title, author = null, format = BookFormat.Txt, pageCount = 1),
            chapters = listOf(ChapterContent(title = title, paragraphs = paragraphs, sentences = sentences)),
            sentences = sentences,
        )
    }

    private fun readText(uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Cannot open text file")
        return decode(bytes)
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        val utf8 = String(bytes, StandardCharsets.UTF_8)
        return if (utf8.contains('\uFFFD')) {
            String(bytes, Charset.forName("windows-1254"))
        } else {
            utf8
        }
    }
}
