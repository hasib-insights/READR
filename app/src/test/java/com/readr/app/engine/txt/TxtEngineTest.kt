package com.readr.app.engine.txt

import com.readr.app.domain.model.TextBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TxtEngineTest {

    private fun tempFile(name: String, bytes: ByteArray): File {
        val file = File.createTempFile(name, ".txt")
        file.deleteOnExit()
        file.writeBytes(bytes)
        return file
    }

    @Test
    fun `scans paragraph offsets for unix line endings`() {
        val file = tempFile("unix", "first\nsecond\n\nthird".toByteArray())
        val offsets = TxtEngine.scanParagraphOffsets(file)
        // "first", "second", "", "third"
        assertEquals(4, offsets.size)
        val provider = TxtParagraphProvider(file, offsets, Charsets.UTF_8)
        assertEquals("first", (provider.load(0) as TextBlock.Paragraph).spans.first().text)
        assertEquals("second", (provider.load(1) as TextBlock.Paragraph).spans.first().text)
        assertEquals("third", (provider.load(3) as TextBlock.Paragraph).spans.first().text)
    }

    @Test
    fun `scans paragraph offsets for windows line endings`() {
        val file = tempFile("win", "alpha\r\nbeta\r\n\ngamma".toByteArray())
        val offsets = TxtEngine.scanParagraphOffsets(file)
        // "alpha", "beta", "", "gamma" — \r\n must not produce a duplicate boundary
        assertEquals(4, offsets.size)
        val provider = TxtParagraphProvider(file, offsets, Charsets.UTF_8)
        assertEquals("alpha", (provider.load(0) as TextBlock.Paragraph).spans.first().text)
        assertEquals("beta", (provider.load(1) as TextBlock.Paragraph).spans.first().text)
        assertEquals("gamma", (provider.load(3) as TextBlock.Paragraph).spans.first().text)
    }

    @Test
    fun `splits very long lines`() {
        val longLine = "x".repeat(10_000)
        val file = tempFile("long", "$longLine\nend".toByteArray())
        val offsets = TxtEngine.scanParagraphOffsets(file)
        assertTrue(offsets.size >= 3)
        // the final paragraph is still reachable
        val provider = TxtParagraphProvider(file, offsets, Charsets.UTF_8)
        assertEquals("end", (provider.load(offsets.size - 1) as TextBlock.Paragraph).spans.first().text)
    }

    @Test
    fun `parses a full document lazily`() {
        val body = buildString {
            for (i in 1..500) append("Paragraph number $i with some prose.\n")
        }
        val file = tempFile("book", body.toByteArray())
        val document = TxtEngine.parse(file)
        assertEquals(1, document.chapters.size)
        assertEquals(500, document.chapters[0].blockCount)
        assertTrue((document.wordCount ?: 0L) > 0)
        val last = document.chapters[0].blockAt(499) as TextBlock.Paragraph
        assertEquals("Paragraph number 500 with some prose.", last.spans.first().text)
        document.close()
    }

    @Test
    fun `detects utf8 and utf16 byte order marks`() {
        assertEquals(Charsets.UTF_8, CharsetDetector.detect(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "hi".toByteArray()))
        assertEquals(Charsets.UTF_16LE, CharsetDetector.detect(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x68, 0x00)))
        assertEquals(Charsets.UTF_16BE, CharsetDetector.detect(byteArrayOf(0xFE.toByte(), 0xFF.toByte())))
        assertEquals(Charsets.UTF_8, CharsetDetector.detect("plain ascii".toByteArray()))
    }

    @Test
    fun `detects gbk text that is not valid utf8`() {
        // "测试，中文" encoded in GBK — not valid UTF-8.
        val gbk = Charset.forName("GBK")
        val bytes = "测试，中文这是一段需要检测编码的文字".toByteArray(gbk)
        val detected = CharsetDetector.detect(bytes)
        assertEquals(gbk, detected)
        assertEquals("测试，中文这是一段需要检测编码的文字", String(bytes, detected))
    }

    @Test
    fun `falls back to windows-1252 for latin high bytes`() {
        val bytes = byteArrayOf('C'.code.toByte(), 0xE9.toByte(), 't'.code.toByte(), 0xE9.toByte()) // "Cété"
        val detected = CharsetDetector.detect(bytes)
        assertEquals(Charset.forName("windows-1252"), detected)
        assertEquals("Cété", String(bytes, detected))
    }
}
