package org.readeram.parser

enum class BookFormat(val key: String, val mimeTypes: List<String>, val extensions: List<String>) {
    Epub("EPUB", listOf("application/epub+zip"), listOf("epub")),
    Pdf("PDF", listOf("application/pdf"), listOf("pdf")),
    Txt("TXT", listOf("text/plain"), listOf("txt"));

    companion object {
        fun fromName(name: String?, mime: String? = null): BookFormat? {
            val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
            entries.firstOrNull { ext in it.extensions }?.let { return it }
            if (!mime.isNullOrBlank()) {
                entries.firstOrNull { mime in it.mimeTypes }?.let { return it }
            }
            return null
        }
    }
}

data class BookMeta(
    val title: String,
    val author: String?,
    val format: BookFormat,
    val pageCount: Int = 0,
    val coverBytes: ByteArray? = null,
)

data class Sentence(
    val id: Int,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val text: String,
)

data class ChapterContent(
    val title: String,
    val paragraphs: List<String>,
    val sentences: List<Sentence>,
)

sealed interface OpenedBook {
    val meta: BookMeta
    val sentences: List<Sentence>

    data class Reflow(
        override val meta: BookMeta,
        val chapters: List<ChapterContent>,
        override val sentences: List<Sentence>,
    ) : OpenedBook

    data class Pdf(
        override val meta: BookMeta,
        val pageCount: Int,
        val pageTexts: List<String>,
        override val sentences: List<Sentence>,
    ) : OpenedBook
}
