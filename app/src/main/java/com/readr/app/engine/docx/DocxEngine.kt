package com.readr.app.engine.docx

import com.readr.app.domain.model.StyledSpan
import com.readr.app.domain.model.TextBlock
import com.readr.app.domain.model.TextChapter
import com.readr.app.domain.model.TextDocument
import com.readr.app.domain.model.plainText
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile

class DocxBook(
    val title: String?,
    val author: String?,
    val document: TextDocument,
)

/**
 * DOCX engine — a compatible, lightweight replacement for Apache POI on
 * Android. POI's full API (plus its javax.xml dependencies) is impractical on
 * Android and would multiply the APK size, so READR implements the parts of
 * OOXML it renders: paragraphs, runs with bold/italic/underline, headings via
 * outline levels, list items and simple tables, straight from
 * word/document.xml inside the zip package.
 *
 * It is NOT a Microsoft Word layout engine: complex layouts (text boxes,
 * footnotes, headers/footers, floating objects) are not reproduced.
 */
object DocxEngine {

    fun parse(file: File): DocxBook {
        ZipFile(file).use { zip ->
            val styles = readStyles(zip)
            val core = runCatching { readCoreProps(zip) }.getOrDefault(null to null)
            val docEntry = zip.getEntry("word/document.xml")
                ?: error("Not a valid DOCX file (missing word/document.xml)")

            val body = BodyParser(styles).parse(zip.getInputStream(docEntry).readBytes())
            if (body.chapters.isEmpty()) error("No readable content in document")

            val document = TextDocument(
                title = core.first ?: file.nameWithoutExtension,
                author = core.second,
                chapters = body.chapters,
                wordCount = body.totalChars.let { (it / 5.5).toLong().coerceAtLeast(1) },
            )
            return DocxBook(core.first, core.second, document)
        }
    }

    // --- styles.xml: styleId -> (style name, outline level) ---

    private class StyleInfo(val name: String?, val outlineLevel: Int?)

