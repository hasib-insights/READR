package com.readr.app.engine.txt

import com.readr.app.domain.model.StyledSpan
import com.readr.app.domain.model.TextBlock
import com.readr.app.domain.model.TextChapter
import com.readr.app.domain.model.TextDocument
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.LinkedHashMap

/**
 * TXT engine. Large files are never loaded whole: the parser scans the file
 * once to build a byte-offset index of paragraph boundaries, then the reader
 * loads paragraphs on demand straight from disk through [TxtParagraphProvider]
 * (with a small read cache).
 */
object TxtEngine {

    internal const val MAX_PARA_BYTES = 4096
    private const val SCAN_BUFFER = 1 shl 16

    fun parse(file: File): TextDocument {
        val charset = CharsetDetector.detect(file)
        val offsets = scanParagraphOffsets(file)
        if (offsets.isEmpty()) offsets.add(0L)

        val provider = TxtParagraphProvider(file, offsets, charset)
        val chapter = TextChapter("text", file.nameWithoutExtension.ifBlank { "Text" }, offsets.size) {
            provider.load(it)
        }
        val avgCharBytes = when {
            charset.name().startsWith("UTF-16") -> 2.2
            charset.name().equals("UTF-8", true) -> 5.8
            else -> 1.1
        }
        return TextDocument(
            title = file.nameWithoutExtension.ifBlank { "Text" },
            author = null,
            chapters = listOf(chapter),
            wordCount = (file.length() / avgCharBytes / 5.5).toLong().coerceAtLeast(1),
            closeCallback = { provider.close() },
        )
    }

    /** One-pass scan producing the byte offset of every paragraph (line). */
    internal fun scanParagraphOffsets(file: File): MutableList<Long> {
        val offsets = ArrayList<Long>()
        val raf = RandomAccessFile(file, "r")
        try {
            val total = file.length()
            if (total == 0L) return offsets
            val buf = ByteArray(SCAN_BUFFER)
            var pos = 0L
            var paraStart = 0L
            var paraLen = 0L
            var prevWasCr = false
            while (pos < total) {
                val toRead = minOf(SCAN_BUFFER.toLong(), total - pos).toInt()
                raf.seek(pos)
                raf.readFully(buf, 0, toRead)
                for (i in 0 until toRead) {
                    val b = buf[i]
                    val abs = pos + i
                    when {
                        b == '\n'.code.toByte() -> {
                            if (!prevWasCr) offsets.add(paraStart)
                            paraStart = abs + 1
                            paraLen = 0
                            prevWasCr = false
                        }
                        b == '\r'.code.toByte() -> {
                            offsets.add(paraStart)
                            paraStart = abs + 1
                            paraLen = 0
                            prevWasCr = true
                        }
                        else -> {
                            prevWasCr = false
                            paraLen++
                            if (paraLen >= MAX_PARA_BYTES) {
                                offsets.add(paraStart)
                                paraStart = abs + 1
                                paraLen = 0
                            }
                        }
                    }
                }
                pos += toRead
            }
            if (paraStart < total) offsets.add(paraStart)
            return offsets
        } finally {
            runCatching { raf.close() }
        }
    }
}

/**
 * Reads paragraphs directly from the file on demand. Shared across the UI
 * thread; the underlying RandomAccessFile is synchronized.
 */
class TxtParagraphProvider(
    private val file: File,
    private val offsets: List<Long>,
    private val charset: Charset,
) {
    private val raf = RandomAccessFile(file, "r")
    private val cache = object : LinkedHashMap<Int, TextBlock>(64, 0.8f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, TextBlock>): Boolean =
            size > 512
    }

    @Synchronized
    fun load(index: Int): TextBlock {
        val cached = cache[index]
        if (cached != null) return cached
        val i = index.coerceIn(0, offsets.size - 1)
        val start = offsets[i]
        val end = if (i + 1 < offsets.size) offsets[i + 1] else file.length()
        val length = (end - start).toLong().coerceIn(1, TxtEngine.MAX_PARA_BYTES * 4L).toInt()
        val buf = ByteArray(length)
        val read = try {
            raf.seek(start)
            raf.read(buf, 0, length).coerceAtLeast(0)
        } catch (e: Exception) {
            0
        }
        val text = String(buf, 0, read, charset)
            .trimEnd('\n', '\r')
            .trim()
        val block = TextBlock.Paragraph(listOf(StyledSpan(text)))
        cache[i] = block
        return block
    }

    fun close() {
        runCatching { raf.close() }
    }
}
