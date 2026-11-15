package com.readr.app.domain.model

import java.io.File

/**
 * A document in the user's library.
 */
data class ReadrDocument(
    val id: Long,
    val title: String,
    val author: String?,
    val format: DocumentFormat,
    val filePath: String,
    val coverPath: String?,
    val sizeBytes: Long,
    val pageCount: Int?,
    val wordCount: Long?,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    /** Reading progress in 0..1, computed from pages or paragraph blocks. */
    val progress: Float,
    val favorite: Boolean,
    val position: ReadingPosition,
) {
    val fileExists: Boolean get() = File(filePath).exists()

    /** Short human description of the document length, e.g. "248 pages". */
    val lengthLabel: String?
        get() = when (format) {
            DocumentFormat.PDF -> pageCount?.let { "$it pages" }
            else -> wordCount?.let { "≈ ${String.format("%,d", it)} words" }
        }
}
