package com.readr.app.domain.model

/**
 * Document formats supported by READR from the first release.
 */
enum class DocumentFormat(val label: String, val extensions: List<String>, val mimeTypes: List<String>) {
    PDF("PDF", listOf("pdf"), listOf("application/pdf")),
    EPUB("EPUB", listOf("epub"), listOf("application/epub+zip")),
    DOCX("DOCX", listOf("docx"), listOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
    TXT("TXT", listOf("txt"), listOf("text/plain"));

    companion object {
        fun fromFileName(name: String): DocumentFormat? {
            val ext = name.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { ext in it.extensions }
        }

        fun fromMimeType(mime: String?): DocumentFormat? {
            if (mime == null) return null
            return entries.firstOrNull { mime in it.mimeTypes }
        }
    }
}
