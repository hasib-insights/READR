package com.readr.app.engine.docx

import com.readr.app.TestZips
import com.readr.app.domain.model.TextBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DocxEngineTest {

    private val contentTypes = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="xml" ContentType="application/xml"/>
        </Types>
    """.trimIndent()

    private val styles = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:style w:type="paragraph" w:styleId="Heading1">
            <w:name w:val="heading 1"/>
            <w:pPr><w:outlineLvl w:val="0"/></w:pPr>
          </w:style>
          <w:style w:type="paragraph" w:styleId="Heading2">
            <w:name w:val="heading 2"/>
            <w:pPr><w:outlineLvl w:val="1"/></w:pPr>
          </w:style>
          <w:style w:type="paragraph" w:styleId="Title">
            <w:name w:val="Title"/>
          </w:style>
        </w:styles>
    """.trimIndent()

    private val document = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:body>
            <w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr><w:r><w:t>The Paper Trail</w:t></w:r></w:p>
            <w:p><w:r><w:t>An introduction with </w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>bold text</w:t></w:r><w:r><w:rPr><w:i/></w:rPr><w:t> and italics</w:t></w:r>.</w:p>
            <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Chapter One</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr/></w:pPr><w:r><w:t>A list item about paper</w:t></w:r></w:p>
            <w:tbl>
              <w:tr><w:tc><w:p><w:r><w:t>Format</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Reader</w:t></w:r></w:p></w:tc></w:tr>
              <w:tr><w:tc><w:p><w:r><w:t>EPUB</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>READR</w:t></w:r></w:p></w:tc></w:tr>
            </w:tbl>
            <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Chapter Two</w:t></w:r></w:p>
            <w:p><w:r><w:t>Tabbed</w:t></w:r><w:r><w:tab/></w:r><w:r><w:t>values</w:t></w:r></w:p>
          </w:body>
        </w:document>
    """.trimIndent()

    private val coreProps = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
            xmlns:dc="http://purl.org/dc/elements/1.1/">
          <dc:title>The Paper Trail</dc:title>
          <dc:creator>A. Author</dc:creator>
        </cp:coreProperties>
    """.trimIndent()

    private fun fixture(): File = TestZips.buildZip(
        listOf(
            "[Content_Types].xml" to contentTypes.toByteArray(),
            "word/styles.xml" to styles.toByteArray(),
            "word/document.xml" to document.toByteArray(),
            "docProps/core.xml" to coreProps.toByteArray(),
        )
    )

    @Test
    fun `parses metadata and splits chapters on Title and Heading1`() {
        val book = DocxEngine.parse(fixture())
        assertEquals("The Paper Trail", book.title)
        assertEquals("A. Author", book.author)
        // Title, "Chapter One" and "Chapter Two" each start a new chapter.
        assertEquals(3, book.document.chapters.size)
        assertEquals("The Paper Trail", book.document.chapters[0].title)
        assertEquals("Chapter One", book.document.chapters[1].title)
        assertEquals("Chapter Two", book.document.chapters[2].title)
    }

    @Test
    fun `parses inline formatting`() {
        val parsed = DocxEngine.parse(fixture()).document
        val intro = parsed.chapters[0].blockAt(1) as TextBlock.Paragraph
        assertTrue(intro.spans.any { it.bold && it.text == "bold text" })
        assertTrue(intro.spans.any { it.italic && it.text == " and italics" })
        assertEquals(0, intro.headingLevel)
    }

    @Test
    fun `parses list items and simple tables inside a chapter`() {
        val parsed = DocxEngine.parse(fixture()).document
        val chapter = parsed.chapters[1]
        val listItem = chapter.blockAt(1) as TextBlock.Paragraph
        assertTrue(listItem.isListItem)

        val table = chapter.blockAt(2) as TextBlock.Table
        assertEquals(listOf("Format", "Reader"), table.rows[0])
        assertEquals(listOf("EPUB", "READR"), table.rows[1])
    }

    @Test
    fun `tabs become spaces inside runs`() {
        val parsed = DocxEngine.parse(fixture()).document
        val para = parsed.chapters[2].blockAt(1) as TextBlock.Paragraph
        assertTrue(para.spans.joinToString("") { it.text }.contains("Tabbed    values"))
    }

    @Test
    fun `documents without core props keep null metadata`() {
        val file = TestZips.buildZip(
            listOf(
                "[Content_Types].xml" to contentTypes.toByteArray(),
                "word/document.xml" to document.toByteArray(),
            )
        )
        val book = DocxEngine.parse(file)
        assertNull(book.title)
        assertNull(book.author)
        assertNotNull(book.document)
    }
}
