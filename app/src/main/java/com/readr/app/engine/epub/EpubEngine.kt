package com.readr.app.engine.epub

import com.readr.app.domain.model.TextBlock
import com.readr.app.domain.model.TextChapter
import com.readr.app.domain.model.TextDocument
import com.readr.app.domain.model.plainText
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

/** Parsed EPUB: metadata, embedded cover bytes and the unified text document. */
class EpubBook(
    val title: String?,
    val author: String?,
    val coverBytes: ByteArray?,
    val document: TextDocument,
)

/**
 * EPUB engine: reads real EPUB 2/3 packages without external dependencies.
 *
 * container.xml → OPF (metadata, manifest, spine) → XHTML chapters.
 * Chapter titles come from the EPUB3 navigation document or the EPUB2 NCX
 * when available. Inline images are resolved lazily through [EpubImageResolver].
 *
 * Known limitations (documented in the README): CSS styling is not applied
 * (READR uses its own paper + typography), footnotes are inlined as text.
 */
object EpubEngine {

    fun parse(file: File): EpubBook {
        ZipFile(file).use { zip ->
            val opfPath = readOpfPath(zip)
            val opfEntry = zip.getEntry(opfPath)
                ?: zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf") }
                ?: error("EPUB package document (.opf) not found")
            val opfDir = opfEntry.name.substringBeforeLast('/', "")
            val opf = parseOpf(zip.getInputStream(opfEntry).readBytes())

            val coverBytes = opf.coverHref
                ?.let { zip.getEntry(resolveZipPath(opfDir, it)) }
                ?.let { zip.getInputStream(it).readBytes() }

            val toc = readToc(zip, opf, opfDir)

            val chapters = ArrayList<TextChapter>()
            var totalChars = 0L
            for (item in opf.spine) {
                val entryPath = resolveZipPath(opfDir, item.href)
                val entry = zip.getEntry(entryPath) ?: continue
                val parsed = XhtmlParser.parse(zip.getInputStream(entry).readBytes())
                if (parsed.blocks.isEmpty()) continue
                // Resolve inline image sources against the chapter's base directory.
                val base = entryPath.substringBeforeLast('/', "")
                val blocks = parsed.blocks.map { block ->
                    if (block is TextBlock.Paragraph && block.imageSource != null) {
                        block.copy(imageSource = resolveZipPath(base, block.imageSource!!))
                    } else {
                        block
                    }
                }
                val chapterTitle = toc[entryPath]
                    ?: parsed.title?.takeIf { it.isNotBlank() }
                    ?: "Chapter ${chapters.size + 1}"
                chapters.add(TextChapter(entryPath, chapterTitle, blocks.size) { blocks[it] })
                totalChars += blocks.sumOf { it.plainText().length }
            }
            if (chapters.isEmpty()) error("EPUB has no readable chapters")

            val wordCount = (totalChars / 5.5).toLong().coerceAtLeast(1)
            val document = TextDocument(
                title = opf.title ?: file.nameWithoutExtension,
                author = opf.author,
                chapters = chapters,
                wordCount = wordCount,
            )
            return EpubBook(opf.title, opf.author, coverBytes, document)
        }
    }

    // --- container.xml ---

