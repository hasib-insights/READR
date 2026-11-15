package com.readr.app.util

import com.readr.app.domain.model.DocumentFormat

/** Pure-JVM helpers shared by the importer, engines and UI. */
object FileUtils {

    /**
     * Detects the document format from file content (magic bytes) and name.
     * Zip-based formats (EPUB/DOCX) are both "PK"; the caller inspects the
     * archive entries when the file name does not disambiguate.
     */
    fun sniffFormat(header: ByteArray, fileName: String?): DocumentFormat? {
        val byName = fileName?.let { DocumentFormat.fromFileName(it) }
        if (header.size >= 4 && header[0] == '%'.code.toByte() &&
            header[1] == 'P'.code.toByte() && header[2] == 'D'.code.toByte() &&
            header[3] == 'F'.code.toByte()
        ) return DocumentFormat.PDF

        if (header.size >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()) {
            if (byName == DocumentFormat.EPUB || byName == DocumentFormat.DOCX) return byName
            return null // zip archive: caller must inspect entries
        }

        if (byName != null) return byName

        // Text heuristic: no NUL bytes in the sampled header.
        val sample = header.take(512)
        val hasNul = sample.any { it == 0.toByte() }
        if (!hasNul) return DocumentFormat.TXT
        return null
    }

    /** Inspects zip-based formats by their characteristic entries. */
    fun sniffZipFormat(entryNames: List<String>, fileName: String?): DocumentFormat? {
        if (entryNames.any { it == "word/document.xml" }) return DocumentFormat.DOCX
        if (entryNames.any { it == "META-INF/container.xml" } || entryNames.any { it == "mimetype" }) {
            return DocumentFormat.EPUB
        }
        return fileName?.let { DocumentFormat.fromFileName(it) }
    }

    fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format("%.1f MB", mb)
    }

    /** Recursively sums the size of a directory (or single file). */
    fun directorySize(dir: java.io.File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Collapses runs of whitespace, keeping single spaces. */
    fun collapseWhitespace(text: String): String =
        text.replace(Regex("\\s+"), " ").trim()
}
