package com.readr.app.engine.epub

import com.readr.app.TestZips
import com.readr.app.domain.model.TextBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EpubEngineTest {

    private fun buildFixtureEpub(): File {
        val container = """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>
        """.trimIndent()

        val opf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>The Sage Bookshelf</dc:title>
                <dc:creator>H. Writer</dc:creator>
                <meta name="cover" content="cover-image"/>
              </metadata>
              <manifest>
                <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="cover-image" href="cover.png" media-type="image/png" properties="cover-image"/>
              </manifest>
              <spine>
                <itemref idref="c1"/>
                <itemref idref="c2"/>
              </spine>
            </package>
        """.trimIndent()

        val chapter1 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
              <head><title>First Chapter</title></head>
              <body>
                <h1>First Chapter</h1>
                <p>Hello <b>bold</b> and <i>italic</i> world &amp; friends.</p>
                <p>Price &mdash;&nbsp;ten &pound;5.</p>
                <ul><li>First item</li><li>Second item</li></ul>
              </body>
            </html>
        """.trimIndent()

        val chapter2 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
              <body>
                <h1>Second Chapter</h1>
                <p>An illustration follows.</p>
                <img src="images/pic.png" alt="pic"/>
                <p>The end.</p>
              </body>
            </html>
        """.trimIndent()

        val nav = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
              <body>
                <nav epub:type="toc">
                  <ol>
                    <li><a href="chapter1.xhtml">First Chapter</a></li>
                    <li><a href="chapter2.xhtml">Second Chapter</a></li>
                  </ol>
                </nav>
              </body>
            </html>
        """.trimIndent()

        return TestZips.buildZip(
            listOf(
                "mimetype" to "application/epub+zip".toByteArray(),
                "META-INF/container.xml" to container.toByteArray(),
                "OEBPS/content.opf" to opf.toByteArray(),
                "OEBPS/chapter1.xhtml" to chapter1.toByteArray(),
                "OEBPS/chapter2.xhtml" to chapter2.toByteArray(),
                "OEBPS/nav.xhtml" to nav.toByteArray(),
                "OEBPS/cover.png" to TestZips.tinyPng,
                "OEBPS/images/pic.png" to TestZips.tinyPng,
            )
        )
    }

    @Test
    fun `parses metadata, spine, toc and cover`() {
        val book = EpubEngine.parse(buildFixtureEpub())

        assertEquals("The Sage Bookshelf", book.title)
        assertEquals("H. Writer", book.author)
        assertNotNull(book.coverBytes)
        assertEquals(2, book.document.chapters.size)
        assertEquals("First Chapter", book.document.chapters[0].title)
        assertEquals("Second Chapter", book.document.chapters[1].title)
        assertTrue((book.document.wordCount ?: 0L) > 0)
    }

    @Test
    fun `parses headings, inline styles, entities and lists`() {
        val document = EpubEngine.parse(buildFixtureEpub()).document
        val chapter = document.chapters[0]

        val heading = chapter.blockAt(0) as TextBlock.Paragraph
        assertEquals(1, heading.headingLevel)
        assertEquals("First Chapter", heading.spans.first().text)

        val body = chapter.blockAt(1) as TextBlock.Paragraph
        assertEquals("Hello ", body.spans[0].text)
        assertTrue(body.spans[1].bold)
        assertEquals("bold", body.spans[1].text)
        assertEquals(" and ", body.spans[2].text)
        assertTrue(body.spans[3].italic)

        val entityPara = chapter.blockAt(2) as TextBlock.Paragraph
        val entityText = entityPara.spans.joinToString("") { it.text }
        assertTrue(entityText.contains("\u2014"))     // &mdash;
        assertTrue(entityText.contains("\u00A0"))     // &nbsp;
        assertTrue(entityText.contains("£5"))         // &pound;5

        // &amp; is resolved by the XML parser itself
        val bodyText = body.spans.joinToString("") { it.text }
        assertTrue(bodyText.contains("world & friends."))

        val listItem = chapter.blockAt(3) as TextBlock.Paragraph
        assertTrue(listItem.isListItem)
        assertEquals("First item", listItem.spans.first().text)
    }

    @Test
    fun `collects inline images with resolved zip paths`() {
        val document = EpubEngine.parse(buildFixtureEpub()).document
        val blocks = document.chapters[1]
        val imageBlock = (1 until blocks.blockCount)
            .map { blocks.blockAt(it) }
            .filterIsInstance<TextBlock.Paragraph>()
            .first { it.imageSource != null }
        assertEquals("OEBPS/images/pic.png", imageBlock.imageSource)
    }

    @Test
    fun `resolves relative zip paths`() {
        assertEquals("OEBPS/x.xhtml", EpubEngine.resolveZipPath("OEBPS", "x.xhtml"))
        assertEquals("OEBPS/texts/y.xhtml", EpubEngine.resolveZipPath("OEBPS", "texts/y.xhtml"))
        assertEquals("A/z.xhtml", EpubEngine.resolveZipPath("A/B", "../z.xhtml"))
        assertEquals("z.xhtml", EpubEngine.resolveZipPath("", "./z.xhtml"))
        assertEquals("OEBPS/a%20b.xhtml".replace("%20", " "), EpubEngine.resolveZipPath("OEBPS", "a%20b.xhtml"))
    }

    @Test
    fun `resolver reads images from the package`() {
        val file = buildFixtureEpub()
        EpubImageResolver(file).use { resolver ->
            val bytes = resolver.image("OEBPS/cover.png")
            assertNotNull(bytes)
            assertEquals(TestZips.tinyPng.size, bytes!!.size)
            assertEquals(null, resolver.image("missing.png"))
        }
    }
}