    private fun readOpfPath(zip: ZipFile): String {
        val entry = zip.getEntry("META-INF/container.xml")
            ?: return "content.opf"
        val text = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
        val parser = newParser(text)
        while (true) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> if (parser.name.equals("rootfile", true)) {
                    return parser.getAttributeValue(null, "full-path") ?: "content.opf"
                }
                XmlPullParser.END_DOCUMENT -> return "content.opf"
            }
            parser.next()
        }
    }

    // --- OPF ---

    private class ManifestItem(val id: String, val href: String, val mediaType: String, val properties: String)
    private class Opf(
        val title: String?, val author: String?,
        val manifest: Map<String, ManifestItem>,
        val spine: List<ManifestItem>,
        val coverHref: String?,
    )

    private fun parseOpf(bytes: ByteArray): Opf {
        val parser = newParser(String(bytes, Charsets.UTF_8))
        val titles = ArrayList<String>()
        val creators = ArrayList<String>()
        val manifest = LinkedHashMap<String, ManifestItem>()
        val spine = ArrayList<ManifestItem>()
        var coverHref: String? = null
        var coverMetaId: String? = null
        var inMetadata = false
        var inManifest = false
        var inSpine = false

        var event = parser.eventType
        loop@ while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    // Non-namespace parser: element names may carry prefixes (dc:title).
                    val name = parser.name.lowercase().substringAfterLast(':')
                    when {
                        name == "metadata" -> inMetadata = true
                        name == "manifest" -> inManifest = true
                        name == "spine" -> {
                            inSpine = true
                            parser.getAttributeValue(null, "toc")?.let { coverMetaId = it }
                        }
                        name == "title" && inMetadata -> titles.add(runCatching { parser.nextText().trim() }.getOrDefault(""))
                        name == "creator" && inMetadata -> creators.add(runCatching { parser.nextText().trim() }.getOrDefault(""))
                        name == "meta" && inMetadata &&
                            parser.getAttributeValue(null, "name") == "cover" ->
                            coverMetaId = parser.getAttributeValue(null, "content")
                        name == "item" && inManifest -> {
                            val id = parser.getAttributeValue(null, "id") ?: ""
                            val href = parser.getAttributeValue(null, "href") ?: ""
                            val media = parser.getAttributeValue(null, "media-type") ?: ""
                            val props = parser.getAttributeValue(null, "properties") ?: ""
                            manifest[id] = ManifestItem(id, href, media, props)
                            if (props.split(' ').contains("cover-image") &&
                                media.startsWith("image/") && coverHref == null
                            ) coverHref = href
                        }
                        name == "itemref" && inSpine -> {
                            val idref = parser.getAttributeValue(null, "idref") ?: ""
                            manifest[idref]?.let { spine.add(it) }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val name = parser.name.lowercase().substringAfterLast(':')
                    when (name) {
                        "metadata" -> inMetadata = false
                        "manifest" -> inManifest = false
                        "spine" -> inSpine = false
                    }
                }

                XmlPullParser.END_DOCUMENT -> break@loop
            }
            event = parser.next()
        }

        if (coverHref == null && coverMetaId != null) {
            coverHref = manifest[coverMetaId]?.href?.takeIf {
                it.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "gif")
            }
        }

        val item = Opf(
            title = titles.firstOrNull()?.takeIf { it.isNotBlank() },
            author = creators.firstOrNull()?.takeIf { it.isNotBlank() },
            manifest = manifest,
            spine = spine,
            coverHref = coverHref,
        )
        if (item.spine.isEmpty()) error("EPUB spine is empty")
        return item
    }

    // --- TOC (EPUB3 nav document or EPUB2 NCX) ---

    private fun readToc(zip: ZipFile, opf: Opf, opfDir: String): Map<String, String> {
        val navItem = opf.manifest.values.firstOrNull { it.properties.split(' ').contains("nav") }
        if (navItem != null) {
            val navPath = resolveZipPath(opfDir, navItem.href)
            val entry = zip.getEntry(navPath)
            if (entry != null) {
                val map = parseNavDocument(zip.getInputStream(entry).readBytes(), navPath.substringBeforeLast('/', ""))
                if (map.isNotEmpty()) return map
            }
        }
        val ncxItem = opf.manifest.values.firstOrNull {
            it.mediaType == "application/x-dtbncx+xml"
        }
        if (ncxItem != null) {
            val ncxPath = resolveZipPath(opfDir, ncxItem.href)
            val entry = zip.getEntry(ncxPath)
            if (entry != null) {
                val map = parseNcx(zip.getInputStream(entry).readBytes(), ncxPath.substringBeforeLast('/', ""))
                if (map.isNotEmpty()) return map
            }
        }
        return emptyMap()
    }

    private fun parseNavDocument(bytes: ByteArray, base: String): Map<String, String> {
        val parser = newParser(String(bytes, Charsets.UTF_8))
        val map = LinkedHashMap<String, String>()
        var inAnchor = false
        var href: String? = null
        val text = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name.equals("a", true)) {
                    inAnchor = true
                    href = parser.getAttributeValue(null, "href")
                    text.setLength(0)
                }
                XmlPullParser.TEXT -> if (inAnchor) text.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name.equals("a", true) && inAnchor) {
                    inAnchor = false
                    val h = href
                    if (h != null) {
                        val resolved = resolveZipPath(base, h.substringBefore('#'))
                        map[resolved] = text.toString().trim()
                    }
                }
            }
            event = parser.next()
        }
        return map
    }

    private fun parseNcx(bytes: ByteArray, base: String): Map<String, String> {
        val parser = newParser(String(bytes, Charsets.UTF_8))
        val map = LinkedHashMap<String, String>()
        var label = StringBuilder()
        var pendingLabel: String? = null
        var inText = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "text" -> { label = StringBuilder(); inText = true }
                        "content" -> {
                            inText = false
                            val src = parser.getAttributeValue(null, "src")
                            val current = label.toString().trim().ifEmpty { pendingLabel }
                            if (src != null && !current.isNullOrEmpty()) {
                                val resolved = resolveZipPath(base, src.substringBefore('#'))
                                map[resolved] = current
                                pendingLabel = null
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> if (inText) label.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name.lowercase() == "text") inText = false
            }
            event = parser.next()
        }
        return map
    }

    // --- helpers ---

    internal fun newParser(text: String): XmlPullParser {
        val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)), null)
        return parser
    }

    /** Resolves a relative href against its base directory into a zip entry path. */
    internal fun resolveZipPath(baseDir: String, href: String): String {
        val decoded = try {
            URLDecoder.decode(href.trim().replace('\\', '/'), "UTF-8")
        } catch (e: Exception) {
            href.trim().replace('\\', '/')
        }
        val baseParts = if (baseDir.isEmpty()) emptyList() else baseDir.split('/')
        val parts = ArrayList(baseParts)
        for (segment in decoded.split('/')) {
            when (segment) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(segment)
            }
        }
        return parts.joinToString("/").removePrefix("/")
    }
}

/**
 * Resolves EPUB inline images by zip entry path with a small byte cache.
 * Kept open for the lifetime of the reading session.
 */
class EpubImageResolver(file: File) : AutoCloseable {
    private val zip = ZipFile(file)
    private val cache = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>): Boolean =
            size > 24
    }

    @Synchronized
    fun image(entryPath: String): ByteArray? {
        cache[entryPath]?.let { return it }
        val entry = zip.getEntry(entryPath) ?: return null
        val bytes = zip.getInputStream(entry).readBytes()
        cache[entryPath] = bytes
        return bytes
    }

    override fun close() {
        runCatching { zip.close() }
    }
}
