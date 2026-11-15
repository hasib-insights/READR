package com.readr.app.engine.epub

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream

/**
 * XML named-entity tables. XmlPullParser only understands the five XML core
 * entities, but real-world XHTML uses HTML entities liberally — they are
 * replaced in a pre-processing pass before parsing.
 */
internal object Entities {

    val MAP: Map<String, String> = mapOf(
        // NOTE: &amp; &lt; &gt; &quot; &apos; are intentionally NOT listed —
        // the XML parser resolves the five core entities itself; replacing
        // them here would produce bare '&'/'<' characters and break parsing.
        "nbsp" to "\u00A0", "mdash" to "\u2014", "ndash" to "\u2013", "hellip" to "\u2026",
        "lsquo" to "\u2018", "rsquo" to "\u2019", "ldquo" to "\u201C", "rdquo" to "\u201D",
        "lsquor" to "\u201A", "rsquor" to "\u201B", "laquo" to "\u00AB", "raquo" to "\u00BB",
        "copy" to "\u00A9", "reg" to "\u00AE", "trade" to "\u2122", "deg" to "\u00B0",
        "plusmn" to "\u00B1", "times" to "\u00D7", "divide" to "\u00F7", "middot" to "\u00B7",
        "bull" to "\u2022", "dagger" to "\u2020", "Dagger" to "\u2021", "permil" to "\u2030",
        "prime" to "\u2032", "Prime" to "\u2033", "sect" to "\u00A7", "para" to "\u00B6",
        "frac12" to "\u00BD", "frac14" to "\u00BC", "frac34" to "\u00BE", "sup2" to "\u00B2",
        "sup3" to "\u00B3", "micro" to "\u00B5", "euro" to "\u20AC", "pound" to "\u00A3",
        "yen" to "\u00A5", "cent" to "\u00A2", "curren" to "\u00A4",
        "agrave" to "\u00E0", "aacute" to "\u00E1", "acirc" to "\u00E2", "auml" to "\u00E4",
        "ccedil" to "\u00E7", "eacute" to "\u00E9", "egrave" to "\u00E8", "ecirc" to "\u00EA",
        "euml" to "\u00EB", "iacute" to "\u00ED", "igrave" to "\u00EC", "icirc" to "\u00EE",
        "iuml" to "\u00EF", "ntilde" to "\u00F1", "oacute" to "\u00F3", "ograve" to "\u00F2",
        "ocirc" to "\u00F4", "ouml" to "\u00F6", "ugrave" to "\u00F9", "uacute" to "\u00FA",
        "ucirc" to "\u00FB", "uuml" to "\u00FC", "szlig" to "\u00DF", "aelig" to "\u00E6",
        "oslash" to "\u00F8", "aring" to "\u00E5", "Agrave" to "\u00C0", "Eacute" to "\u00C9",
        "Ecirc" to "\u00CA", "Ntilde" to "\u00D1", "Ouml" to "\u00D6", "Uuml" to "\u00DC",
    )

    private val ENTITY_REGEX = Regex("&([A-Za-z][A-Za-z0-9]*);")

    /** Entities the XML parser must resolve itself — never rewritten here. */
    private val CORE_ENTITIES = setOf("amp", "lt", "gt", "quot", "apos", "AMP", "LT", "GT", "QUOT")

    /** Replaces known named entities; unknown ones become a space so parsing never fails. */
    fun replaceHtmlEntities(text: String): String =
        ENTITY_REGEX.replace(text) { match ->
            val name = match.groupValues[1]
            if (name in CORE_ENTITIES) match.value
            else MAP[name] ?: " "
        }
}

/**
 * Parses XHTML/HTML chapter content into the unified [com.readr.app.domain.model.TextBlock]
 * model. Understands headings h1-h6, p / li / blockquote, inline b/strong/i/em/u
 * and img / svg-image resources. CSS classes are intentionally not resolved —
 * READR applies its own paper + typography styling instead.
 */
internal object XhtmlParser {

    private val factory: XmlPullParserFactory = XmlPullParserFactory.newInstance()

    private val BLOCK_TAGS = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote")
    private val HEADING_TAGS = mapOf("h1" to 1, "h2" to 2, "h3" to 3, "h4" to 4, "h5" to 5, "h6" to 6)
    private const val XLINK = "http://www.w3.org/1999/xlink"

