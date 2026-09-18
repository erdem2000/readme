package org.readeram.ui.reader

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import org.readeram.parser.ChapterContent
import org.readeram.parser.Sentence

data class ReflowPage(
    val chapterIndex: Int,
    val pageInChapter: Int,
    val blocks: List<ReflowBlock>,
) {
    val sentenceIds: List<Int>
        get() = blocks.flatMap { block -> block.sentences.map { it.id } }

    fun containsText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return blocks.any { it.text.contains(text) || it.sentences.any { sentence -> sentence.text == text } }
    }
}

data class ReflowBlock(
    val paragraphIndex: Int,
    val text: String,
    val isTitle: Boolean,
    val sentences: List<Sentence>,
)

fun paginateChapter(
    chapter: ChapterContent,
    chapterIndex: Int,
    maxWidth: Int,
    maxHeight: Int,
    titleStyle: TextStyle,
    bodyStyle: TextStyle,
    paragraphSpacingPx: Int,
    measurer: TextMeasurer,
): List<ReflowPage> {
    if (maxWidth <= 0 || maxHeight <= 0) {
        return listOf(
            ReflowPage(
                chapterIndex = chapterIndex,
                pageInChapter = 0,
                blocks = listOf(ReflowBlock(-1, chapter.title, true, emptyList())),
            ),
        )
    }
    val pages = mutableListOf<ReflowPage>()
    var blocks = mutableListOf<ReflowBlock>()
    var remaining = maxHeight

    fun flush() {
        if (blocks.isEmpty()) return
        pages += ReflowPage(chapterIndex, pages.size, blocks.toList())
        blocks = mutableListOf()
        remaining = maxHeight
    }

    fun measure(text: String, style: TextStyle): Int {
        if (text.isEmpty()) return 0
        return measurer.measure(
            text = text,
            style = style,
            constraints = Constraints(maxWidth = maxWidth),
        ).size.height
    }

    fun add(block: ReflowBlock, height: Int) {
        val gap = if (blocks.isEmpty()) 0 else paragraphSpacingPx
        if (blocks.isNotEmpty() && gap + height > remaining) {
            flush()
            add(block, height)
            return
        }
        val usedGap = if (blocks.isEmpty()) 0 else paragraphSpacingPx
        blocks += block
        remaining -= usedGap + height
        if (remaining < 0) flush()
    }

    fun addFitted(text: String, style: TextStyle, paragraphIndex: Int, sentences: List<Sentence>, isTitle: Boolean) {
        val height = measure(text, style)
        val gap = if (blocks.isEmpty()) 0 else paragraphSpacingPx
        if (height <= remaining - gap) {
            add(ReflowBlock(paragraphIndex, text, isTitle, sentences), height)
            return
        }
        val measured = measurer.measure(
            text = text,
            style = style,
            constraints = Constraints(maxWidth = maxWidth),
        )
        if (measured.lineCount == 0) return
        var line = 0
        while (line < measured.lineCount) {
            val lineGap = if (blocks.isEmpty()) 0 else paragraphSpacingPx
            val avail = remaining - lineGap
            val lineH = (measured.getLineBottom(line) - measured.getLineTop(line)).toInt().coerceAtLeast(1)
            if (blocks.isNotEmpty() && lineH > avail) {
                flush()
                continue
            }
            val startLine = line
            var endLine = line
            val top = measured.getLineTop(startLine)
            while (endLine < measured.lineCount) {
                val h = (measured.getLineBottom(endLine) - top).toInt()
                if (endLine > startLine && h > remaining - (if (blocks.isEmpty()) 0 else paragraphSpacingPx)) break
                endLine++
            }
            if (endLine == startLine) endLine = (startLine + 1).coerceAtMost(measured.lineCount)
            val start = measured.getLineStart(startLine)
            val end = measured.getLineEnd(endLine - 1, visibleEnd = true).coerceIn(start, text.length)
            val slice = text.substring(start, end).trim()
            if (slice.isNotEmpty()) {
                val h = (measured.getLineBottom(endLine - 1) - measured.getLineTop(startLine)).toInt().coerceAtLeast(lineH)
                add(
                    ReflowBlock(
                        paragraphIndex = paragraphIndex,
                        text = slice,
                        isTitle = isTitle,
                        sentences = sentencesForSlice(text, start, end, sentences),
                    ),
                    h,
                )
            }
            line = endLine
            if (blocks.isNotEmpty() && remaining <= lineH / 4) flush()
        }
    }

    if (chapter.title.isNotBlank()) {
        addFitted(chapter.title, titleStyle, -1, emptyList(), true)
    }
    chapter.paragraphs.forEachIndexed { index, paragraph ->
        val sentences = chapter.sentences.filter { it.paragraphIndex == index }
        addFitted(paragraph, bodyStyle, index, sentences, false)
    }
    flush()
    if (pages.isEmpty()) {
        pages += ReflowPage(chapterIndex, 0, listOf(ReflowBlock(-1, chapter.title, true, emptyList())))
    }
    return pages
}

fun sentenceAtOffset(text: String, sentences: List<Sentence>, offset: Int): Sentence? {
    if (sentences.isEmpty()) return null
    if (sentences.size == 1 && !text.contains(sentences.first().text)) return sentences.first()
    var cursor = 0
    var last: Sentence? = null
    for (sentence in sentences) {
        val start = text.indexOf(sentence.text, cursor)
        if (start < 0) continue
        val end = start + sentence.text.length
        if (offset < start) return last ?: sentence
        if (offset <= end) return sentence
        cursor = end
        last = sentence
    }
    return last ?: sentences.lastOrNull()
}

private fun sentencesForSlice(
    original: String,
    start: Int,
    end: Int,
    sentences: List<Sentence>,
): List<Sentence> {
    if (sentences.isEmpty()) return emptyList()
    var cursor = 0
    val matched = sentences.filter { sentence ->
        val index = original.indexOf(sentence.text, cursor)
        if (index < 0) return@filter false
        cursor = index + sentence.text.length
        index < end && index + sentence.text.length > start
    }
    return matched.ifEmpty { sentences }
}
