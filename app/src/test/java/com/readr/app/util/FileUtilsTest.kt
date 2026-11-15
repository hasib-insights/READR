package com.readr.app.util

import com.readr.app.domain.model.DocumentFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileUtilsTest {

    private val pdfHeader = "%PDF-1.4".toByteArray()
    private val pkHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    @Test
    fun `detects pdf by magic bytes`() {
        assertEquals(DocumentFormat.PDF, FileUtils.sniffFormat(pdfHeader, "renamed.bin"))
        assertEquals(DocumentFormat.PDF, FileUtils.sniffFormat(pdfHeader, null))
    }

    @Test
    fun `detects zip formats by file name`() {
        assertEquals(DocumentFormat.EPUB, FileUtils.sniffFormat(pkHeader, "book.epub"))
        assertEquals(DocumentFormat.DOCX, FileUtils.sniffFormat(pkHeader, "notes.docx"))
    }

    @Test
    fun `unknown zip requires archive inspection`() {
        assertNull(FileUtils.sniffFormat(pkHeader, "archive.bin"))
        assertNull(FileUtils.sniffFormat(pkHeader, null))
    }

    @Test
    fun `detects plain text by name and content`() {
        assertEquals(DocumentFormat.TXT, FileUtils.sniffFormat("hello world".toByteArray(), "note.txt"))
        assertEquals(DocumentFormat.TXT, FileUtils.sniffFormat("hello world".toByteArray(), null))
        assertNull(FileUtils.sniffFormat(byteArrayOf(0, 1, 2, 0), null))
    }

    @Test
    fun `sniffs zip archives by characteristic entries`() {
        assertEquals(
            DocumentFormat.DOCX,
            FileUtils.sniffZipFormat(listOf("word/document.xml", "docProps/core.xml"), "x.epub"),
        )
        assertEquals(
            DocumentFormat.EPUB,
            FileUtils.sniffZipFormat(listOf("META-INF/container.xml", "OEBPS/x.xhtml"), null),
        )
        assertEquals(
            DocumentFormat.EPUB,
            FileUtils.sniffZipFormat(listOf("mimetype", "content.opf"), null),
        )
        assertNull(FileUtils.sniffZipFormat(listOf("some/data.bin"), null))
    }

    @Test
    fun `formats human sizes`() {
        assertEquals("512 B", FileUtils.humanSize(512))
        assertEquals("1.5 KB", FileUtils.humanSize(1536))
        assertEquals("2.0 MB", FileUtils.humanSize(2 * 1024 * 1024))
    }

    @Test
    fun `collapses whitespace`() {
        assertEquals("a b c", FileUtils.collapseWhitespace("a \n\t b   c"))
        assertEquals("", FileUtils.collapseWhitespace("   "))
    }
}