    class Result(
        val blocks: MutableList<com.readr.app.domain.model.TextBlock>,
        val title: String?,
    )

    fun parse(
        bytes: ByteArray,
        onImage: (source: String) -> Unit = {},
    ): Result {
        val cleaned = Entities.replaceHtmlEntities(String(bytes, Charsets.UTF_8))
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(cleaned.toByteArray(Charsets.UTF_8)), null)

        val blocks = ArrayList<com.readr.app.domain.model.TextBlock>()
        var docTitle: String? = null

        var inHead = false
        var inTitle = false
        var titleBuilder = StringBuilder()
        var skipDepth = 0 // inside script/style

        var inBlock = false
        var headingLevel = 0
        var isListItem = false
        var bold = 0
        var italic = 0
        var underline = 0
        var runStart = -1
        val textBuffer = StringBuilder()
        val segments = ArrayList<SpanSeg>()

        fun closeRun() {
            if (runStart < 0) return
            val t = textBuffer.substring(runStart)
            if (t.isNotEmpty()) {
                segments.add(SpanSeg(t, bold > 0, italic > 0, underline > 0))
            }
            runStart = -1
        }

        fun finishParagraph() {
            closeRun()
            val anyText = segments.any { it.text.isNotBlank() }
            if (anyText) {
                val spans = segments
                    .filter { it.text.isNotBlank() }
                    .map { com.readr.app.domain.model.StyledSpan(it.text, it.bold, it.italic, it.underline) }
                blocks.add(com.readr.app.domain.model.TextBlock.Paragraph(spans, headingLevel, isListItem))
            }
            segments.clear()
            textBuffer.setLength(0)
            runStart = -1
            inBlock = false
            headingLevel = 0
            isListItem = false
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.lowercase()
                    when {
                        name == "script" || name == "style" -> skipDepth++
                        name == "head" -> inHead = true
                        name == "title" && inHead -> { inTitle = true; titleBuilder = StringBuilder() }
                        name == "body" -> inHead = false
                        name in BLOCK_TAGS && skipDepth == 0 -> {
                            if (inBlock) finishParagraph()
                            inBlock = true
                            headingLevel = HEADING_TAGS[name] ?: 0
                            isListItem = name == "li"
                        }
                        name == "strong" || name == "b" -> { closeRun(); bold++ }
                        name == "em" || name == "i" -> { closeRun(); italic++ }
                        name == "u" -> { closeRun(); underline++ }
                        name == "img" && skipDepth == 0 -> {
                            val src = parser.getAttributeValue(null, "src")
                                ?: parser.getAttributeValue(XLINK, "href")
                            if (src != null) {
                                if (inBlock && textBuffer.isNotBlank()) finishParagraph()
                                onImage(src)
                                blocks.add(com.readr.app.domain.model.TextBlock.Paragraph(emptyList(), imageSource = src))
                            }
                        }
                        name == "image" && skipDepth == 0 -> {
                            val src = parser.getAttributeValue(XLINK, "href")
                                ?: parser.getAttributeValue(null, "href")
                            if (src != null) {
                                if (inBlock && textBuffer.isNotBlank()) finishParagraph()
                                onImage(src)
                                blocks.add(com.readr.app.domain.model.TextBlock.Paragraph(emptyList(), imageSource = src))
                            }
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    if (skipDepth == 0) {
                        if (inTitle) {
                            titleBuilder.append(parser.text)
                        } else if (inBlock) {
                            if (runStart < 0) runStart = textBuffer.length
                            textBuffer.append(parser.text)
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val name = parser.name.lowercase()
                    when {
                        (name == "script" || name == "style") && skipDepth > 0 -> skipDepth--
                        name == "title" && inTitle -> { inTitle = false; docTitle = titleBuilder.toString().trim() }
                        name == "head" -> inHead = false
                        name == "strong" || name == "b" -> { closeRun(); if (bold > 0) bold-- }
                        name == "em" || name == "i" -> { closeRun(); if (italic > 0) italic-- }
                        name == "u" -> { closeRun(); if (underline > 0) underline-- }
                        name in BLOCK_TAGS && inBlock -> finishParagraph()
                    }
                }
            }
            event = parser.next()
        }
        if (inBlock) finishParagraph()

        return Result(blocks, docTitle)
    }

    private class SpanSeg(val text: String, val bold: Boolean, val italic: Boolean, val underline: Boolean)
}