    private fun readStyles(zip: ZipFile): Map<String, StyleInfo> {
        val entry = zip.getEntry("word/styles.xml") ?: return emptyMap()
        val parser = newParser(zip.getInputStream(entry).readBytes())
        val result = HashMap<String, StyleInfo>()
        var styleId: String? = null
        var name: String? = null
        var outline: Int? = null
        var inStyle = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfterLast(':')) {
                    "style" -> {
                        inStyle = true
                        styleId = attr(parser, "styleId")
                        name = null
                        outline = null
                    }
                    "name" -> if (inStyle) name = attr(parser, "val")
                    "outlineLvl" -> if (inStyle) outline = attr(parser, "val")?.toIntOrNull()?.coerceIn(0, 8)
                }
                XmlPullParser.END_TAG -> if (parser.name.substringAfterLast(':') == "style") {
                    val id = styleId
                    if (id != null && (outline != null || name != null)) {
                        result[id] = StyleInfo(name, outline)
                    }
                    inStyle = false
                }
            }
            event = parser.next()
        }
        return result
    }

    // --- docProps/core.xml: title, author ---

    private fun readCoreProps(zip: ZipFile): Pair<String?, String?> {
        val entry = zip.getEntry("docProps/core.xml") ?: return null to null
        val parser = newParser(zip.getInputStream(entry).readBytes())
        var title: String? = null
        var creator: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.substringAfterLast(':')) {
                    "title" -> title = runCatching { parser.nextText().trim() }
                        .getOrNull()?.takeIf { it.isNotEmpty() }
                    "creator" -> creator = runCatching { parser.nextText().trim() }
                        .getOrNull()?.takeIf { it.isNotEmpty() }
                }
            }
            event = parser.next()
        }
        return title to creator
    }

    // --- body parsing ---

    private class RunSeg(val text: String, val bold: Boolean, val italic: Boolean, val underline: Boolean)

    private class BodyParser(private val styles: Map<String, StyleInfo>) {

        val chapters = ArrayList<TextChapter>()
        var totalChars = 0L

        private val currentBlocks = ArrayList<TextBlock>()
        private var currentChapterTitle: String? = null

        fun parse(bytes: ByteArray): BodyParser {
            val parser = newParser(bytes)
            var event = parser.eventType
            var inBody = false
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val name = parser.name.substringAfterLast(':')
                    if (name == "body") inBody = true
                    else if (inBody && name == "p") {
                        val paragraph = parseParagraph(parser)
                        if (paragraph != null) accept(paragraph.first, paragraph.second)
                    } else if (inBody && name == "tbl") {
                        val table = parseTable(parser)
                        if (table != null && table.isNotEmpty()) accept(null, TextBlock.Table(table))
                    }
                } else if (event == XmlPullParser.END_TAG && parser.name.substringAfterLast(':') == "body") {
                    inBody = false
                }
                event = parser.next()
            }
            finishChapter()
            return this
        }

        private fun accept(headingLevel: Int?, block: TextBlock) {
            if (headingLevel == 1) finishChapter()
            if (currentChapterTitle == null && headingLevel != null) {
                currentChapterTitle = block.plainText().trim().take(120).ifEmpty { null }
            }
            currentBlocks.add(block)
            totalChars += block.plainText().length
        }

        private fun finishChapter() {
            if (currentBlocks.isEmpty()) return
            val blocks = ArrayList(currentBlocks)
            val index = chapters.size + 1
            chapters.add(
                TextChapter(
                    id = "chapter-$index",
                    title = currentChapterTitle ?: "Section $index",
                    blockCount = blocks.size,
                ) { blocks[it] }
            )
            currentBlocks.clear()
            currentChapterTitle = null
        }

        /**
         * Parses one w:p; returns (headingLevel, block) or null for blank paragraphs.
         *
         * Depth counting: XmlPullParser emits an END_TAG for self-closing
         * elements too, so the counter is incremented on EVERY start tag and
         * decremented on every end tag; w:t consumes its own end tag through
         * nextText(), compensated with a manual decrement.
         *
         * Run flags follow OOXML semantics: b/i/u appear inside w:rPr of the
         * current run and apply to that run's text.
         */
        private fun parseParagraph(parser: XmlPullParser): Pair<Int?, TextBlock.Paragraph>? {
            var depth = 1 // consumed the w:p START_TAG
            var styleId: String? = null
            var isListItem = false
            var runBold = false
            var runItalic = false
            var runUnderline = false
            var segStart = -1
            val buf = StringBuilder()
            val segments = ArrayList<RunSeg>()

            fun closeSeg() {
                if (segStart < 0) return
                val t = buf.substring(segStart)
                if (t.isNotEmpty()) segments.add(RunSeg(t, runBold, runItalic, runUnderline))
                segStart = -1
            }

            while (depth > 0) {
                when (parser.next()) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        when (parser.name.substringAfterLast(':')) {
                            "pStyle" -> styleId = attr(parser, "val")
                            "numPr" -> isListItem = true
                            "r" -> { closeSeg(); runBold = false; runItalic = false; runUnderline = false }
                            "b" -> runBold = !isOff(parser)
                            "i" -> runItalic = !isOff(parser)
                            "u" -> runUnderline = !isOff(parser)
                            "t" -> {
                                if (segStart < 0) segStart = buf.length
                                buf.append(parser.nextText())
                                depth-- // nextText() consumed the END_TAG
                            }
                            "tab" -> { if (segStart < 0) segStart = buf.length; buf.append("    ") }
                            "br" -> { closeSeg(); buf.append('\n'); segStart = buf.length }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        depth--
                        if (parser.name.substringAfterLast(':') == "r") closeSeg()
                    }
                }
            }
            closeSeg()

            val text = buf.toString().trim()
            val info = styleId?.let { styles[it] }
            val headingLevel = when {
                info?.outlineLevel != null && info.outlineLevel <= 4 -> info.outlineLevel + 1
                info?.name?.lowercase()?.matches(Regex("heading\\s*[1-5].*")) == true ->
                    Regex("heading\\s*([1-5])").find(info.name.lowercase())!!.groupValues[1].toInt()
                info?.name?.equals("Title", ignoreCase = true) == true -> 1
                else -> null
            }
            if (text.isEmpty() && headingLevel == null) return null
            val spans = if (segments.isEmpty()) listOf(StyledSpan(text))
            else segments.map { StyledSpan(it.text, it.bold, it.italic, it.underline) }
            return headingLevel to TextBlock.Paragraph(spans, headingLevel ?: 0, isListItem)
        }

        /** Parses one w:tbl into rows of plain-text cells. */
        private fun parseTable(parser: XmlPullParser): List<List<String>> {
            var depth = 1
            val rows = ArrayList<List<String>>()
            var row: MutableList<String>? = null
            var cell = StringBuilder()
            var inCell = false
            while (depth > 0) {
                when (parser.next()) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        when (parser.name.substringAfterLast(':')) {
                            "tr" -> row = ArrayList()
                            "tc" -> { inCell = true; cell = StringBuilder() }
                            "t" -> if (inCell) {
                                val text = runCatching { parser.nextText() }.getOrNull()
                                if (text != null) {
                                    cell.append(text)
                                    depth-- // nextText() consumed the END_TAG
                                }
                            }
                            "p" -> if (inCell && cell.isNotEmpty()) cell.append(' ')
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name.substringAfterLast(':')
                        depth--
                        when (name) {
                            "tc" -> { inCell = false; row?.add(cell.toString().trim()) }
                            "tr" -> row?.let { if (it.isNotEmpty()) rows.add(it) }
                        }
                    }
                }
            }
            return rows
        }
    }

    private fun isOff(parser: XmlPullParser): Boolean {
        val v = attr(parser, "val") ?: return false
        return v.equals("false", true) || v == "0" || v.equals("none", true)
    }

    private fun attr(parser: XmlPullParser, name: String): String? {
        // Non-namespace-aware parser: attributes carry their original prefix.
        return parser.getAttributeValue(null, name)
            ?: parser.getAttributeValue(null, "w:$name")
            ?: parser.getAttributeValue(null, "dc:$name")
            ?: parser.getAttributeValue(null, "cp:$name")
    }

    private fun newParser(bytes: ByteArray): XmlPullParser {
        val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        return parser
    }
}
