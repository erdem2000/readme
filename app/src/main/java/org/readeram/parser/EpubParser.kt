package org.readeram.parser

import android.content.Context
import android.net.Uri
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.zip.ZipFile

class EpubParser(private val context: Context) {
    fun parseMeta(uri: Uri, cacheKey: String): BookMeta {
        return withZip(uri, cacheKey) { zip ->
            val opf = readOpf(zip)
            val title = opf.title
            val author = opf.author
            val cover = opf.coverHref?.let { href ->
                zip.getEntry(href)?.let { entry ->
                    zip.getInputStream(entry).use { it.readBytes() }
                }
            }
            BookMeta(title = title, author = author, format = BookFormat.Epub, coverBytes = cover)
        }
    }

    fun open(uri: Uri, cacheKey: String): OpenedBook.Reflow {
        return withZip(uri, cacheKey) { zip ->
            val opf = readOpf(zip)
            val chapters = mutableListOf<ChapterContent>()
            val allSentences = mutableListOf<Sentence>()
            opf.spineHrefs.forEachIndexed { index, href ->
                val html = zip.getEntry(href)?.let { entry ->
                    zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                } ?: return@forEachIndexed
                val doc = Jsoup.parse(html)
                doc.select("script, style, nav").remove()
                val title = doc.selectFirst("h1, h2, h3, title")?.text()?.ifBlank { null }
                    ?: context.getString(org.readeram.R.string.chapter_n, index + 1)
                val paragraphs = doc.body()
                    .select("p, h1, h2, h3, h4, li, blockquote")
                    .map { it.text().trim() }
                    .filter { it.isNotEmpty() }
                    .ifEmpty {
                        doc.body().text().trim().takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()
                    }
                val sentences = TextSplitter.paragraphsToSentences(paragraphs, index, allSentences.size)
                allSentences += sentences
                chapters += ChapterContent(title = title, paragraphs = paragraphs, sentences = sentences)
            }
            OpenedBook.Reflow(
                meta = BookMeta(
                    title = opf.title,
                    author = opf.author,
                    format = BookFormat.Epub,
                    pageCount = chapters.size,
                    coverBytes = null,
                ),
                chapters = chapters.ifEmpty {
                    listOf(
                        ChapterContent(
                            title = opf.title,
                            paragraphs = listOf(""),
                            sentences = emptyList(),
                        ),
                    )
                },
                sentences = allSentences,
            )
        }
    }

    private data class Opf(
        val title: String,
        val author: String?,
        val coverHref: String?,
        val spineHrefs: List<String>,
    )

    private fun readOpf(zip: ZipFile): Opf {
        val containerXml = zip.read("META-INF/container.xml")
            ?: error("EPUB container.xml missing")
        val container = Jsoup.parse(containerXml, "", Parser.xmlParser())
        val opfPath = container.select("rootfile").first()?.attr("full-path")
            ?: error("OPF path missing")
        val opfDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "").let {
            if (it.isEmpty()) "" else "$it/"
        }
        val opfXml = zip.read(opfPath) ?: error("OPF missing")
        val opf = Jsoup.parse(opfXml, "", Parser.xmlParser())
        val title = opf.getElementsByTag("title").first()?.text()?.ifBlank { null }
            ?: File(opfPath).nameWithoutExtension
        val author = opf.getElementsByTag("creator").first()?.text()?.ifBlank { null }
        val manifest = opf.select("manifest > item").associate { item ->
            item.attr("id") to resolve(opfDir, item.attr("href"))
        }
        val coverId = opf.select("meta[name=cover]").attr("content").ifBlank { null }
            ?: opf.select("manifest > item[properties~=cover-image]").attr("id").ifBlank { null }
        val coverHref = coverId?.let { manifest[it] }
            ?: manifest.values.firstOrNull { it.lowercase().matches(Regex(".*cover.*\\.(jpe?g|png|webp|gif)$")) }
        val spine = opf.select("spine > itemref").mapNotNull { ref ->
            manifest[ref.attr("idref")]
        }.filter { href ->
            val lower = href.lowercase()
            lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".xml")
        }
        return Opf(title = title, author = author, coverHref = coverHref, spineHrefs = spine)
    }

    private fun resolve(baseDir: String, href: String): String {
        val raw = href.substringBefore('#').replace('\\', '/')
        val decoded = try {
            URLDecoder.decode(raw, Charsets.UTF_8.name())
        } catch (_: Exception) {
            raw
        }
        return normalizePath(baseDir + decoded)
    }

    private fun normalizePath(path: String): String {
        val parts = mutableListOf<String>()
        path.split('/').forEach { segment ->
            when (segment) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += segment
            }
        }
        return parts.joinToString("/")
    }

    private fun ZipFile.read(path: String): String? {
        val entry = getEntry(path) ?: getEntry(path.replace('/', '\\')) ?: return null
        return getInputStream(entry).use { it.readBytes().toString(Charset.forName("UTF-8")) }
    }

    private fun <T> withZip(uri: Uri, cacheKey: String, block: (ZipFile) -> T): T {
        val file = copyToCache(uri, cacheKey)
        return ZipFile(file).use(block)
    }

    private fun copyToCache(uri: Uri, cacheKey: String): File {
        val dir = File(context.cacheDir, "epub")
        dir.mkdirs()
        val file = File(dir, "$cacheKey.epub")
        if (file.length() > 0) return file
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot open EPUB")
        return file
    }
}
