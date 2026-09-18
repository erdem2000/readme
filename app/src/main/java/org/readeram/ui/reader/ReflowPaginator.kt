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
    titleSpacingPx: Int,
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
    // 2px slack covers rounding between TextMeasurer and the Text composable.
    var remaining = (maxHeight - 2).coerceAtLeast(0)
    val pageHeight = remaining

    fun flush() {
        if (blocks.isEmpty()) return
        pages += ReflowPage(chapterIndex, pages.size, blocks.toList())
        blocks = mutableListOf()
        remaining = pageHeight
    }

    fun spacingAfter(isTitle: Boolean) = if (isTitle) titleSpacingPx else paragraphSpacingPx

    fun measure(text: String, style: TextStyle): Int {
        if (text.isEmpty()) return 0
        return measurer.measure(
            text = text,
            style = style,
            constraints = Constraints(maxWidth = maxWidth),
        ).size.height
    }

    fun add(block: ReflowBlock, height: Int) {
        val used = height + spacingAfter(block.isTitle)
        if (blocks.isNotEmpty() && used > remaining) {
            flush()
            add(block, height)
            return
        }
        blocks += block
        remaining -= used
        if (remaining <= 0) flush()
    }

    fun addFitted(
        text: String,
        style: TextStyle,
        paragraphIndex: Int,
        sentences: List<Sentence>,
        isTitle: Boolean,
    ) {
        var index = 0
        while (index < text.length) {
            while (index < text.length && text[index].isWhitespace()) index++
            if (index >= text.length) break

            val spacing = spacingAfter(isTitle)
            if (blocks.isNotEmpty() && remaining <= spacing) {
                flush()
                continue
            }

            val chunk = text.substring(index)
            val avail = (remaining - spacing).coerceAtLeast(0)
            val split = takeFittingPrefix(chunk, style, avail, measurer, maxWidth)
            if (split == null) {
                if (blocks.isNotEmpty()) {
                    flush()
                    continue
                }
                val forced = firstLine(chunk, style, measurer, maxWidth) ?: return
                if (forced.consumed <= 0) break
                add(
                    ReflowBlock(
                        paragraphIndex = paragraphIndex,
                        text = forced.text,
                        isTitle = isTitle,
                        sentences = sentencesForSlice(text, index, index + forced.consumed, sentences),
                    ),
                    measure(forced.text, style),
                )
                index += forced.consumed
                if (index < text.length) flush()
                continue
            }

            if (split.consumed <= 0) break
            val prefix = split.text
            add(
                ReflowBlock(
                    paragraphIndex = paragraphIndex,
                    text = prefix,
                    isTitle = isTitle,
                    sentences = sentencesForSlice(text, index, index + split.consumed, sentences),
                ),
                measure(prefix, style),
            )
            index += split.consumed
            if (index < text.length) flush()
        }
    }

    if (chapter.title.isNotBlank()) {
        addFitted(chapter.title, titleStyle, -1, emptyList(), true)
    }
    chapter.paragraphs.forEachIndexed { pIndex, paragraph ->
        val sentences = chapter.sentences.filter { it.paragraphIndex == pIndex }
        addFitted(paragraph, bodyStyle, pIndex, sentences, false)
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

private data class TextSplit(val text: String, val consumed: Int)

private fun takeFittingPrefix(
    text: String,
    style: TextStyle,
    maxHeight: Int,
    measurer: TextMeasurer,
    maxWidth: Int,
): TextSplit? {
    if (text.isEmpty() || maxHeight <= 0) return null
    val constraints = Constraints(maxWidth = maxWidth)
    val full = measurer.measure(text = text, style = style, constraints = constraints)
    if (full.size.height <= maxHeight) {
        return TextSplit(text.trimEnd(), text.length)
    }
    if (full.lineCount == 0) return null

    var lastFit: TextSplit? = null
    for (line in 0 until full.lineCount) {
        val end = full.getLineEnd(line, visibleEnd = true).coerceIn(0, text.length)
        if (end <= 0) continue
        val prefix = text.substring(0, end).trimEnd()
        if (prefix.isEmpty()) continue
        val height = measurer.measure(text = prefix, style = style, constraints = constraints).size.height
        if (height <= maxHeight) {
            lastFit = TextSplit(prefix, end)
        } else {
            break
        }
    }
    return lastFit
}

private fun firstLine(
    text: String,
    style: TextStyle,
    measurer: TextMeasurer,
    maxWidth: Int,
): TextSplit? {
    val measured = measurer.measure(
        text = text,
        style = style,
        constraints = Constraints(maxWidth = maxWidth),
    )
    if (measured.lineCount == 0) return null
    val end = measured.getLineEnd(0, visibleEnd = true).coerceIn(0, text.length)
    val prefix = text.substring(0, end).trimEnd()
    if (prefix.isEmpty()) return null
    return TextSplit(prefix, end)
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
