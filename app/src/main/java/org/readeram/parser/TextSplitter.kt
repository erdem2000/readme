package org.readeram.parser

object TextSplitter {
    private val sentenceRegex = Regex("(?<=[.!?…])\\s+(?=[\"“‘(\\[]?[\\p{L}\\p{N}])")

    fun paragraphsToSentences(
        paragraphs: List<String>,
        chapterIndex: Int,
        startId: Int = 0,
    ): List<Sentence> {
        val out = mutableListOf<Sentence>()
        var id = startId
        paragraphs.forEachIndexed { pIndex, paragraph ->
            split(paragraph).forEach { piece ->
                out += Sentence(id = id, chapterIndex = chapterIndex, paragraphIndex = pIndex, text = piece)
                id += 1
            }
        }
        return out
    }

    fun split(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isEmpty()) return emptyList()
        val chunks = sentenceRegex.split(normalized).map { it.trim() }.filter { it.isNotEmpty() }
        return chunks.ifEmpty { listOf(normalized) }
    }

    fun progressFor(index: Int, total: Int): Float {
        if (total <= 0) return 0f
        return (index + 1).toFloat() / total.toFloat()
    }
}
